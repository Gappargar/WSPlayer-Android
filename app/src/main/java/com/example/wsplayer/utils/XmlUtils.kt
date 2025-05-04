// app/src/main/java/com/example/wsplayer/utils/XmlUtils.kt
package com.example.wsplayer.utils

import android.util.Log
import com.example.wsplayer.data.models.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader

// Objekt pro parsování XML odpovědí z Webshare.cz API
object XmlUtils {

    private const val TAG = "XmlUtils"

    // Pomocná funkce pro přeskočení nepodstatných tagů a jejich obsahu
    // Tato funkce musí být volána POUZE, když je parser NA START_TAGU elementu, který chcete přeskočit.
    @Throws(XmlPullParserException::class, IOException::class)
    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException("skip called at wrong parser state ${parser.eventType}")
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_DOCUMENT -> throw XmlPullParserException("Unexpected end of document while skipping")
            }
        }
    }

    // Pomocná funkce pro získání textu aktuálního tagu
    // Předpokládá, že parser je NA START_TAGU volaného elementu
    // Přečte textový obsah a ponechá parser PŘED END_TAGEM elementu.
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readText(parser: XmlPullParser): String {
        // Parser je nyní na START_TAGu elementu, jehož text chceme číst (např. <status>).
        // Přejdeme na další událost uvnitř elementu.
        when (parser.next()) {
            XmlPullParser.TEXT -> {
                // Našli jsme textový obsah (např. "OK"). Přečteme ho.
                val result = parser.text
                // Nyní je parser na TEXT události. Další událostí by měl být END_TAG.
                // Přejdeme na další událost.
                parser.next() // Posune parser z TEXT události (např. na END_TAG </status>)

                // Parser by měl být nyní na END_TAGu (např. </status>). Vyžadujeme a konzumujeme ho.
                parser.require(XmlPullParser.END_TAG, null, null)

                // Vrátíme přečtený text.
                return result
            }
            XmlPullParser.END_TAG -> {
                // Element byl prázdný (<tag></tag>). Parser.next() rovnou narazilo na END_TAG.
                // Jsme již na END_TAGu. Vyžadujeme a konzumujeme ho.
                parser.require(XmlPullParser.END_TAG, null, null)

                // Vrátíme prázdný řetězec.
                return ""
            }
            // Pokud zde narazíme na START_TAG, znamená to, že element má vnořené tagy místo textu.
            // Pro jednoduché tagy jako <status>, <salt> atd. toto není očekávaná struktura podle API.
            // Je to chyba v XML nebo v parsovací logice. Vyhodíme výjimku.
            XmlPullParser.START_TAG -> {
                throw XmlPullParserException("Expected TEXT or END_TAG but found START_TAG inside element ${parser.name}", parser, null)
            }
            // Jiné typy událostí (komentáře, zpracovatelské instrukce) uvnitř jednoduchého tagu jsou také neočekávané.
            else -> {
                throw XmlPullParserException("Expected TEXT or END_TAG but found event type ${parser.eventType} inside element ${parser.name}", parser, null)
            }
        }
    }

    // Parsuje XML odpověď z /api/salt/ na SaltResponse (Předpokládáme, že toto funguje správně)
    fun parseSaltResponseXml(xmlString: String): SaltResponse {
        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser().apply { setInput(StringReader(xmlString)) }

            Log.d(TAG, "parseSaltResponseXml: Starting parsing. XML: $xmlString") // Log vstupu XML

            parser.nextTag() // Přesunout z START_DOCUMENT na kořenový tag (<response>)
            parser.require(XmlPullParser.START_TAG, null, "response") // Ověřit, že kořenový tag je <response>

            var status: String? = null
            var salt: String? = null
            var code: Int? = null
            var message: String? = null

            // Loop through the elements *within* the root tag <response>
            while (parser.next() != XmlPullParser.END_TAG) { // Loop until the end of the <response> tag
                if (parser.eventType != XmlPullParser.START_TAG) {
                    // Skip non-START_TAG events (whitespace, comments)
                    continue
                }

                val tagName = parser.name // Get the current tag name
                Log.d(TAG, "parseSaltResponseXml: Found START_TAG: <$tagName>")

                when (tagName) {
                    "status" -> {
                        status = readText(parser) // Read status tag content
                        Log.d(TAG, "parseSaltResponseXml: Read status content: '$status'") // Log read status
                    }
                    "salt" -> {
                        salt = readText(parser)     // Read salt tag content
                        Log.d(TAG, "parseSaltResponseXml: Read salt content: '$salt'") // Log read salt
                    }
                    "code" -> {
                        val codeString = readText(parser)
                        code = codeString.toIntOrNull() // Read code tag content
                        Log.d(TAG, "parseSaltResponseXml: Read code content: '$codeString', parsed: $code") // Log read code
                    }
                    "message" -> {
                        message = readText(parser) // Read message tag content
                        Log.d(TAG, "parseSaltResponseXml: Read message content: '$message'") // Log read message
                    }
                    else -> {
                        Log.d(TAG, "parseSaltResponseXml: Skipping unknown tag: <$tagName>")
                        skip(parser) // Přeskočit any other tags
                    }
                }
            }
            // Parser je nyní na END_TAG tagu <response>

            Log.d(TAG, "parseSaltResponseXml: Parsování dokončeno. Final Status=$status, Salt=$salt, Kod=$code, Zprava=$message") // Log final results before returning
            // Vrací instanci datové třídy SaltResponse
            return SaltResponse(status ?: "FATAL", salt, code, message)

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Chyba při parsování SaltResponse XML (Catch blok): ${e.message}") // Log v catch bloku
            // Return a fatal error object if parsing itself fails
            return SaltResponse("FATAL", null, null, "Chyba při parsování XML: ${e.message}")
        }
    }

    // Parsuje XML odpověď z /api/login/ na LoginResponse (Předpokládáme, že toto funguje správně)
    fun parseLoginResponseXml(xmlString: String): LoginResponse {
        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser().apply { setInput(StringReader(xmlString)) }

            Log.d(TAG, "parseLoginResponseXml: Starting parsing.")

            parser.nextTag() // Přesunout z START_DOCUMENT na první tag
            parser.require(XmlPullParser.START_TAG, null, "response") // Ověřit kořenový tag

            var status: String? = null
            var token: String? = null
            var code: Int? = null
            var message: String? = null

            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.eventType != XmlPullParser.START_TAG) {
                    continue
                }
                when (parser.name) {
                    "status" -> status = readText(parser)
                    "token" -> token = readText(parser)
                    "code" -> code = readText(parser).toIntOrNull()
                    "message" -> message = readText(parser)
                    else -> skip(parser)
                }
            }
            parser.require(XmlPullParser.END_TAG, null, "response")

            Log.d(TAG, "parseLoginResponseXml: Parsing finished. Final Status=$status, Token Found=${token != null}, Kod=$code, Zprava=$message")
            return LoginResponse(status ?: "FATAL", token, code, message)

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Chyba při parsování LoginResponse XML: ${e.message}")
            return LoginResponse("FATAL", null, null, "Chyba při parsování XML: ${e.message}")
        }
    }


    // Parsuje XML odpověď z /api/logout/ na status String (Upraveno pro konzistenci)
    fun parseLogoutResponseXml(xmlString: String): String {
        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser().apply { setInput(StringReader(xmlString)) }

            parser.nextTag() // Přesun na první tag
            parser.require(XmlPullParser.START_TAG, null, "response") // Očekává <response>

            var status: String? = null

            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.eventType != XmlPullParser.START_TAG) continue

                when (parser.name) {
                    "status" -> status = readText(parser)
                    // Můžete přidat i ošetření pro "code" a "message" pokud je v logout odpovědi API posílá
                    // "code" -> { val code = readText(parser).toIntOrNull(); /* uložit/zalogovat code */ }
                    // "message" -> { val msg = readText(parser); /* uložit/zalogovat msg */ }
                    else -> skip(parser) // Přeskočit jiné tagy uvnitř <response>
                }
            }
            parser.require(XmlPullParser.END_TAG, null, "response")

            return status ?: "FATAL"

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Chyba při parsování LogoutResponse XML: ${e.message}")
            return "FATAL"
        }
    }


    // Pomocná funkce pro parsování JEDNOHO <file> elementu (Přidáno a použito ve search)
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readFileItem(parser: XmlPullParser): FileModel {
        parser.require(XmlPullParser.START_TAG, null, "file")
        // Log.d(TAG, "readFileItem: Starting parsing single <file>.") // Detailnější log

        var ident: String = ""
        var name: String = ""
        var type: String = ""
        var img: String? = null
        var stripe: String? = null
        var stripe_count: Int? = null
        var size: Long = 0L
        var queued: Int = 0
        var positive_votes: Int = 0
        var negative_votes: Int = 0
        var password: Int = 0

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            val tagName = parser.name
            when (tagName) {
                "ident" -> ident = readText(parser)
                "name" -> name = readText(parser)
                "type" -> type = readText(parser)
                "img" -> img = readText(parser)
                "stripe" -> stripe = readText(parser)
                "stripe_count" -> stripe_count = readText(parser).toIntOrNull()
                "size" -> size = readText(parser).toLongOrNull() ?: 0L
                "queued" -> queued = readText(parser).toIntOrNull() ?: 0
                "positive_votes" -> positive_votes = readText(parser).toIntOrNull() ?: 0
                "negative_votes" -> negative_votes = readText(parser).toIntOrNull() ?: 0
                "password" -> password = readText(parser).toIntOrNull() ?: 0
                else -> {
                    Log.d(TAG, "readFileItem: Skipping unknown tag inside <file>: <$tagName>")
                    skip(parser)
                }
            }
        }
        parser.require(XmlPullParser.END_TAG, null, "file")
        // Log.d(TAG, "readFileItem: Finished parsing single <file>.") // Detailnější log

        return FileModel(
            ident = ident, name = name, type = type, img = img,
            stripe = stripe, stripe_count = stripe_count, size = size,
            queued = queued, positive_votes = positive_votes, negative_votes = negative_votes,
            password = password
        )
    }


    // Parsuje XML odpověď z /api/search/ na SearchResponse (OPRAVENO)
    fun parseSearchResponseXml(xmlString: String): SearchResponse {
        val filesList = mutableListOf<FileModel>()
        var status: String? = null
        var total: Int? = null
        var code: Int? = null
        var message: String? = null
        var appVersion: Int? = null

        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser().apply { setInput(StringReader(xmlString)) }

            parser.nextTag() // Přesunout parser z START_DOCUMENT na první tag. Očekáváme <response>.
            parser.require(XmlPullParser.START_TAG, null, "response") // Ověřit kořenový tag

            Log.d(TAG, "parseSearchResponseXml: Parsing children within <response>...")

            // Smyčka pro procházení elementů *uvnitř* <response> tagu.
            // parser.next() posune parser na další událost. Smyčka končí na </response>
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.eventType != XmlPullParser.START_TAG) {
                    continue // Přeskočíme TEXT, COMMENT, atd.
                }

                val tagName = parser.name
                Log.d(TAG, "parseSearchResponseXml: Found child START_TAG: <$tagName>")

                when (tagName) {
                    "status" -> status = readText(parser)
                    "total" -> total = readText(parser).toIntOrNull()
                    "code" -> code = readText(parser).toIntOrNull()
                    "message" -> message = readText(parser)
                    "app_version" -> appVersion = readText(parser).toIntOrNull()
                    "file" -> {
                        // Když narazíme na START_TAG <file>, zavoláme pomocnou funkci
                        filesList.add(readFileItem(parser)) // readFileItem posune parser ZA </file> END_TAG
                    }
                    else -> {
                        Log.d(TAG, "parseSearchResponseXml: Skipping unknown child tag: <$tagName>")
                        skip(parser) // Přeskočit tento celý neznámý element
                    }
                }
            }
            parser.require(XmlPullParser.END_TAG, null, "response")
            Log.d(TAG, "parseSearchResponseXml: Finished parsing <response>. Found ${filesList.size} files.")

            return SearchResponse(status ?: "FATAL", total ?: 0, filesList, code, message, appVersion)

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Chyba při parsování SearchResponse XML: ${e.message}")
            return SearchResponse("FATAL", 0, emptyList(), null, "Chyba při parsování XML: ${e.message}", null)
        }
    }


    // Parsuje XML odpověď z /api/file_link/ (Upraveno pro konzistenci)
    fun parseFileLinkResponseXml(xmlString: String): FileLinkResponse {
        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser().apply { setInput(StringReader(xmlString)) }

            parser.nextTag() // Přesun na první tag
            parser.require(XmlPullParser.START_TAG, null, "response") // Očekává <response>

            var status: String? = null
            var link: String? = null
            var code: Int? = null
            var message: String? = null

            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.eventType != XmlPullParser.START_TAG) {
                    continue
                }
                when (parser.name) {
                    "status" -> status = readText(parser)
                    "link" -> link = readText(parser)
                    "code" -> code = readText(parser).toIntOrNull()
                    "message" -> message = readText(parser)
                    else -> skip(parser) // Přeskočit neznámé tagy uvnitř <response>
                }
            }
            parser.require(XmlPullParser.END_TAG, null, "response")

            return FileLinkResponse(status ?: "FATAL", link, code, message)

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Chyba při parsování FileLinkResponse XML: ${e.message}")
            return FileLinkResponse("FATAL", null, null, "Chyba při parsování XML: ${e.message}")
        }
    }


    // Parsuje XML odpověď z /api/user_data/ (Upraveno pro konzistenci, předpokládá tagy odpovídající DataModels)
    fun parseUserDataResponseXml(xmlString: String): UserDataResponse {
        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser().apply { setInput(StringReader(xmlString)) }

            parser.nextTag() // Přesun na první tag
            parser.require(XmlPullParser.START_TAG, null, "response") // Očekává <response>

            var status: String? = null
            var id: String? = null
            var ident: String? = null
            var username: String? = null
            var email: String? = null
            var points: String? = null
            var files: String? = null
            var bytes: String? = null
            var score_files: String? = null
            var score_bytes: String? = null
            var private_files: String? = null
            var private_bytes: String? = null
            var private_space: String? = null
            var tester: String? = null
            var vip: String? = null
            var vip_days: String? = null
            var vip_hours: String? = null
            var vip_minutes: String? = null
            var vip_until: String? = null
            var email_verified: String? = null
            var code: Int? = null
            var message: String? = null


            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.eventType != XmlPullParser.START_TAG) {
                    continue
                }

                when (parser.name) { // Název tagu v XML
                    "status" -> status = readText(parser)
                    "id" -> id = readText(parser)
                    "ident" -> ident = readText(parser)
                    "username" -> username = readText(parser)
                    "email" -> email = readText(parser)
                    "points" -> points = readText(parser)
                    "files" -> files = readText(parser)
                    "bytes" -> bytes = readText(parser)
                    "score_files" -> score_files = readText(parser)
                    "score_bytes" -> score_bytes = readText(parser)
                    "private_files" -> private_files = readText(parser)
                    "private_bytes" -> private_bytes = readText(parser)
                    "private_space" -> private_space = readText(parser)
                    "tester" -> tester = readText(parser)
                    "vip" -> vip = readText(parser)
                    "vip_days" -> vip_days = readText(parser)
                    "vip_hours" -> vip_hours = readText(parser)
                    "vip_minutes" -> vip_minutes = readText(parser)
                    "vip_until" -> vip_until = readText(parser)
                    "email_verified" -> email_verified = readText(parser)
                    "code" -> code = readText(parser).toIntOrNull()
                    "message" -> message = readText(parser)
                    else -> {
                        Log.d(TAG, "parseUserDataResponseXml: Skipping unknown tag: ${parser.name}")
                        skip(parser)
                    }
                }
            }
            parser.require(XmlPullParser.END_TAG, null, "response")

            return UserDataResponse(
                status = status ?: "FATAL",
                id = id, ident = ident, username = username, email = email, points = points,
                files = files, bytes = bytes, score_files = score_files, score_bytes = score_bytes,
                private_files = private_files, private_bytes = private_bytes, private_space = private_space,
                tester = tester, vip = vip, vip_days = vip_days, vip_hours = vip_hours,
                vip_minutes = vip_minutes, vip_until = vip_until, email_verified = email_verified,
                code = code, message = message
            )

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Chyba při parsování UserDataResponse XML: ${e.message}")
            return UserDataResponse(
                status = "FATAL", code = null, message = "Chyba při parsování XML: ${e.message}",
                id = null, ident = null, username = null, email = null, points = null,
                files = null, bytes = null, score_files = null, score_bytes = null,
                private_files = null, private_bytes = null, private_space = null,
                tester = null, vip = null, vip_days = null, vip_hours = null,
                vip_minutes = null, vip_until = null, email_verified = null
            )
        }
    }

    // TODO: Implementovat metodu pro parsování odpovědi z /api/file_password_salt/
    /*
    fun parseFilePasswordSaltResponseXml(xmlString: String): FilePasswordSaltResponse {
        // ... parsovací logika ...
         return FilePasswordSaltResponse("FATAL", null, null, "Není implementováno") // Placeholder
    }
    */

}