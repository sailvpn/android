package com.illiad.troad.service.security

interface Secret {
    @get:Throws(Exception::class)
    val secret: ByteArray?

    val cryptoType: Cryptos?

    val cryptoTypeByte: Byte

    val cryptoLength: Short

    fun offset(): ByteArray?
}