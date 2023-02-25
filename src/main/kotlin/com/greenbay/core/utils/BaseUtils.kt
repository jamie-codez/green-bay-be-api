package com.greenbay.core.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.Claim
import com.greenbay.core.Collections
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.JWTAuthHandler
import java.util.*

class BaseUtils {
    companion object {
        private val logger = LoggerFactory.getLogger(BaseUtils::class.java)
        private const val MAX_BODY_SIZE = 5_000
        private val dbUtil = DatabaseUtils(Vertx.vertx())
        fun getResponse(code: Int, message: String): String =
            JsonObject.of("code", code, "message", message).encodePrettily()

        fun getResponse(code: Int, message: String, payload: JsonObject): String =
            JsonObject.of("code", code, "message", message, "payload", payload).encodePrettily()

        fun getResponse(code: Int, message: String, payload: JsonArray): String =
            JsonObject.of("code", code, "message", message, "payload", payload).encodePrettily()

        /**
         * Generates an access token with a 1 week expiry
         */
        fun generateAccessJwt(email: String, roles: Array<String>): String {
            return JWT.create().withSubject(email).withIssuer(System.getenv("ISSUER"))
                .withExpiresAt(Date(System.currentTimeMillis() + (60 * 60 * 24 * 7 * 1000L)))
                .withAudience(System.getenv("AUDIENCE")).withIssuedAt(Date(System.currentTimeMillis()))
                .sign(Algorithm.HMAC256(System.getenv("JWT_SECRET")))
        }

        /**
         * Generates a refresh token a week expiry
         */
        fun generateRefreshJwt(email: String, roles: Array<String>): String {
            return JWT.create().withSubject(email).withIssuer(System.getenv("ISSUER"))
                .withExpiresAt(Date(System.currentTimeMillis() + (60 * 60 * 24 * 30 * 1000L)))
                .withAudience(System.getenv("AUDIENCE")).withIssuedAt(Date(System.currentTimeMillis()))
                .sign(Algorithm.HMAC256(System.getenv("JWT_SECRET")))
        }

        /**
         * Code Injected before any request
         */
        fun execute(
            task: String,
            rc: RoutingContext,
            role:String,
            inject: (accessToken: String, body: JsonObject,response:HttpServerResponse) -> Unit,
            vararg values: String
        ) {
            logger.info("execute($task) --> ")
            val body = rc.body().asJsonObject()
            val accessToken = rc.request().getHeader("access-token")
            val response = rc.response().apply {
                statusCode = OK.code()
                statusMessage = OK.reasonPhrase()
            }.putHeader("content-type","application/json")
            if (accessToken.isNullOrEmpty()){
                response.end(getResponse(UNAUTHORIZED.code(),"Access token missing"))
                return
            }
            bodyHandler(task, accessToken, role, body, response)
        }

        private fun bodyHandler(task: String,accessToken: String,role: String,body: JsonObject,response: HttpServerResponse){
            if (body.encode().length/1024> MAX_BODY_SIZE){
                response.end(getResponse(REQUEST_ENTITY_TOO_LARGE.code(),"Request body is too large. [${body.encode().length/1024} mbs]"))
                return
            }

        }

        /**
         * checks if the body has the required fields
         */
        fun hasValues(body: JsonObject, vararg values: String): Boolean {
            if (values.isEmpty()) {
                return true
            }
            if (body.isEmpty) {
                return false
            }
            var isKey = true
            values.forEach {
                isKey = isKey && body.containsKey(it)
            }
            return isKey
        }

        /**
         * Verifies that the users is authenticated and authorized
         */
        fun verifyToken(
            task: String,
            jwt: String,
            inject: () -> Unit,
            role: String,
            response: HttpServerResponse
        ) {
            logger.info("verifyAccess($task) -->")
            val decodedJwt = JWT.decode(jwt)
            val verifier = JWT.require(Algorithm.HMAC256(System.getenv("JWT_SECRET")))
                .withAudience(System.getenv("AUDIENCE"))
                .withIssuer(System.getenv("ISSUER")).build()
            val decodedToken = verifier.verify(decodedJwt)
            val subject = decodedToken.subject
            val expiresAt = decodedToken.expiresAt
            val issuer = decodedToken.issuer
            val res = response.apply {
                statusCode = OK.code()
                statusMessage = OK.reasonPhrase()
            }.putHeader("content-type", "application/json")
            if (Date(System.currentTimeMillis()) > expiresAt) {
                res.end(getResponse(UNAUTHORIZED.code(), "Token expired"))
                return
            }
            if (issuer != System.getenv("ISSUER")) {
                res.end(getResponse(BAD_REQUEST.code(), "Seems you are lost"))
                return
            }
            if (subject.isNullOrEmpty()) {
                res.end(getResponse(BAD_REQUEST.code(), "Invalid JWT"))
                return
            }
            getUser(subject, {
                if (hasRole(it.getJsonArray("roles"), role)) {
                    inject()
                } else {
                    response.end(getResponse(UNAUTHORIZED.code(), "Not enough permissions"))
                }
            }, res)
        }

        /**
         * Get the use from the database for purposes of verification and validation
         */
        private fun getUser(
            email: String,
            inject: (user: JsonObject) -> Unit,
            response: HttpServerResponse
        ) {
            dbUtil.findOne(Collections.ADMINS.toString(), JsonObject.of("email", email), {
                if (it.isEmpty) {
                    response.end(getResponse(NOT_FOUND.code(), "User does not exist"))
                    return@findOne
                }
               inject(it)
            }, {
                logger.error("getUser() --> ${it.cause}")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        }

        /**
         * Checks to conform that user has the role required to access the route in question
         */
        private fun hasRole(roles: JsonArray, role: String): Boolean {
            var isRole = true
            for (i in 0 until roles.size()) {
                isRole = isRole && roles.getJsonObject(i).getBoolean(role) == true
            }
            return isRole
        }

    }
}