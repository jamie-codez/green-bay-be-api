package com.greenbay.core.service

import com.greenbay.core.Collections
import com.greenbay.core.utils.BaseUtils.Companion.execute
import com.greenbay.core.utils.BaseUtils.Companion.verifyAccess
import com.greenbay.core.utils.DatabaseUtils
import io.vertx.core.AbstractVerticle
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext

class UserService : AbstractVerticle() {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)
    private val dbUtil = DatabaseUtils(this.vertx)

    fun setUserRoutes(router: Router) {
        router.post("/createUser").handler(::createUser)
        router.post("/getUsers").handler(::getUsers)
        router.put("/update/:email").handler(::updateUser)
        router.delete("/delete/:email").handler(::deleteUser)
    }

    private fun createUser(rc: RoutingContext) {
        execute("createUser", rc, { accessToken, body, response ->
            verifyAccess("createUser", accessToken, {
                val email = body.getString("email")
                dbUtil.findOne(Collections.APP_USERS.toString(), JsonObject.of("email",email),{

                },{})
            }, "admin", response)
        }, "email", "password", "username")
    }
    private fun getUsers(rc: RoutingContext){

    }
    private fun updateUser(rc:RoutingContext){

    }
    private fun deleteUser(rc:RoutingContext){

    }
}