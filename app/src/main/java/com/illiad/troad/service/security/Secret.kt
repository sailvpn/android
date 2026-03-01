package com.illiad.troad.service.security

interface Secret {
    @get:Throws(Exception::class)
    val secret: ByteArray?

    val cryptoType: Cryptos?

    val cryptoTypeByte: Int

    val cryptoLength: Int

    fun offset(): ByteArray?
}