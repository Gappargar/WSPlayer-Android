package com.example.wsplayer.data.models // Váš balíček - ZKONTROLUJTE

// Tento soubor by měl obsahovat VŠECHNY datové třídy pro API odpovědi a stavy UI

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

// Datová třída pro jeden nalezený soubor ve výsledcích vyhledávání (POUŽÍVANÁ V UI)
data class FileModel(
    val ident: String,
    val name: String,
    val type: String?, // Typ souboru (např. "video") - může být null
    val img: String?, // URL náhledu
    val stripe: String?,
    val stripe_count: Int?,
    val size: Long,
    val queued: Int?, // Změněno na nullable
    val positive_votes: Int?, // Změněno na nullable
    val negative_votes: Int?, // Změněno na nullable
    val password: Int,
    // ***** PŘIDÁNO: Volitelné pole pro datum zobrazení (např. z historie) *****
    val displayDate: String? = null
    // *************************************************************************
)

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

