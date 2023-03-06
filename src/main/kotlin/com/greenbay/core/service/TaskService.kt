package com.greenbay.core.service

import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.ext.web.Router

open class TaskService:CommunicationService() {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)
    fun setTaskRoutes(router: Router){
        setCommunicationRoutes(router)
    }
}