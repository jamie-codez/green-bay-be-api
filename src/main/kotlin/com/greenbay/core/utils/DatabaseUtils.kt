package com.greenbay.core.utils

import io.vertx.core.Vertx
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonObject
import io.vertx.ext.mongo.MongoClient

class DatabaseUtils(vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)
    private val dbClient = MongoClient.createShared(vertx, config())

    private fun config(): JsonObject =
        JsonObject.of("connection_string", System.getenv("DB_CON_STRING"), "db_name", System.getenv("DB_NAME"))

    fun getDBClient() = this.dbClient

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
        getDBClient().findOneAndDelete(collection, query){
            if (it.succeeded()){
                logger.info("Delete successful ${query.encodePrettily()} -->")
                success(it.result())
            }else{
                logger.error("Error deleting ${query.encodePrettily()} -->")
                fail(it.cause())
            }
        }
    }
}