package com.greenbay.core.service

import com.greenbay.core.Collections
import com.greenbay.core.TaskStatus
import com.greenbay.core.utils.BaseUtils.Companion.execute
import com.greenbay.core.utils.BaseUtils.Companion.getResponse
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import java.util.Date

open class TaskService : CommunicationService() {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)
    fun setTaskRoutes(router: Router) {
        router.post("/tasks").handler(::createTask)
        router.get("/tasks/:pageNumber").handler(::getTasks)
        router.get("/tasks/:term/:pageNumber").handler(::searchTask)
        router.put("/tasks/:id").handler(::updateTask)
        router.delete("/tasks/:id").handler(::deleteTask)
        setCommunicationRoutes(router)
    }

    private fun createTask(rc: RoutingContext) {
        logger.info("createTask() -->")
        execute("createTask", rc, "admin", { user, body, response ->
            body.put("createdBy", user.getString("email"))
                .put("createdOn", Date(System.currentTimeMillis()))
                .put("status", TaskStatus.PENDING.toString())
            dbUtil.save(Collections.TASKS.toString(), body, {
                response.end(getResponse(CREATED.code(), "Task created successfully"))
            }, {
                logger.error("createTask(${it.cause} -> creatingTask) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred"))
            })
        }, "to", "title", "description", "scheduleDate")
        logger.info("createTask() <--")
    }

    private fun getTasks(rc: RoutingContext) {
        logger.info("getTasks() -->")
        execute("getTasks", rc, "user", { _, _, response ->
            val pageNumber = Integer.valueOf(rc.request().getParam("pageNumber")) - 1
            val limit = 20
            val skip = limit * pageNumber
            val pipeline = JsonArray()
                .add(
                    JsonObject.of(
                        "\$lookup", JsonObject
                            .of(
                                "collection", "app_users",
                                "localField", "to",
                                "foreignField", "email",
                                "as", "client"
                            )
                    )
                )
                .add(
                    JsonObject.of(
                        "\$lookup", JsonObject.of(
                            "collection", "app_users",
                            "localField", "createdBy",
                            "foreignField", "email",
                            "as", "createdBy"
                        )
                    )
                )
                .add(
                    JsonObject.of(
                        "\$unwind",
                        JsonObject.of("path", "\$client", "preserveNullAndEmptyArrays", true)
                    )
                )
                .add(
                    JsonObject.of(
                        "\$unwind",
                        JsonObject.of("path", "\$createdBy", "preserveNullAndEmptyArrays", true)
                    )
                )
                .add(
                    JsonObject.of(
                        "\$project", JsonObject
                            .of(
                                "_id", "\$id",
                                "clientFirstName", "\$client.firstName",
                                "clientLastName", "\$client.lastName",
                                "clientPhoneNumber", "\$client.phoneNumber",
                                "clientEmail", "\$client.email",
                            )
                    )
                )
                .add(JsonObject.of("\$skip", skip))
                .add(JsonObject.of("\$limit", limit))
            dbUtil.aggregate(Collections.TASKS.toString(), pipeline, {
                it.add(JsonObject.of("paging", JsonObject.of("page", pageNumber, "sorted", false)))
                response.end(getResponse(OK.code(), "Success", JsonObject.of("data", it)))
            }, {
                logger.error("getTasks(${it.cause}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred "))
            })
        })
        logger.info("getTasks() <--")
    }

    private fun searchTask(rc: RoutingContext) {
        logger.info("searchTask() -->")
        execute("getTasks", rc, "user", { _, _, response ->
            val pageNumber = Integer.valueOf(rc.request().getParam("pageNumber")) - 1
            val term = rc.request().getParam("term")?:""
            val limit = 20
            val skip = limit * pageNumber
            if (term.isEmpty()){
                response.end(getResponse(BAD_REQUEST.code(),"Expected param search-term"))
                return@execute
            }
            val query = JsonObject.of("\$text",JsonObject.of("\$search",term))
            val pipeline = JsonArray()
                .add(JsonObject.of("\$match",query))
                .add(
                    JsonObject.of(
                        "\$lookup", JsonObject
                            .of(
                                "collection", "app_users",
                                "localField", "to",
                                "foreignField", "email",
                                "as", "client"
                            )
                    )
                )
                .add(
                    JsonObject.of(
                        "\$lookup", JsonObject.of(
                            "collection", "app_users",
                            "localField", "createdBy",
                            "foreignField", "email",
                            "as", "createdBy"
                        )
                    )
                )
                .add(
                    JsonObject.of(
                        "\$unwind",
                        JsonObject.of("path", "\$client", "preserveNullAndEmptyArrays", true)
                    )
                )
                .add(
                    JsonObject.of(
                        "\$unwind",
                        JsonObject.of("path", "\$createdBy", "preserveNullAndEmptyArrays", true)
                    )
                )
                .add(
                    JsonObject.of(
                        "\$project", JsonObject
                            .of(
                                "_id", "\$id",
                                "clientFirstName", "\$client.firstName",
                                "clientLastName", "\$client.lastName",
                                "clientPhoneNumber", "\$client.phoneNumber",
                                "clientEmail", "\$client.email",
                            )
                    )
                )
                .add(JsonObject.of("\$skip", skip))
                .add(JsonObject.of("\$limit", limit))
            dbUtil.aggregate(Collections.TASKS.toString(), pipeline, {
                it.add(JsonObject.of("paging", JsonObject.of("page", pageNumber, "sorted", false)))
                response.end(getResponse(OK.code(), "Success", JsonObject.of("data", it)))
            }, {
                logger.error("getTasks(${it.cause}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred "))
            })
        })
        logger.info("searchTask() <--")
    }

    private fun updateTask(rc: RoutingContext) {
        logger.info("updateTask() -->")

        logger.info("updateTask() <--")
    }

    private fun deleteTask(rc: RoutingContext) {
        logger.info("deleteTask() -->")
        logger.info("deleteTask() <--")
    }

}