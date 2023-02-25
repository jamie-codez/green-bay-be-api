package com.greenbay.core


enum class Collections(private var value: String) {
    APP_USERS("app_user"),
    ADMINS("admins");

    override fun toString(): String {
        return value.trim()
    }
}