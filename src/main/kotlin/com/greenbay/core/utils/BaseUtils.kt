package com.greenbay.core.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.greenbay.core.Collections
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.mail.*
import io.vertx.ext.web.RoutingContext
import java.util.*

open class BaseUtils {
    companion object {
        private val logger = LoggerFactory.getLogger(BaseUtils::class.java)
        const val MAX_BODY_SIZE = 5_000
        private val dbUtil = DatabaseUtils(Vertx.vertx())
        fun getResponse(code: Int, message: String): String =
            JsonObject.of("code", code, "message", message).encodePrettily()

        fun getResponse(code: Int, message: String, payload: JsonObject): String =
            JsonObject.of("code", code, "message", message, "payload", payload).encodePrettily()

        fun getResponse(code: Int, message: String, payload: JsonArray): String =
            JsonObject.of("code", code, "message", message, "payload", payload).encodePrettily()

        /**
         * Generates an access token with a 1-week expiry
         */
        fun generateAccessJwt(email: String): String {
            return JWT.create().withSubject(email).withIssuer(System.getenv("ISSUER"))
                .withExpiresAt(Date(System.currentTimeMillis() + (60 * 60 * 24 * 7 * 1000L)))
                .withAudience(System.getenv("AUDIENCE")).withIssuedAt(Date(System.currentTimeMillis()))
                .sign(Algorithm.HMAC256(System.getenv("JWT_SECRET")))
        }

        /**
         * Generates a refresh token a week expiry
         */
        fun generateRefreshJwt(email: String): String {
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
            role: String,
            inject: (user: JsonObject, body: JsonObject, response: HttpServerResponse) -> Unit,
            vararg values: String
        ) {
            logger.info("execute($task) --> ")
            val accessToken = rc.request().getHeader("access-token")
            val response = rc.response().apply {
                statusCode = OK.code()
                statusMessage = OK.reasonPhrase()
            }.putHeader("content-type", "application/json")
            if (accessToken.isNullOrEmpty()) {
                response.end(getResponse(UNAUTHORIZED.code(), "Access token missing"))
                return
            }
            verifyToken(task, accessToken, { user ->
                bodyHandler(task, rc, user, response, inject, *values)
            }, role, response)
            logger.info("execute($task) <--")
        }

        private fun bodyHandler(
            task: String,
            rc: RoutingContext,
            user: JsonObject,
            response: HttpServerResponse,
            inject: (user: JsonObject, body: JsonObject, response: HttpServerResponse) -> Unit,
            vararg values: String
        ) {
            logger.info("bodyHandler($task) -->")
            val body = rc.body().asJsonObject()
            if (body.encode().length / 1024 > MAX_BODY_SIZE) {
                response.end(
                    getResponse(
                        REQUEST_ENTITY_TOO_LARGE.code(),
                        "Request body is too large. [${body.encode().length / 1024} mbs]"
                    )
                )
                return
            }
            if (!user.getBoolean("verified")) {
                response.end(getResponse(UNAUTHORIZED.code(), "User account not verified"))
                return
            }
            if (hasValues(body, *values)) {
                logger.info("missing field : [${values.contentDeepToString()}")
                response.end(getResponse(BAD_REQUEST.code(), "expected fields [${values.contentDeepToString()}]"))
                return
            }
            inject(user, body, response)
            logger.info("bodyHandler($task) <--")

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
            inject: (user: JsonObject) -> Unit,
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
                if (hasRole(it.getJsonObject("roles"), role)) {
                    inject(it)
                } else {
                    response.end(getResponse(UNAUTHORIZED.code(), "Not enough permissions"))
                }
            }, res)
            logger.info("verifyAccess($task) <--")
        }

        /**
         * Get the use from the database for purposes of verification and validation
         */
        private fun getUser(
            email: String,
            inject: (user: JsonObject) -> Unit,
            response: HttpServerResponse
        ) {
            logger.info("getUser() -->")
            dbUtil.findOne(Collections.APP_USERS.toString(), JsonObject.of("email", email), {
                if (it.isEmpty) {
                    response.end(getResponse(NOT_FOUND.code(), "User does not exist"))
                    return@findOne
                }
                inject(it)
            }, {
                logger.error("getUser() --> ${it.cause}")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
            logger.info("getUser() <--")
        }

        /**
         * Checks to conform that user has the role required to access the route in question
         */
        fun hasRole(roles: JsonObject, role: String): Boolean {
            var isRole = true
            for (i in 0 until roles.size()) {
                isRole = isRole && roles.getBoolean(role) == true
            }
            return isRole
        }

        /**
         * Sends emails
         */
        fun sendEmail(
            email: String,
            subject: String,
            messageText: String,
            htmlText: String? = null,
            attachment: MailAttachment? = null,
            vertx: Vertx
        ) {
            val config = MailConfig()
                .setPort(Integer.valueOf(System.getenv("GB_MAIL_PORT")))
                .setHostname(System.getenv("GB_MAIL_HOST"))
                .setSsl(true)
                .setStarttls(StartTLSOptions.OPTIONAL)
                .setUsername(System.getenv("GB_MAIL_USERNAME"))
                .setPassword(System.getenv("GB_MAIL_PASSWORD"))
                .setLogin(LoginOption.XOAUTH2)
            val htmlString = String.format("<a href=\"http://%s\"> Activate account</a>", htmlText)
            val client = MailClient.createShared(vertx, config, "mailme")
            val message = MailMessage()
                .setFrom("${System.getenv("GB_MAIL_ADDRESS")} (No reply)")
                .setTo(email)
                .setSubject(subject)
                .setText(messageText)
                .setHtml("Click link to reset password.$htmlString")
            client.sendMail(message) {
                if (it.succeeded()) {
                    logger.info("INFO: Mail sent")

                } else if (it.failed()) {
                    logger.error("ERROR: Mail not sent")
                }
            }

        }
    }
}