/*
 * Copyright (c) 2020 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.core.crypto.common.pgp

import me.proton.core.crypto.common.pgp.exception.CryptoException
import me.proton.core.crypto.common.keystore.use

/**
 * PGP Cryptographic interface (e.g. [lock], [unlock], [encryptData], [decryptData], [signData], [verifyData], ...).
 */
interface PGPCrypto {

    /**
     * Lock [unlockedKey] using [passphrase].
     *
     * @throws [CryptoException] if [unlockedKey] cannot be locked using [passphrase].
     *
     * @see [unlock]
     */
    fun lock(unlockedKey: Unarmored, passphrase: ByteArray): Armored

    /**
     * Unlock [privateKey] using [passphrase].
     *
     * @return [UnlockedKey] implementing Closeable to clear memory after usage.
     *
     * @throws [CryptoException] if [privateKey] cannot be unlocked using [passphrase].
     *
     * @see [lock]
     */
    fun unlock(privateKey: Armored, passphrase: ByteArray): UnlockedKey

    /**
     * Sign [plainText] using [unlockedKey].
     *
     * @throws [CryptoException] if [plainText] cannot be signed.
     *
     * @see [verifyText]
     */
    fun signText(plainText: String, unlockedKey: Unarmored): Signature

    /**
     * Sign [data] using [unlockedKey].
     *
     * @throws [CryptoException] if [data] cannot be signed.
     *
     * @see [verifyData]
     */
    fun signData(data: ByteArray, unlockedKey: Unarmored): Signature

    /**
     * Verify [signature] of [plainText] is correctly signed using [publicKey].
     *
     * @param validAtUtc UTC time for [signature] validation, or 0 to ignore time.
     *
     * @see [signText]
     */
    fun verifyText(plainText: String, signature: Armored, publicKey: Armored, validAtUtc: Long = 0): Boolean

    /**
     * Verify [signature] of [data] is correctly signed using [publicKey].
     *
     * @param validAtUtc UTC time for [signature] validation, or 0 to ignore time.
     *
     * @see [signData]
     */
    fun verifyData(data: ByteArray, signature: Armored, publicKey: Armored, validAtUtc: Long = 0): Boolean

    /**
     * Decrypt [message] as [String] using [unlockedKey].
     *
     * Note: String canonicalization/standardization is applied.
     *
     * @throws [CryptoException] if [message] cannot be decrypted.
     *
     * @see [encryptText]
     */
    fun decryptText(message: EncryptedMessage, unlockedKey: Unarmored): String

    /**
     * Decrypt [message] as [ByteArray] using [unlockedKey].
     *
     * @throws [CryptoException] if [message] cannot be decrypted.
     *
     * @see [encryptData]
     */
    fun decryptData(message: EncryptedMessage, unlockedKey: Unarmored): ByteArray

    /**
     * Encrypt [plainText] using [publicKey].
     *
     * @throws [CryptoException] if [plainText] cannot be encrypted.
     *
     * @see [decryptText].
     */
    fun encryptText(plainText: String, publicKey: Armored): EncryptedMessage

    /**
     * Encrypt [data] using [publicKey].
     *
     * @throws [CryptoException] if [data] cannot be encrypted.
     *
     * @see [decryptData].
     */
    fun encryptData(data: ByteArray, publicKey: Armored): EncryptedMessage

    /**
     * Encrypt [plainText] using [publicKey] and sign using [unlockedKey] in an embedded [EncryptedMessage].
     *
     * @throws [CryptoException] if [plainText] cannot be encrypted or signed.
     *
     * @see [decryptAndVerifyText].
     */
    fun encryptAndSignText(plainText: String, publicKey: Armored, unlockedKey: Unarmored): EncryptedMessage

    /**
     * Encrypt [data] using [publicKey] and sign using [unlockedKey] in an embedded [EncryptedMessage].
     *
     * @throws [CryptoException] if [data] cannot be encrypted or signed.
     *
     * @see [decryptAndVerifyData].
     */
    fun encryptAndSignData(data: ByteArray, publicKey: Armored, unlockedKey: Unarmored): EncryptedMessage

    /**
     * Decrypt [message] as [String] using [unlockedKeys] and verify using [publicKeys].
     *
     * Note: String canonicalization/standardization is applied.
     *
     * @param validAtUtc UTC time for embedded signature validation, or 0 to ignore time.
     *
     * @throws [CryptoException] if [message] cannot be decrypted.
     *
     * @see [DecryptedText]
     * @see [VerificationStatus]
     * @see [encryptAndSignText]
     */
    fun decryptAndVerifyText(
        message: EncryptedMessage,
        publicKeys: List<Armored>,
        unlockedKeys: List<Unarmored>,
        validAtUtc: Long = 0
    ): DecryptedText

    /**
     * Decrypt [message] as [ByteArray] using [unlockedKeys] and verify using [publicKeys].
     *
     * @param validAtUtc UTC time for embedded signature validation, or 0 to ignore time.
     *
     * @throws [CryptoException] if [message] cannot be decrypted.
     *
     * @see [DecryptedData]
     * @see [VerificationStatus]
     * @see [encryptAndSignData]
     */
    fun decryptAndVerifyData(
        message: EncryptedMessage,
        publicKeys: List<Armored>,
        unlockedKeys: List<Unarmored>,
        validAtUtc: Long = 0
    ): DecryptedData

    /**
     * Get [Armored] public key from [privateKey].
     *
     * @throws [CryptoException] if public key cannot be extracted from [privateKey].
     */
    fun getPublicKey(privateKey: Armored): Armored

    /**
     * Get fingerprint from [key].
     *
     * @throws [CryptoException] if fingerprint cannot be extracted from [key].
     */
    fun getFingerprint(key: Armored): String

    /**
     * Get JSON SHA256 fingerprints from [key] and corresponding sub-keys.
     *
     * @throws [CryptoException] if fingerprints cannot be extracted from [key].
     */
    fun getJsonSHA256Fingerprints(key: Armored): String

    /**
     * Get passphrase from [password] using [encodedSalt].
     *
     * Note: Consider using [use] on returned [ByteArray], to clear memory after usage.
     */
    fun getPassphrase(password: ByteArray, encodedSalt: String): ByteArray

    /**
     * Generate new random salt.
     *
     * @return 16-byte, base64-ed random salt as String, without newline character.
     */
    fun generateNewKeySalt(): String

    /**
     * Generate new random Token.
     */
    fun generateNewToken(size: Long): ByteArray

    /**
     * Generate new private key.
     *
     * @param username for which the key is generated.
     * @param domain for which the key is generated.
     * @param passphrase passphrase to lock the key.
     * @param keyType [KeyType.RSA] or [KeyType.X25519].
     * @param keySecurity key length in bits.
     *
     * @throws [CryptoException] if key cannot be generated.
     */
    fun generateNewPrivateKey(
        username: String,
        domain: String,
        passphrase: ByteArray,
        keyType: KeyType,
        keySecurity: KeySecurity
    ): Armored

    enum class KeySecurity(val value: Int) {
        HIGH(2048),
        EXTREME(4096),
    }

    enum class KeyType(private val value: String) {
        RSA("rsa"),
        X25519("x25519");

        override fun toString(): String = value
    }
}
