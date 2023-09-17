package com.greenbay.core.service

import com.greenbay.core.Collections
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import java.util.*

open class HouseService : UserService() {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)

    fun setHouseRoutes(router: Router) {
        router.post("/houses").handler(::createHouse)
        router.get("/houses/:pageNumber").handler(::getHouses)
        router.get("/house/:id").handler(::getHouse)
        router.get("/houses/:term/:pageNumber").handler(::searchHouse)
        router.put("/houses/:houseNumber").handler(::updateHouse)
        router.delete("/houses/:houseNumber").handler(::deleteHouse)
        setUserRoutes(router)
    }

    private fun createHouse(rc: RoutingContext) {
        logger.info("createHouse() -->")
        execute("createHouse", rc, "admin", { user, body, response ->
            findOne(Collections.HOUSES.toString(), JsonObject.of("houseNumber", body.getString("houseNumber")), {
                if (!it.isEmpty) {
                    response.end(getResponse(CONFLICT.code(), "House already exists"))
                    return@findOne
                }
                body.put("addedBy", user.getString("email"))
                body.put("createdOn", System.currentTimeMillis())
                body.put("occupied", false)
                save(Collections.HOUSES.toString(), body, {
                    response.end(getResponse(CREATED.code(), "Houses created successfully"))
                }, { error ->
                    logger.error("createHouse(${error.message} adding) -> saveHouse <--")
                    response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                })
            }, {
                logger.error("createHouse(${it.message} checking) -> getHouse <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        }, "houseNumber", "rent", "deposit", "floorNumber")
        logger.info("createHouse() <--")
    }

    private fun getHouse(rc: RoutingContext) {
        logger.info("getHouse() -->")
        execute("getHouse", rc, "user", { _, _, response ->
            val id = rc.request().getParam("id")
            if (id.isNullOrEmpty()) {
                response.end(getResponse(BAD_REQUEST.code(), "Expected param id"))
            } else {
                findOne(Collections.HOUSES.toString(), JsonObject.of("_id", id), {
                    response.end(getResponse(OK.code(), "Success", it))
                }, {
                    logger.error("getHouse(${it.message}) -> fetchingHouses <--", it)
                    response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                })
            }
        })
        logger.info("getHouse() <--")
    }

    private fun getHouses(rc: RoutingContext) {
        logger.info("getHouses() -->")
        execute("getHouses", rc, "user", { _, _, response ->
            val pageNumber = Integer.parseInt(rc.request().getParam("pageNumber")) - 1
            val limit = 20
            val skip = pageNumber * limit
            val pipeline = JsonArray()
                .add(JsonObject.of("\$skip", skip))
                .add(JsonObject.of("\$limit", limit))
                .add(JsonObject.of("\$sort", JsonObject.of("_id", -1)))
                .add(
                    JsonObject.of(
                        "\$project",
                        JsonObject.of(
                            "houseNumber", 1,
                            "rent", 1,
                            "deposit", 1,
                            "occupied", 1,
                            "floorNumber", 1,
                            "createdBy", 1,
                            "createdOn", 1
                        )
                    )
                )
            aggregate(Collections.HOUSES.toString(), pipeline, {
                val paging = JsonObject.of("page", pageNumber, "sorted", true)
                response.end(getResponse(OK.code(), "Success", JsonObject.of("data", it, "pagination", paging)))
            }, {
                logger.error("getHouses(${it.message}) -> getHousePipeline <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        })
        logger.info("getHouses() <--")
    }

    private fun searchHouse(rc: RoutingContext) {
        logger.info("searchHouse() -->")
        execute("searchHouse", rc, "admin", { _, _, response ->
            val pageNumber = Integer.parseInt(rc.request().getParam("pageNumber")) - 1
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
                .add(JsonObject.of("\$skip", skip))
                .add(JsonObject.of("\$limit", limit))
                .add(JsonObject.of("\$sort", 1))
                .add(
                    JsonObject.of(
                        "\$project",
                        JsonObject.of(
                            "houseNumber", 1,
                            "rent", 1,
                            "deposit", 1,
                            "occupied", 1,
                            "floorNumber", 1,
                            "createdBy", 1,
                            "createdOn", 1
                        )
                    )
                )
            createIndex(Collections.HOUSES.toString(), JsonObject.of("houseNumber", 1), {
                aggregate(Collections.HOUSES.toString(), pipeline, {
                    val paging = JsonObject.of("page", pageNumber, "sorted", true)
                    response.end(getResponse(OK.code(), "Success", JsonObject.of("data", it, "pagination", paging)))
                }, {
                    logger.error("searchHouse(${it.message}) -> performingSearch <--")
                    response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                })
            }, {
                logger.error("searchHouse(${it.message} -> Failed to create index) <--")
            })

        })
        logger.info("searchHouse() <--")
    }

    private fun updateHouse(rc: RoutingContext) {
        logger.info("updateHouse() -->")
        execute("updateHouse", rc, "admin", { _, body, response ->
            val houseNumber = rc.request().getParam("houseNumber")
            if (houseNumber.isNullOrEmpty()) {
                response.end(getResponse(BAD_REQUEST.code(), "Expected param houseNumber"))
            } else {
                if (body.isEmpty){
                    response.end(getResponse(BAD_REQUEST.code(), "Expected request body"))
                    return@execute
                }
                findAndUpdate(Collections.HOUSES.toString(), JsonObject.of("_id", houseNumber), body, {
                    response.end(getResponse(OK.code(), "House updated successfully", it))
                }, {
                    logger.error("updateHouse(${it.message}) -> updatingHouse <--")
                    response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                })
            }
        })
        logger.info("updateHouse() <---")
    }

    private fun deleteHouse(rc: RoutingContext) {
        logger.info("deleteHouse() -->")
        execute("deleteHouse", rc, "admin", { _, _, response ->
            val houseNumber = rc.request().getParam("houseNumber")
            if (houseNumber.isNullOrEmpty()) {
                logger.error("deleteHouse(houseNumber Empty) <--")
                response.end(getResponse(BAD_REQUEST.code(), "Expected param houseNumber"))
            } else {
                findOneAndDelete(Collections.HOUSES.toString(), JsonObject.of("houseNumber", houseNumber), {
                    response.end(getResponse(OK.code(), "Deleted house successfully"))
                }, {
                    logger.error("deleteHouse(${it.message}) -> deletingHouse <--")
                    response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                })
            }
        })
        logger.info("deleteHouse() <--")
    }
}