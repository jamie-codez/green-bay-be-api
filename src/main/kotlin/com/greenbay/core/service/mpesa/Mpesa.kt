package com.greenbay.core.service.mpesa

import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.sql.Time
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class Mpesa {
    companion object {
        private val logger = LoggerFactory.getLogger(Mpesa::class.java.simpleName)

        private fun authenticate(customerId: String, customerSecret: String): String {
            logger.info("authenticate() -->")
            val passKey = encodePass(customerId, customerSecret)
            val base64Password = Base64.getEncoder().encodeToString(passKey.toByteArray())
            val client = client()
            val body = "".toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("https://sandbox.safaricom.co.ke/oauth/v1/generate?grant_type=client_credentials")
                .method("GET", body)
                .addHeader("Authorization", "Bearer $base64Password")
                .addHeader("Accepts", "application/json")
                .build()
            val response = client.newCall(request).execute()
            val jsonResponse = JsonObject.mapFrom(response.body?.string())
            logger.info("authenticate() <--")
            return jsonResponse.getString("access-token")
        }

        private fun encodePass(consumerId: String, consumerSecret: String) =
            Base64.getEncoder().encodeToString("$consumerId:$consumerSecret".toByteArray())

        fun express(payload: JsonObject): JsonObject {
            logger.info("express() -->")
            val client = client()
            val mediaType = "application/json".toMediaTypeOrNull();
            val body = payload.encode().toRequestBody(mediaType)
            val request = Request.Builder()
                .url("https://sandbox.safaricom.co.ke/mpesa/stkpush/v1/processrequest")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader(
                    "Authorization",
                    "Bearer ${authenticate(System.getenv("GB_MPESA_APP_ID"), System.getenv("GB_MPESA_APP_SECRET"))}"
                )
                .build()
            val response = client.newCall(request).execute()
            logger.info("express() <--")
            return JsonObject(response.body?.string())
        }

        fun registerCallback(payload: JsonObject): JsonObject {
            logger.info("registerCallback() -->")
            val client = client()
            val mediaType = "application/json".toMediaTypeOrNull();
            val body = payload.encode().toRequestBody(mediaType)
            val request = Request.Builder()
                .url("https://sandbox.safaricom.co.ke/mpesa/c2b/v1/registerurl")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader(
                    "Authorization",
                    "Bearer ${authenticate(System.getenv("GB_MPESA_APP_ID"), System.getenv("GB_MPESA_APP_SECRET"))}"
                )
                .build()
            val response = client.newCall(request).execute()
            logger.info("registerCallback() <--")
            return JsonObject(response.body?.string())
        }

        fun getTimeStamp() =
            SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date(System.currentTimeMillis()))

        fun getPassword(shortCode: String, passKey: String) =
            Base64.getEncoder().encodeToString("$shortCode$passKey${getTimeStamp()}".toByteArray())

        @JvmStatic
        fun client(): OkHttpClient =
            OkHttpClient.Builder()
                .callTimeout(10000, TimeUnit.MILLISECONDS)
                .connectTimeout(20000, TimeUnit.MILLISECONDS)
                .readTimeout(30000, TimeUnit.MILLISECONDS)
                .writeTimeout(15000, TimeUnit.MILLISECONDS)
                .connectTimeout(30000, TimeUnit.MILLISECONDS)
                .pingInterval(60000, TimeUnit.MILLISECONDS)
                .addInterceptor(interceptor())
                .build()

        @JvmStatic
        private fun interceptor(): HttpLoggingInterceptor =
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
                level = HttpLoggingInterceptor.Level.HEADERS
                level = HttpLoggingInterceptor.Level.BASIC
            }

    }
}