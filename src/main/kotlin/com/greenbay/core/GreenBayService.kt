package com.greenbay.core

import com.greenbay.core.service.AuthService
import com.greenbay.core.service.STKService
import com.greenbay.core.utils.BaseUtils.Companion.getResponse
import io.netty.handler.codec.http.HttpResponseStatus.OK
import io.vertx.core.Promise
import io.vertx.core.http.HttpMethod
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.ext.healthchecks.HealthCheckHandler
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CorsHandler

open class GreenBayService : STKService() {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)
    private val port = Integer.valueOf(System.getenv("GB_PORT"))

    override fun start(startPromise: Promise<Void>) {
        val vertx = this.getVertx()
        val router = Router.router(vertx)
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(port) {
                if (it.succeeded()) {
                    logger.info("Server started on port: $port")
                    startPromise.complete()
                    setRoutes(router)
                } else {
                    logger.error("Server failed to start")
                    startPromise.fail(it.cause().message)
                }
            }
    }

    protected open fun setRoutes(router: Router){
        router.route().handler(BodyHandler.create())
        router.route().handler(
            CorsHandler.create()
                .allowedHeaders(
                    setOf(
                        "access-token",
                        "refresh-token",
                        "Access-Control-Request-Method",
                        "Access-Control-Allow-Credentials",
                        "Access-Control-Allow-Origin",
                        "Access-Control-Allow-Headers",
                        "Content-Type",
                        "Accept"
                    )
                )
                .allowedMethods(setOf(HttpMethod.POST, HttpMethod.GET, HttpMethod.PUT, HttpMethod.DELETE))
        )
        router.get("/").handler(::ping)
        setSTKRoutes(router)
    }

    open fun setHealthChech(router: Router){
        val health = HealthCheckHandler.create(vertx)
        health.register("ws",)
    }

    private fun ping(rc: RoutingContext) {
        rc.response().apply {
            statusCode = OK.code()
            statusMessage = OK.reasonPhrase()
        }.putHeader("content-type", "application/json")
            .end(getResponse(OK.code(), "Server running on port: $port"))
    }
}