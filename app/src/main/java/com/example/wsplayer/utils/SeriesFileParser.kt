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
        Pattern.compile("""\b([0-9]{1,2})[xX]([0-9]{1,3})\b(?!\.?\d)""", Pattern.CASE_INSENSITIVE), // (?!\.?\d) zabráni zhode s časťami ako 1920x1080
        // Season 1 Episode 1, season 01 episode 01
        Pattern.compile("""Season\s*([0-9]{1,2})\s*Episode\s*([0-9]{1,3})""", Pattern.CASE_INSENSITIVE),
        // Séria 1 Epizóda 1, série 01 epizoda 01
        Pattern.compile("""S[ée]rie\s*([0-9]{1,2})\s*Epizod[aáuy]\s*([0-9]{1,3})""", Pattern.CASE_INSENSITIVE),
        // 1. séria - 1. diel, 01.seria-01.dil
        Pattern.compile("""([0-9]{1,2})\s*\.\s*[Ss][ée]ri[ae]\s*-\s*([0-9]{1,3})\s*\.\s*[Dd][íi]l""", Pattern.CASE_INSENSITIVE),
        // [01.01], [1.1], [01-01] (v zátvorkách, s bodkou alebo pomlčkou)
        Pattern.compile("""\[\s*([0-9]{1,2})\s*[\._-]\s*([0-9]{1,3})\s*]"""),
        // Samostatné čísla oddelené bodkou/pomlčkou, napr. 01.02, 1-2. Musí byť opatrný, aby nezachytil dátumy alebo iné čísla.
        // Tento vzor je menej spoľahlivý a mal by byť ku koncu.
        Pattern.compile("""\b([0-9]{1,2})[\._-]([0-9]{1,3})\b(?![-\.\d])"""), // (?![-\.\d]) zabráni zachyteniu dátumov alebo verzií softvéru
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

    // Regulárne výrazy pre čistenie názvu od technických detailov, rokov, jazykov atď.
    // Tieto sa aplikujú na názov PRED detekciou S/E a ZNOVU na potenciálny názov epizódy.
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
        // Audio formáty (aj v zátvorkách alebo s pomlčkami)
        Pattern.compile("""\b(?:\[)?(5\.1|7\.1|2\.0|atmos|dts-hd|dts-es|dts|truehd|ac3|eac3|aac|lc-aac|he-aac|mp3|opus|flac|pcm)(?:\])?\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""-(5\.1|7\.1|dts|ac3|aac)-\b""", Pattern.CASE_INSENSITIVE),
        // Rozlíšenia
        Pattern.compile("""\b(1920x1080|1280x720|3840x2160)\b""", Pattern.CASE_INSENSITIVE),
        // Rok v zátvorkách alebo samostatne (4 číslice)
        Pattern.compile("""\(\s*\d{4}\s*\)"""), Pattern.compile("""\b\d{4}\b"""),
        // Jazykové označenia a tagy releasov
        Pattern.compile("""\b(cz|sk|en|pl|de|es|fr|it|rus|multi|dubbing|titulky|dabing|czech|slovak|english|subtitles|subs|dual audio)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(REPACK|PROPER|INTERNAL|LIMITED|SUBBED|UNRATED|DIRECTORS CUT|EXTENDED|FINAL CUT|UNCUT|REMASTERED)\b""", Pattern.CASE_INSENSITIVE),
        // Odstránenie prípon súborov
        Pattern.compile("""\.(mkv|avi|mp4|mov|wmv|flv|srt|sub|idx)$""", Pattern.CASE_INSENSITIVE),
        // Odstránenie bežných skupín releasov (príklady, môže byť rozšírené)
        Pattern.compile("""-(?:[a-zA-Z0-9]+)$"""), // Napr. -GalaxyRG, -RARBG
        // Odstránenie špeciálnych znakov, ktoré často oddeľujú časti názvu, ale nie sú súčasťou názvu epizódy
        Pattern.compile("""[\[\]\(\)\{\}]""")
    )

    fun parseEpisodeInfo(fileName: String, targetSeriesName: String): ParsedEpisodeInfo? {
        var seasonNum: Int? = null
        var episodeNum: Int? = null
        var quality: String? = null

        Log.d(TAG, "Parsing file: '$fileName' for series: '$targetSeriesName'")

        // 1. Najprv extrahujeme kvalitu z pôvodného názvu
        var nameForQualityExtraction = fileName
        for (pattern in QUALITY_PATTERNS) {
            val matcher = pattern.matcher(nameForQualityExtraction)
            if (matcher.find()) {
                quality = matcher.group(1)?.uppercase()?.trim() ?: matcher.group(0)?.uppercase()?.trim()
                Log.d(TAG, "Found quality hint: $quality in '$nameForQualityExtraction' using pattern: $pattern")
                // Neodstraňujeme hneď, aby sme to mohli použiť na ďalšie čistenie
                break
            }
        }

        // 2. Vytvoríme pracovnú kópiu názvu a odstránime z nej názov seriálu (case-insensitive)
        // Toto je dôležité urobiť pred aplikovaním CLEANUP_PATTERNS, aby sa neodstránili časti názvu seriálu,
        // ktoré by mohli byť podobné technickým detailom (napr. ak seriál má v názve "1080").
        var nameForSEParsing = fileName.replace(targetSeriesName, " ", ignoreCase = true)
        nameForSEParsing = nameForSEParsing.replace(Regex("\\s+"), " ").trim() // Normalizácia medzier
        Log.d(TAG, "Name after removing target series name: '$nameForSEParsing'")

        // 3. Aplikujeme CLEANUP_PATTERNS na odstránenie technických detailov, rokov, jazykov atď.
        for (pattern in CLEANUP_PATTERNS) {
            nameForSEParsing = pattern.matcher(nameForSEParsing).replaceAll(" ")
        }
        nameForSEParsing = nameForSEParsing.replace(Regex("\\s+"), " ").trim()
        Log.d(TAG, "Name after cleanup patterns for S/E parsing: '$nameForSEParsing'")

        // 4. Pokus o extrakciu série a epizódy z očisteného názvu
        var nameAfterSEExtraction = nameForSEParsing
        for (pattern in EPISODE_PATTERNS) {
            val matcher = pattern.matcher(nameForSEParsing)
            if (matcher.find()) {
                try {
                    if (matcher.groupCount() >= 2) {
                        val sGroup = matcher.group(1)
                        val eGroup = matcher.group(2)
                        if (sGroup != null && eGroup != null) {
                            seasonNum = sGroup.toIntOrNull()
                            episodeNum = eGroup.toIntOrNull()
                            if (seasonNum != null && episodeNum != null && seasonNum < 100) { // Pridaná kontrola pre rozumné číslo série
                                Log.d(TAG, "Parsed S${seasonNum}E${episodeNum} from '$nameForSEParsing' using pattern: $pattern")
                                nameAfterSEExtraction = nameForSEParsing.replace(matcher.group(0), " ").trim()
                                break
                            } else { seasonNum = null; episodeNum = null } // Reset, ak čísla nie sú validné
                        }
                    } else if (matcher.groupCount() == 1 && (pattern.toString().contains("isode|[Dd]iel") || pattern.toString().contains("pisode"))) {
                        val eGroup = matcher.group(1)
                        if (eGroup != null) {
                            episodeNum = eGroup.toIntOrNull()
                            seasonNum = 1 // Predpokladáme prvú sériu
                            if (episodeNum != null) {
                                Log.d(TAG, "Parsed S${seasonNum}E${episodeNum} (implicit season 1) from '$nameForSEParsing' using pattern: $pattern")
                                nameAfterSEExtraction = nameForSEParsing.replace(matcher.group(0), " ").trim()
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing S/E from '$nameForSEParsing' with pattern $pattern: ${e.message}")
                }
            }
        }

        if (seasonNum == null || episodeNum == null) {
            Log.d(TAG, "Could not parse season/episode from original: '$fileName' (cleaned for S/E: '$nameForSEParsing')")
            return null
        }

        // 5. Finálne očistenie názvu epizódy
        // Začíname s názvom, z ktorého bol odstránený S/E vzor, ale ešte nebol odstránený názov seriálu (ak nebol na začiatku)
        // a neboli aplikované všetky cleanupy na tento konkrétny zvyšok.
        var potentialEpisodeTitle = nameAfterSEExtraction

        // Odstrániť zvyšky z CLEANUP_PATTERNS, ktoré mohli zostať alebo byť blízko názvu epizódy
        for (pattern in CLEANUP_PATTERNS) {
            potentialEpisodeTitle = pattern.matcher(potentialEpisodeTitle).replaceAll(" ")
        }
        potentialEpisodeTitle = potentialEpisodeTitle.replace(Regex("\\s+"), " ").trim()

        // Odstrániť bežné oddeľovače a zvyšky na začiatku a konci
        potentialEpisodeTitle = potentialEpisodeTitle.replace(Regex("""^[\s\._-]+|[\s\._-]+$"""), "") // Odstrániť na začiatku a konci
        potentialEpisodeTitle = potentialEpisodeTitle.replace(Regex("""[\._\-\s]+"""), " ").trim() // Nahradiť vnútorné za medzeru

        // Odstrániť prípony súborov (aj keď by už mali byť preč)
        val commonExtensions = listOf("mkv", "avi", "mp4", "srt", "ts")
        commonExtensions.forEach { ext ->
            if (potentialEpisodeTitle.lowercase().endsWith(".$ext")) {
                potentialEpisodeTitle = potentialEpisodeTitle.substring(0, potentialEpisodeTitle.length - (ext.length + 1))
            }
        }
        potentialEpisodeTitle = potentialEpisodeTitle.trim { it <= ' ' || it == '-' || it == '.' || it == '(' || it == ')' || it == '[' || it == ']' || it == ':'}


        Log.d(TAG, "Final Parsed Info for '$fileName': S${seasonNum}E${episodeNum}, Q: $quality, Title: '$potentialEpisodeTitle'")
        return ParsedEpisodeInfo(seasonNum, episodeNum, quality, potentialEpisodeTitle.ifEmpty { null })
    }
}
