package com.greenbay.core.service

import com.greenbay.core.Collections
import com.greenbay.core.utils.BaseUtils.Companion.execute
import com.greenbay.core.utils.BaseUtils.Companion.getResponse
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.Json
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
                    .put("dateCreated", Date(System.currentTimeMillis()))
                dbUtil.save(Collections.COMMUNICATIONS.toString(), body, {
                    response.end(getResponse(CREATED.code(), "Communication created successfully"))
                }, {
                    logger.error("createCommunication(${it.message} -> ${it.cause}) <--")
                    response.end(getResponse(BAD_REQUEST.code(), "Error occurred try again"))
                })
            }, "to", "title", "description", "opened"
        )
        logger.info("createCommunication() <--")
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
                            "collection", "app_users",
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
            dbUtil.aggregate(Collections.COMMUNICATIONS.toString(), pipeline, {
                it.add(JsonObject.of("page", pageNumber, "sorted", false))
                response.end(getResponse(OK.code(), "Successful", JsonObject.of("data", it)))
            }, {
                logger.error("getCommunications(${it.message} -> ${it.cause}) <--")
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
            val query = JsonObject.of("\$text", JsonObject.of("\$search", term))
            val pipeline = JsonArray()
                .add(JsonObject.of("\$match", query))
                .add(
                    JsonObject.of(
                        "\$lookup", JsonObject.of(
                            "collection", "app_users",
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
            dbUtil.aggregate(Collections.COMMUNICATIONS.toString(), pipeline, {
                it.add(JsonObject.of("page", pageNumber, "sorted", false))
                response.end(getResponse(OK.code(), "Successful", JsonObject.of("data", it)))
            }, {
                logger.error("searchCommunication(${it.message} -> ${it.cause}) <--")
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
            dbUtil.findAndUpdate(Collections.COMMUNICATIONS.toString(), query, update, {
                response.end(getResponse(OK.code(), "Successful", it))
            }, {
                logger.error("updateCommunication(${it.message} -> ${it.cause}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        }, "to")
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
            val query = JsonObject.of("id", id)
            dbUtil.findOneAndDelete(Collections.COMMUNICATIONS.toString(), query, {
                response.end(getResponse(OK.code(), "Successfully deleted communication"))
            }, {
                logger.error("deleteCommunication(${it.message} -> ${it.cause}) <--")
                response.end(getResponse(BAD_REQUEST.code(), "Error occurred try again"))
            })
        })
        logger.info("deleteCommunication() <--")
    }
}