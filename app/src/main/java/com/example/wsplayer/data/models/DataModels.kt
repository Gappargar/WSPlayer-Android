package com.example.wsplayer.data.models // Ujistěte se, že balíček odpovídá

import android.os.Parcelable // Import pro Parcelable
import kotlinx.parcelize.Parcelize // Import pro @Parcelize anotaci

// Datová třída pro odpověď z /api/salt/
@Parcelize
data class SaltResponse(
    val status: String,
    val salt: String?,
    val code: Int?,
    val message: String?
) : Parcelable

// Datová třída pro odpověď z /api/login/
@Parcelize
data class LoginResponse(
    val status: String,
    val token: String?,
    val code: Int?,
    val message: String?
) : Parcelable

// Datová třída pro jeden nalezený soubor ve výsledcích vyhledávání (POUŽÍVANÁ V UI)
@Parcelize
data class FileModel(
    val ident: String, // 1
    val name: String,  // 2
    val type: String?, // 3 <<<< TENTO PARAMETR MUSÍ EXISTOVAT
    val img: String?,  // 4
    val stripe: String?, // 5
    val stripe_count: Int?, // 6
    val size: Long, // 7
    val queued: Int?, // 8
    val positive_votes: Int?, // 9
    val negative_votes: Int?, // 10
    val password: Int, // 11
    val displayDate: String? = null, // 12
    var seriesName: String? = null, // 13
    var seasonNumber: Int? = null, // 14
    var episodeNumber: Int? = null, // 15
    var episodeTitle: String? = null, // 16
    var videoQuality: String? = null, // 17
    var videoLanguage: String? = null // 18
) : Parcelable

// Datová třída pro celou odpověď z /api/search/
@Parcelize
data class SearchResponse(
    val status: String,
    val total: Int,
    val files: List<FileModel>?,
    val code: Int?,
    val message: String?,
    val appVersion: Int? = null
) : Parcelable

// Datová třída pro odpověď z /api/file_link/
@Parcelize
data class FileLinkResponse(
    val status: String,
    val link: String?,
    val code: Int?,
    val message: String?
) : Parcelable

// Datová třída pro odpověď z /api/user_data/
@Parcelize
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
) : Parcelable

// Modely pro historii
@Parcelize
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
) : Parcelable

@Parcelize
data class HistoryResponse(
    val status: String,
    val total: Int = 0,
    val historyItems: List<HistoryItem> = emptyList(),
    val code: Int? = null,
    val message: String? = null
) : Parcelable

// DATOVÉ TŘÍDY PRO SERIÁLY (Parcelable)
@Parcelize
data class ParsedEpisodeInfo(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val quality: String?,
    val language: String?,
    val remainingName: String?
) : Parcelable

@Parcelize
data class SeriesEpisodeFile(
    val fileModel: FileModel,
    val quality: String?,
    val language: String?
) : Parcelable

@Parcelize
data class SeriesEpisode(
    val seasonNumber: Int,
    val episodeNumber: Int,
    var commonEpisodeTitle: String? = null,
    val files: MutableList<SeriesEpisodeFile> = mutableListOf()
) : Parcelable {
    fun addFile(file: SeriesEpisodeFile) {
        if (files.none { it.fileModel.ident == file.fileModel.ident }) {
            files.add(file)
        }
        val newEpisodeTitle = file.fileModel.episodeTitle
        if (commonEpisodeTitle.isNullOrBlank() && !newEpisodeTitle.isNullOrBlank()){
            commonEpisodeTitle = newEpisodeTitle
        } else if (!newEpisodeTitle.isNullOrBlank() && (newEpisodeTitle.length > (commonEpisodeTitle?.length ?: 0))) {
            commonEpisodeTitle = newEpisodeTitle
        }
    }
}

@Parcelize
data class SeriesSeason(
    val seasonNumber: Int,
    val episodes: MutableMap<Int, SeriesEpisode> = mutableMapOf()
) : Parcelable {
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
                fileModel = fileModel.copy(
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

@Parcelize
data class OrganizedSeries(
    val title: String,
    val seasons: MutableMap<Int, SeriesSeason> = mutableMapOf()
) : Parcelable {
    fun getSortedSeasons(): List<SeriesSeason> {
        return seasons.values.sortedBy { it.seasonNumber }
    }
}

// Sealed classy pro stavy UI
sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    data class Success(val results: List<FileModel>, val totalResults: Int) : SearchState()
    data class Error(val message: String) : SearchState()
    object EmptyResults : SearchState()
    object LoadingMore : SearchState()
}

sealed class FileLinkState {
    object Idle : FileLinkState()
    object LoadingLink : FileLinkState()
    data class LinkSuccess(val fileUrl: String) : FileLinkState()
    data class Error(val message: String) : FileLinkState()
}

object LoadMoreAction
