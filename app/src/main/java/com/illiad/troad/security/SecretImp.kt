package com.illiad.troad.security

import android.content.res.Resources
import com.illiad.troad.R
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Random

object SecretImp : Secret {
    private val random: Random = Random()

    @get:Throws(NoSuchAlgorithmException::class)
    override var secret: ByteArray? = null
        get() {
            val digest =
                MessageDigest.getInstance(
                    Cryptos.valueOf(
                        Resources.getSystem().getString(R.string.crypto)
                    ).value!!
                )
            return digest.digest(
                Resources.getSystem().getString(R.string.secret).encodeToByteArray()
            )
        }

    override var cryptoType: Cryptos? = null
        get() = Cryptos.valueOf(Resources.getSystem().getString(R.string.crypto))

    override var cryptoTypeByte: Byte? = null
        get() = CryptoByte.toByte(Cryptos.valueOf(Resources.getSystem().getString(R.string.crypto)))

    override var cryptoLength: Short? = null
        get() = CryptoByte.byteLength(
            Cryptos.valueOf(
                Resources.getSystem().getString(R.string.crypto)
            )
        )

    override fun offset(): ByteArray {
        val length = random.nextInt(R.integer.max - R.integer.min) + R.integer.min
        val byteArray = ByteArray(length)
        random.nextBytes(byteArray)
        return byteArray
    }

}
