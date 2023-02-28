package com.greenbay.core

import com.greenbay.core.service.AuthService
import io.vertx.core.Promise
import io.vertx.core.impl.logging.LoggerFactory

class GreenBayService:AuthService() {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)

    override fun start(startPromise: Promise<Void>?) {
        super.start(startPromise)
    }
}