package com.greenbay.core.service.mpesa

import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.sql.Time
import java.util.Base64
import java.util.concurrent.TimeUnit

class Mpesa {
    companion object {
        private val logger = LoggerFactory.getLogger(Mpesa::class.java.simpleName)

        private fun authenticate(customerId: String, customerSecret: String): String {
            val password = "$customerId:$customerSecret"
            val base64Password = Base64.getEncoder().encodeToString(password.toByteArray())
            val client = client()
            val body = RequestBody.create("application/json".toMediaTypeOrNull(),"")
            val request = Request.Builder()
                .url("")
                .method("GET",body)
                .addHeader("Authorization","Bearer $base64Password")
                .addHeader("Accepts","application/json")
                .build()
            val response = client.newCall(request).execute()
            val jsonResponse = JsonObject.mapFrom(response.body?.string())
            return jsonResponse.getString("access-token")
        }

        fun express(payload:JsonObject): JsonObject {
            val client =  client()
            val mediaType = "application/json".toMediaTypeOrNull();
            val body = RequestBody.create(mediaType,payload.encode() )
            val request = Request.Builder()
                .url("https://sandbox.safaricom.co.ke/mpesa/stkpush/v1/processrequest")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer ${authenticate("","")}")
                .build()
            val response = client.newCall(request).execute()
            return JsonObject(response.body?.string())
        }

        fun registerCallback(payload: JsonObject): JsonObject {
            val client =  client()
            val mediaType = "application/json".toMediaTypeOrNull();
            val body = RequestBody.create(mediaType,payload.encode())
            val request = Request.Builder()
                .url("https://sandbox.safaricom.co.ke/mpesa/c2b/v1/registerurl")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer ${authenticate("","")}")
                .build();
            val response = client.newCall(request).execute()
            return JsonObject(response.body?.string())
        }

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


        private fun interceptor(): HttpLoggingInterceptor =
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
                level = HttpLoggingInterceptor.Level.HEADERS
                level = HttpLoggingInterceptor.Level.BASIC
            }

    }
}