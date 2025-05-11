package com.example.wsplayer.utils

import android.util.Log
import com.example.wsplayer.data.models.ParsedEpisodeInfo // Uistite sa, že tento import je správny
import java.util.regex.Pattern

object SeriesFileParser {

    private const val TAG = "SeriesFileParser"

    // Regulárne výrazy na detekciu série a epizódy.
    // Poradie je dôležité - od špecifickejších k všeobecnejším.
    private val EPISODE_PATTERNS = listOf(
        // S01E01, s01e01, S1E1, s1e1, S01xE01, S01x01
        Pattern.compile("""[Ss]([0-9]{1,2})[\s\._-]*[EeXx]([0-9]{1,3})""", Pattern.CASE_INSENSITIVE),
        // 1x01, 10x22 (s explicitným 'x' ako oddeľovačom)
        Pattern.compile("""\b([0-9]{1,2})[xX]([0-9]{1,3})\b(?!\.?\d)""", Pattern.CASE_INSENSITIVE),
        // Season 1 Episode 1, season 01 episode 01
        Pattern.compile("""Season\s*([0-9]{1,2})\s*Episode\s*([0-9]{1,3})""", Pattern.CASE_INSENSITIVE),
        // Séria 1 Epizóda 1, série 01 epizoda 01
        Pattern.compile("""S[ée]rie\s*([0-9]{1,2})\s*Epizod[aáuy]\s*([0-9]{1,3})""", Pattern.CASE_INSENSITIVE),
        // ***** NOVÝ VZOR: 1 seria 06, 1 season 06 *****
        Pattern.compile("""\b([0-9]{1,2})\s*(?:s[eé]rie|season)\s*([0-9]{1,3})\b""", Pattern.CASE_INSENSITIVE),
        // 1. séria - 1. diel, 01.seria-01.dil
        Pattern.compile("""([0-9]{1,2})\s*\.\s*[Ss][ée]ri[ae]\s*-\s*([0-9]{1,3})\s*\.\s*[Dd][íi]l""", Pattern.CASE_INSENSITIVE),
        // [01.01], [1.1], [01-01] (v zátvorkách, s bodkou alebo pomlčkou)
        Pattern.compile("""\[\s*([0-9]{1,2})\s*[\._-]\s*([0-9]{1,3})\s*]"""),
        // Samostatné čísla oddelené bodkou/pomlčkou, napr. 01.02, 1-2.
        // Tento vzor je menej spoľahlivý a mal by byť ku koncu.
        // Pridaná kontrola, aby to neboli časti desatinných čísel (napr. 5.1)
        Pattern.compile("""\b([0-9]{1,2})([\._-])([0-9]{1,3})\b(?!(?:\.\d|\d))"""),
        // Vzor pre samostatnú epizódu, kde séria je implicitne 1 (napr. "Episode 01", "diel 5")
        Pattern.compile("""(?:[Ee][Pp](?:isode)?|[Dd]iel)\s*([0-9]{1,3})""", Pattern.CASE_INSENSITIVE)
    )

    // Regulárne výrazy na extrakciu kvality videa.
    private val QUALITY_PATTERNS = listOf(
        Pattern.compile("""\b(2160p)\b""", Pattern.CASE_INSENSITIVE), Pattern.compile("""\b(4k)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(uhd)\b""", Pattern.CASE_INSENSITIVE), Pattern.compile("""\b(1080p)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(fhd|fullhd)\b""", Pattern.CASE_INSENSITIVE), Pattern.compile("""\b(720p)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(hd)\b""", Pattern.CASE_INSENSITIVE), Pattern.compile("""\b(sd)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(480p)\b""", Pattern.CASE_INSENSITIVE), Pattern.compile("""\b(576p)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(WEB-DL|WEBDL|WEB)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(WEB-RIP|WEBRIP)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(BluRay|BRRip|BDRip)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(BDMV)\b""", Pattern.CASE_INSENSITIVE), Pattern.compile("""\b(HDTV|PDTV)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(REMUX)\b""", Pattern.CASE_INSENSITIVE), Pattern.compile("""\b(HDR)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(DV|Dolby Vision)\b""", Pattern.CASE_INSENSITIVE), Pattern.compile("""\b(dvdrip)\b""", Pattern.CASE_INSENSITIVE)
    )

    // Regulárne výrazy na extrakciu jazyka.
    private val LANGUAGE_PATTERNS = listOf(
        Pattern.compile("""\b(CZ|CZECH|CESKY|CS|CZ DABING|CZSK|SKCZ)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(SK|SLOVAK|SLOVENSKY|SK DABING)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(EN|ENG|ENGLISH)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(MULTI|DUAL[- ]AUDIO|DUAL)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(SUB|SUBS|SUBTITLES|TITULKY)\b""", Pattern.CASE_INSENSITIVE)
    )

