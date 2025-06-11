package com.illiad.troad.security

interface Secret {
    @get:Throws(Exception::class)
    var secret: ByteArray?

    var cryptoType: Cryptos?

    var cryptoTypeByte: Byte?

    var cryptoLength: Short?

    fun offset(): ByteArray
}
