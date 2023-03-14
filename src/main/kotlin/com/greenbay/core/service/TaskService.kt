package com.greenbay.core.service

import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext

open class TaskService : CommunicationService() {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)
    fun setTaskRoutes(router: Router) {
        router.post("/tasks").handler(::createTask)
        router.get("/tasks/:pageNumber").handler(::getTasks)
        router.get("/tasks/:term/:pageNumber").handler(::searchTask)
        router.put("/tasks/:id").handler(::updateTask)
        router.delete("/tasks/:id").handler(::deleteTask)
        setCommunicationRoutes(router)
    }

    private fun createTask(rc: RoutingContext) {
        logger.info("createTask() -->")
        logger.info("createTask() <--")
    }

    private fun getTasks(rc: RoutingContext) {
        logger.info("getTasks() -->")
        logger.info("getTasks() <--")
    }

    private fun searchTask(rc: RoutingContext) {

    }

    private fun updateTask(rc: RoutingContext) {

    }

    private fun deleteTask(rc: RoutingContext) {

    }

}