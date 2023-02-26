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
import java.util.*

open class UserService : AbstractVerticle() {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)
    val dbUtil = DatabaseUtils(this.vertx)

    fun setUserRoutes(router: Router) {
        router.post("/createUser").handler(::createUser)
        router.post("/getUsers/:pageNumber").handler(::getUsers)
        router.put("/update/:email").handler(::updateUser)
        router.delete("/delete/:email").handler(::deleteUser)
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
                val payload = JsonObject.of("data", it, "page", pageNumber, "sorted", true,"scheme","asc")
                response.end(getResponse(OK.code(), "Success", payload))
            }, {
                logger.error("getUsers(${it.cause}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        })
        logger.info("getUsers() <--")
    }

    private fun updateUser(rc: RoutingContext) {
        logger.info("updateUser() -->")
        execute("updateUser", rc, "user", { user, body, response ->
            val email = rc.request().getParam("email")
            if (hasValues(body, "roles") && !hasRole(user.getJsonArray("roles"), "admin")) {
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