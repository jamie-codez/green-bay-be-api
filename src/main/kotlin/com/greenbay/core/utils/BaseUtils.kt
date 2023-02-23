package com.greenbay.core.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.handler.JWTAuthHandler
import java.util.*

class BaseUtils {
    companion object {
        fun getResponse(code: Int, message: String): String =
            JsonObject.of("code", code, "message", message).encodePrettily()

        fun getResponse(code: Int, message: String, payload: JsonObject): String =
            JsonObject.of("code", code, "message", message, "payload", payload).encodePrettily()

        fun getResponse(code: Int, message: String, payload: JsonArray): String =
            JsonObject.of("code", code, "message", message, "payload", payload).encodePrettily()

        fun generateAccessJwt(email: String, roles: Array<String>): String {
            return JWT.create().withSubject(email).withIssuer(System.getenv("ISSUER"))
                .withArrayClaim("roles", roles)
                .withExpiresAt(Date(System.currentTimeMillis() + (60 * 60 * 24 * 7 * 1000L)))
                .withAudience(System.getenv("AUDIENCE")).withIssuedAt(Date(System.currentTimeMillis()))
                .sign(Algorithm.HMAC256(System.getenv("JWT_SECRET")))
        }

        fun generateRefreshJwt(email: String, roles: Array<String>): String {
            return JWT.create().withSubject(email).withIssuer(System.getenv("ISSUER"))
                .withArrayClaim("roles", roles)
                .withExpiresAt(Date(System.currentTimeMillis() + (60 * 60 * 24 * 30 * 1000L)))
                .withAudience(System.getenv("AUDIENCE")).withIssuedAt(Date(System.currentTimeMillis()))
                .sign(Algorithm.HMAC256(System.getenv("JWT_SECRET")))
        }

        fun verifyJwt(jwt: String): Boolean {
            val decodedJwt = JWT.decode(jwt)
            val verifier =
                JWT.require(Algorithm.HMAC256(System.getenv("JWT_SECRET"))).withAudience(System.getenv("AUDIENCE"))
                    .withIssuer(System.getenv("ISSUER")).build()
            val decodedToken = verifier.verify(decodedJwt)
            val payload = decodedJwt.payload.
        }
    }
}