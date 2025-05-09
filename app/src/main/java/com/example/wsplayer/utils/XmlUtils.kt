package com.example.wsplayer.utils // Ujistěte se, že balíček odpovídá

import android.util.Log
import android.util.Xml // Standardní Android XML parser
import com.example.wsplayer.data.models.* // Import vašich modelů
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader

// Objekt pro parsování XML odpovědí z Webshare.cz API
object XmlUtils {

    private val ns: String? = null // Namespace - obvykle null pro jednoduché XML
    private const val TAG = "XmlUtils"

    // Pomocná funkce pro přeskočení nepodstatných tagů a jejich obsahu
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
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readText(parser: XmlPullParser): String {
        parser.require(XmlPullParser.START_TAG, ns, null) // Vyžaduje START_TAG před čtením
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text ?: "" // Získání textu, pokud existuje
            parser.nextTag() // Posun na END_TAG
        }
        // Pokud nebyl TEXT, parser je už na END_TAG (prázdný element) nebo jinde (chyba)
        parser.require(XmlPullParser.END_TAG, ns, null) // Vyžaduje END_TAG po přečtení
        return result
    }

    // Parsuje XML odpověď z /api/salt/
    fun parseSaltResponseXml(xmlString: String): SaltResponse {
        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser().apply { setInput(StringReader(xmlString)) }

            Log.d(TAG, "parseSaltResponseXml: Starting parsing. XML: $xmlString")

            parser.nextTag()
            parser.require(XmlPullParser.START_TAG, ns, "response")

            var status: String? = null
            var salt: String? = null
            var code: Int? = null
            var message: String? = null

            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
                    "status" -> status = readText(parser)
                    "salt" -> salt = readText(parser)
                    "code" -> code = readText(parser).toIntOrNull()
                    "message" -> message = readText(parser)
                    else -> skip(parser)
                }
            }
            parser.require(XmlPullParser.END_TAG, ns, "response")
            Log.d(TAG, "parseSaltResponseXml: Parsování dokončeno. Final Status=$status, Salt=$salt, Kod=$code, Zprava=$message")
            return SaltResponse(status ?: "FATAL", salt, code, message)

        } catch (e: Exception) {
            Log.e(TAG, "Chyba při parsování SaltResponse XML (Catch blok): ${e.message}", e)
            return SaltResponse("FATAL", null, null, "Chyba při parsování XML: ${e.message}")
        }
    }

    // Parsuje XML odpověď z /api/login/
    fun parseLoginResponseXml(xmlString: String): LoginResponse {
        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser().apply { setInput(StringReader(xmlString)) }

            Log.d(TAG, "parseLoginResponseXml: Starting parsing.")

            parser.nextTag()
            parser.require(XmlPullParser.START_TAG, ns, "response")

            var status: String? = null
            var token: String? = null
            var code: Int? = null
            var message: String? = null

            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
                    "status" -> status = readText(parser)
                    "token" -> token = readText(parser)
                    "code" -> code = readText(parser).toIntOrNull()
                    "message" -> message = readText(parser)
                    else -> skip(parser)
                }
            }
            parser.require(XmlPullParser.END_TAG, ns, "response")

            Log.d(TAG, "parseLoginResponseXml: Parsing finished. Final Status=$status, Token Found=${token != null}, Kod=$code, Zprava=$message")
            return LoginResponse(status ?: "FATAL", token, code, message)

        } catch (e: Exception) {
            Log.e(TAG, "Chyba při parsování LoginResponse XML: ${e.message}", e)
            return LoginResponse("FATAL", null, null, "Chyba při parsování XML: ${e.message}")
        }
    }


    // Parsuje XML odpověď z /api/logout/
    fun parseLogoutResponseXml(xmlString: String): String {
        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser().apply { setInput(StringReader(xmlString)) }

            parser.nextTag()
            parser.require(XmlPullParser.START_TAG, ns, "response")

            var status: String? = null

            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
                    "status" -> status = readText(parser)
                    else -> skip(parser)
                }
            }
            parser.require(XmlPullParser.END_TAG, ns, "response")
            return status ?: "FATAL"

        } catch (e: Exception) {
            Log.e(TAG, "Chyba při parsování LogoutResponse XML: ${e.message}", e)
            return "FATAL"
        }
    }


    // Pomocná funkce pro parsování JEDNOHO <file> elementu z vyhledávání
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readFileItem(parser: XmlPullParser): FileModel {
        parser.require(XmlPullParser.START_TAG, ns, "file")

        var ident: String = ""
        var name: String = ""
        var type: String? = null // Načítáme jako nullable
        var img: String? = null
        var stripe: String? = null
        var stripe_count: Int? = null
        var size: Long = 0L
        var queued: Int? = null // Načítáme jako nullable
        var positive_votes: Int? = null // Načítáme jako nullable
        var negative_votes: Int? = null // Načítáme jako nullable
        var password: Int = 0 // Default 0

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "ident" -> ident = readText(parser)
                "name" -> name = readText(parser)
                "type" -> type = readText(parser).ifEmpty { null }
                "img" -> img = readText(parser).ifEmpty { null }
                "stripe" -> stripe = readText(parser).ifEmpty { null }
                "stripe_count" -> stripe_count = readText(parser).toIntOrNull()
                "size" -> size = readText(parser).toLongOrNull() ?: 0L
                "queued" -> queued = readText(parser).toIntOrNull()
                "positive_votes" -> positive_votes = readText(parser).toIntOrNull()
                "negative_votes" -> negative_votes = readText(parser).toIntOrNull()
                "password" -> password = readText(parser).toIntOrNull() ?: 0
                else -> skip(parser)
            }
        }
        parser.require(XmlPullParser.END_TAG, ns, "file")

        // ***** OPRAVA ZDE: Použití výchozích hodnot pro non-null typy *****
        return FileModel(
            ident = ident,
            name = name,
            type = type ?: "", // Výchozí prázdný string, pokud je type null
            img = img,
            stripe = stripe,
            stripe_count = stripe_count,
            size = size,
            queued = queued ?: 0, // Výchozí 0, pokud je queued null
            positive_votes = positive_votes ?: 0, // Výchozí 0, pokud je positive_votes null
            negative_votes = negative_votes ?: 0, // Výchozí 0, pokud je negative_votes null
            password = password
        )
    }


    // Parsuje XML odpověď z /api/search/
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

            parser.nextTag()
            parser.require(XmlPullParser.START_TAG, ns, "response")
            Log.d(TAG, "parseSearchResponseXml: Parsing children within <response>...")

            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
                    "status" -> status = readText(parser)
                    "total" -> total = readText(parser).toIntOrNull()
                    "code" -> code = readText(parser).toIntOrNull()
                    "message" -> message = readText(parser)
                    "app_version" -> appVersion = readText(parser).toIntOrNull()
                    "file" -> filesList.add(readFileItem(parser))
                    else -> skip(parser)
                }
            }
            parser.require(XmlPullParser.END_TAG, ns, "response")
            Log.d(TAG, "parseSearchResponseXml: Finished parsing <response>. Found ${filesList.size} files.")
            return SearchResponse(status ?: "FATAL", total ?: 0, filesList, code, message, appVersion)

        } catch (e: Exception) {
            Log.e(TAG, "Chyba při parsování SearchResponse XML: ${e.message}", e)
            return SearchResponse("FATAL", 0, emptyList(), null, "Chyba při parsování XML: ${e.message}", null)
        }
    }


    // Parsuje XML odpověď z /api/file_link/
    fun parseFileLinkResponseXml(xmlString: String): FileLinkResponse {
        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser().apply { setInput(StringReader(xmlString)) }

            parser.nextTag()
            parser.require(XmlPullParser.START_TAG, ns, "response")

            var status: String? = null
            var link: String? = null
            var code: Int? = null
            var message: String? = null

            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
                    "status" -> status = readText(parser)
                    "link" -> link = readText(parser).ifEmpty { null }
                    "code" -> code = readText(parser).toIntOrNull()
                    "message" -> message = readText(parser)
                    else -> skip(parser)
                }
            }
            parser.require(XmlPullParser.END_TAG, ns, "response")
            return FileLinkResponse(status ?: "FATAL", link, code, message)

        } catch (e: Exception) {
            Log.e(TAG, "Chyba při parsování FileLinkResponse XML: ${e.message}", e)
            return FileLinkResponse("FATAL", null, null, "Chyba při parsování XML: ${e.message}")
        }
    }


    // Parsuje XML odpověď z /api/user_data/
    fun parseUserDataResponseXml(xmlString: String): UserDataResponse {
        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser().apply { setInput(StringReader(xmlString)) }

            parser.nextTag()
            parser.require(XmlPullParser.START_TAG, ns, "response")

            var status: String? = null; var id: String? = null; var ident: String? = null;
            var username: String? = null; var email: String? = null; var points: String? = null;
            var files: String? = null; var bytes: String? = null; var score_files: String? = null;
            var score_bytes: String? = null; var private_files: String? = null; var private_bytes: String? = null;
            var private_space: String? = null; var tester: String? = null; var vip: String? = null;
            var vip_days: String? = null; var vip_hours: String? = null; var vip_minutes: String? = null;
            var vip_until: String? = null; var email_verified: String? = null;
            var code: Int? = null; var message: String? = null

            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
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
                    else -> skip(parser)
                }
            }
            parser.require(XmlPullParser.END_TAG, ns, "response")
            return UserDataResponse(
                status = status ?: "FATAL", id = id, ident = ident, username = username, email = email, points = points,
                files = files, bytes = bytes, score_files = score_files, score_bytes = score_bytes,
                private_files = private_files, private_bytes = private_bytes, private_space = private_space,
                tester = tester, vip = vip, vip_days = vip_days, vip_hours = vip_hours,
                vip_minutes = vip_minutes, vip_until = vip_until, email_verified = email_verified,
                code = code, message = message
            )

        } catch (e: Exception) {
            Log.e(TAG, "Chyba při parsování UserDataResponse XML: ${e.message}", e)
            return UserDataResponse(status = "FATAL", code = null, message = "Chyba při parsování XML: ${e.message}",
                id = null, ident = null, username = null, email = null, points = null, files = null, bytes = null,
                score_files = null, score_bytes = null, private_files = null, private_bytes = null, private_space = null,
                tester = null, vip = null, vip_days = null, vip_hours = null, vip_minutes = null, vip_until = null, email_verified = null
            )
        }
    }


    // ***** PŘIDÁNY METODY PRO PARSOVÁNÍ HISTORIE *****
    /**
     * Parsuje XML odpověď z /api/history/.
     */
    @Throws(XmlPullParserException::class, IOException::class)
    fun parseHistoryResponseXml(xmlString: String): HistoryResponse {
        try {
            StringReader(xmlString).use { reader ->
                val parser: XmlPullParser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(reader)
                parser.nextTag()
                return readHistoryResponse(parser)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Chyba při parsování HistoryResponse XML: ${e.message}", e)
            return HistoryResponse(status = "FATAL", message = "Chyba při parsování XML: ${e.message}")
        }
    }

    // Čte <response> tag pro historii
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readHistoryResponse(parser: XmlPullParser): HistoryResponse {
        parser.require(XmlPullParser.START_TAG, ns, "response")
        var status: String? = null
        var total: Int = 0
        val historyItems = mutableListOf<HistoryItem>()
        var code: Int? = null
        var message: String? = null

        Log.d(TAG, "parseHistoryResponseXml: Parsing children within <response>...")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "status" -> status = readText(parser)
                "total" -> total = readText(parser).toIntOrNull() ?: 0
                "file" -> historyItems.add(readHistoryItem(parser))
                "code" -> code = readText(parser).toIntOrNull()
                "message" -> message = readText(parser)
                else -> skip(parser)
            }
        }
        parser.require(XmlPullParser.END_TAG, ns, "response")
        Log.d(TAG, "parseHistoryResponseXml: Finished parsing <response>. Found ${historyItems.size} history items.")
        return HistoryResponse(status ?: "FATAL", total, historyItems, code, message)
    }

    // Čte <file> tag (položku historie)
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readHistoryItem(parser: XmlPullParser): HistoryItem {
        parser.require(XmlPullParser.START_TAG, ns, "file")
        var downloadId: String = ""
        var ident: String = ""
        var name: String = ""
        var size: Long = 0
        var startedAt: String? = null
        var endedAt: String? = null
        var ipAddress: String? = null
        var password: Int? = null
        var copyrighted: Int? = null

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "download_id" -> downloadId = readText(parser)
                "ident" -> ident = readText(parser)
                "name" -> name = readText(parser)
                "size" -> size = readText(parser).toLongOrNull() ?: 0
                "started_at" -> startedAt = readText(parser).ifEmpty { null }
                "ended_at" -> endedAt = readText(parser).ifEmpty { null }
                "ip_address" -> ipAddress = readText(parser).ifEmpty { null }
                "password" -> password = readText(parser).toIntOrNull()
                "copyrighted" -> copyrighted = readText(parser).toIntOrNull()
                else -> skip(parser)
            }
        }
        parser.require(XmlPullParser.END_TAG, ns, "file")
        // ***** OPRAVA: Výchozí hodnoty pro password a copyrighted *****
        return HistoryItem(downloadId, ident, name, size, startedAt, endedAt, ipAddress, password ?: 0, copyrighted ?: 0)
    }
    // ****************************************************

}
