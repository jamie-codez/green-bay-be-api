package com.greenbay.core.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.Claim
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.handler.JWTAuthHandler
import java.util.*

class BaseUtils {
    companion object {
        private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)
        fun getResponse(code: Int, message: String): String =
            JsonObject.of("code", code, "message", message).encodePrettily()

        fun getResponse(code: Int, message: String, payload: JsonObject): String =
            JsonObject.of("code", code, "message", message, "payload", payload).encodePrettily()

        fun getResponse(code: Int, message: String, payload: JsonArray): String =
            JsonObject.of("code", code, "message", message, "payload", payload).encodePrettily()

        fun generateAccessJwt(email: String, roles: Array<String>): String {
            return JWT.create().withSubject(email).withIssuer(System.getenv("ISSUER"))
                .withExpiresAt(Date(System.currentTimeMillis() + (60 * 60 * 24 * 7 * 1000L)))
                .withAudience(System.getenv("AUDIENCE")).withIssuedAt(Date(System.currentTimeMillis()))
                .sign(Algorithm.HMAC256(System.getenv("JWT_SECRET")))
        }

        fun generateRefreshJwt(email: String, roles: Array<String>): String {
            return JWT.create().withSubject(email).withIssuer(System.getenv("ISSUER"))
                .withExpiresAt(Date(System.currentTimeMillis() + (60 * 60 * 24 * 30 * 1000L)))
                .withAudience(System.getenv("AUDIENCE")).withIssuedAt(Date(System.currentTimeMillis()))
                .sign(Algorithm.HMAC256(System.getenv("JWT_SECRET")))
        }

        fun verifyAccess(
            task: String,
            jwt: String,
            vertx: Vertx,
            inject: (usr:JsonObject) -> Unit,
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
            }.putHeader("content-type","application/json")
            if(Date(System.currentTimeMillis())>expiresAt){
                res.end(getResponse(UNAUTHORIZED.code(),"Token expired"))
                return
            }
            if (issuer!=System.getenv("ISSUER")){
                res.end(getResponse(BAD_REQUEST.code(),"Seems you are lost"))
                return
            }
            if (subject.isNullOrEmpty()){
                res.end(getResponse(BAD_REQUEST.code(),"Invalid JWT"))
                return
            }
            getUser(subject,vertx,inject,res)

        }
        fun getUser(email: String,vertx: Vertx,inject: (user:JsonObject) -> Unit,response: HttpServerResponse){

        }


    }
}