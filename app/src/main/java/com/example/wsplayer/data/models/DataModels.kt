package com.example.wsplayer.data.models // Váš balíček

// Tento soubor obsahuje VŠECHNY datové třídy pro API odpovědi

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

// Datová třída pro jeden nalezený soubor ve výsledcích vyhledávání
data class FileModel(
    val ident: String,
    val name: String,
    val type: String,
    val img: String?,
    val stripe: String?,
    val stripe_count: Int?,
    val size: Long, // Bude parsováno z stringu
    val queued: Int,
    val positive_votes: Int,
    val negative_votes: Int,
    val password: Int // 0=Ne, 1=Ano
)

// Datová třída pro celou odpověď z /api/search/
data class SearchResponse(
    val status: String,
    val total: Int, // Bude parsováno z stringu
    val files: List<FileModel>?, // Seznam nalezených souborů (tag <file> se opakuje)
    val code: Int?,
    val message: String?
)

// Datová třída pro odpověď z /api/file_link/
data class FileLinkResponse(
    val status: String, // "OK", "FATAL"
    val link: String?, // Přímá URL pro přehrávání/stažení, pokud status je "OK"
    val code: Int?,     // Kód chyby
    val message: String? // Popis chyby
)

// Datová třída pro odpověď z /api/user_data/
// Obsahuje informace o uživatelském účtu
data class UserDataResponse(
    val status: String, // "OK", "FATAL"
    val id: String?, // The ID of the user. (API docs say string, usually int) - use string for safety
    val ident: String?, // The identifier of the user.
    val username: String?, // The username of the user.
    val email: String?, // The email address of the user.
    val points: String?, // The number of points collected by the user. (API docs say string, should be number) - use string for safety, parse later
    val files: String?, // The number of files the user currently has. (string)
    val bytes: String?, // The total size in bytes of all the user's files. (string)
    val score_files: String?, // The number of the user's files which have been downloaded by other users. (string)
    val score_bytes: String?, // The total size of all the user's files which have been downloaded by other users. (string)
    val private_files: String?, // The number of private files the user currently has. (string)
    val private_bytes: String?, // The total size in bytes of all the user's private files. (string)
    val private_space: String?, // The size in bytes of the user's private space. (string)
    val tester: String?, // Tells whether the user is a tester (0 -> No, 1 -> Yes). (string)
    val vip: String?, // Tells whether the user is a VIP (0 -> No, 1 -> Yes). (string)
    val vip_days: String?, // The number of days for which the user remain a VIP. (string)
    val vip_hours: String?, // The number of hours for which the user remain a VIP. (string)
    val vip_minutes: String?, // The number of minutes for which the user remain a VIP. (string)
    val vip_until: String?, // The date and time until which the user remain a VIP. (string)
    val email_verified: String?, // Tells whether or not the user's email address is verified (0 -> No, 1 -> Yes). (string)
    val code: Int?,     // Kód chyby (pro stav FATAL)
    val message: String? // Popis chyby (pro stav FATAL)
)

sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    data class Success(val results: List<FileModel>, val totalResults: Int) : SearchState()
    // **Zkontrolujte, že máte PŘESNĚ tento řádek:**
    data class Error(val message: String) : SearchState() // <-- Tato definice je nutná!
    object EmptyResults : SearchState()
    object LoadingMore : SearchState()
}

// Sealed class reprezentující různé stavy procesu získávání přímého odkazu na soubor (STAVY UI)
sealed class FileLinkState {
    object Idle : FileLinkState() // Počáteční stav
    object LoadingLink : FileLinkState() // Získávání odkazu probíhá
    data class LinkSuccess(val fileUrl: String) : FileLinkState() // Odkaz úspěšně získán
    data class LinkError(val message: String) : FileLinkState() // Došlo k chybě při získání odkazu
}
// TODO: Přidat datové třídy pro další API odpovědi (např. file_password_salt)
/*
data class FilePasswordSaltResponse(
    val status: String,
    val salt: String?,
    val code: Int?,
    val message: String?
)
*/