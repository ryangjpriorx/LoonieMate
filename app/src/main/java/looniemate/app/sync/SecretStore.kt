package looniemate.app.sync

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the sync password on this phone, encrypted with a key that lives in Android's Keystore (it can't be copied
 * off the phone). Never sent anywhere. docs/plans/sync.md.
 */
object SecretStore {
    private const val ALIAS = "looniemate-sync"
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return g.generateKey()
    }
    private val b64e = java.util.Base64.getEncoder(); private val b64d = java.util.Base64.getDecoder()

    fun seal(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key())
        return b64e.encodeToString(c.iv) + ":" + b64e.encodeToString(c.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    fun open(sealed: String?): String? = try {
        val (iv, ct) = sealed!!.split(":").let { b64d.decode(it[0]) to b64d.decode(it[1]) }
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(c.doFinal(ct), Charsets.UTF_8)
    } catch (_: Exception) { null }
}
