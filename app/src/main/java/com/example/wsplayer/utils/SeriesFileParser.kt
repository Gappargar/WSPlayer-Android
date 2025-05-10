package com.example.wsplayer.utils

import android.util.Log
import java.util.regex.Pattern
import com.example.wsplayer.data.models.*

object SeriesFileParser {

    private const val TAG = "SeriesFileParser"

    // Zoznam regulárnych výrazov na detekciu série a epizódy.
    // Poradie je dôležité - od špecifickejších k všeobecnejším.
    // Každý výraz by mal mať dve zachytávacie skupiny: (číslo série) a (číslo epizódy).
    private val EPISODE_PATTERNS = listOf(
        // S01E01, s01e01, S1E1, s1e1
        Pattern.compile("""[Ss]([0-9]+)[Ee]([0-9]+)""", Pattern.CASE_INSENSITIVE),
        // 1x01, 10x22
        Pattern.compile("""(\d+)x(\d+)""", Pattern.CASE_INSENSITIVE),
        // Season 1 Episode 1, season 01 episode 01
        Pattern.compile("""Season\s*([0-9]+)\s*Episode\s*([0-9]+)""", Pattern.CASE_INSENSITIVE),
        // Serie 1 Epizoda 1, série 01 epizoda 01
        Pattern.compile("""S[ée]rie\s*([0-9]+)\s*Epizod[aay]\s*([0-9]+)""", Pattern.CASE_INSENSITIVE),
        // 1. série - 1. díl, 01.seria-01.dil
        Pattern.compile("""([0-9]+)\s*\.\s*[Ss][ée]rie\s*-\s*([0-9]+)\s*\.\s*[Dd][íi]l""", Pattern.CASE_INSENSITIVE),
        // [01.01], [1.1] (s bodkou ako oddeľovačom)
        Pattern.compile("""\[\s*([0-9]+)\s*\.\s*([0-9]+)\s*]"""),
        // 01-01, 1-1 (s pomlčkou)
        Pattern.compile("""\b([0-9]+)-([0-9]+)\b"""),
        // Vzor pre samostatnú epizódu, kde séria je implicitne 1 alebo sa musí určiť inak
        // napr. "Episode 01", "ep 1" - toto je zložitejšie pre určenie série
        // Pattern.compile("""[Ee][Pp](?:isode)?\s*([0-9]+)""", Pattern.CASE_INSENSITIVE),
    )

    // Regulárne výrazy pre kvalitu videa
    private val QUALITY_PATTERNS = listOf(
        Pattern.compile("""\b(2160p|4k)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(1080p|fullhd)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(720p|hd)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(dvdrip|sd|480p|576p)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(WEB-DL|WEBDL|WEB)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(BluRay|BRRip|BDRip)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(HDTV|PDTV)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(REMUX)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(HDR)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(DV|Dolby Vision)\b""", Pattern.CASE_INSENSITIVE)
    )

    /**
     * Pokúsi sa extrahovať informácie o sérii, epizóde a kvalite z názvu súboru.
     * @param fileName Názov súboru na analýzu.
     * @param targetSeriesName Očakávaný názov seriálu (pre presnejšie parsovanie).
     * @return ParsedEpisodeInfo ak sa podarí extrahovať aspoň sériu a epizódu, inak null.
     */
    fun parseEpisodeInfo(fileName: String, targetSeriesName: String): ParsedEpisodeInfo? {
        var seasonNum: Int? = null
        var episodeNum: Int? = null
        var quality: String? = null
        var remainingName = fileName // Začneme s celým názvom

        // 1. Extrahovať kvalitu a odstrániť ju z názvu pre ďalšie parsovanie
        for (pattern in QUALITY_PATTERNS) {
            val matcher = pattern.matcher(remainingName)
            if (matcher.find()) {
                quality = matcher.group(1)?.uppercase() // Prvá zachytávacia skupina
                // Odstrániť nájdenú kvalitu z názvu pre ďalšie parsovanie S/E
                // remainingName = matcher.replaceAll("").trim() // Toto môže byť príliš agresívne
                Log.d(TAG, "Found quality: $quality in '$fileName'")
                break // Našli sme kvalitu, ďalej nehľadáme
            }
        }

        // 2. Pokus o extrakciu série a epizódy
        for (pattern in EPISODE_PATTERNS) {
            val matcher = pattern.matcher(remainingName)
            if (matcher.find()) {
                try {
                    val sGroup = matcher.group(1)
                    val eGroup = matcher.group(2)
                    if (sGroup != null && eGroup != null) {
                        seasonNum = sGroup.toIntOrNull()
                        episodeNum = eGroup.toIntOrNull()
                        if (seasonNum != null && episodeNum != null) {
                            Log.d(TAG, "Parsed S${seasonNum}E${episodeNum} from '$fileName' using pattern: $pattern")
                            // Pokúsime sa odstrániť SxxExx časť pre získanie názvu epizódy
                            // remainingName = remainingName.substring(0, matcher.start()) + remainingName.substring(matcher.end())
                            // remainingName = remainingName.replace(matcher.group(0), "").trim()
                            break // Našli sme sériu aj epizódu
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing S/E from '$fileName' with pattern $pattern: ${e.message}")
                }
            }
        }

        // Ak sme nenašli sériu a epizódu, vrátime null
        if (seasonNum == null || episodeNum == null) {
            Log.d(TAG, "Could not parse season/episode from: $fileName")
            return null
        }

        // 3. Pokus o extrakciu názvu epizódy (zjednodušený)
        // Odstránime názov seriálu (case-insensitive) a bežné oddeľovače
        var potentialEpisodeTitle = fileName.replace(targetSeriesName, "", ignoreCase = true)
        // Odstrániť SxxExx a podobné vzory, ktoré sme už našli
        EPISODE_PATTERNS.forEach { pattern ->
            val matcher = pattern.matcher(potentialEpisodeTitle)
            if (matcher.find()) {
                potentialEpisodeTitle = potentialEpisodeTitle.replace(matcher.group(0), "")
            }
        }
        // Odstrániť kvalitu, ak bola nájdená
        quality?.let { potentialEpisodeTitle = potentialEpisodeTitle.replace(it, "", ignoreCase = true) }

        // Vyčistiť bežné znaky a zvyšky
        potentialEpisodeTitle = potentialEpisodeTitle.replace(Regex("""[\._\-\s]+"""), " ").trim()
        // Odstrániť prípadné prípony súborov na konci
        val commonExtensions = listOf(".mkv", ".avi", ".mp4", ".srt")
        commonExtensions.forEach { ext ->
            if (potentialEpisodeTitle.endsWith(ext, ignoreCase = true)) {
                potentialEpisodeTitle = potentialEpisodeTitle.substring(0, potentialEpisodeTitle.length - ext.length)
            }
        }
        potentialEpisodeTitle = potentialEpisodeTitle.trim { it <= ' ' || it == '-' || it == '.' }


        Log.d(TAG, "Parsed info for '$fileName': S${seasonNum}E${episodeNum}, Q: $quality, Title: '$potentialEpisodeTitle'")
        return ParsedEpisodeInfo(seasonNum, episodeNum, quality, potentialEpisodeTitle.ifEmpty { null })
    }
}
