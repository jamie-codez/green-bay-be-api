package com.greenbay.core.service

import com.greenbay.core.Collections
import com.greenbay.core.service.mpesa.Mpesa
import com.greenbay.core.service.mpesa.Mpesa.Companion.getPassword
import com.greenbay.core.service.mpesa.Mpesa.Companion.getTimeStamp
import com.greenbay.core.utils.randomAlphabetic
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext

open class STKService : AuthService() {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)
    val string = String()

    fun setSTKRoutes(router: Router) {
        router.post("/stk-push").handler(::stkPushExpress)
        router.post("/registerCallBack").handler(::registerMpesaCallback)
        router.post("/callback").handler(::callback)
        setAuthRoutes(router)
    }

    private fun stkPushExpress(rc: RoutingContext) {
        logger.info("stkPushExpress() -->")
        execute("stkPushExpress", rc, "user", { user, body, response ->
            var amount = body.getString("amount") ?: ""
            val shortCode = System.getenv("")
            val passKey = System.getenv("")
            if (Integer.parseInt(amount) < 1) {
                response.end(getResponse(BAD_REQUEST.code(), "Amount cannot be less than 1"))
                return@execute
            }
            val phone = sanitize(user.getString("phoneNumber"))
            val referenceId = string.randomAlphabetic(8)
            amount = 0.toString()
            val payload = JsonObject()
                .put("BusinessShortCode", System.getenv("GB_MPESA_BUSINESS_NUMBER"))
                .put("Password", getPassword(shortCode, passKey))
                .put("Timestamp", getTimeStamp())
                .put("TransactionType", "CustomerPayBillOnline")
                .put("Amount", amount)
                .put("PartyA", phone)
                .put("PartyB", shortCode)
                .put("PhoneNumber", phone)
                .put("CallBackURL", System.getenv("GB_MPESA_CALLBACK"))
                .put("AccountReference", "GreenBay-${referenceId}")
                .put("TransactionDesc", "Rent")

            val result = Mpesa.express(payload)
            val resultCode = result.getString("").toInt()
            if (resultCode == 0) {
                response.end(
                    getResponse(
                        OK.code(),
                        "Payment processing. You will receive payment prompt shortly on your phone"
                    )
                )
            } else {
                response.end(getResponse(OK.code(), "Payment processing failed. Try again"))
            }
        }, "amount")
        logger.info("stkPushExpress() <--")
    }


    open fun sanitize(phone: String): Long {
        return if (phone.startsWith("+254")) {
            "254${phone.substring(4)}".toLong()
        } else if (phone.startsWith("0")) {
            "254${phone.substring(1)}".toLong()
        } else if (phone.startsWith("7")) {
            "254$phone".toLong()
        } else {
            phone.toLong()
        }
    }

    private fun registerMpesaCallback(rc: RoutingContext) {
        logger.info("callback() -->")
        execute("callback", rc, "admin", { _, _, response ->
            val res = Mpesa.registerCallback(
                JsonObject.of(
                    "ShortCode", System.getenv("GB_MPESA_BUSINESS_NUMBER"),
                    "ResponseType", "Complete",
                    "ConfirmationURL", System.getenv("GB_MPESA_CALLBACK"),
                    "ValidationURL", System.getenv("GB_MPESA_CALLBACK")
                )
            )
            if (res.getString("ResponseDescription") == "success") {
                response.end(getResponse(OK.code(), "Callback registered successful", JsonObject()))
                return@execute
            }
            response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error registering callback url", JsonObject()))
        })
        logger.info("callback() <--")
    }

    private fun callback(rc: RoutingContext) {
        logger.info("callback() -->")
        val requestBody = rc.body().asJsonObject()
        save(Collections.CALL_BACKS.toString(), requestBody, {
            logger.info("callback(Callback data saved successfully) <--")
        }, {
            logger.error("callback(${it.message} -> Error saving callback data) <--", it)
        })
        logger.info("callback() <--")
    }
}
