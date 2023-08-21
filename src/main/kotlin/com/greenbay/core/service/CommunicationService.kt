package com.greenbay.core.service

import com.greenbay.core.Collections
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import java.util.*

open class CommunicationService : PaymentService() {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)
    fun setCommunicationRoutes(router: Router) {
        router.post("/communications").handler(::createCommunication)
        router.get("/communications/:pageNumber").handler(::getCommunications)
        router.get("/communications/:id").handler(::getCommunication)
        router.get("/communication/:term/:pageNumber").handler(::searchCommunication)
        router.put("/communications/:id").handler(::updateCommunication)
        router.delete("/communications/:id").handler(::deleteCommunication)
        setPaymentRoutes(router)
    }

    private fun createCommunication(rc: RoutingContext) {
        logger.info("createCommunication() -->")
        execute(
            "createCommunication", rc, "user", { user, body, response ->
                body.put("id", UUID.randomUUID().toString())
                    .put("createdBy", user.getString("email"))
                    .put("dateCreated", System.currentTimeMillis())
                save(Collections.COMMUNICATIONS.toString(), body, {
                    response.end(getResponse(CREATED.code(), "Communication created successfully"))
                }, {
                    logger.error("createCommunication(${it.message} -> ${it.cause}) <--")
                    response.end(getResponse(BAD_REQUEST.code(), "Error occurred try again"))
                })
            }, "to","title", "description")
        logger.info("createCommunication() <--")
    }

    private fun getCommunication(rc: RoutingContext) {
        logger.info("getCommunication() -->")
        execute("getCommunication", rc, "user", { _, _, response ->
            val id = rc.request().getParam("id")
            if (id.isNullOrEmpty()) {
                response.end(getResponse(BAD_REQUEST.code(), "Expected param id"))
                return@execute
            }
            findOne(Collections.COMMUNICATIONS.toString(), JsonObject.of("_id", id), {
                response.end(getResponse(OK.code(), "Success", it))
            }, {
                logger.error("getCommunication(${it.message}) <--",it)
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        })
        logger.info("getCommunication() <--")
    }

    private fun getCommunications(rc: RoutingContext) {
        logger.info("getCommunications() -->")
        execute("getCommunications", rc, "user", { _, _, response ->
            val limit = 20
            val pageNumber = Integer.valueOf(rc.request().getParam("pageNumber")) - 1
            val skip = pageNumber * limit
            val pipeline = JsonArray()
                .add(
                    JsonObject.of(
                        "\$lookup", JsonObject.of(
                            "from", "app_users",
                            "localField", "to",
                            "foreignField", "email",
                            "as", "user"
                        )
                    )
                )
                .add(
                    JsonObject.of(
                        "\$unwind", JsonObject.of(
                            "path", "\$user",
                            "preserveNullAndEmptyArrays", true
                        )
                    )
                )
                .add(JsonObject.of("\$limit", limit))
                .add(JsonObject.of("\$skip", skip))
                .add(JsonObject.of("\$sort", JsonObject.of("_id", -1)))
            aggregate(Collections.COMMUNICATIONS.toString(), pipeline, {
                val paging = JsonObject.of("page", pageNumber, "sorted", false)
                response.end(getResponse(OK.code(), "Success", JsonObject.of("data", it, "pagination", paging)))
            }, {
                logger.error("getCommunications(${it.message}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        })
        logger.info("getCommunications() <--")
    }

    private fun searchCommunication(rc: RoutingContext) {
        logger.info("searchCommunication() -->")
        execute("searchCommunication", rc, "user", { _, _, response ->
            val pageNumber = Integer.valueOf(rc.request().getParam("pageNumber")) - 1
            val term = rc.request().getParam("term") ?: ""
            val limit = 20
            val skip = pageNumber * limit
            if (term.isEmpty()) {
                response.end(getResponse(BAD_REQUEST.code(), "Expected param search-term"))
                return@execute
            }
            val query = JsonObject.of("user.email", JsonObject.of("\$regex", term, "\$options", "i"))
            val pipeline = JsonArray()
                .add(JsonObject.of("\$match", query))
                .add(
                    JsonObject.of(
                        "\$lookup", JsonObject.of(
                            "from", "app_users",
                            "localField", "to",
                            "foreignField", "email",
                            "as", "user"
                        )
                    )
                )
                .add(
                    JsonObject.of(
                        "\$unwind", JsonObject.of(
                            "path", "\$user",
                            "preserveNullAndEmptyArrays", true
                        )
                    )
                )
                .add(JsonObject.of("\$limit", limit))
                .add(JsonObject.of("\$skip", skip))
            aggregate(Collections.COMMUNICATIONS.toString(), pipeline, {
                val paging = JsonObject.of("page", pageNumber, "sorted", false)
                response.end(getResponse(OK.code(), "Success", JsonObject.of("data", it, "pagination", paging)))
            }, {
                logger.error("searchCommunication(${it.message}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        })
        logger.info("searchCommunication() <--")
    }

    private fun updateCommunication(rc: RoutingContext) {
        logger.info("updateCommunication() -->")
        execute("updateCommunication", rc, "user", { _, body, response ->
            val id = rc.request().getParam("id")
            if (id.isNullOrEmpty()) {
                response.end(getResponse(BAD_REQUEST.code(), "Expected param id"))
                return@execute
            }
            val query = JsonObject.of("id", id)
            val update = JsonObject.of("\$set", body)
            findAndUpdate(Collections.COMMUNICATIONS.toString(), query, update, {
                response.end(getResponse(OK.code(), "Successful", it))
            }, {
                logger.error("updateCommunication(${it.message}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        }, "to","payload")
        logger.info("updateCommunication() <--")
    }

    private fun deleteCommunication(rc: RoutingContext) {
        logger.info("deleteCommunication() -->")
        execute("deleteCommunication", rc, "admin", { _, _, response ->
            val id = rc.request().getParam("id")
            if (id.isNullOrEmpty()) {
                response.end(getResponse(BAD_REQUEST.code(), "Expected field id"))
                return@execute
            }
            val query = JsonObject.of("_id", id)
            findOneAndDelete(Collections.COMMUNICATIONS.toString(), query, {
                response.end(getResponse(OK.code(), "Successfully deleted communication"))
            }, {
                logger.error("deleteCommunication(${it.message}) <--")
                response.end(getResponse(BAD_REQUEST.code(), "Error occurred try again"))
            })
        })
        logger.info("deleteCommunication() <--")
    }
}