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

open class TenantService : HouseService() {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)
    fun setTenantRoutes(router: Router) {
        router.post("/tenant").handler(::createTenant)
        router.get("/tenant/:pageNumber").handler(::getTenants)
        router.put("/tenant/:client").handler(::updateTenant)
        router.delete("/tenant/:client").handler(::deleteTenant)
        setHouseRoutes(router)
    }

    private fun createTenant(rc: RoutingContext) {
        logger.info("createTenant() -->")
        execute("createTenant", rc, "admin", { user, body, response ->
            dbUtil.findOne(
                Collections.APP_USERS.toString(),
                JsonObject.of("email", body.getString("client")),
                { client ->
                    if (user.isEmpty) {
                        response.end(getResponse(NOT_FOUND.code(), "User does not exist"))
                        return@findOne
                    }
                    dbUtil.findOne(
                        Collections.HOUSES.toString(),
                        JsonObject.of("houseNumber", body.getString("houseNumber")),
                        { house ->
                            val tenant = JsonObject.of("user", client, "house", house)
                            dbUtil.save(Collections.TENANTS.toString(), tenant, {
                                response.end(getResponse(CREATED.code(), "Tenant created successfully"))
                            }, {error->
                                logger.error("createTenant(${error.message}) <--")
                                response.end(getResponse(INTERNAL_SERVER_ERROR.code(),"Error occurred try again"))
                            })
                        },
                        {mError->
                            logger.error("createTenant(${mError.message}) <--")
                            response.end(getResponse(INTERNAL_SERVER_ERROR.code(),"Error occurred try again"))
                        })
                },
                {kError->
                    logger.error("createTenant(${kError.message}) <--")
                    response.end(getResponse(INTERNAL_SERVER_ERROR.code(),"Error occurred try again"))
                })
        }, "user", "house")
        logger.info("createTenant() <--")
    }

    private fun getTenants(rc: RoutingContext) {
        logger.info("getTenants() -->")
        execute("getTenants", rc, "admin", { _, _, response ->
            val pageNumber = Integer.valueOf(rc.request().getParam("pageNumber"))
            val limit = 20
            val skip = pageNumber * limit
            val pipeline = JsonArray()
                .add(JsonObject.of("\$skip", skip))
                .add(JsonObject.of("\$limit", limit))
                .add(JsonObject.of("\$sort", 1))
            dbUtil.aggregate(Collections.TENANTS.toString(), pipeline, {
                response.end(getResponse(OK.code(), "Successful", it))
            }, {
                logger.error("getTenants() <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        })
        logger.info("getTenants() <--")
    }

    private fun updateTenant(rc: RoutingContext) {
        logger.info("updateTenant() -->")
        execute("updateTenant", rc, "admin", { _, body, response ->
            val client = rc.request().getParam("client")
            if (client.isNullOrEmpty()) {
                response.end(getResponse(BAD_REQUEST.code(), "Expected parameter client"))
                return@execute
            }
            dbUtil.findAndUpdate(Collections.TENANTS.toString(), JsonObject.of("client", client), body, {
                response.end(getResponse(OK.code(), "Successfully updated tenant"))
            }, {
                logger.error("updateTenant(${it.cause}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        }, "client", "house")
        logger.info("updateTenant() <--")
    }

    private fun deleteTenant(rc: RoutingContext) {
        logger.info("deleteTenant() -->")
        execute("deleteTenant", rc, "admin", { _, _, response ->
            val client = rc.request().getParam("client")
            if (client.isNullOrEmpty()) {
                response.end(getResponse(BAD_REQUEST.code(), "Expected field client"))
                return@execute
            }
            dbUtil.findOneAndDelete(Collections.TENANTS.toString(), JsonObject.of("client", client), {
                response.end(getResponse(OK.code(), "Successfully deleted tenant"))
            }, {
                logger.error("deleteTenant(${it.cause}) <--")
            })
        })
        logger.info("deleteTenant() <--")
    }
}