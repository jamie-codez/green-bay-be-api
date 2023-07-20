package com.greenbay.core.service

import com.greenbay.core.Collections
import com.greenbay.core.utils.BaseUtils
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.netty.util.internal.StringUtil
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.util.*
import kotlin.random.Random

open class UserService : BaseUtils() {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)

    fun setUserRoutes(router: Router) {
        router.post("/users").handler(::createUser)
        router.post("/users/admin").handler(::createAdmin)
        router.get("/users/:pageNumber").handler(::getUsers)
        router.get("/user/activate/:email/:code").handler(::activateEmail)
        router.get("/users/search/:term/:pageNumber").handler(::searchUser)
        router.put("/users/:id").handler(::updateUser)
        router.delete("/users/:id").handler(::deleteUser)
    }

    private fun createUser(rc: RoutingContext) {
        logger.info("createUser() -->")
        execute("createUser", rc, "admin", { user, body, response ->
            findOne(Collections.APP_USERS.toString(), JsonObject.of("email", body.getString("email")), {
                if (!it.isEmpty) {
                    response.end(getResponse(CONFLICT.code(), "User already exists"))
                } else {
                    body.put("addedBy", user.getString("email"))
                        .put("verified", false)
                        .put("roles", JsonObject.of("user", true))
                        .put("addedOn", System.currentTimeMillis())
                    val encodedPassword = BCryptPasswordEncoder().encode(body.getString("password"))
                    body.remove("password")
                    body.put("password", encodedPassword)
                    save(Collections.APP_USERS.toString(), body, {
                        val code = UUID.randomUUID().toString()
                        sendConfirmationEmail(body.getString("email"), code, {
                            response.end(getResponse(CREATED.code(), "User created successfully, now attach roles"))
                        }, {
                            response.end(
                                getResponse(
                                    INTERNAL_SERVER_ERROR.code(),
                                    "Error occurred sending activation mail"
                                )
                            )
                        })
                    }, { error ->
                        logger.error("createUser(${error.message}) <--", error)
                        response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                    })
                }
            }, {
                logger.error("createUser(${it.message}) <--", it)
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        }, "username", "email", "phone", "idNumber", "password", "profileImage")
        logger.info("createUser() <--")
    }

    private fun sendConfirmationEmail(
        email: String,
        code: String,
        success: () -> Unit,
        fail: (throwable: Throwable) -> Unit
    ) {
        logger.info("sendVerificationEmail() -->")
        val verificationCode = JsonObject.of("owner", email, "code", code)
        val htmlText = "${System.getenv("GB_HOST_URL")}/user/activate/$email/$code"
        val link = "Click <a href=\"$htmlText\">Here</a> to activate your account"
        save(Collections.ACTIVATION_CODES.toString(), verificationCode, {
            sendEmail(
                email,
                "Account Activation",
                "Click on the link below to activate your account",
                htmlText = link,
                null,
                {
                    logger.info("sendVerificationEmail(Mail sent) <--")
                    success()
                },
                {
                    logger.error("sendVerificationEmail(${it.message} -> Sending the email) <--", it)
                    fail(it)
                })
        }, {
            logger.error("sendVerificationEmail(${it.message}) <--", it)
            fail(it)
        })
        logger.info("sendVerificationEmail() <--")
    }

    private fun activateEmail(rc: RoutingContext) {
        logger.info("activateEmail() -->")
        val email = rc.request().getParam("email") ?: ""
        val code = rc.request().getParam("code") ?: ""
        val response = rc.response().apply {
            statusCode = OK.code()
            statusMessage = OK.reasonPhrase()
        }.putHeader("content-type", "application/json")
        if (email.isEmpty() || code.isEmpty()) {
            response.end(getResponse(BAD_REQUEST.code(), "Code or email cannot be empty"))
            return
        }
        val verificationCode = JsonObject.of("owner", email, "code", code)
        findOne(Collections.ACTIVATION_CODES.toString(), verificationCode, {
            if (it.isEmpty) {
                response.end(getResponse(NOT_FOUND.code(), "Activation code already used"))
                return@findOne
            }
            findAndUpdate(
                Collections.APP_USERS.toString(),
                JsonObject.of("email", email),
                JsonObject.of("\$set", JsonObject.of("verified", true)), { user ->
                    if (user.isEmpty) {
                        response.end(getResponse(NOT_FOUND.code(), "User does not exist"))
                        return@findAndUpdate
                    }
                    response.end(getResponse(OK.code(), "User verified successfully"))
                }, { error ->
                    logger.error("activateEmail(${error.message}) <--", error)
                }
            )
        }, {
            response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Unexpected error occurred try again"))
        })
        logger.info("activateEmail() <--")
    }

    private fun createAdmin(rc: RoutingContext) {
        logger.info("createAdmin() -->")
        execute("createAdmin", rc, { body, response ->
            findOne(Collections.APP_USERS.toString(), JsonObject.of("email", body.getString("email")), {
                if (!it.isEmpty) {
                    response.end(getResponse(CONFLICT.code(), "User already exists"))
                } else {
                    body.put("addedBy", body.getString("email"))
                        .put("roles", JsonObject.of("user", true, "admin", true, "manager", true))
                        .put("addedOn", System.currentTimeMillis())
                        .put("verified", true)
                    val encodedPassword = BCryptPasswordEncoder().encode(body.getString("password"))
                    body.remove("password")
                    body.put("password", encodedPassword)
                    save(Collections.APP_USERS.toString(), body, {
                        response.end(getResponse(CREATED.code(), "User created successfully, now attach roles"))
                    }, { error ->
                        logger.error("createAdmin(${error.message}) <--", error)
                        response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                    })
                }
            }, {
                logger.error("createAdmin(${it.message}) <--", it)
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        }, "username", "firstName", "lastName", "email", "phone", "idNumber", "password", "profileImage")
        logger.info("createAdmin() <--")
    }

    private fun getUsers(rc: RoutingContext) {
        logger.info("getUsers() -->")
        execute("getUsers", rc, "admin", { _, _, response ->
            val pageNumber = Integer.valueOf(rc.request().getParam("pageNumber")) - 1
            val limit = 20
            val skip = pageNumber * limit
            val pipeline = JsonArray()
                .add(JsonObject.of("\$skip", skip))
                .add(JsonObject.of("\$limit", limit))
                .add(JsonObject.of("\$sort", JsonObject.of("_id", 1)))
                .add(
                    JsonObject.of(
                        "\$project",
                        JsonObject.of(
                            "username", 1,
                            "firstName", 1,
                            "lastName", 1,
                            "email", 1,
                            "phone", 1,
                            "idNumber", 1,
                            "profileImage", 1,
                            "verified", 1,
                            "addedBy", 1,
                            "verified", 1
                        )
                    )
                )
            aggregate(Collections.APP_USERS.toString(), pipeline, {
                val paging = JsonObject.of("page", pageNumber, "sorted", true)
                response.end(getResponse(OK.code(), "Success", JsonObject.of("data", it, "pagination", paging)))
            }, {
                logger.error("getUsers(${it.cause}) <--", it)
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        })
        logger.info("getUsers() <--")
    }

    private fun searchUser(rc: RoutingContext) {
        logger.info("searchUser() -->")
        execute("searchUser", rc, "admin", { _, _, response ->
            val term = rc.request().getParam("term") ?: ""
            val pageNumber = Integer.valueOf(rc.request().getParam("pageNumber")) - 1
            val limit = 20
            val skip = limit * pageNumber
            if (term.isEmpty()) {
                response.end(getResponse(BAD_REQUEST.code(), "Expected param term"))
                return@execute
            }
            val query = JsonObject.of("\$text", JsonObject.of("\$search", term))
            val pipeline = JsonArray()
                .add(JsonObject.of("\$match", query))
                .add(JsonObject.of("\$limit", limit))
                .add(JsonObject.of("\$skip", skip))
            createIndex(
                Collections.APP_USERS.toString(),
                JsonObject.of("firstName", 1, "lastName", 1, "username", 1, "email", 1, "phone", 1),
                {
                    aggregate(Collections.APP_USERS.toString(), pipeline, {
                        val paging = JsonObject.of("page", pageNumber, "sorted", false)
                        response.end(getResponse(OK.code(), "Success", JsonObject.of("data", it, "pagination", paging)))
                    }, {
                        logger.error("searchUser(${it.message} -> pipeline) <--", it)
                        response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                    })
                },
                {
                    logger.error("searchUser(${it.message} -> creating Index) <--", it)
                    response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                })
        })
        logger.info("searchUser() <--")
    }

    private fun updateUser(rc: RoutingContext) {
        logger.info("updateUser() -->")
        execute("updateUser", rc, "user", { user, body, response ->
            val id = rc.request().getParam("id")
            if (hasValues(body, "roles") && !hasRole(user.getJsonObject("roles"), "admin")) {
                response.end(getResponse(UNAUTHORIZED.code(), "You don't have permission for this task"))
            }
            findAndUpdate(Collections.APP_USERS.toString(), JsonObject.of("_id", id), body, {
                response.end(getResponse(OK.code(), "Successfully updated user", it))
            }, {
                logger.error("updateUser(${it.message}) <--", it)
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        })
        logger.info("updateUser() <--")
    }

    private fun deleteUser(rc: RoutingContext) {
        logger.info("deleteUser() -->")
        execute("deleteUser", rc, "user", { _, _, response ->
            val id = rc.request().getParam("id")
            if (id.isNullOrEmpty()) {
                response.end(getResponse(BAD_REQUEST.code(), "Expected param email"))
                return@execute
            }
            val query = JsonObject.of("_id", id)
            findOne(Collections.APP_USERS.toString(), query, {
                if (it.isEmpty) {
                    response.end(getResponse(NOT_FOUND.code(), "User not found"))
                    return@findOne
                }
                findOneAndDelete(Collections.APP_USERS.toString(), query, {
                    response.end(getResponse(OK.code(), "User deleted successfully"))
                }, { error ->
                    logger.error("deleteUser(${error.message}) <--", error)
                    response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                })
            }, {
                logger.error("deleteUser(${it.message}) <--", it)
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error has occurred"))
            })
        })
    }
}