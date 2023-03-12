package com.greenbay.core.service

import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext

open class STKService:AuthService() {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)
    fun setSTKRoutes(router: Router){
        router.post("/stk-push").handler(::stkPushExpress)
        setAuthRoutes(router)
    }
    private fun stkPushExpress(rc:RoutingContext){

    }
}