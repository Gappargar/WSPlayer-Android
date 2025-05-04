
package com.example.wsplayer.utils

import java.nio.charset.Charset
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Random

/*------------------------------------------------------------------------------
                                                                           class
                                                                        MD5Crypt

------------------------------------------------------------------------------*/
/**
 * This class defines a method,
 * [crypt()][MD5Crypt.crypt], which
 * takes a password and a salt string and generates an OpenBSD/FreeBSD/Linux-compatible
 * md5-encoded password entry.
 */
object MD5Crypt {
    /**
     * Command line test rig.
     */
    @JvmStatic
    fun main(argv: Array<String>) {
        if ((argv.size < 1) || (argv.size > 3)) {
            System.err.println("Usage: MD5Crypt [-apache] password salt")
            System.exit(1)
        }

        if (argv.size == 3) {
            System.err.println(apacheCrypt(argv[1], argv[2]))
        } else if (argv.size == 2) {
            System.err.println(crypt(argv[0], argv[1]))
        } else {
            System.err.println(crypt(argv[0]))
        }

        System.exit(0)
    }

    private fun UTF8(): Charset {
        return Charset.forName("UTF-8")
    }

    private const val SALTCHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"
    private const val itoa64 = "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    private fun to64(v: Long, size: Int): String {
        var v = v
        var size = size
        val result = StringBuilder()

        while (--size >= 0) {
            result.append(itoa64[(v and 0x3fL).toInt()])
            v = v ushr 6
        }

