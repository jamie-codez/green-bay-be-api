package com.greenbay.core.service

import com.greenbay.core.Collections
import com.greenbay.core.TaskStatus
import com.greenbay.core.utils.BaseUtils.Companion.execute
import com.greenbay.core.utils.BaseUtils.Companion.getResponse
import io.netty.handler.codec.http.HttpResponseStatus.CREATED
import io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonArray
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import java.util.Date

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
        execute("createTask", rc, "admin", { user, body, response ->
            body.put("createdBy", user.getString("email"))
                .put("createdOn", Date(System.currentTimeMillis()))
                .put("status", TaskStatus.PENDING.toString())
            dbUtil.save(Collections.TASKS.toString(), body, {
                response.end(getResponse(CREATED.code(), "Task created successfully"))
            }, {
                logger.error("createTask(${it.cause} -> creatingTask) <--")
                response.end(getResponse(INTERNAL_SERVER_ERROR.code(), "Error occurred"))
            })
        }, "to", "title", "description", "scheduleDate")
        logger.info("createTask() <--")
    }

    private fun getTasks(rc: RoutingContext) {
        logger.info("getTasks() -->")
        execute("getTasks", rc, "user", { user, body, response ->
            val pageNumber = Integer.valueOf(rc.request().getParam("pageNumber")) - 1
            val limit = 20
            val skip = limit*pageNumber
            val pipeline = JsonArray()

        })
        logger.info("getTasks() <--")
    }

    private fun searchTask(rc: RoutingContext) {
        logger.info("searchTask() -->")
        logger.info("searchTask() <--")
    }

    private fun updateTask(rc: RoutingContext) {
        logger.info("updateTask() -->")
        logger.info("updateTask() <--")
    }

    private fun deleteTask(rc: RoutingContext) {
        logger.info("deleteTask() -->")
        logger.info("deleteTask() <--")
    }

}