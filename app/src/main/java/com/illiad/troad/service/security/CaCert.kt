package com.illiad.troad.service.security

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Base64

object CaCert {
    private const val TAG = "LECertProcurer"
    private const val CERT_FILE_NAME = "letsencrypt_root.pem"

    @Volatile
    private var cachedCertFile: File? = null

    // Memory cache for the raw PEM string content
    @Volatile
    private var cachedCertString: String? = null

    /**
     * Retrieves the Let's Encrypt Root CA as a raw PEM-formatted String.
     * Procures it from the system store if it hasn't been cached yet.
     */
    fun getCertString(context: Context): String? {
        // Return instantly if the string is already cached in memory
        cachedCertString?.let { return it }

        // Otherwise, make sure the file is created/loaded first
        val file = getCertFile(context)
        if (file != null && file.exists()) {
            try {
                val content = file.readText()
                cachedCertString = content
                return content
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read certificate file into string", e)
            }
        }
        return null
    }

    /**
     * Finds the Let's Encrypt Root CA from Android system trust store,
     * caches it to a local file, and returns the File reference.
     */
    fun getCertFile(context: Context): File? {
        cachedCertFile?.let { if (it.exists()) return it }

        synchronized(this) {
            cachedCertFile?.let { if (it.exists()) return it }

            val targetFile = File(context.cacheDir, CERT_FILE_NAME)

            if (targetFile.exists() && targetFile.length() > 0) {
                Log.d(TAG, "Reusing existing certificate file: ${targetFile.absolutePath}")
                cachedCertFile = targetFile
                return targetFile
            }

            try {
                val keyStore = KeyStore.getInstance("AndroidCAStore").apply {
                    load(null, null)
                }

                val aliases = keyStore.aliases()
                var leCert: X509Certificate? = null

                while (aliases.hasMoreElements()) {
                    val alias = aliases.nextElement()
                    val cert = keyStore.getCertificate(alias) as? X509Certificate ?: continue
                    val issuerName = cert.issuerX500Principal.name

                    if (issuerName.contains("ISRG Root X1", ignoreCase = true) ||
                        issuerName.contains("ISRG Root X2", ignoreCase = true)) {
                        leCert = cert
                        Log.d(TAG, "Located Let's Encrypt Root CA in System Store: $alias")
                        break
                    }
                }

                if (leCert != null) {
                    writeCertToPemFile(leCert, targetFile)
                    Log.d(TAG, "Successfully procured and saved root cert to: ${targetFile.absolutePath}")
                    cachedCertFile = targetFile
                    return targetFile
                } else {
                    Log.e(TAG, "Could not find Let's Encrypt Root CAs inside the Android System Trust Store.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Fatal exception while processing system trust store lookup strings", e)
            }

            return null
        }
    }

    private fun writeCertToPemFile(cert: X509Certificate, file: File) {
        val encoder = Base64.getMimeEncoder(64, "\n".toByteArray())
        val certBegin = "-----BEGIN CERTIFICATE-----\n"
        val certEnd = "\n-----END CERTIFICATE-----\n"

        val encodedCertText = String(encoder.encode(cert.encoded))

        FileOutputStream(file).use { output ->
            output.write(certBegin.toByteArray())
            output.write(encodedCertText.toByteArray())
            output.write(certEnd.toByteArray())
        }
    }
}

