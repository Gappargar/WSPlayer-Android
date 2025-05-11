package com.example.wsplayer.data.models // Váš balíček - ZKONTROLUJTE

// Tento súbor by mal obsahovať VŠETKY dátové triedy pre API odpovede a stavy UI

// Datová třída pro odpověď z /api/salt/
data class SaltResponse(
    val status: String, // "OK", "FATAL"
    val salt: String?,
    val code: Int?,
    val message: String?
)

// Datová třída pro odpověď z /api/login/
data class LoginResponse(
    val status: String, // "OK", "ERROR", "FATAL"
    val token: String?,
    val code: Int?,
    val message: String?
)

// ***** FileModel DEFINOVANÝ SKÔR *****
// Datová třída pro jeden nalezený soubor ve výsledcích vyhledávání (POUŽÍVANÁ V UI)
data class FileModel(
    val ident: String,
    val name: String,
    val type: String?,
    val img: String?,
    val stripe: String?,
    val stripe_count: Int?,
    val size: Long,
    val queued: Int?,
    val positive_votes: Int?,
    val negative_votes: Int?,
    val password: Int,
    val displayDate: String? = null,
    var seriesName: String? = null,
    var seasonNumber: Int? = null,
    var episodeNumber: Int? = null,
    var episodeTitle: String? = null, // Názov epizódy extrahovaný parserom
    var videoQuality: String? = null, // Kvalita extrahovaná parserom
    var videoLanguage: String? = null // Jazyk extrahovaný parserom
)
// ***********************************

// Datová třída pro celou odpověď z /api/search/ (RAW API ODPOVĚĎ)
data class SearchResponse(
    val status: String,
    val total: Int,
    val files: List<FileModel>?,
    val code: Int?,
    val message: String?,
    val appVersion: Int? = null
)

// Datová třída pro odpověď z /api/file_link/ (RAW API ODPOVĚĎ)
data class FileLinkResponse(
    val status: String,
    val link: String?,
    val code: Int?,
    val message: String?
)

// Datová třída pro odpověď z /api/user_data/ (RAW API ODPOVĚĎ)
data class UserDataResponse(
    val status: String,
    val id: String?,
    val ident: String?,
    val username: String?,
    val email: String?,
    val points: String?,
    val files: String?,
    val bytes: String?,
    val score_files: String?,
    val score_bytes: String?,
    val private_files: String?,
    val private_bytes: String?,
    val private_space: String?,
    val tester: String?,
    val vip: String?,
    val vip_days: String?,
    val vip_hours: String?,
    val vip_minutes: String?,
    val vip_until: String?,
    val email_verified: String?,
    val code: Int?,
    val message: String?
)

// Modely pro historii
data class HistoryItem(
    val downloadId: String,
    val ident: String,
    val name: String,
    val size: Long,
    val startedAt: String?,
    val endedAt: String?,
    val ipAddress: String?,
    val password: Int,
    val copyrighted: Int
)

data class HistoryResponse(
    val status: String,
    val total: Int = 0,
    val historyItems: List<HistoryItem> = emptyList(),
    val code: Int? = null,
    val message: String? = null
)

// ***** DÁTOVÉ TRIEDY PRE SERIÁLY (PO FileModel) *****
/**
 * Informace extrahované z názvu souboru epizody.
 */
data class ParsedEpisodeInfo(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val quality: String?,
    val language: String?,
    val remainingName: String?
)

/**
 * Reprezentuje jednu konkrétnu verziu (súbor/kvalitu/jazyk) epizódy seriálu.
 */
data class SeriesEpisodeFile(
    val fileModel: FileModel, // Teraz by mal byť FileModel rozpoznaný
    val quality: String?,
    val language: String?
)

/**
 * Reprezentuje jednu logickú epizódu seriálu, ktorá môže mať viacero súborov (kvalít/jazykov).
 */
data class SeriesEpisode(
    val seasonNumber: Int,
    val episodeNumber: Int,
    var commonEpisodeTitle: String? = null,
    val files: MutableList<SeriesEpisodeFile> = mutableListOf()
) {
    fun addFile(file: SeriesEpisodeFile) {
        // Kontrola duplicity podľa identu FileModelu
        if (files.none { it.fileModel.ident == file.fileModel.ident }) {
            files.add(file)
        }
        // Aktualizácia spoločného názvu epizódy
        val newEpisodeTitle = file.fileModel.episodeTitle
        if (commonEpisodeTitle.isNullOrBlank() && !newEpisodeTitle.isNullOrBlank()){
            commonEpisodeTitle = newEpisodeTitle
        } else if (!newEpisodeTitle.isNullOrBlank() && (newEpisodeTitle.length > (commonEpisodeTitle?.length ?: 0))) {
            commonEpisodeTitle = newEpisodeTitle
        }
    }
}

/**
 * Reprezentuje jednu sériu (sezónu) seriálu.
 * Kľúčom v mape epizód je číslo epizódy.
 */
data class SeriesSeason(
    val seasonNumber: Int,
    val episodes: MutableMap<Int, SeriesEpisode> = mutableMapOf()
) {
    fun addEpisodeFile(parsedInfo: ParsedEpisodeInfo, fileModel: FileModel, seriesQuery: String) {
        val episode = episodes.getOrPut(parsedInfo.episodeNumber) {
            SeriesEpisode(
                seasonNumber = parsedInfo.seasonNumber,
                episodeNumber = parsedInfo.episodeNumber,
                commonEpisodeTitle = parsedInfo.remainingName
            )
        }
        episode.addFile(
            SeriesEpisodeFile(
                fileModel = fileModel.copy( // FileModel.copy() by teraz malo fungovať
                    seriesName = seriesQuery,
                    seasonNumber = parsedInfo.seasonNumber,
                    episodeNumber = parsedInfo.episodeNumber,
                    videoQuality = parsedInfo.quality,
                    videoLanguage = parsedInfo.language,
                    episodeTitle = parsedInfo.remainingName
                ),
                quality = parsedInfo.quality,
                language = parsedInfo.language
            )
        )
    }

    fun getSortedEpisodes(): List<SeriesEpisode> {
        return episodes.values.sortedBy { it.episodeNumber }
    }
}

/**
 * Reprezentuje celý seriál so všetkými jeho sériami.
 */
data class OrganizedSeries(
    val title: String, // Názov seriálu zadaný používateľom
    val seasons: MutableMap<Int, SeriesSeason> = mutableMapOf()
) {
    fun getSortedSeasons(): List<SeriesSeason> {
        return seasons.values.sortedBy { it.seasonNumber }
    }
}
// **************************************************


// Sealed class reprezentující RŮZNÉ STAVY PROCESU VYhledávání SOUBORŮ
sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    data class Success(val results: List<FileModel>, val totalResults: Int) : SearchState()
    data class Error(val message: String) : SearchState()
    object EmptyResults : SearchState()
    object LoadingMore : SearchState()
}

// Sealed class reprezentující RŮZNÉ STAVY PROCESU ZÍSKÁVÁNÍ ODKAZU NA SOUBOR
sealed class FileLinkState {
    object Idle : FileLinkState()
    object LoadingLink : FileLinkState()
    data class LinkSuccess(val fileUrl: String) : FileLinkState()
    data class Error(val message: String) : FileLinkState()
}

// Objekt pro reprezentaci akce "Načíst další" v Leanback seznamech
object LoadMoreAction

