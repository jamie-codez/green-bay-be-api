package com.greenbay.core.service

import com.greenbay.core.Collections
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import java.util.*

open class PaymentService : TenantService() {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)

    fun setPaymentRoutes(router: Router) {
        router.post("/payments").handler(::createPayment)
        router.get("/payments/:pageNumber").handler(::getPayments)
        router.get("/payments/:term/:pageNumber").handler(::searchPayment)
        router.put("/payments/:referenceNumber").handler(::updatePayment)
        router.delete("/payments/:referenceNumber").handler(::deletePayment)
        router.delete("/payments/:referenceNumber/:email").handler(::deleteMyPayment)
        setTenantRoutes(router)
    }

    private fun createPayment(rc: RoutingContext) {
        logger.info("createPayment() -->")
        execute("createPayment", rc, "user", { user, body, response ->
            body
                .put("from", user.getString("email"))
                .put("dateCreated", Date(System.currentTimeMillis()))
                .put("verified", false)
            save(Collections.PAYMENTS.toString(), body, {
                response.end(getResponse(OK.code(), "Payment recorded successfully"))
            }, {
                logger.error("createPayment(${it.message} -> ${it.cause})")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        }, "title", "description", "transactionCode", "amount")
        logger.info("createPayment() <--")
    }

    private fun getPayments(rc: RoutingContext) {
        logger.info("searchPayment() -->")
        execute("searchPayment", rc, "user", { user, body, response ->
            val limit = 20
            val pageNumber = Integer.valueOf(rc.request().getParam("pageNumber")) - 1
            val skip = pageNumber * limit
            val owner = body.getString("owner") ?: ""
            val pipeline = JsonArray()
            if (owner == "mine") {
                pipeline.add(JsonObject.of("\$match", JsonObject.of("from", user.getString("email"))))
            }
            pipeline.add(
                JsonObject.of(
                    "\$lookup", JsonObject
                        .of(
                            "from", "app_users",
                            "localField", "from",
                            "foreignField", "email",
                            "as", "user"
                        )
                )
            )
                .add(
                    JsonObject.of(
                        "\$project", JsonObject
                            .of(
                                "_id", "\$_id",
                                "title", "\$title",
                                "description", "\$description",
                                "transactionCode", "\$transactionCode",
                                "amount", "\$amount",
                                "firstName", "user.firstname",
                                "lastName", "user.lastname",
                                "phoneNumber", "user.phoneNumber",
                                "email", "user.email"
                            )
                    )
                )
                .add(JsonObject.of("\$skip", skip))
                .add(JsonObject.of("\$limit", limit))
                .add(JsonObject.of("\$sort",JsonObject.of("_id",-1)))
            aggregate(Collections.PAYMENTS.toString(), pipeline, {
                val paging = JsonObject.of("page", pageNumber, "sorted", true)
                response.end(getResponse(OK.code(), "Success", JsonObject.of("data", it, "pagination", paging)))
            }, {
                logger.error("getPayments(${it.message} -> ${it.cause})")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        }, "owner")
        logger.info("getPayments() <--")
    }

    private fun searchPayment(rc: RoutingContext) {
        logger.info("searchPayment() -->")
        execute("searchPayment", rc, "user", { _, _, response ->
            val pageNumber = Integer.valueOf(rc.request().getParam("pageNumber")) - 1
            val term = rc.request().getParam("term") ?: ""
            val limit = 20
            val skip = pageNumber * limit
            if (term.isEmpty()) {
                response.end(getResponse(BAD_REQUEST.code(), "Expected search term"))
                return@execute
            }
            val query = JsonObject.of("\$firstName", JsonObject.of("\$regex", term,"\$options","i"))
            val pipeline = JsonArray()
                .add(JsonObject.of("\$match", query))
                .add(
                    JsonObject.of(
                        "\$lookup", JsonObject
                            .of(
                                "from", "app_users",
                                "localField", "from",
                                "foreignField", "email",
                                "as", "user"
                            )
                    )
                )
                .add(
                    JsonObject.of(
                        "\$project", JsonObject
                            .of(
                                "_id", "\$_id",
                                "title", "\$title",
                                "description", "\$description",
                                "transactionCode", "\$transactionCode",
                                "amount", "\$amount",
                                "firstName", "user.firstname",
                                "lastName", "user.lastname",
                                "phoneNumber", "user.phoneNumber",
                                "email", "user.email"
                            )
                    )
                )
                .add(JsonObject.of("\$skip", skip))
                .add(JsonObject.of("\$limit", limit))
            aggregate(Collections.PAYMENTS.toString(), pipeline, {
                val paging = JsonObject.of("page", pageNumber, "sorted", false)
                response.end(getResponse(OK.code(), "Success", JsonObject.of("data", it, "pagination", paging)))
            }, {
                logger.error("searchPayment(${it.message} -> ${it.cause})")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        })
        logger.info("searchPayment() <--")
    }

    private fun updatePayment(rc: RoutingContext) {
        logger.info("updatePayment() -->")
        execute("updatePayment", rc, "user", { user, body, response ->
            if (body.getString("from") != user.getString("email")) {
                response.end(getResponse(BAD_REQUEST.code(), "Task not authorized"))
                return@execute
            }
            val query = JsonObject
                .of(
                    "from", body.getString("from"),
                    "transactionCode", body.getString("transactionCode")
                )
            body.remove("amount")
            body.remove("verified")
            findAndUpdate(Collections.PAYMENTS.toString(), query, body, {
                response.end(getResponse(OK.code(), "Payment updated successfully", it))
            }, {
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        }, "from", "transactionCode")
        logger.info("updatePayment() <--")
    }

    private fun deletePayment(rc: RoutingContext) {
        logger.info("deletePayment() -->")
        execute("deletePayment", rc, "admin", { _, _, response ->
            val referenceNumber = rc.request().getParam("referenceNumber")
            if (referenceNumber.isNullOrEmpty()) {
                response.end(getResponse(BAD_REQUEST.code(), "Expected parameter referenceNumber"))
                return@execute
            }
            val query = JsonObject.of("referenceNumber", referenceNumber)
            findOneAndDelete(Collections.PAYMENTS.toString(), query, {
                response.end(getResponse(OK.code(), "Payment deleted successfully", it))
            }, {
                logger.error("deletePayment(${it.message} -> ${it.cause}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        })
        logger.info("deletePayment() <--")
    }

    private fun deleteMyPayment(rc: RoutingContext) {
        logger.info("deleteMyPayment() -->")
        execute("deleteMyPayment", rc, "admin", { user, _, response ->
            val referenceNumber = rc.request().getParam("referenceNumber") ?: ""
            val email = rc.request().getParam("email") ?: ""
            if (referenceNumber.isEmpty() || email.isEmpty()) {
                response.end(getResponse(BAD_REQUEST.code(), "Expected parameter referenceNumber and email"))
                return@execute
            }
            if (user.getString("email") == email) {
                val query = JsonObject.of("referenceNumber", referenceNumber, "from", email)
                findOneAndDelete(Collections.PAYMENTS.toString(), query, {
                    response.end(getResponse(OK.code(), "Payment deleted successfully", it))
                }, {
                    logger.error("deletePayment(${it.message} -> ${it.cause}) <--")
                    response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
                })
            } else {
                response.end(getResponse(BAD_REQUEST.code(), "You can only delete your own data"))
            }
        })
        logger.info("deletePayment() <--")
    }

    //            {
//                "BusinessShortCode": 174379,
//                "Password": "MTc0Mzc5YmZiMjc5ZjlhYTliZGJjZjE1OGU5N2RkNzFhNDY3Y2QyZTBjODkzMDU5YjEwZjc4ZTZiNzJhZGExZWQyYzkxOTIwMjMwMzE3MDAwMzM0",
//                "Timestamp": "20230317000334",
//                "TransactionType": "CustomerPayBillOnline",
//                "Amount": 1,
//                "PartyA": 254708374149,
//                "PartyB": 174379,
//                "PhoneNumber": 254708374149,
//                "CallBackURL": "https://mydomain.com/path",
//                "AccountReference": "CompanyXLTD",
//                "TransactionDesc": "Payment of X"
//            }

//    {
//        "ShortCode": 600989,
//        "ResponseType": "Completed",
//        "ConfirmationURL": "https://mydomain.com/confirmation",
//        "ValidationURL": "https://mydomain.com/validation",
//    }



}