    // Regulárne výrazy pre čistenie názvu od technických detailov, rokov, jazykov atď.
    // Aplikujú sa PRED parsovaním S/E a ZNOVU na finálne čistenie názvu epizódy.
    private val CLEANUP_PATTERNS = listOf(
        // Kvalita a typy zdroja (mnohé sú aj v QUALITY_PATTERNS, ale tu sú pre odstránenie)
        Pattern.compile("""\b(2160p|4k|uhd|1080p|fhd|fullhd|720p|hd|dvdrip|sd|480p|576p)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(WEB-DL|WEBDL|WEB|WEB-RIP|WEBRIP)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(BluRay|BRRip|BDRip|BDMV)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(HDTV|PDTV|DSR)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(REMUX)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(HDR|DV|Dolby Vision)\b""", Pattern.CASE_INSENSITIVE),
        // Video kodeky
        Pattern.compile("""\b(x264|h264|x265|h265|avc|hevc|mpeg2|mpeg4|divx|xvid)\b""", Pattern.CASE_INSENSITIVE),
        // Audio formáty - agresívnejšie odstránenie vrátane okolitých znakov, ak je to možné
        Pattern.compile("""\b(?:\[)?(5\.1|7\.1|2\.0|atmos|dts-hd|dts-es|dts|truehd|ac3|eac3|aac|lc-aac|he-aac|mp3|opus|flac|pcm)(?:\])?\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(DD5\.1|AC-3)\b""", Pattern.CASE_INSENSITIVE), // Častejšie varianty
        Pattern.compile("""-(5\.1|7\.1|dts|ac3|aac)-\b""", Pattern.CASE_INSENSITIVE),
        // Rozlíšenia
        Pattern.compile("""\b(1920x1080|1280x720|3840x2160)\b""", Pattern.CASE_INSENSITIVE),
        // Rok v zátvorkách alebo samostatne (4 číslice)
        Pattern.compile("""\(\s*\d{4}\s*\)"""), Pattern.compile("""\b\d{4}\b"""),
        // Jazykové označenia (sú tu pre odstránenie, ak neboli špecificky extrahované)
        Pattern.compile("""\b(cz|sk|en|pl|de|es|fr|it|rus|multi|dubbing|titulky|dabing|czech|slovak|english|subtitles|subs|dual audio)\b""", Pattern.CASE_INSENSITIVE),
        // Tagy releasov
        Pattern.compile("""\b(REPACK|PROPER|INTERNAL|LIMITED|SUBBED|UNRATED|DIRECTORS CUT|EXTENDED|FINAL CUT|UNCUT|REMASTERED)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""-(?:[a-zA-Z0-9]+)$"""), // Napr. -GalaxyRG, -RARBG (na konci názvu)
        Pattern.compile("""\.(mkv|avi|mp4|mov|wmv|flv|srt|sub|idx)$""", Pattern.CASE_INSENSITIVE), // Prípony súborov
        Pattern.compile("""[\[\]\(\)\{\}]""") // Samostatné zátvorky
    )

