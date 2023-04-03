package com.greenbay.core.service

import com.greenbay.core.Collections
import com.greenbay.core.utils.BaseUtils.Companion.execute
import com.greenbay.core.utils.BaseUtils.Companion.getResponse
import com.greenbay.core.utils.BaseUtils.Companion.hasRole
import com.greenbay.core.utils.BaseUtils.Companion.hasValues
import com.greenbay.core.utils.DatabaseUtils
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.vertx.core.AbstractVerticle
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.util.*

open class UserService : AbstractVerticle() {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)
    lateinit var dbUtil: DatabaseUtils

    fun setUserRoutes(router: Router) {
        dbUtil = DatabaseUtils(this.vertx)
        router.post("/users").handler(::createUser)
        router.get("/users/:pageNumber").handler(::getUsers)
        router.get("/users/search/:term/:pageNumber").handler(::searchUser)
        router.put("/users/:email").handler(::updateUser)
        router.delete("/users/:email").handler(::deleteUser)
    }

    private fun createUser(rc: RoutingContext) {
        logger.info("createUser() -->")
        execute("createUser", rc, "admin", { user, body, response ->
            dbUtil.findOne(Collections.APP_USERS.toString(), JsonObject.of("email", body.getString("email")), {
                if (!it.isEmpty) {
                    response.end(getResponse(CONFLICT.code(), "User already exists"))
                } else {
                    body.put("addedBy", user.getString("email"))
                    body.put("addedOn", Date(System.currentTimeMillis()))
                    val encodedPassword = BCryptPasswordEncoder().encode(body.getString("password"))
                    body.remove("password")
                    body.put("password", encodedPassword)
                    dbUtil.save(Collections.APP_USERS.toString(), body, {
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
        execute("createAdmin", rc, "", { _, body, response ->
            body.put("username", "admin user").put("email", "admin@admin.com").put("phone", "+254712345678")
                .put("idNumber", "1234567890").put("password", "admin1234").put("profileImage", "")
                .put("email", "admin@admin.com")
                .put("addedOn", Date(System.currentTimeMillis()))
            dbUtil.findOne(Collections.APP_USERS.toString(), JsonObject.of("email", body.getString("email")), {
                if (!it.isEmpty) {
                    response.end(getResponse(CONFLICT.code(), "User already exists"))
                } else {
                    val encodedPassword = BCryptPasswordEncoder().encode(body.getString("password"))
                    body.remove("password")
                    body.put("password", encodedPassword)
                    dbUtil.save(Collections.APP_USERS.toString(), body, {
                        response.end(
                            getResponse(
                                CREATED.code(),
                                "Admin created successfully, now attach roles,Delete this user after setup"
                            )
                        )
                    }, { error ->
                        logger.error("createAdmin(${error.cause}) <--")
                        response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                    })
                }
            }, {
                logger.error("createAdmin(${it.cause}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        })
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
            dbUtil.aggregate(Collections.APP_USERS.toString(), pipeline, {
                it.add(JsonObject.of("page", pageNumber, "sorted", true))
                response.end(getResponse(OK.code(), "Success", JsonObject.of("data", it)))
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
            dbUtil.aggregate(Collections.APP_USERS.toString(), pipeline, {
                it.add(JsonObject.of("page", pageNumber, "sorted", false))
                response.end(getResponse(OK.code(), "Success", JsonObject.of("data", it)))
            }, {
                logger.error("searchUser(${it.cause} -> pipeline) <--")
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
            dbUtil.findAndUpdate(Collections.APP_USERS.toString(), JsonObject.of("email", email), body, {
                response.end(getResponse(OK.code(), "Successfully updated user", it))
            }, {
                logger.error("updateUser(${it.cause}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        })
        logger.info("updateUser() <--")
    }

    private fun deleteUser(rc: RoutingContext) {
        logger.info("deleteUser() -->")
        execute("deleteUser", rc, "user", { _, _, response ->
            response.end(getResponse(OK.code(), "User Deleted successfully"))
        })
    }
}