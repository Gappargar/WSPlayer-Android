// app/src/main/java/com/example/wsplayer/utils/XmlUtils.kt
package com.example.wsplayer.utils // Váš balíček pro utility - ZKONTROLUJTE

import android.util.Log
import com.example.wsplayer.data.models.* // <-- Důležité: Importujte vaše modelové třídy!
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader

// Objekt pro parsování XML odpovědí z Webshare.cz API
object XmlUtils {

    private const val TAG = "XmlUtils" // Logovací tag

    // Pomocná funkce pro přeskočení nepodstatných tagů
    @Throws(XmlPullParserException::class, IOException::class)
    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }

    // Pomocná funkce pro získání textu aktuálního tagu
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readText(parser: XmlPullParser): String {
        var result = ""
        // Ensure we are on a START_TAG before reading text
        if (parser.eventType == XmlPullParser.START_TAG) {
            // Advance to the next event, which should be TEXT
            if (parser.next() == XmlPullParser.TEXT) {
                result = parser.text
                // Advance past the TEXT event to the next tag (usually END_TAG for the current element)
                parser.nextTag()
            }
        }
        return result
    }

    // Parsuje XML odpověď z /api/salt/ na SaltResponse
    fun parseSaltResponseXml(xmlString: String): SaltResponse {
        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser().apply { setInput(StringReader(xmlString)) }

            var eventType = parser.eventType
            var status: String? = null
            var salt: String? = null
            var code: Int? = null
            var message: String? = null

            // Najít kořenový tag <response> nebo začít parsovat přímo
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "status" -> status = readText(parser)
                        "salt" -> salt = readText(parser)
                        "code" -> code = readText(parser).toIntOrNull()
                        "message" -> message = readText(parser)
                        else -> skip(parser) // Přeskočit neznámé tagy
                    }
                }
                eventType = parser.next()
            }
            return SaltResponse(status ?: "FATAL", salt, code, message)

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Chyba při parsování SaltResponse XML: ${e.message}")
            return SaltResponse("FATAL", null, null, "Chyba při parsování XML: ${e.message}")
        }
    }

    // Parsuje XML odpověď z /api/login/ na LoginResponse
    fun parseLoginResponseXml(xmlString: String): LoginResponse {
        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser().apply { setInput(StringReader(xmlString)) }

            var eventType = parser.eventType
            var status: String? = null
            var token: String? = null
            var code: Int? = null
            var message: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "status" -> status = readText(parser)
                        "token" -> token = readText(parser)
                        "code" -> code = readText(parser).toIntOrNull()
                        "message" -> message = readText(parser)
                        else -> skip(parser) // Přeskočit neznámé tagy
                    }
                }
                eventType = parser.next()
            }
            return LoginResponse(status ?: "FATAL", token, code, message)

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Chyba při parsování LoginResponse XML: ${e.message}")
            return LoginResponse("FATAL", null, null, "Chyba při parsování XML: ${e.message}")
        }
    }


    // Parsuje XML odpověď z /api/logout/ na status String
    fun parseLogoutResponseXml(xmlString: String): String { // Vrací jen String status
        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser().apply { setInput(StringReader(xmlString)) }

            var eventType = parser.eventType
            var status: String? = null

            // Najít tag <status>
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "status") {
                    status = readText(parser)
                    break // Status nalezen, končíme
                }
                eventType = parser.next()
            }
            return status ?: "FATAL" // Vrátí nalezený status nebo FATAL

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Chyba při parsování LogoutResponse XML: ${e.message}")
            return "FATAL" // V případě chyby parsování vrátit FATAL
        }
    }


    // Parsuje XML odpověď z /api/search/ na SearchResponse
    fun parseSearchResponseXml(xmlString: String): SearchResponse {
        val filesList = mutableListOf<FileModel>() // Seznam pro ukládání nalezených FileModel
        var status: String? = null
        var total: Int? = null
        var code: Int? = null
        var message: String? = null

        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser().apply { setInput(StringReader(xmlString)) }

            var eventType = parser.eventType
            var currentFile: FileModel? = null // Pro parsování jednotlivého FileModelu

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "status" -> status = readText(parser)
                            "total" -> total = readText(parser).toIntOrNull()
                            "code" -> code = readText(parser).toIntOrNull()
                            "message" -> message = readText(parser)
                            "file" -> {
                                // Začátek tagu <file> - připravit pro parsování jednoho FileModelu
                                currentFile = FileModel( // Inicializovat s výchozími hodnotami
                                    ident = "", name = "", type = "", img = null,
                                    stripe = null, stripe_count = null, size = 0L,
                                    queued = 0, positive_votes = 0, negative_votes = 0,
                                    password = 0
                                )
                            }
                            // Vlastnosti uvnitř tagu <file>
                            "ident" -> currentFile = currentFile?.copy(ident = readText(parser))
                            "name" -> currentFile = currentFile?.copy(name = readText(parser))
                            "type" -> currentFile = currentFile?.copy(type = readText(parser))
                            "img" -> currentFile = currentFile?.copy(img = readText(parser))
                            "stripe" -> currentFile = currentFile?.copy(stripe = readText(parser))
                            "stripe_count" -> currentFile = currentFile?.copy(stripe_count = readText(parser).toIntOrNull())
                            "size" -> currentFile = currentFile?.copy(size = readText(parser).toLongOrNull() ?: 0L)
                            "queued" -> currentFile = currentFile?.copy(queued = readText(parser).toIntOrNull() ?: 0)
                            "positive_votes" -> currentFile = currentFile?.copy(positive_votes = readText(parser).toIntOrNull() ?: 0)
                            "negative_votes" -> currentFile = currentFile?.copy(negative_votes = readText(parser).toIntOrNull() ?: 0)
                            "password" -> currentFile = currentFile?.copy(password = readText(parser).toIntOrNull() ?: 0)
                            else -> skip(parser) // Přeskočit neznámé tagy
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "file" -> {
                                // Konec tagu <file> - FileModel je kompletní, přidat do seznamu
                                if (currentFile != null) {
                                    filesList.add(currentFile!!) // Přidat non-null FileModel
                                }
                                currentFile = null // Reset pro další soubor
                            }
                        }
                    }
                    else -> skip(parser) // Přeskočit neznámé tagy
                }
                eventType = parser.next()
            }
            // Vrací instanci datové třídy SearchResponse
            return SearchResponse(status ?: "FATAL", total ?: 0, filesList, code, message)

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Chyba při parsování SearchResponse XML: ${e.message}")
            // V případě chyby parsování vrátit chybový objekt
            return SearchResponse("FATAL", 0, emptyList(), null, "Chyba při parsování XML: ${e.message}")
        }
    }


    // Parsuje XML odpověď z /api/file_link/ na FileLinkResponse
    fun parseFileLinkResponseXml(xmlString: String): FileLinkResponse {
        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser().apply { setInput(StringReader(xmlString)) }

            var eventType = parser.eventType
            var status: String? = null
            var link: String? = null
            var code: Int? = null
            var message: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "status" -> status = readText(parser)
                            "link" -> link = readText(parser)
                            "code" -> code = readText(parser).toIntOrNull()
                            "message" -> message = readText(parser)
                            else -> skip(parser) // Přeskočit neznámé tagy
                        }
                    }
                }
                eventType = parser.next()
            }
            // Vrací instanci datové třídy FileLinkResponse
            return FileLinkResponse(status ?: "FATAL", link, code, message)

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Chyba při parsování FileLinkResponse XML: ${e.message}")
            // V případě chyby parsování vrátit chybový objekt
            return FileLinkResponse("FATAL", null, null, "Chyba při parsování XML: ${e.message}")
        }
    }


    // Parsuje XML odpověď z /api/user_data/ na UserDataResponse
    fun parseUserDataResponseXml(xmlString: String): UserDataResponse {
        // TODO: Zkontrolujte, jak se jmenují tagy v XML odpovědi z API pro user_data
        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser().apply { setInput(StringReader(xmlString)) }

            var eventType = parser.eventType

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
            var private_space: String? = null // Název pole v DataModels.kt a tag v XML?
            var tester: String? = null
            var vip: String? = null
            var vip_days: String? = null
            var vip_hours: String? = null
            var vip_minutes: String? = null
            var vip_until: String? = null
            var email_verified: String? = null
            var code: Int? = null
            var message: String? = null


            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
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
                            "private_space" -> private_space = readText(parser) // Název tagu v XML?
                            "tester" -> tester = readText(parser)
                            "vip" -> vip = readText(parser)
                            "vip_days" -> vip_days = readText(parser)
                            "vip_hours" -> vip_hours = readText(parser)
                            "vip_minutes" -> vip_minutes = readText(parser)
                            "vip_until" -> vip_until = readText(parser)
                            "email_verified" -> email_verified = readText(parser)
                            "code" -> code = readText(parser).toIntOrNull()
                            "message" -> message = readText(parser)
                            else -> skip(parser) // Přeskočit neznámé tagy
                        }
                    }
                }
                eventType = parser.next()
            }
            // Volání konstruktoru s extrahovanými hodnotami
            return UserDataResponse(
                status = status ?: "FATAL",
                id = id,
                ident = ident,
                username = username,
                email = email,
                points = points,
                files = files,
                bytes = bytes,
                score_files = score_files,
                score_bytes = score_bytes,
                private_files = private_files,
                private_bytes = private_bytes,
                private_space = private_space, // Předat správnou hodnotu
                tester = tester,
                vip = vip,
                vip_days = vip_days,
                vip_hours = vip_hours,
                vip_minutes = vip_minutes,
                vip_until = vip_until,
                email_verified = email_verified,
                code = code,
                message = message
            )

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Chyba při parsování UserDataResponse XML: ${e.message}")
            // V případě chyby parsování vrátit chybový objekt
            return UserDataResponse(
                status = "FATAL", // Status chyby
                id = null, ident = null, username = null, email = null, points = null,
                files = null, bytes = null, score_files = null, score_bytes = null,
                private_files = null, private_bytes = null, private_space = null,
                tester = null, vip = null, vip_days = null, vip_hours = null,
                vip_minutes = null, vip_until = null, email_verified = null,
                code = null, message = "Chyba při parsování XML: ${e.message}"
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

// Import pro XmlPullParserException, která je v jiném paketu
// import org.xmlpull.v1.XmlPullParserException
// Přidáno na začátek souboru.