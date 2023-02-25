package com.greenbay.core


enum class Collections(private var value: String) {
    TENANTS("app_user"),
    ADMINS("admins");

    override fun toString(): String {
        return value.trim()
    }
}