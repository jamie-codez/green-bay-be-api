package com.greenbay.core


enum class Collections(private var value: String) {
    APP_USERS("app_user"),
    HOUSES("houses"),
    TENANTS("tenants"),
    PAYMENTS("payments"),
    SESSIONS("sessions"),
    RESET_CODES("reset_codes"),
    TASKS("TASKS"),
    COMMUNICATIONS("communications");

    override fun toString(): String {
        return value.trim()
    }
}

enum class TaskStatus(private var value: String){
    COMPLETED("COMPLETED"),
    STARTED("STARTED"),
    PENDING("PENDING");

    override fun toString(): String {
        return value.trim()
    }
}