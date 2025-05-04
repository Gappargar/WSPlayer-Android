package com.example.wsplayer.utils // Ujistěte se, že balíček odpovídá složce utils

// Importy potřebné pro SHA1 hašování a Result
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlin.Result // Import třídy Result
import kotlin.runCatching // Import funkce runCatching
import com.example.wsplayer.utils.MD5Crypt

// Import pro MD5-Crypt z kódu, který jste přidal/a.
// Předpokládá se, že soubor MD5Crypt.java je ve stejném balíčku (utils)

object HashingUtils {

    // Hlavní funkce pro výpočet finálního hashe hesla pro Webshare.cz
    // Implementuje: SHA1(MD5_CRYPT(password, salt))
    // Vrací Result<String>, kde String je finální hash, nebo chyba při selhání hašování.
    fun calculateWebsharePasswordHash(password: String, salt: String): Result<String> {

        // --- Krok 1: Provedení MD5_CRYPT ---
        // Voláme metodu md5crypt z třídy MD5Crypt, kterou jsme integrovali.
        val md5CryptResult: String = try {
            // Volejte statickou metodu md5crypt z integrovaného kódu MD5Crypt.java
            // Tato metoda by měla vzít heslo a sůl a vrátit řetězec výsledku MD5-Crypt.
            MD5Crypt.crypt(password, salt) // <-- Změněno z MD5Crypt na Md5CryptUtil

        } catch (e: Exception) {
            // Zachycení jakékoli chyby, která by mohla nastat během provádění MD5_CRYPT
            // (např. neplatná sůl formát, chyba v implementaci atd.)
            e.printStackTrace()
            println("HashingUtils: Chyba při provádění MD5_CRYPT: ${e.message}") // Logovací zpráva
            return Result.failure(Exception("Chyba při provádění MD5_CRYPT: ${e.message}")) // Vrátíme chybu
        }

        // Kontrola, zda výsledek z MD5_CRYPT není prázdný nebo null (některé implementace vrací prázdné stringy při chybě sůl formátu)
        if (md5CryptResult.isNullOrEmpty()) {
            println("HashingUtils: MD5_CRYPT vrátil prázdný nebo null výsledek pro sůl: $salt") // Logovací zpráva
            return Result.failure(Exception("MD5_CRYPT vrátil prázdný výsledek. Zkontrolujte formát soli nebo implementaci."))
        }


        // --- Krok 2: Vypočítání SHA1 hashe výsledku z MD5_CRYPT ---
        val sha1HashBytes: ByteArray = try {
            val digest = MessageDigest.getInstance("SHA1")
            // Důležité: Hašuje se binární reprezentace řetězce výsledku z MD5_CRYPT (typicky UTF-8)
            digest.update(md5CryptResult.toByteArray(Charsets.UTF_8))
            digest.digest() // Získat SHA1 hash jako pole bytů

        } catch (e: NoSuchAlgorithmException) {
            // Tato výjimka by neměla nastat pro "SHA1" na standardních Android/Java systémech
            e.printStackTrace()
            println("HashingUtils: SHA1 algoritmus nenalezen: ${e.message}") // Logovací zpráva
            return Result.failure(Exception("SHA1 algoritmus nenalezen (neočekávaná chyba systému): ${e.message}"))
        } catch (e: Exception) {
            // Zachycení jiných chyb
            e.printStackTrace()
            println("HashingUtils: Chyba při provádění SHA1 hašování: ${e.message}") // Logovací zpráva
            return Result.failure(Exception("Chyba při provádění SHA1 hašování: ${e.message}"))
        }


        // --- Krok 3: Převod SHA1 hashe (bytů) na šestnáctkový (hex) řetězec ---
        // Server očekává finální hash v hex formátu
        val finalHash = sha1HashBytes.joinToString("") {
            String.format("%02x", it) // Formátování každého bytu na dvouciferné hex číslo (např. 0a, 1f, ff)
        }

        // println("HashingUtils: Vypočítán finální hash (předpokládá funkční MD5_CRYPT). Hash: $finalHash") // Volitelně pro debug
        return Result.success(finalHash) // Úspěch, vracíme výsledný hex hash v Result.success
    }

    // Volitelně můžete přidat pomocné funkce, pokud byste je potřeboval/a
    /*
    private fun sha1(input: String): String { ... } // Pokud byste chtěl/a SHA1 oddělit
    */
}