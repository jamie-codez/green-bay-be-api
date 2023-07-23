package com.greenbay.core.service

import com.greenbay.core.Collections
import com.greenbay.core.TaskStatus
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import java.util.*

open class TaskService : CommunicationService() {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)
    fun setTaskRoutes(router: Router) {
        router.post("/tasks").handler(::createTask)
        router.get("/tasks/:pageNumber").handler(::getTasks)
        router.get("/tasks/:id").handler(::getTask)
        router.get("/tasks/:term/:pageNumber").handler(::searchTask)
        router.put("/tasks/:id").handler(::updateTask)
        router.delete("/tasks/:id").handler(::deleteTask)
        setCommunicationRoutes(router)
    }

    private fun createTask(rc: RoutingContext) {
        logger.info("createTask() -->")
        execute("createTask", rc, "admin", { user, body, response ->
            body.put("createdBy", user.getString("email"))
                .put("createdOn", System.currentTimeMillis())
                .put("status", TaskStatus.PENDING.toString())
            save(Collections.TASKS.toString(), body, {
                response.end(getResponse(CREATED.code(), "Task created successfully"))
            }, {
                logger.error("createTask(${it.cause} -> creatingTask) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred"))
            })
        }, "to", "title", "description", "scheduleDate")
        logger.info("createTask() <--")
    }

    private fun getTask(rc: RoutingContext) {
        logger.info("getTask() -->")
        execute("getTask", rc, "user", { _, _, response ->
            val id = rc.request().getParam("id") ?: ""
            if (id.isEmpty()) {
                response.end(getResponse(BAD_REQUEST.code(), "Expected parameter id"))
                return@execute
            }
            val query = JsonObject.of("_id", id)
            findOne(Collections.TASKS.toString(), query, {
                if (it.isEmpty) {
                    response.end(getResponse(NOT_FOUND.code(), "Task not found"))
                    return@findOne
                }
                response.end(getResponse(OK.code(), "Success", it))
            }, {
                logger.error("getTask(${it.cause}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred"))
            })
        })
        logger.info("getTask() <--")
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
                                "from", "app_users",
                                "localField", "to",
                                "foreignField", "email",
                                "as", "client"
                            )
                    )
                )
                .add(
                    JsonObject.of(
                        "\$lookup", JsonObject.of(
                            "from", "app_users",
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
                .add(JsonObject.of("\$sort", JsonObject.of("_id", -1)))
            aggregate(Collections.TASKS.toString(), pipeline, {
                val paging = JsonObject.of("page", pageNumber, "sorted", true)
                response.end(getResponse(OK.code(), "Success", JsonObject.of("data", it, "pagination", paging)))
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
            val term = rc.request().getParam("term") ?: ""
            val limit = 20
            val skip = limit * pageNumber
            if (term.isEmpty()) {
                response.end(getResponse(BAD_REQUEST.code(), "Expected param search-term"))
                return@execute
            }
            val query = JsonObject.of("\$text", JsonObject.of("\$search", term))
            val pipeline = JsonArray()
                .add(JsonObject.of("\$match", query))
                .add(
                    JsonObject.of(
                        "\$lookup", JsonObject
                            .of(
                                "from", "app_users",
                                "localField", "to",
                                "foreignField", "email",
                                "as", "client"
                            )
                    )
                )
                .add(
                    JsonObject.of(
                        "\$lookup", JsonObject.of(
                            "from", "app_users",
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
            aggregate(Collections.TASKS.toString(), pipeline, {
                val paging = JsonObject.of("page", pageNumber, "sorted", false)
                response.end(getResponse(OK.code(), "Success", JsonObject.of("data", it, "pagination", paging)))
            }, {
                logger.error("getTasks(${it.cause}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred "))
            })
        })
        logger.info("searchTask() <--")
    }

    private fun updateTask(rc: RoutingContext) {
        logger.info("updateTask() -->")
        execute("updateTask", rc, "use", { _, body, response ->
            val id = rc.request().getParam("id") ?: ""
            if (id.isEmpty()) {
                response.end(getResponse(BAD_REQUEST.code(), "Expected parameter id"))
                return@execute
            }
            if (body.containsKey("status")) {
                val completed = body.getString("status") == TaskStatus.COMPLETED.toString()
                val started = body.getString("status") == TaskStatus.STARTED.toString()
                body.remove("status")
                if (completed)
                    body.put("status", TaskStatus.STARTED.toString())
                else if (started)
                    body.put("status", TaskStatus.STARTED.toString())
                else
                    body.put("status", TaskStatus.PENDING.toString())
            }
            val query = JsonObject.of("_id", id)
            findOne(Collections.TASKS.toString(), query, {
                if (it.isEmpty) {
                    response.end(getResponse(NOT_FOUND.code(), "Task not found"))
                    return@findOne
                }
                findAndUpdate(Collections.TASKS.toString(), query, body, {
                    response.end(getResponse(OK.code(), "Task updated successfully"))
                }, { err ->
                    logger.error("updateTask(${err.cause} -> updatingTask) <--")
                    response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred"))
                })
            }, {
                logger.error("updateTask(${it.cause}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred"))
            })
        })
        logger.info("updateTask() <--")
    }

    private fun deleteTask(rc: RoutingContext) {
        logger.info("deleteTask() -->")
        execute("deleteTask", rc, "admin", { _, _, response ->
            val id = rc.request().getParam("id") ?: ""
            if (id.isEmpty()) {
                response.end(getResponse(BAD_REQUEST.code(), "Expected parameter id"))
                return@execute
            }
            val query = JsonObject.of("_id", id)
            findOne(Collections.TASKS.toString(), query, {
                if (it.isEmpty) {
                    response.end(getResponse(NOT_FOUND.code(), "Task not found"))
                    return@findOne
                }
                findOneAndDelete(Collections.TASKS.toString(), query, {
                    response.end(getResponse(OK.code(), "Task deleted successfully"))
                }, { err ->
                    logger.error("deleteTask(${err.cause} -> deletingTask) <--")
                    response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred"))
                })
            }, {
                logger.error("deleteTask(${it.cause}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred"))
            })
        })
        logger.info("deleteTask() <--")
    }

}