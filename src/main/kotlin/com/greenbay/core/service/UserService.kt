package com.greenbay.core.service

import com.greenbay.core.Collections
import com.greenbay.core.utils.BaseUtils
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.util.*

open class UserService : BaseUtils() {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)

    fun setUserRoutes(router: Router) {
        router.post("/users").handler(::createUser)
        router.post("/users/admin").handler(::createAdmin)
        router.get("/users/:pageNumber").handler(::getUsers)
        router.get("/users/search/:term/:pageNumber").handler(::searchUser)
        router.put("/users/:email").handler(::updateUser)
        router.delete("/users/:email").handler(::deleteUser)
    }

    private fun createUser(rc: RoutingContext) {
        logger.info("createUser() -->")
        execute("createUser", rc, "admin", { user, body, response ->
            findOne(Collections.APP_USERS.toString(), JsonObject.of("email", body.getString("email")), {
                if (!it.isEmpty) {
                    response.end(getResponse(CONFLICT.code(), "User already exists"))
                } else {
                    body.put("addedBy", user.getString("email"))
                        .put("roles", JsonObject.of("user", true))
                        .put("addedOn", Date(System.currentTimeMillis()))
                    val encodedPassword = BCryptPasswordEncoder().encode(body.getString("password"))
                    body.remove("password")
                    body.put("password", encodedPassword)
                    save(Collections.APP_USERS.toString(), body, {
                        response.end(getResponse(CREATED.code(), "User created successfully, now attach roles"))
                    }, { error ->
                        logger.error("createUser(${error.cause}) <--")
                        response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                    })
                }
            }, {
                logger.error("createUser(${it.cause}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        }, "username", "email", "phone", "idNumber", "password", "profileImage")
        logger.info("createUser() <--")
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
                    val encodedPassword = BCryptPasswordEncoder().encode(body.getString("password"))
                    body.remove("password")
                    body.put("password", encodedPassword)
                    save(Collections.APP_USERS.toString(), body, {
                        response.end(getResponse(CREATED.code(), "User created successfully, now attach roles"))
                    }, { error ->
                        logger.error("createAdmin(${error.message}) <--", error.cause?.cause)
                        response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                    })
                }
            }, {
                logger.error("createAdmin(${it.message}) <--", it.cause?.cause)
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        }, "username", "email", "phone", "idNumber", "password", "profileImage")
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
                .add(JsonObject.of("\$sort", 1))
                .add(
                    JsonObject.of(
                        "\$project",
                        JsonObject.of(
                            "username", 1,
                            "email", 1,
                            "phone", 1,
                            "idNumber", 1,
                            "profileImage", 1
                        )
                    )
                )
            aggregate(Collections.APP_USERS.toString(), pipeline, {
                val paging = JsonObject.of("page", pageNumber, "sorted", false)
                response.end(getResponse(OK.code(), "Success", JsonObject.of("data", it, "pagination", paging)))
            }, {
                logger.error("getUsers(${it.cause}) <--")
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
            aggregate(Collections.APP_USERS.toString(), pipeline, {
                val paging = JsonObject.of("page", pageNumber, "sorted", false)
                response.end(getResponse(OK.code(), "Success", JsonObject.of("data", it, "pagination", paging)))
            }, {
                logger.error("searchUser(${it.message} -> pipeline) <--", it.cause?.cause)
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        })
        logger.info("searchUser() <--")
    }

    private fun updateUser(rc: RoutingContext) {
        logger.info("updateUser() -->")
        execute("updateUser", rc, "user", { user, body, response ->
            val email = rc.request().getParam("email")
            if (hasValues(body, "roles") && !hasRole(user.getJsonObject("roles"), "admin")) {
                response.end(getResponse(UNAUTHORIZED.code(), "You dont have permission for this task"))
            }
            findAndUpdate(Collections.APP_USERS.toString(), JsonObject.of("email", email), body, {
                response.end(getResponse(OK.code(), "Successfully updated user", it))
            }, {
                logger.error("updateUser(${it.message}) <--", it.cause?.cause)
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        })
        logger.info("updateUser() <--")
    }

    private fun deleteUser(rc: RoutingContext) {
        logger.info("deleteUser() -->")
        execute("deleteUser", rc, "user", { _, _, response ->
            val email = rc.request().getParam("email")
            if (email.isNullOrEmpty()) {
                response.end(getResponse(BAD_REQUEST.code(), "Expected param email"))
                return@execute
            }
            val query = JsonObject.of("email", email)
            findOne(Collections.APP_USERS.toString(), query, {
                if (it.isEmpty) {
                    response.end(getResponse(NOT_FOUND.code(), "User not found"))
                    return@findOne
                }
                findOneAndDelete(Collections.APP_USERS.toString(), query, {
                    response.end(getResponse(OK.code(), "User deleted successfully"))
                }, { error ->
                    logger.error("deleteUser(${error.message}) <--", error.cause?.cause)
                    response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                })
            }, {
                logger.error("deleteUser(${it.message}) <--", it.cause?.cause)
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error has occurred"))
            })
            response.end(getResponse(OK.code(), "User Deleted successfully"))
        })
    }
}