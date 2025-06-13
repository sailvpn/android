package com.illiad.troad.service

import java.util.Random

object HandlerNamer {

    private val random: Random = Random()

    val prefix: String = generateRandomAlphanumeric(5)

    var name: String = ""
        get() {
            return this.prefix + generateRandomAlphanumeric(5)
        }

    private fun generateRandomAlphanumeric(length: Int): String {
        val sb = StringBuilder(length)
        val alphanumeric = "abcdefghijklmnopqrstuvwxyz0123456789"
        for (i in 0..<length) {
            sb.append(alphanumeric.get(random.nextInt(alphanumeric.length)))
        }
        return sb.toString()
    }

}