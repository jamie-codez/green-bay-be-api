package com.greenbay.core.service

import com.greenbay.core.Collections
import com.greenbay.core.utils.BaseUtils.Companion.execute
import com.greenbay.core.utils.BaseUtils.Companion.getResponse
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import java.util.Date

open class HouseService : UserService() {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)

    fun setHouseRoutes(router: Router) {
        router.post("/createHouse").handler(::createHouse)
        router.post("/getHouses/:pageNumber").handler(::getHouses)
        router.post("/updateHouse/:houseNumber").handler(::updateHouse)
        router.post("/deleteHouse/:houseNumber").handler(::deleteHouse)
        setUserRoutes(router)
    }

    private fun createHouse(rc: RoutingContext) {
        logger.info("createHouse() -->")
        execute("createHouse", rc, "admin", { user, body, response ->
            dbUtil.findOne(Collections.HOUSES.toString(), JsonObject.of("houseNumber", body.getString("houseNumber")), {
                if (!it.isEmpty) {
                    response.end(getResponse(CONFLICT.code(), "House already exists"))
                    return@findOne
                }
                body.put("addedBy", user.getString("email"))
                body.put("createdOn", Date(System.currentTimeMillis()))
                body.put("occupied", false)
                dbUtil.save(Collections.HOUSES.toString(), body, {
                    response.end(getResponse(CREATED.code(), "Houses created successfully"))
                }, {error->
                    logger.error("createHouse(${error.cause} adding) <--")
                    response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "House already exists"))
                })
            }, {
                logger.error("createHouse(${it.cause} checking) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "House already exists"))
            })
        }, "houseNumber", "rent", "deposit")
        logger.info("createHouse() <--")
    }

    private fun getHouses(rc: RoutingContext) {
        logger.info("getHouses() -->")
        execute("getHouses", rc, "admin", { _, _, response ->
            val pageNumber = Integer.parseInt(rc.request().getParam("pageNumber")) - 1
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
                            "houseNumber", 1,
                            "rent", 1,
                            "deposit", 1,
                            "occupied", 1
                        )
                    )
                )
            dbUtil.aggregate(Collections.HOUSES.toString(), pipeline, {
                val payload = JsonObject.of("data", it, "page", pageNumber, "sorted", true, "scheme", "asc")
                response.end(getResponse(OK.code(), "Successful", payload))
            }, {
                logger.error("getHouses(${it.cause}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        })
        logger.info("getHouses() <--")
    }

    private fun updateHouse(rc: RoutingContext) {
        logger.info("updateHouse() -->")
        execute("updateHouse", rc, "admin", { _, body, response ->
            val houseNumber = rc.request().getParam("houseNumber")
            if (houseNumber.isNullOrEmpty()) {
                response.end(getResponse(BAD_REQUEST.code(), "Expected param houseNumber"))
            } else {
                dbUtil.findAndUpdate(Collections.HOUSES.toString(), JsonObject.of("houseNumber", houseNumber), body, {
                    response.end(getResponse(OK.code(), "House updated successfully", it))
                }, {
                    logger.error("updateHouse(${it.cause}) <--")
                    response.end(getResponse(BAD_REQUEST.code(), "Error occurred try again"))
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
                dbUtil.findOneAndDelete(Collections.HOUSES.toString(), JsonObject.of("houseNumber", houseNumber), {
                    response.end(getResponse(OK.code(), "Deleted house successfully"))
                }, {
                    logger.error("deleteHouse(${it.cause}) <--")
                    response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                })
            }
        })
        logger.info("deleteHouse() <--")
    }
}