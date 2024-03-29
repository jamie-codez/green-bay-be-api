package com.greenbay.core.service

import com.greenbay.core.Collections
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.awt.SystemTray
import java.util.*

@Suppress("LABEL_NAME_CLASH")
open class AuthService : TaskService() {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)

    fun setAuthRoutes(router: Router) {
        router.post("/login").handler(::login)
        router.post("/logout").handler(::logout)
        router.post("/sendPasswordResetEmail").handler(::sendPasswordResetEmail)
        router.get("/reset/:email").handler(::sendPasswordPage)
        router.post("/reset/:email").handler(::resetPassword)
        setTaskRoutes(router)
    }

    private fun login(rc: RoutingContext) {
        logger.info("login() -->")
        execute("login", rc, { body, response ->
            val query = JsonObject.of("email", body.getString("email"))
            findOne(Collections.APP_USERS.toString(), query, {
                if (it.isEmpty) {
                    response.end(getResponse(NOT_FOUND.code(), "User does not exists"))
                    return@findOne
                }
                val matches = BCryptPasswordEncoder().matches(body.getString("password"), it.getString("password"))
                if (matches) {
                    val jwt = generateAccessJwt(it.getString("email"))
                    val refresh = generateRefreshJwt(it.getString("email"))
                    val session = JsonObject.of("email", it.getString("email"), "refreshToken", refresh)
                    findOne(Collections.SESSIONS.toString(), JsonObject.of("email", it.getString("email")), { req->
                        if (req.isEmpty) {
                            save(Collections.SESSIONS.toString(), session, {
                                response
                                    .putHeader("access-token", jwt)
                                    .putHeader("refresh-token", refresh)
                                response.end(
                                    getResponse(
                                        OK.code(),
                                        "Login successful",
                                        JsonObject.of("accessToken", jwt, "refreshToken", refresh)
                                    )
                                )
                            }, { thr ->
                                logger.error("login(${thr.cause}) <--")
                                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                            })
                        }else{
                            findAndUpdate(Collections.SESSIONS.toString(),JsonObject.of("email",it.getString("email")),
                                JsonObject.of("\$set",JsonObject.of("refreshToken",refresh)),{
                                    response
                                        .putHeader("access-token", jwt)
                                        .putHeader("refresh-token", refresh)
                                    response.end(
                                        getResponse(
                                            OK.code(),
                                            "Login successful",
                                            JsonObject.of("accessToken", jwt, "refreshToken", refresh)
                                        )
                                    )
                                },{tr->
                                    logger.error("login(${tr.cause}) <--")
                                    response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                                }
                            )
                        }
                    }, { err ->
                        logger.info("login(${err.message} -> Creating session) <--", err)
                        response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                    })
                }
            }, {
                logger.error("login(${it.cause}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        }, "email", "password")
        logger.info("login() <---")
    }

    private fun logout(rc: RoutingContext) {
        logger.info("logout() -->")
        execute("logout", rc, "user", { user, body, response ->
            val query = JsonObject.of("email", body)
            findOne(Collections.SESSIONS.toString(), query, {
                if (it.getString("email") != user.getString("email")) {
                    response.end(getResponse(BAD_REQUEST.code(), "Operation not allowed"))
                    return@findOne
                }
                findOneAndDelete(Collections.SESSIONS.toString(), query, {
                    response.end(getResponse(OK.code(), "Logout successful"))
                }, { thr ->
                    logger.error("logout(${thr.cause}) <--")
                    response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                })
            }, {
                logger.error("logout(${it.cause}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        }, "email")
        logger.info("logout() <--")
    }

    private fun sendPasswordResetEmail(rc: RoutingContext) {
        logger.info("sendPasswordResetEmail() -->")
        execute("sendPasswordResetEmail", rc, { body, response ->
            val query = JsonObject.of("email", body.getString("email"))
            findOne(Collections.APP_USERS.toString(), query, {
                if (it.isEmpty) {
                    response.end(getResponse(NOT_FOUND.code(), "User not found"))
                    return@findOne
                }
                val email = it.getString("email")
                val code = UUID.randomUUID().toString()
                val resetCode = JsonObject.of("email", email, "code", code)
                save(Collections.RESET_CODES.toString(), resetCode, {
                    val scheme = rc.request().scheme()
                    val address = rc.request().localAddress().hostAddress()
                    val port = rc.request().localAddress().port()
                    val htmlText =
                        if (System.getenv("GB_ENVIRONMENT") == "development" || System.getenv("GB_ENVIRONMENT")
                                .isNullOrEmpty()
                        ) {
                            "$scheme://$address:$port/code/$email/$code"
                        } else {
                            "${System.getenv("GB_HOST_URL")}/code/$email/$code"
                        }
                    val htmlString = String.format("<a href=%s\">click Here</a>", htmlText)
                    val mailBody = "Click link to reset password."
                    sendEmail(email, "Password Reset", mailBody, htmlString, success = {
                        logger.info("sendPasswordResetEmail(Mail sent) <--")
                        response.end(getResponse(OK.code(), "Password reset email sent to you mail inbox"))
                    }, fail = { err ->
                        logger.error("sendPasswordResetEmail(${err.message}) <--", err)
                    })
                }, { thr ->
                    logger.info("sendPasswordResetEmail(${thr.cause}) <--")
                    response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                })
            }, {
                logger.info("sendPasswordResetEmail(${it.cause}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        }, "email")
        logger.info("sendPasswordResetEmail() <--")
    }

    private fun sendPasswordPage(rc: RoutingContext) {
        rc.response().sendFile("src/main/resources/passwordReset.html")
    }

    private fun resetPassword(rc: RoutingContext) {
        logger.info("resetPassword() -->")
        execute("resetPassword", rc, { body, response ->
            val query = JsonObject.of("email", body.getString("email"))
            findOne(Collections.RESET_CODES.toString(), query, { res ->
                if (res.isEmpty) {
                    response.end(getResponse(OK.code(), "Reset link has already been used"))
                    return@findOne
                }
                findOne(Collections.APP_USERS.toString(), query, {
                    if (it.isEmpty) {
                        response.end(getResponse(NOT_FOUND.code(), "User not found"))
                        return@findOne
                    }
                    val encodedPassword = BCryptPasswordEncoder().encode(body.getString("password"))
                    val update = JsonObject.of("\$set", JsonObject.of("password", encodedPassword))
                    findAndUpdate(Collections.APP_USERS.toString(), query, update, {
                        findOneAndDelete(Collections.RESET_CODES.toString(), query, { re ->
                            logger.info("resetPassword(${re} delete successful) <--")
                        }, { err ->
                            logger.error("resetPassword(${err.cause} delete code) <--")
                        })
                        response.end(getResponse(OK.code(), "Password updated successfully"))
                    }, { thr ->
                        logger.error("resetPassword(${thr.cause}) <--")
                        response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                    })
                }, {
                    logger.error("resetPassword(${it.cause}) <--")
                    response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                })
            }, {
                logger.error("resetPassword(${it.cause}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        }, "email", "password")
        logger.info("resetPassword() <--")
    }

}