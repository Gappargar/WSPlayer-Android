package com.example.wsplayer.utils // Váš balíček + .utils

import org.xmlpull.v1.XmlPullParserFactory // Import Factory pro vytvoření parseru
import org.xmlpull.v1.XmlPullParser // Import samotného parseru
import java.io.StringReader // Import pro čtení stringu jako stream
import java.text.CharacterIterator // Potřeba pro formátování velikosti
import java.text.StringCharacterIterator // Import pro formátování velikosti

// Importy datových tříd, které parsing funkce používají
import com.example.wsplayer.data.models.SaltResponse
import com.example.wsplayer.data.models.LoginResponse
import com.example.wsplayer.data.models.FileModel
import com.example.wsplayer.data.models.SearchResponse
import com.example.wsplayer.data.models.FileLinkResponse
import com.example.wsplayer.data.models.UserDataResponse


// Objekt pro pomocné funkce pro parsování XML odpovědí z Webshare.cz API
object XmlUtils {

    // Funkce pro parsování XML stringu do SaltResponse (/api/salt/)
    fun parseSaltResponseXml(xmlString: String): SaltResponse {
        var status: String? = null
        var salt: String? = null
        var code: Int? = null
        var message: String? = null

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true // Důležité pro XML se jmennými prostory (i když zde nejsou, dobrá praxe)
            val xpp = factory.newPullParser() // Vytvoření parseru

            xpp.setInput(StringReader(xmlString)) // Nastavení vstupu pro parser (XML string)
            var eventType = xpp.eventType // Získání prvního typu události (START_DOCUMENT)

            var currentTag: String? = null // Proměnná pro uchování názvu aktuálního tagu, jehož text čteme

            // Cyklus přes všechny události v XML dokumentu
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    // Když parser narazí na začátek tagu (např. <status>, <salt>)
                    XmlPullParser.START_TAG -> {
                        currentTag = xpp.name // Zapamatujeme si název začínajícího tagu
                    }
                    // Když parser narazí na text uvnitř tagu
                    XmlPullParser.TEXT -> {
                        val text = xpp.text.trim() // Získáme text a odstraníme bílé znaky na začátku/konci
                        if (text.isNotEmpty()) {
                            // Pokud text není prázdný, přiřadíme ho příslušné proměnné podle názvu tagu
                            when (currentTag) {
                                "status" -> status = text
                                "salt" -> salt = text
                                "code" -> code = text.toIntOrNull() // Zkusíme převést text na Int
                                "message" -> message = text
                            }
                        }
                    }
                    // Když parser narazí na konec tagu (např. </status>, </salt>)
                    XmlPullParser.END_TAG -> {
                        currentTag = null // Resetujeme aktuální tag, přestáváme číst text pro něj
                    }
                }
                eventType = xpp.next() // Přejít na další událost v XML
            }

        } catch (e: Exception) {
            e.printStackTrace() // Vypíše chybu parsování do Logcatu
            // V případě chyby parsování vrátíme chybovou odpověď
            println("XmlUtils: Chyba při parsování odpovědi soli: ${e.message}")
            return SaltResponse("FATAL", null, null, "Chyba při parsování odpovědi soli: ${e.message}")
        }

        // Vrátíme vytvořený objekt SaltResponse na základě shromážděných dat
        return SaltResponse(
            status = status ?: "FATAL", // Pokud status chyběl v XML, považujeme to za chybu
            salt = salt, // Sůl může být null, pokud API vrátilo chybu
            code = code, // Kód chyby může být null
            message = message // Zpráva o chybě může být null
        )
    }

    // Funkce pro parsování XML stringu do LoginResponse (/api/login/)
    // Stejná logika jako Salt, hledá <status>, <token>, <code>, <message>
    fun parseLoginResponseXml(xmlString: String): LoginResponse {
        var status: String? = null
        var token: String? = null
        var code: Int? = null
        var message: String? = null

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val xpp = factory.newPullParser()

            xpp.setInput(StringReader(xmlString))
            var eventType = xpp.eventType

            var currentTag: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = xpp.name
                    }
                    XmlPullParser.TEXT -> {
                        val text = xpp.text.trim()
                        if (text.isNotEmpty()) {
                            when (currentTag) {
                                "status" -> status = text
                                "token" -> token = text // Získáváme text z tagu <token>
                                "code" -> code = text.toIntOrNull()
                                "message" -> message = text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        currentTag = null
                    }
                }
                eventType = xpp.next()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            println("XmlUtils: Chyba při parsování odpovědi přihlášení: ${e.message}")
            return LoginResponse("FATAL", null, null, "Chyba při parsování odpovědi přihlášení: ${e.message}")
        }

        return LoginResponse(
            status = status ?: "FATAL",
            token = token, // Token může být null při chybě
            code = code,
            message = message
        )
    }

    // Funkce pro parsování XML stringu do SearchResponse (/api/search/)
    // Tato funkce je komplexnější kvůli opakujícím se <file> tagům uvnitř <response>
    fun parseSearchResponseXml(xmlString: String): SearchResponse {
        var status: String? = null
        var total: Int? = null // Celkový počet souborů
        val files = mutableListOf<FileModel>() // Seznam pro ukládání nalezených souborů
        var code: Int? = null // Kód chyby
        var message: String? = null // Zpráva o chybě

        // Proměnné pro data aktuálního souboru při parsování <file> tagu
        var currentFileIdent: String? = null
        var currentFileName: String? = null
        var currentFileType: String? = null
        var currentFileImg: String? = null
        var currentFileStripe: String? = null
        var currentFileStripeCount: Int? = null
        var currentFileSize: Long? = null // Velikost v bytech
        var currentFileQueued: Int? = null
        var currentFilePositiveVotes: Int? = null
        var currentFileNegativeVotes: Int? = null
        var currentFilePassword: Int? = null


        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val xpp = factory.newPullParser()

            xpp.setInput(StringReader(xmlString))
            var eventType = xpp.eventType

            var currentTag: String? = null // Tag, jehož text právě čteme

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = xpp.name // Zapamatujeme si název začínajícího tagu

                        // Pokud narazíme na začátek tagu <file>, resetujeme proměnné pro soubor
                        if (currentTag == "file") {
                            // Zde začíná nový FileModel
                            currentFileIdent = null
                            currentFileName = null
                            currentFileType = null
                            currentFileImg = null
                            currentFileStripe = null
                            currentFileStripeCount = null
                            currentFileSize = null
                            currentFileQueued = null
                            currentFilePositiveVotes = null
                            currentFileNegativeVotes = null
                            currentFilePassword = null
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = xpp.text.trim()
                        if (text.isNotEmpty()) {
                            // Při čtení textu tagu ho přiřadíme příslušné proměnné
                            when (currentTag) {
                                "status" -> status = text
                                "total" -> total = text.toIntOrNull() // API dokumentace říká, že total je string, ale očekáváme číslo. Zkusíme převést na Int.
                                "code" -> code = text.toIntOrNull()
                                "message" -> message = text
                                // Pokud jsme uvnitř tagu <file>, přiřazujeme data k proměnným souboru
                                "ident" -> currentFileIdent = text
                                "name" -> currentFileName = text
                                "type" -> currentFileType = text
                                "img" -> currentFileImg = text
                                "stripe" -> currentFileStripe = text
                                "stripe_count" -> currentFileStripeCount = text.toIntOrNull() // Také očekáváme Int
                                "size" -> {
                                    // Velikost je vrácena jako string (např. "1.2 GB", "500 MB", nebo jen "1234567")
                                    // Použijeme pomocnou funkci pro parsování stringu na Long (Byty)
                                    currentFileSize = parseSizeString(text)
                                }
                                "queued" -> currentFileQueued = text.toIntOrNull() // Očekáváme Int (0 nebo 1)
                                "positive_votes" -> currentFilePositiveVotes = text.toIntOrNull() // Očekáváme Int
                                "negative_votes" -> currentFileNegativeVotes = text.toIntOrNull() // Očekáváme Int
                                "password" -> currentFilePassword = text.toIntOrNull() // Očekáváme Int (0 nebo 1)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        // Při ukončení tagu <file>, vytvoříme objekt FileModel a přidáme ho do seznamu
                        if (xpp.name == "file") {
                            // Ujistíme se, že máme alespoň povinná pole (ident, name, type) před vytvořením FileModel
                            if (currentFileIdent != null && currentFileName != null && currentFileType != null) {
                                files.add(
                                    FileModel(
                                        ident = currentFileIdent!!, // !! = říkáme kompilátoru, že víme, že to není null
                                        name = currentFileName!!,
                                        type = currentFileType!!,
                                        img = currentFileImg,
                                        stripe = currentFileStripe,
                                        stripe_count = currentFileStripeCount,
                                        size = currentFileSize ?: 0L, // Pokud se nepodařilo parsovat velikost, default 0 bytů
                                        queued = currentFileQueued ?: 0, // Pokud se nepodařilo parsovat, default 0
                                        positive_votes = currentFilePositiveVotes ?: 0, // Default 0
                                        negative_votes = currentFileNegativeVotes ?: 0, // Default 0
                                        password = currentFilePassword ?: 0 // Default 0 (není chráněno heslem)
                                    )
                                )
                            } else {
                                println("XmlUtils: Nalezen neúplný <file> tag. Ident: $currentFileIdent, Název: $currentFileName, Typ: $currentFileType") // Logovací zpráva pro debug
                            }
                        }
                        currentTag = null // Reset aktuálního tagu
                    }
                }
                eventType = xpp.next() // Přejít na další událost v XML
            }

        } catch (e: Exception) {
            e.printStackTrace() // Vypíše chybu parsování do Logcatu
            // V případě chyby parsování vrátíme chybovou odpověď
            println("XmlUtils: Chyba při parsování odpovědi vyhledávání: ${e.message}")
            return SearchResponse(
                status = "FATAL", // Status je FATAL při chybě parsování
                total = 0, // Celkový počet 0 při chybě
                files = null, // Seznam souborů je null při chybě
                code = null, // Kód chyby nemusí být k dispozici při chybě parsování
                message = "Chyba při parsování odpovědi vyhledávání: ${e.message}" // Zpráva o chybě parsování
            )
        }

        // Vrátíme vytvořený objekt SearchResponse na základě shromážděných dat
        return SearchResponse(
            status = status ?: "FATAL", // Pokud status chyběl, FATAL
            total = total ?: 0, // Pokud total chyběl, default 0
            files = files, // Vrátíme seznam nalezených souborů (může být prázdný)
            code = code,
            message = message
        )
    }

    // Pomocná funkce pro parsování velikosti souboru ze stringu na Long (Byty)
    // Zjednodušeno, zkusí parsovat jako čisté číslo (byty) nebo jako "číslo jednotka".
    // TODO: Otestovat přesný formát velikosti z API a doladit tuto funkci.
    private fun parseSizeString(sizeString: String): Long? {
        val trimmed = sizeString.trim()
        if (trimmed.isEmpty()) return null

        // Zkusit parsovat jako čisté číslo (byty)
        val byteValue = trimmed.toLongOrNull()
        if (byteValue != null) {
            return byteValue
        }

        // Zkusit parsovat formát s jednotkami (KB, MB, GB, TB)
        try {
            val parts = trimmed.split(" ")
            if (parts.size == 2) {
                val value = parts[0].toDoubleOrNull() ?: return null
                val unit = parts[1].uppercase() // Použijte uppercase pro porovnání
                val multiplier = when (unit) {
                    "B" -> 1.0
                    "KB" -> 1024.0
                    "MB" -> 1024.0 * 1024
                    "GB" -> 1024.0 * 1024 * 1024
                    "TB" -> 1024.0 * 1024 * 1024 * 1024
                    else -> {
                        println("XmlUtils: Neznámá jednotka velikosti '$unit' ve stringu '$sizeString'.")
                        return null // Neznámá jednotka
                    }
                }
                return (value * multiplier).toLong() // Převést na Long
            }
        } catch (e: Exception) {
            println("XmlUtils: Chyba při parsování velikosti '$sizeString' s jednotkami: ${e.message}")
            // Ignore error, return null
        }

        println("XmlUtils: Nelze parsovat velikost souboru '$sizeString'.")
        return null // Nelze parsovat ani jako číslo, ani s jednotkami
    }


    // Funkce pro parsování XML stringu do FileLinkResponse (/api/file_link/)
    // Hledá <status>, <link>, <code>, <message>
    fun parseFileLinkResponseXml(xmlString: String): FileLinkResponse {
        var status: String? = null
        var link: String? = null // Přímá URL pro přehrávání/stažení
        var code: Int? = null
        var message: String? = null

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val xpp = factory.newPullParser()
            xpp.setInput(StringReader(xmlString))
            var eventType = xpp.eventType
            var currentTag: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> currentTag = xpp.name
                    XmlPullParser.TEXT -> {
                        val text = xpp.text.trim()
                        if (text.isNotEmpty()) {
                            when (currentTag) {
                                "status" -> status = text
                                "link" -> link = text
                                "code" -> code = text.toIntOrNull()
                                "message" -> message = text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> currentTag = null
                }
                eventType = xpp.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("XmlUtils: Chyba při parsování odkazu na soubor: ${e.message}")
            return FileLinkResponse("FATAL", null, null, "Chyba při parsování odkazu na soubor: ${e.message}")
        }

        return FileLinkResponse(
            status = status ?: "FATAL",
            link = link,
            code = code,
            message = message
        )
    }

    // Funkce pro parsování XML stringu pro odpověď z /api/logout/
    // Očekává se <response><status>OK</status></response>
    fun parseLogoutResponseXml(xmlString: String): String? {
        var status: String? = null

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val xpp = factory.newPullParser()
            xpp.setInput(StringReader(xmlString))
            var eventType = xpp.eventType
            var currentTag: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> currentTag = xpp.name
                    XmlPullParser.TEXT -> {
                        val text = xpp.text.trim()
                        if (text.isNotEmpty()) {
                            when (currentTag) {
                                "status" -> status = text
                                "code" -> { /* API logout OK doesn't return code here */ }
                                "message" -> { /* API logout OK doesn't return message here */ }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> currentTag = null
                }
                eventType = xpp.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("XmlUtils: Chyba při parsování odpovědi logout: ${e.message}")
            return "FATAL_PARSE_ERROR"
        }

        println("XmlUtils: Logout status parsován: $status")
        return status
    }

    // **NOVÁ Funkce pro parsování XML stringu do UserDataResponse (/api/user_data/)**
    fun parseUserDataResponseXml(xmlString: String): UserDataResponse {
        var status: String? = null
        var id: String? = null
        var ident: String? = null
        var username: String? = null
        var email: String? = null
        var points: String? = null
        var files: String? = null
        var bytes: String? = null
        var scoreFiles: String? = null // score_files z API
        var scoreBytes: String? = null // score_bytes z API
        var privateFiles: String? = null // private_files z API
        var privateBytes: String? = null // private_bytes z API
        var privateSpace: String? = null // private_space z API
        var tester: String? = null
        var vip: String? = null
        var vipDays: String? = null // vip_days z API
        var vipHours: String? = null // vip_hours z API
        var vipMinutes: String? = null // vip_minutes z API
        var vipUntil: String? = null // vip_until z API
        var emailVerified: String? = null // email_verified z API
        var code: Int? = null
        var message: String? = null


        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val xpp = factory.newPullParser()

            xpp.setInput(StringReader(xmlString))
            var eventType = xpp.eventType

            var currentTag: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = xpp.name
                    }
                    XmlPullParser.TEXT -> {
                        val text = xpp.text.trim()
                        if (text.isNotEmpty()) {
                            when (currentTag) {
                                "status" -> status = text
                                "id" -> id = text
                                "ident" -> ident = text
                                "username" -> username = text
                                "email" -> email = text
                                "points" -> points = text
                                "files" -> files = text
                                "bytes" -> bytes = text
                                "score_files" -> scoreFiles = text // Mapování z XML tagu na proměnnou
                                "score_bytes" -> scoreBytes = text // Mapování z XML tagu na proměnnou
                                "private_files" -> privateFiles = text // Mapování z XML tagu na proměnnou
                                "private_bytes" -> privateBytes = text // Mapování z XML tagu na proměnnou
                                "private_space" -> privateSpace = text // Mapování z XML tagu na proměnnou
                                "tester" -> tester = text
                                "vip" -> vip = text
                                "vip_days" -> vipDays = text // Mapování z XML tagu na proměnnou
                                "vip_hours" -> vipHours = text // Mapování z XML tagu na proměnnou
                                "vip_minutes" -> vipMinutes = text // Mapování z XML tagu na proměnnou
                                "vip_until" -> vipUntil = text // Mapování z XML tagu na proměnnou
                                "email_verified" -> emailVerified = text // Mapování z XML tagu na proměnnou
                                "code" -> code = text.toIntOrNull()
                                "message" -> message = text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        currentTag = null
                    }
                }
                eventType = xpp.next()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            println("XmlUtils: Chyba při parsování uživatelských dat: ${e.message}")
            return UserDataResponse(
                status = "FATAL", // Status je FATAL při chybě parsování
                id = null, ident = null, username = null, email = null,
                points = null, files = null, bytes = null,
                score_files = null, score_bytes = null,
                private_files = null, private_bytes = null, private_space = null,
                tester = null, vip = null, vip_days = null, vip_hours = null, vip_minutes = null, vip_until = null, email_verified = null,
                code = null, message = "Chyba při parsování uživatelských dat: ${e.message}"
            )
        }

        // Vrátíme vytvořený objekt UserDataResponse na základě shromážděných dat
        return UserDataResponse(
            status = status ?: "FATAL", // Pokud status chyběl, FATAL
            id = id, ident = ident, username = username, email = email,
            points = points, files = files, bytes = bytes,
            score_files = scoreFiles, score_bytes = scoreBytes,
            private_files = privateFiles, private_bytes = privateBytes, private_space = privateSpace,
            tester = tester, vip = vip, vip_days = vipDays, vip_hours = vipHours, vip_minutes = vipMinutes, vip_until = vipUntil, email_verified = emailVerified,
            code = code,
            message = message
        )
    }

    // TODO: Implementujte další parsing funkce
}