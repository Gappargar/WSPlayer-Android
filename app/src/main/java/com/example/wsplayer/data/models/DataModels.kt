// app/src/main/java/com/example/wsplayer/data/models/DataModels.kt
package com.example.wsplayer.data.models // Váš balíček - ZKONTROLUJTE

// Tento soubor obsahuje VŠECHNY datové třídy pro API odpovědi a stavy UI

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
data class FileModel( // <- TOTO JE FileModel, ne FileItem!
    val ident: String,
    val name: String,
    val type: String,
    val img: String?, // URL náhledu
    val stripe: String?,
    val stripe_count: Int?,
    val size: Long, // Velikost v bytech
    val queued: Int,
    val positive_votes: Int,
    val negative_votes: Int,
    val password: Int // 0=Ne, 1=Ano
)

// Datová třída pro celou odpověď z /api/search/ (RAW API ODPOVĚĎ)
data class SearchResponse(
    val status: String,
    val total: Int, // Celkový počet výsledků
    val files: List<FileModel>?, // Seznam nalezených souborů (tag <file> se opakuje)
    val code: Int?,
    val message: String?
)

// Datová třída pro odpověď z /api/file_link/ (RAW API ODPOVĚĎ)
data class FileLinkResponse(
    val status: String, // "OK", "FATAL"
    val link: String?, // Přímá URL pro přehrávání/stažení
    val code: Int?,     // Kód chyby
    val message: String? // Popis chyby
)

// Datová třída pro odpověď z /api/user_data/ (RAW API ODPOVĚĎ)
data class UserDataResponse(
    val status: String, // "OK", "FATAL"
    val id: String?, // ID uživatele
    val ident: String?, // Identifikátor uživatele
    val username: String?, // Uživatelské jméno
    val email: String?, // Email
    val points: String?, // Body
    val files: String?, // Počet souborů
    val bytes: String?, // Velikost souborů
    val score_files: String?, // Stažené soubory
    val score_bytes: String?, // Velikost stažených souborů
    val private_space: String?, // Soukromý prostor
    val private_bytes: String?, // Velikost soukromých souborů
    val private_space: String?, // Velikost soukromého prostoru (pozor, duplicitní název v API docs?)
    val tester: String?, // Tester
    val vip: String?, // VIP
    val vip_days: String?, // VIP dny
    val vip_hours: String?, // VIP hodiny
    val vip_minutes: String?, // VIP minuty
    val vip_until: String?, // VIP do
    val email_verified: String?, // Email ověřen
    val code: Int?, // Kód chyby
    val message: String? // Popis chyby
)

// **Sealed class reprezentující RŮZNÉ STAVY PROCESU VYHL**E**DÁVÁNÍ SOUBORŮ (STAVY UI pro ViewModel/Activity)**
sealed class SearchState {
    object Idle : SearchState() // Počáteční stav nebo po dokončení vyhledávání
    object Loading : SearchState() // Vyhledávání probíhá (první stránka)
    data class Success(val results: List<FileModel>, val totalResults: Int) : SearchState() // Úspěch s výsledky (používá FileModel)
    data class Error(val message: String) : SearchState() // Chyba (nese zprávu)
    object EmptyResults : SearchState() // Nalezeno 0 výsledků
    object LoadingMore : SearchState() // Načítání dalších stránek
}

// **Sealed class reprezentující RŮZNÉ STAVY PROCESU ZÍSKÁVÁNÍ ODKAZU NA SOUBOR (STAVY UI pro ViewModel/Activity)**
sealed class FileLinkState {
    object Idle : FileLinkState() // Počáteční stav
    object LoadingLink : FileLinkState() // Získávání odkazu probíhá
    data class LinkSuccess(val fileUrl: String) : FileLinkState() // Odkaz úspěšně získán (nese URL)
    data class Error(val message: String) : FileLinkState() // Chyba (nese zprávu)
    // Poznámka: Pokud byste chtěl(a) stav LinkError explicitně, přejmenujte toto na LinkError
    // a opravte to i v SearchActivity a SearchViewModel. Nyní se stav jmenuje Error.
}

// TODO: Přidat datové třídy pro další API odpovědi (např. file_password_salt)
// TODO: Zkontrolovat UserDataResponse - zdá se, že 'private_space' je 2x definováno v API docs/vašem kódu.