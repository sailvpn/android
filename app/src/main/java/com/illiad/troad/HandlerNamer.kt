package com.illiad.troad

import java.util.Random

class HandlerNamer {
    companion object {
        private val random: Random = Random()
        val prefix: String = generateRandomAlphanumeric(random, 5)

        fun generateName(): String {
            return this.prefix + generateRandomAlphanumeric(random, 5)
        }

        private fun generateRandomAlphanumeric(random: Random, length: Int): String {
            val sb = StringBuilder(length)
            val alphanumeric = "abcdefghijklmnopqrstuvwxyz0123456789"
            for (i in 0..<length) {
                sb.append(alphanumeric.get(random.nextInt(alphanumeric.length)))
            }
            return sb.toString()
        }
    }
}
