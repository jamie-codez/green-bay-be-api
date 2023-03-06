package com.greenbay.core.service

import com.greenbay.core.Collections
import com.greenbay.core.utils.BaseUtils.Companion.MAX_BODY_SIZE
import com.greenbay.core.utils.BaseUtils.Companion.execute
import com.greenbay.core.utils.BaseUtils.Companion.generateAccessJwt
import com.greenbay.core.utils.BaseUtils.Companion.generateRefreshJwt
import com.greenbay.core.utils.BaseUtils.Companion.getResponse
import com.greenbay.core.utils.BaseUtils.Companion.sendEmail
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.util.*

open class AuthService : TaskService() {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)

    fun setAuthRoutes(router: Router) {
        router.post("/login").handler(::login)
        router.post("/logout").handler(::logout)
        router.post("/sendPasswordRestEmail").handler(::sendPasswordResetEmail)
        router.get("/sendPasswordPage").handler(::sendPasswordPage)
        router.post("/resetEmail").handler(::resetPassword)
        setTenantRoutes(router)
    }

    private fun login(rc: RoutingContext) {
        val body = rc.body().asJsonObject()
        val response = rc.response().apply {
            statusCode = OK.code()
            statusMessage = OK.reasonPhrase()
        }.putHeader("content-type", "application/json")
        val bodySize = body.encode().length / 1024
        if (bodySize > MAX_BODY_SIZE) {
            response.end(getResponse(REQUEST_ENTITY_TOO_LARGE.code(), "Request entity too larger [$bodySize]kb"))
            return
        }
        val query = JsonObject.of("email", body.getJsonObject("email"))

        dbUtil.findOne(Collections.APP_USERS.toString(), query, {
            if (it.isEmpty) {
                response.end(getResponse(NOT_FOUND.code(), "User does not exists"))
                return@findOne
            }
            val matches = BCryptPasswordEncoder().matches(body.getString("password"), it.getString("password"))
            if (matches) {
                val jwt = generateAccessJwt(it.getString("email"))
                val refresh = generateRefreshJwt(it.getString("email"))
                val session = JsonObject.of("email", it.getString("email"), "refreshToken", refresh)
                dbUtil.save(Collections.SESSIONS.toString(), session, {
                    response
                        .putHeader("access-token", jwt)
                        .putHeader("refresh-token", refresh)
                    response.end(getResponse(OK.code(), "Login successful"))
                }, { thr ->
                    logger.error("login(${thr.cause}) <--")
                    response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                })
            }
        }, {
            logger.error("login(${it.cause}) <--")
            response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
        })
        logger.info("login() <---")
    }

    private fun logout(rc: RoutingContext) {
        logger.info("logout() -->")
        execute("logout", rc, "user", { user, body, response ->
            val query = JsonObject.of("email", body)
            dbUtil.findOne(Collections.SESSIONS.toString(), query, {
                if (it.getString("email") != user.getString("email")) {
                    response.end(getResponse(BAD_REQUEST.code(), "Operation not allowed"))
                    return@findOne
                }
                dbUtil.findOneAndDelete(Collections.SESSIONS.toString(), query, {
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
        logger.info("sendPasswordResetPassword() -->")
        val body = rc.body().asJsonObject()
        val bodySize = body.encode().length / 1024
        val response = rc.response().apply {
            statusCode = OK.code()
            statusMessage = OK.reasonPhrase()
        }.putHeader("content-type", "application/json")
        if (bodySize > MAX_BODY_SIZE) {
            response.end(getResponse(REQUEST_ENTITY_TOO_LARGE.code(), "Body too large [$bodySize]kb"))
            return
        }
        val query = JsonObject.of("email", body.getString("email"))
        dbUtil.findOne(Collections.APP_USERS.toString(), query, {
            if (it.isEmpty) {
                response.end(getResponse(NOT_FOUND.code(), "User not found"))
                return@findOne
            }
            val resetCode = JsonObject.of("email", it.getString("email"), "code", UUID.randomUUID().toString())
            dbUtil.save(Collections.RESET_CODES.toString(), resetCode, {
                sendEmail(
                    body.getString("email"),
                    "Password Reset email",
                    "Click on the link to reset password",
                    vertx = this.vertx
                )
                response.end(getResponse(OK.code(), "Password reset email send to your inbox"))
            }, { thr ->
                logger.info("sendPasswordResetEmail(${thr.cause}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        }, {
            logger.info("sendPasswordResetEmail(${it.cause}) <--")
            response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
        })
        logger.info("sendPasswordResetPassword() <--")
    }

    private fun sendPasswordPage(rc: RoutingContext) {
        rc.response().sendFile("src/main/resources/passwordReset.html")
    }

    private fun resetPassword(rc: RoutingContext) {
        logger.info("resetPassword() -->")
        val email = rc.request().getParam("email")
        val password = rc.body().asJsonObject()
        val response = rc.response().apply {
            statusCode = OK.code()
            statusMessage = OK.reasonPhrase()
        }.putHeader("content-type", "application/json")
        val query = JsonObject.of("email", email)
        dbUtil.findOne(Collections.APP_USERS.toString(), query, {
            if (it.isEmpty) {
                response.end(getResponse(NOT_FOUND.code(), "User not found"))
                return@findOne
            }
            val encodedPassword = BCryptPasswordEncoder().encode(password.getString("password"))
            val update = JsonObject.of("\$set", JsonObject.of("password", encodedPassword))
            dbUtil.findAndUpdate(Collections.APP_USERS.toString(), query, update, {
                response.end(getResponse(OK.code(), "Password updated successfully"))
            }, { thr ->
                logger.error("resetPassword(${thr.cause}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        }, {
            logger.error("resetPassword(${it.cause}) <--")
            response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
        })
        logger.info("resetPassword() <--")
    }

}