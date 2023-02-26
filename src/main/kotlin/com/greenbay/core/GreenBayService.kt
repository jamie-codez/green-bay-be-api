package com.greenbay.core

import com.greenbay.core.service.AuthService
import io.vertx.core.Promise

class GreenBayService:AuthService() {

    override fun start(startPromise: Promise<Void>?) {
        super.start(startPromise)
    }
}