    fun parseEpisodeInfo(fileName: String, targetSeriesName: String): ParsedEpisodeInfo? {
        var seasonNum: Int? = null
        var episodeNum: Int? = null
        var quality: String? = null
        var language: String? = null
        var workName = fileName // Pracovná kópia názvu súboru

        Log.d(TAG, "Parsing file: '$fileName' for series: '$targetSeriesName'")

        // 1. Extrahovať kvalitu z pôvodného názvu
        for (pattern in QUALITY_PATTERNS) {
            val matcher = pattern.matcher(workName)
            if (matcher.find()) {
                quality = matcher.group(if (matcher.groupCount() > 0) 1 else 0)?.uppercase()?.trim()
                Log.d(TAG, "Found quality hint: $quality in '$workName' using pattern: $pattern")
                // Neodstraňujeme hneď, aby sme to mohli použiť na ďalšie čistenie
                break
            }
        }

        // 2. Extrahovať jazyk z pôvodného názvu
        for (pattern in LANGUAGE_PATTERNS) {
            val matcher = pattern.matcher(workName)
            if (matcher.find()) {
                val foundLang = matcher.group(if (matcher.groupCount() > 0) 1 else 0)?.uppercase()?.trim()
                if (foundLang != null) {
                    if (language == null || (language in listOf("MULTI", "SUB", "SUBS", "SUBTITLES", "TITULKY") && foundLang !in listOf("MULTI", "SUB", "SUBS", "SUBTITLES", "TITULKY"))) {
                        language = foundLang
                    } else if (language != null && foundLang !in listOf("MULTI", "SUB", "SUBS", "SUBTITLES", "TITULKY") && language !in listOf("MULTI", "SUB", "SUBS", "SUBTITLES", "TITULKY") && !language.contains(foundLang, ignoreCase = true) ) {
                        language += "+$foundLang"
                    }
                    Log.d(TAG, "Found language hint: $foundLang, current language set to: $language in '$workName' using pattern: $pattern")
                    if (language !in listOf("MULTI", "SUB", "SUBS", "SUBTITLES", "TITULKY") && !language.isNullOrEmpty() && !(language?.contains("+") == true)) break
                }
            }
        }
        language?.let { lang ->
            if ((lang.contains("CZ") || lang.contains("CESKY")) && (lang.contains("TITULKY") || lang.contains("SUB"))) language = "CZ"
            if ((lang.contains("SK") || lang.contains("SLOVAK")) && (lang.contains("TITULKY") || lang.contains("SUB"))) language = "SK"
            if ((lang.contains("EN") || lang.contains("ENGLISH")) && (lang.contains("TITULKY") || lang.contains("SUB"))) language = "EN"
            if (lang in listOf("SUB", "SUBS", "SUBTITLES", "TITULKY")) language = "SUB"
        }
        Log.d(TAG, "Final determined language: $language")

        // 3. Odstrániť názov seriálu (case-insensitive) z pracovnej kópie
        var cleanedForSE = workName.replace(targetSeriesName, " ", ignoreCase = true)
        cleanedForSE = cleanedForSE.replace(Regex("\\s+"), " ").trim()
        Log.d(TAG, "Name after removing target series name ('$targetSeriesName'): '$cleanedForSE'")

        // 4. Aplikovať CLEANUP_PATTERNS na odstránenie technických detailov, rokov, jazykov atď.
        for (pattern in CLEANUP_PATTERNS) {
            cleanedForSE = pattern.matcher(cleanedForSE).replaceAll(" ")
        }
        cleanedForSE = cleanedForSE.replace(Regex("\\s+"), " ").trim() // Odstrániť viacnásobné medzery
        Log.d(TAG, "Name after cleanup patterns for S/E parsing: '$cleanedForSE'")


        // 5. Pokus o extrakciu série a epizódy z očisteného názvu
        var nameAfterSEExtraction = cleanedForSE
        for (pattern in EPISODE_PATTERNS) {
            val matcher = pattern.matcher(cleanedForSE)
            if (matcher.find()) {
                try {
                    val sGroupIndex = 1
                    val eGroupIndex = if (matcher.groupCount() >= 2) 2 else 1 // Pre vzory len s epizódou

                    val sGroup = if (matcher.groupCount() >= 2) matcher.group(sGroupIndex) else null
                    val eGroup = matcher.group(eGroupIndex)

                    val tempSeasonNum = sGroup?.toIntOrNull() ?: if (eGroup != null && matcher.groupCount() == 1) 1 else null // Ak len epizóda, predpokladaj S1
                    val tempEpisodeNum = eGroup?.toIntOrNull()

                    if (tempSeasonNum != null && tempEpisodeNum != null && tempSeasonNum > 0 && tempSeasonNum < 100 && tempEpisodeNum > 0 && tempEpisodeNum < 200) { // Pridané kontroly
                        seasonNum = tempSeasonNum
                        episodeNum = tempEpisodeNum
                        Log.d(TAG, "Parsed S${seasonNum}E${episodeNum} from '$cleanedForSE' using pattern: $pattern")
                        nameAfterSEExtraction = cleanedForSE.replace(matcher.group(0), " ").trim()
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing S/E from '$cleanedForSE' with pattern $pattern: ${e.message}")
                }
            }
        }

        if (seasonNum == null || episodeNum == null) {
            Log.d(TAG, "Could not parse season/episode from original: '$fileName' (cleaned for S/E: '$cleanedForSE')")
            return null
        }

        // 6. Finálne očistenie názvu epizódy
        var potentialEpisodeTitle = nameAfterSEExtraction
        // Názov seriálu už bol odstránený
        for (pattern in CLEANUP_PATTERNS) { // Znovu aplikujeme cleanup na zvyšok
            potentialEpisodeTitle = pattern.matcher(potentialEpisodeTitle).replaceAll(" ")
        }
        potentialEpisodeTitle = potentialEpisodeTitle.replace(Regex("\\s+"), " ").trim()
        potentialEpisodeTitle = potentialEpisodeTitle.replace(Regex("""^[\s\._-]+|[\s\._-]+$"""), "")
        potentialEpisodeTitle = potentialEpisodeTitle.replace(Regex("""[\._\-\s]+"""), " ").trim()
        potentialEpisodeTitle = potentialEpisodeTitle.trim { it <= ' ' || it == '-' || it == '.' || it == '(' || it == ')' || it == '[' || it == ']' || it == ':'}


        Log.d(TAG, "Final Parsed Info for '$fileName': S${seasonNum}E${episodeNum}, Q: $quality, Lang: $language, Title: '$potentialEpisodeTitle'")
        return ParsedEpisodeInfo(seasonNum, episodeNum, quality, language, potentialEpisodeTitle.ifEmpty { null })
    }
}
