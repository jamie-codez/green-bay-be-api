package com.greenbay.core.utils

import io.vertx.core.AbstractVerticle
import io.vertx.core.Vertx
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.mongo.MongoClient

open class DatabaseUtils : AbstractVerticle() {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)
    private lateinit var dbClient: MongoClient

    init {
        dbClient = MongoClient.createShared(Vertx.vertx(), this.getConfig())
    }

    private fun getConfig(): JsonObject =
        JsonObject.of(
            "keepAlive", true,
            "socketTimeoutMS", 5_000,
            "connectTimeoutMS", 5_000,
            "maxIdleTimeMS", 90_000,
            "autoReconnect", true,
            "db_name", "greenbay_db",
            "url", "",
            "authSource", "admin"
        )

    fun getDBClient(): MongoClient = this.dbClient

    open fun save(
        collection: String,
        document: JsonObject,
        success: (result: String) -> Unit,
        fail: (throwable: Throwable) -> Unit
    ) {
        this.getDBClient().save(collection, document) {
            if (it.succeeded()) {
                logger.info("Inserted successfully -> ${document.encodePrettily()} ")
                success(it.result())
            } else {
                logger.error("Failed to insert -> ${document.encodePrettily()}")
                fail(it.cause())
            }
        }
    }

    open fun find(
        collection: String,
        query: JsonObject,
        success: (result: List<JsonObject>) -> Unit,
        fail: (throwable: Throwable) -> Unit
    ) {
        this.getDBClient().find(collection, query) {
            if (it.succeeded()) {
                logger.info("Retrieve successful")
                success(it.result())
            } else {
                logger.error("Error fetching documents")
                fail(it.cause())
            }
        }
    }

    open fun findOne(
        collection: String,
        query: JsonObject,
        success: (result: JsonObject) -> Unit,
        fail: (throwable: Throwable) -> Unit
    ) {
        this.getDBClient().findOne(collection, query, JsonObject()) {
            if (it.succeeded()) {
                logger.info("Retrieve successful")
                success(it.result())
            } else {
                logger.error("Error fetching document")
                fail(it.cause())
            }
        }
    }

    open fun findAndUpdate(
        collection: String,
        query: JsonObject,
        update: JsonObject,
        success: (result: JsonObject) -> Unit,
        fail: (throwable: Throwable) -> Unit
    ) {
        this.getDBClient().findOneAndUpdate(collection, query, update) {
            if (it.succeeded()) {
                logger.info("Update successful ${query.encodePrettily()} -->")
                success(it.result())
            } else {
                logger.error("Update failed ${query.encodePrettily()} -->")
                fail(it.cause())
            }
        }
    }

    open fun findOneAndDelete(
        collection: String,
        query: JsonObject,
        success: (result: JsonObject) -> Unit,
        fail: (throwable: Throwable) -> Unit
    ) {
        this.getDBClient().findOneAndDelete(collection, query) {
            if (it.succeeded()) {
                logger.info("Delete successful ${query.encodePrettily()} -->")
                success(it.result())
            } else {
                logger.error("Error deleting ${query.encodePrettily()} -->")
                fail(it.cause())
            }
        }
    }

    open fun aggregate(
        collection: String,
        pipeline: JsonArray,
        success: (result: ArrayList<JsonObject?>) -> Unit,
        fail: (throwable: Throwable) -> Unit
    ) {
        val results = ArrayList<JsonObject?>()
        this.getDBClient().aggregate(collection, pipeline).handler {
            logger.info("aggregate(streaming data) -->")
            results.add(it)
        }.endHandler {
            logger.info("Pipeline was successful ${pipeline.encodePrettily()}")
            success(results)
        }.exceptionHandler {
            logger.error("Pipeline failed")
            fail(Throwable("Error occurred"))
        }
    }

    open fun createIndex(
        collection: String,
        query: JsonObject,
        success: (result:Void) -> Unit,
        fail: (throwable: Throwable) -> Unit
    ) {
        this.getDBClient().createIndex(collection,query){
            if (it.succeeded()){
                success(it.result())
            }else{
                fail(it.cause())
            }
        }
    }
}
