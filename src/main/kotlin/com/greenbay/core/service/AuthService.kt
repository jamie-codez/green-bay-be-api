package com.greenbay.core.service

import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.ext.web.Router

open class AuthService:TaskService(){
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)

    fun setAuthRoutes(router: Router){
        setTenantRoutes(router)
    }
}