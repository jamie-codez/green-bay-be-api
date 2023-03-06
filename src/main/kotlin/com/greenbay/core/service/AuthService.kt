package com.greenbay.core.service

import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.ext.web.Router

open class AuthService:TaskService(){
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)

    fun setAuthRoutes(router: Router){
        router.post("/login").handler(::login)
        router.post("/logout").handler(::logout)
        router.post("/sendPasswordRestEmail").handler(::sendPasswordResetEmail)
        router.get("/sendPasswordPage").handler(::sendPaaswordPage)
        router.post("/resetEmail").handler(::resetPassword)
        setTenantRoutes(router)
    }
}