        return result.toString()
    }

    private fun clearbits(bits: ByteArray) {
        for (i in bits.indices) {
            bits[i] = 0
        }
    }

    /**
     * convert an encoded unsigned byte value into a int
     * with the unsigned value.
     */
    private fun bytes2u(inp: Byte): Int {
        return inp.toInt() and 0xff
    }

    private val mD5: MessageDigest
        get() {
            try {
                return MessageDigest.getInstance("MD5")
            } catch (ex: NoSuchAlgorithmException) {
                throw RuntimeException(ex)
            }
        }

    /**
     *
     * This method actually generates a OpenBSD/FreeBSD/Linux PAM compatible
     * md5-encoded password hash from a plaintext password and a
     * salt.
     *
     *
     * The resulting string will be in the form '$1$&lt;salt&gt;$&lt;hashed mess&gt;
     *
     * @param password Plaintext password
     *
     * @return An OpenBSD/FreeBSD/Linux-compatible md5-hashed password field.
     */
    fun crypt(password: String): String {
        val salt = StringBuilder()
        val randgen = Random()

        /* -- */
        while (salt.length < 8) {
            val index = (randgen.nextFloat() * SALTCHARS.length).toInt()
            salt.append(SALTCHARS.substring(index, index + 1))
        }

        return crypt(password, salt.toString())
    }

    /**
     *
     * This method actually generates a OpenBSD/FreeBSD/Linux PAM compatible
     * md5-encoded password hash from a plaintext password and a
     * salt.
     *
     *
     * The resulting string will be in the form '$1$&lt;salt&gt;$&lt;hashed mess&gt;
     *
     * @param password Plaintext password
     * @param salt A short string to use to randomize md5.  May start with $1$, which
     * will be ignored.  It is explicitly permitted to pass a pre-existing
     * MD5Crypt'ed password entry as the salt.  crypt() will strip the salt
     * chars out properly.
     *
     * @return An OpenBSD/FreeBSD/Linux-compatible md5-hashed password field.
     */
    fun crypt(password: String, salt: String): String {
        return crypt(password, salt, "$1$")
    }

    /**
     *
     * This method generates an Apache MD5 compatible
     * md5-encoded password hash from a plaintext password and a
     * salt.
     *
     *
     * The resulting string will be in the form '$apr1$&lt;salt&gt;$&lt;hashed mess&gt;
     *
     * @param password Plaintext password
     *
     * @return An Apache-compatible md5-hashed password string.
     */
    fun apacheCrypt(password: String): String {
        val salt = StringBuilder()
        val randgen = Random()

        /* -- */
        while (salt.length < 8) {
            val index = (randgen.nextFloat() * SALTCHARS.length).toInt()
            salt.append(SALTCHARS.substring(index, index + 1))
        }

        return apacheCrypt(password, salt.toString())
    }

    /**
     *
     * This method actually generates an Apache MD5 compatible
     * md5-encoded password hash from a plaintext password and a
     * salt.
     *
     *
     * The resulting string will be in the form '$apr1$&lt;salt&gt;$&lt;hashed mess&gt;
     *
     * @param password Plaintext password
     * @param salt A short string to use to randomize md5.  May start with $apr1$, which
     * will be ignored.  It is explicitly permitted to pass a pre-existing
     * MD5Crypt'ed password entry as the salt.  crypt() will strip the salt
     * chars out properly.
     *
     * @return An Apache-compatible md5-hashed password string.
     */
    fun apacheCrypt(password: String, salt: String): String {
        return crypt(password, salt, "\$apr1$")
    }

    /**
     *
     * This method actually generates md5-encoded password hash from
     * a plaintext password, a salt, and a magic string.
     *
     *
     * There are two magic strings that make sense to use here.. '$1$' is the
     * magic string used by the FreeBSD/Linux/OpenBSD MD5Crypt algorithm, and
     * '$apr1$' is the magic string used by the Apache MD5Crypt algorithm.
     *
     *
     * The resulting string will be in the form '&lt;magic&gt;&lt;salt&gt;$&lt;hashed mess&gt;
     *
     * @param password Plaintext password
     * @param salt A short string to use to randomize md5.  May start
     * with the magic string, which will be ignored.  It is explicitly
     * permitted to pass a pre-existing MD5Crypt'ed password entry as
     * the salt.  crypt() will strip the salt chars out properly.
     * @param magic Either "$apr1$" or "$1$", which controls whether we
     * are doing Apache-style or FreeBSD-style md5Crypt.
     *
     * @return An md5-hashed password string.
     */
    fun crypt(password: String, salt: String, magic: String): String {
        /* This string is magic for this algorithm.  Having it this way,
     * we can get get better later on */

        var salt = salt
        var finalState: ByteArray

        /* -- */

        /* Refine the Salt first */

        /* If it starts with the magic string, then skip that */
        if (salt.startsWith(magic)) {
            salt = salt.substring(magic.length)
        }

        /* It stops at the first '$', max 8 chars */
        if (salt.indexOf('$') != -1) {
            salt = salt.substring(0, salt.indexOf('$'))
        }

        if (salt.length > 8) {
            salt = salt.substring(0, 8)
        }

        val ctx = mD5

        ctx.update(password.toByteArray(UTF8())) // The password first, since that is what is most unknown
        ctx.update(magic.toByteArray(UTF8())) // Then our magic string
        ctx.update(salt.toByteArray(UTF8())) // Then the raw salt

        /* Then just as many characters of the MD5(pw,salt,pw) */
        val ctx1 = mD5
        ctx1.update(password.toByteArray(UTF8()))
        ctx1.update(salt.toByteArray(UTF8()))
        ctx1.update(password.toByteArray(UTF8()))
        finalState = ctx1.digest()

        var pl = password.length
        while (pl > 0) {
            ctx.update(finalState, 0, if (pl > 16) 16 else pl)
            pl -= 16
        }

        /* the original code claimed that finalState was being cleared
       to keep dangerous bits out of memory, but doing this is also
       required in order to get the right output. */
        clearbits(finalState)

        /* Then something really weird... */
        run {
            var i = password.length
            while (i != 0) {
                if ((i and 1) != 0) {
                    ctx.update(finalState, 0, 1)
                } else {
                    ctx.update(password.toByteArray(UTF8()), 0, 1)
                }
                i = i ushr 1
            }
        }

        finalState = ctx.digest()

        /*
     * and now, just to make sure things don't run too fast
     * On a 60 Mhz Pentium this takes 34 msec, so you would
     * need 30 seconds to build a 1000 entry dictionary...
     *
     * (The above timings from the C version)
     */
        for (i in 0..999) {
            ctx1.reset()

            if ((i and 1) != 0) {
                ctx1.update(password.toByteArray(UTF8()))
            } else {
                ctx1.update(finalState, 0, 16)
            }

            if ((i % 3) != 0) {
                ctx1.update(salt.toByteArray(UTF8()))
            }

            if ((i % 7) != 0) {
                ctx1.update(password.toByteArray(UTF8()))
            }

            if ((i and 1) != 0) {
                ctx1.update(finalState, 0, 16)
            } else {
                ctx1.update(password.toByteArray(UTF8()))
            }

            finalState = ctx1.digest()
        }

        /* Now make the output string */
        val result = StringBuilder()

        result.append(magic)
        result.append(salt)
        result.append("$")

        var l = ((bytes2u(finalState[0]) shl 16) or (bytes2u(finalState[6]) shl 8) or bytes2u(
            finalState[12]
        )).toLong()
        result.append(to64(l, 4))

        l = ((bytes2u(finalState[1]) shl 16) or (bytes2u(finalState[7]) shl 8) or bytes2u(
            finalState[13]
        )).toLong()
        result.append(to64(l, 4))

        l = ((bytes2u(finalState[2]) shl 16) or (bytes2u(finalState[8]) shl 8) or bytes2u(
            finalState[14]
        )).toLong()
        result.append(to64(l, 4))

        l = ((bytes2u(finalState[3]) shl 16) or (bytes2u(finalState[9]) shl 8) or bytes2u(
            finalState[15]
        )).toLong()
        result.append(to64(l, 4))

        l = ((bytes2u(finalState[4]) shl 16) or (bytes2u(finalState[10]) shl 8) or bytes2u(
            finalState[5]
        )).toLong()
        result.append(to64(l, 4))

        l = bytes2u(finalState[11]).toLong()
        result.append(to64(l, 2))

        /* Don't leave anything around in vm they could use. */
        clearbits(finalState)

        return result.toString()
    }

    /**
     * This method tests a plaintext password against a md5Crypt'ed hash and returns
     * true if the password matches the hash.
     *
     * This method will work properly whether the hashtext was crypted
     * using the default FreeBSD md5Crypt algorithm or the Apache
     * md5Crypt variant.
     *
     * @param plaintextPass The plaintext password text to test.
     * @param md5CryptText The Apache or FreeBSD-md5Crypted hash used to authenticate the plaintextPass.
     */
    fun verifyPassword(plaintextPass: String, md5CryptText: String): Boolean {
        return if (md5CryptText.startsWith("$1$")) {
            md5CryptText == crypt(plaintextPass, md5CryptText)
        } else if (md5CryptText.startsWith("\$apr1$")) {
            md5CryptText == apacheCrypt(plaintextPass, md5CryptText)
        } else {
            throw RuntimeException("Bad md5CryptText")
        }
    }
}