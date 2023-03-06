package com.greenbay.core.service

import com.greenbay.core.Collections
import com.greenbay.core.utils.BaseUtils.Companion.MAX_BODY_SIZE
import com.greenbay.core.utils.BaseUtils.Companion.execute
import com.greenbay.core.utils.BaseUtils.Companion.generateAccessJwt
import com.greenbay.core.utils.BaseUtils.Companion.generateRefreshJwt
import com.greenbay.core.utils.BaseUtils.Companion.getResponse
import com.greenbay.core.utils.BaseUtils.Companion.verifyToken
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

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
                    response.end(getResponse(BAD_REQUEST.code(),"Operation not allowed"))
                    return@findOne
                }
                dbUtil.findOneAndDelete(Collections.SESSIONS.toString(),query,{
                    response.end(getResponse(OK.code(), "Logout successful"))
                },{thr->
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

    }

    private fun sendPasswordPage(rc: RoutingContext) {

    }

    private fun resetPassword(rc: RoutingContext) {

    }

}