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
import java.util.*

open class PaymentService : TenantService() {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)

    fun setPaymentRoutes(router: Router) {
        router.post("/payment").handler(::createPayment)
        router.get("/payment/:pageNumber").handler(::getPayments)
        router.put("/payment/:referenceNumber").handler(::updatePayment)
        router.delete("/payment/:referenceNumber").handler(::deletePayment)
        setTenantRoutes(router)
    }

    private fun createPayment(rc: RoutingContext) {
        logger.info("createPayment() -->")
        execute("createPayment", rc, "user", { user, body, response ->
            body
                .put("from", user.getString("email"))
                .put("dateCreated", Date(System.currentTimeMillis()))
                .put("verified", false)
            dbUtil.save(Collections.PAYMENTS.toString(), body, {
                response.end(getResponse(OK.code(), "Payment recorded successfully"))
            }, {
                logger.error("createPayment(${it.message} -> ${it.cause})")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        }, "title", "description", "transactionCode", "amount")
        logger.info("createPayment() <--")
    }

    private fun getPayments(rc: RoutingContext) {
        logger.info("getPayments() -->")
        execute("createPayment", rc, "user", { user, body, response ->
            val limit = 20
            val pageNumber = Integer.valueOf(rc.request().getParam("pageNumber")) - 1
            val skip = pageNumber * limit
            val pipeline = JsonArray()
                .add(JsonObject.of("\$skip", skip))
                .add(JsonObject.of("\$limit", limit))
            dbUtil.aggregate(Collections.PAYMENTS.toString(), pipeline, {
                response.end(getResponse(OK.code(), "Successful", it))
            }, {
                logger.error("getPayments(${it.message} -> ${it.cause})")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        })
        logger.info("getPayments() <--")
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
            dbUtil.findAndUpdate(Collections.PAYMENTS.toString(), query, body, {
                response.end(getResponse(OK.code(), "Payment updated successfully", it))
            }, {
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        }, "from", "transactionCode")
        logger.info("updatePayment() <--")
    }

    private fun deletePayment(rc: RoutingContext) {
        logger.info("deletePayment() -->")
        execute("deletePayment", rc, "admin", { user, body, response ->
            val referenceNumber = rc.request().getParam("referenceNumber")
            if (referenceNumber.isNullOrEmpty()) {
                response.end(getResponse(BAD_REQUEST.code(), "Expected parameter referenceNumber"))
                return@execute
            }
            val query = JsonObject.of("referenceNumber", referenceNumber)
            dbUtil.findOneAndDelete(Collections.PAYMENTS.toString(), query, {
                response.end(getResponse(OK.code(), "Payment deleted successfully", it))
            }, {
                logger.error("deletePayment(${it.message} -> ${it.cause}) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred try again"))
            })
        })
        logger.info("deletePayment() <--")
    }

}