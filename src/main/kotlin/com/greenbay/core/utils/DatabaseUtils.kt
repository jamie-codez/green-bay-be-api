package com.greenbay.core.utils

import io.vertx.core.Vertx
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.mongo.MongoClient

class DatabaseUtils(vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)
    private val dbClient = MongoClient.create(vertx, config())

    private fun config(): JsonObject =
        JsonObject.of(
            "connection_string", System.getenv("GB_DB_CON_STRING"),
            "db_name", System.getenv("GB_DB_NAME")
        )

    private fun getDBClient(): MongoClient = this.dbClient

    fun save(
        collection: String,
        document: JsonObject,
        success: (result: String) -> Unit,
        fail: (throwable: Throwable) -> Unit
    ) {
        getDBClient().save(collection, document) {
            if (it.succeeded()) {
                logger.info("Inserted successfully -> ${document.encodePrettily()} ")
                success(it.result())
            } else {
                logger.error("Failed to insert -> ${document.encodePrettily()}")
                fail(it.cause())
            }
        }
    }

    fun find(
        collection: String,
        query: JsonObject,
        success: (result: List<JsonObject>) -> Unit,
        fail: (throwable: Throwable) -> Unit
    ) {
        getDBClient().find(collection, query) {
            if (it.succeeded()) {
                logger.info("Retrieve successful")
                success(it.result())
            } else {
                fail(it.cause())
            }
        }
    }

    fun findOne(
        collection: String,
        query: JsonObject,
        success: (result: JsonObject) -> Unit,
        fail: (throwable: Throwable) -> Unit
    ) {
        getDBClient().findOne(collection, query, JsonObject()) {
            if (it.succeeded()) {
                logger.info("Retrieve successful")
                success(it.result())
            } else {
                fail(it.cause())
            }
        }
    }

    fun findAndUpdate(
        collection: String,
        query: JsonObject,
        update: JsonObject,
        success: (result: JsonObject) -> Unit,
        fail: (throwable: Throwable) -> Unit
    ) {
        getDBClient().findOneAndUpdate(collection, query, update) {
            if (it.succeeded()) {
                logger.info("Update successful ${query.encodePrettily()} -->")
                success(it.result())
            } else {
                logger.error("Update failed ${query.encodePrettily()} -->")
                fail(it.cause())
            }
        }
    }

    fun findOneAndDelete(
        collection: String,
        query: JsonObject,
        success: (result: JsonObject) -> Unit,
        fail: (throwable: Throwable) -> Unit
    ) {
        getDBClient().findOneAndDelete(collection, query) {
            if (it.succeeded()) {
                logger.info("Delete successful ${query.encodePrettily()} -->")
                success(it.result())
            } else {
                logger.error("Error deleting ${query.encodePrettily()} -->")
                fail(it.cause())
            }
        }
    }

    fun aggregate(
        collection: String,
        pipeline: JsonArray,
        success: (result: ArrayList<JsonObject?>) -> Unit,
        fail: (throwable: Throwable) -> Unit
    ) {
        val results = ArrayList<JsonObject?>()
        getDBClient().aggregate(collection, pipeline).handler {
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
}
