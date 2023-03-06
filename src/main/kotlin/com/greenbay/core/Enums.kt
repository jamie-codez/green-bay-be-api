package com.greenbay.core


enum class Collections(private var value: String) {
    APP_USERS("app_user"),
    HOUSES("houses"),
    TENANTS("tenants"),
    PAYMENTS("payments"),
    SESSIONS("sessions"),
    COMMUNICATIONS("communications");

    override fun toString(): String {
        return value.trim()
    }
}