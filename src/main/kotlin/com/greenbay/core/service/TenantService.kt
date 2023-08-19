package com.greenbay.core.service

import com.greenbay.core.Collections
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext

open class TenantService : HouseService() {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)
    fun setTenantRoutes(router: Router) {
        router.post("/tenants").handler(::createTenant)
        router.get("/tenants/:pageNumber").handler(::getTenants)
        router.get("/tenants/:id").handler(::getTenant)
        router.get("/tenants/:term/pageNumber").handler(::searchTenant)
        router.put("/tenants/:client").handler(::updateTenant)
        router.delete("/tenants/:client").handler(::deleteTenant)
        setHouseRoutes(router)
    }

    private fun createTenant(rc: RoutingContext) {
        logger.info("createTenant() -->")
        execute("createTenant", rc, "admin", { user, body, response ->
            findOne(
                Collections.APP_USERS.toString(),
                JsonObject.of("email", body.getString("client")),
                { client ->
                    if (user.isEmpty) {
                        response.end(getResponse(NOT_FOUND.code(), "User does not exist"))
                        return@findOne
                    }
                    findOne(
                        Collections.HOUSES.toString(),
                        JsonObject.of("houseNumber", body.getString("houseNumber")),
                        { house ->
                            val tenant = JsonObject.of("user", client, "house", house)
                            save(Collections.TENANTS.toString(), tenant, {
                                response.end(getResponse(CREATED.code(), "Tenant created successfully"))
                            }, { error ->
                                logger.error("createTenant(${error.message}) <--")
                                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                            })
                        },
                        { mError ->
                            logger.error("createTenant(${mError.message}) <--")
                            response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                        })
                },
                { kError ->
                    logger.error("createTenant(${kError.message}) <--")
                    response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                })
        }, "client", "houseNumber")
        logger.info("createTenant() <--")
    }

    private fun getTenant(rc: RoutingContext) {
        logger.info("getTenant() -->")
        execute("getTenant", rc, "user", { user, body, response ->
            val id = rc.request().getParam("id")
            if (id.isNullOrEmpty()) {
                response.end(getResponse(BAD_REQUEST.code(), "Expected parameter id"))
                return@execute
            }
            val query = JsonObject.of("_id", id)
            findOne(Collections.TENANTS.toString(), query, {
                findOne(Collections.APP_USERS.toString(), JsonObject.of("email", it.getString("client")), { client ->
                    findOne(
                        Collections.HOUSES.toString(),
                        JsonObject.of("houseNumber", it.getString("houseNumber")),
                        { house ->
                            val tenant = JsonObject.of(
                                "firstName", client.getString("firstName"),
                                "lastName", client.getString("lastName"),
                                "email", client.getString("email"),
                                "phone", client.getString("phoneNumber"),
                                "houseNumber", house.getString("houseNumber"),
                                "rent", house.getString("rent"),
                                "deposit", house.getString("deposit"),
                                "floorNumber", house.getString("floorNumber")
                            )
                            response.end(getResponse(OK.code(), "Success", tenant))
                        },
                        { error ->
                            logger.error("getTenant(${error.message}) <--")
                            response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                        })
                }, { error ->
                    logger.error("getTenant(${error.message}) <--")
                    response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                })
            }, {
                logger.error("getTenant(${it.cause}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        })
        logger.info("getTenant() <--")
    }

    private fun getTenants(rc: RoutingContext) {
        logger.info("getTenants() -->")
        execute("getTenants", rc, "admin", { _, _, response ->
            val pageNumber = Integer.valueOf(rc.request().getParam("pageNumber")) - 1
            val limit = 20
            val skip = pageNumber * limit
            val pipeline = JsonArray()
                .add(
                    JsonObject.of(
                        "\$lookup", JsonObject
                            .of(
                                "from", "app_users",
                                "localField", "client",
                                "foreignField", "email",
                                "as", "user"
                            )
                    )
                )
                .add(
                    JsonObject.of(
                        "\$lookup", JsonObject
                            .of(
                                "from", "houses",
                                "localField", "houseNumber",
                                "foreignField", "houseNumber",
                                "as", "house"
                            )
                    )
                )
                .add(
                    JsonObject.of(
                        "\$unwind", JsonObject
                            .of(
                                "path", "\$user",
                                "preserveNullAndEmptyArrays", true
                            )
                    )
                )
                .add(
                    JsonObject.of(
                        "\$unwind", JsonObject
                            .of(
                                "path", "\$house",
                                "preserveNullAndEmptyArrays", true
                            )
                    )
                )
                .add(
                    JsonObject.of(
                        "\$project", JsonObject
                            .of(
                                "_id", "\$_id",
                                "firstName", "\$user.firstName",
                                "lastName", "\$user.lastName",
                                "email", "\$user.email",
                                "phone", "\$user.phoneNumber",
                                "houseNumber", "\$house.houseNumber",
                                "rent", "\$house.rent",
                                "deposit", "\$house.deposit",
                                "floorNumber", "\$house.floorNumber"
                            )
                    )
                )
                .add(JsonObject.of("\$skip", skip))
                .add(JsonObject.of("\$limit", limit))
                .add(JsonObject.of("\$sort", JsonObject.of("_id", -1)))
            aggregate(Collections.TENANTS.toString(), pipeline, {
                val paging = JsonObject.of("page", pageNumber, "sorted", false)
                response.end(getResponse(OK.code(), "Success", JsonObject.of("data", it, "pagination", paging)))
            }, {
                logger.error("getTenants(${it.message}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        })
        logger.info("getTenants() <--")
    }

    private fun searchTenant(rc: RoutingContext) {
        logger.info("searchTenants() -->")
        execute("searchTenants", rc, "admin", { _, _, response ->
            val pageNumber = Integer.valueOf(rc.request().getParam("pageNumber")) - 1
            val term = rc.request().getParam("term") ?: ""
            val limit = 20
            val skip = pageNumber * limit
            if (term.isEmpty()) {
                response.end(getResponse(BAD_REQUEST.code(), "Expected search term"))
                return@execute
            }
            val query = JsonObject.of(
                "\$or",
                JsonArray.of(
                    JsonObject.of("house.houseNumber", JsonObject.of("\$regex", term, "\$options", "i")),
                    JsonObject.of("user.username", JsonObject.of("\$regex", term, "\$options", "i"))
                )

            )
            val pipeline = JsonArray()
                .add(
                    JsonObject.of(
                        "\$lookup", JsonObject
                            .of(
                                "from", "app_users",
                                "localField", "client",
                                "foreignField", "email",
                                "as", "user"
                            )
                    )
                )
                .add(
                    JsonObject.of(
                        "\$lookup", JsonObject
                            .of(
                                "from", "houses",
                                "localField", "houseNumber",
                                "foreignField", "houseNumber",
                                "as", "house"
                            )
                    )
                )
                .add(
                    JsonObject.of(
                        "\$unwind", JsonObject
                            .of(
                                "path", "\$user",
                                "preserveNullAndEmptyArrays", true
                            )
                    )
                )
                .add(
                    JsonObject.of(
                        "\$unwind", JsonObject
                            .of(
                                "path", "\$house",
                                "preserveNullAndEmptyArrays", true
                            )
                    )
                )
                .add(JsonObject.of("\$match", query))
                .add(
                    JsonObject.of(
                        "\$project", JsonObject
                            .of(
                                "_id", "\$_id",
                                "firstName", "\$user.firstName",
                                "lastName", "\$user.lastName",
                                "email", "\$user.email",
                                "phone", "\$user.phoneNumber",
                                "houseNumber", "\$house.houseNumber",
                                "rent", "\$house.rent",
                                "deposit", "\$house.deposit",
                                "floorNumber", "\$house.floorNumber"
                            )
                    )
                )
                .add(JsonObject.of("\$skip", skip))
                .add(JsonObject.of("\$limit", limit))
                .add(JsonObject.of("\$sort", 1))
            aggregate(Collections.TENANTS.toString(), pipeline, {
                val paging = JsonObject.of("page", pageNumber, "sorted", false)
                response.end(getResponse(OK.code(), "Success", JsonObject.of("data", it, "pagination", paging)))
            }, {
                logger.error("searchTenants(${it.message}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        })
        logger.info("searchTenants() <--")
    }

    private fun updateTenant(rc: RoutingContext) {
        logger.info("updateTenant() -->")
        execute("updateTenant", rc, "admin", { _, body, response ->
            val client = rc.request().getParam("client")
            if (client.isNullOrEmpty()) {
                response.end(getResponse(BAD_REQUEST.code(), "Expected parameter client"))
                return@execute
            }
            findAndUpdate(Collections.TENANTS.toString(), JsonObject.of("client", client), body, {
                response.end(getResponse(OK.code(), "Successfully updated tenant"))
            }, {
                logger.error("updateTenant(${it.message}) <--")
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
            findOneAndDelete(Collections.TENANTS.toString(), JsonObject.of("client", client), {
                response.end(getResponse(OK.code(), "Successfully deleted tenant"))
            }, {
                logger.error("deleteTenant(${it.message}) <--")
            })
        })
        logger.info("deleteTenant() <--")
    }
}