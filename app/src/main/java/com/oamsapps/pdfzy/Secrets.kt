package com.oamsapps.pdfzy

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Passwords stay in memory only. WorkManager persists input Data to its database,
 * so jobs get a one-time token instead of the password itself.
 */
object Secrets {
    private val map = ConcurrentHashMap<String, String>()
    fun put(value: String): String = UUID.randomUUID().toString().also { map[it] = value }
    fun take(token: String?): String? = token?.let(map::remove)
}
