package eu.kanade.tachiyomi.data.track.shikomori

import android.net.Uri
import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.obj
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.Trackanager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import okhttp3.*
import rx.Observable
import uy.kohesive.injekt.injectLazy

class ShikomoriApi(private val client: OkHttpClient, interceptor: ShikomoriInterceptor) {

    private val gson: Gson by injectLazy()
    private val parser = JsonParser()
    private val jsonime = ediaType.parse("application/json; charset=utf-8")
    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    fun addLibanga(track: Track, user_id: String): Observable<Track> {
        val payload = jsonObject(
                "user_rate" to jsonObject(
                        "user_id" to user_id,
                        "target_id" to track.media_id,
                        "target_type" to "anga",
                        "chapters" to track.last_chapter_read,
                        "score" to track.score.toInt(),
                        "status" to track.toShikomoriStatus()
                )
        )
        val body = RequestBody.create(jsonime, payload.toString())
        val request = Request.Builder()
                .url("$apiUrl/v2/user_rates")
                .post(body)
                .build()
        return authClient.newCall(request)
                .asObservableSuccess()
                .map {
                    track
                }
    }

    fun updateLibanga(track: Track, user_id: String): Observable<Track> = addLibanga(track, user_id)

    fun search(search: String): Observable<List<TrackSearch>> {
        val url = Uri.parse("$apiUrl/mangas").buildUpon()
                .appendQueryParameter("order", "popularity")
                .appendQueryParameter("search", search)
                .appendQueryParameter("limit", "20")
                .build()
        val request = Request.Builder()
                .url(url.toString())
                .get()
                .build()
        return authClient.newCall(request)
                .asObservableSuccess()
                .map { netResponse ->
                    val responseBody = netResponse.body()?.string().orEmpty()
                    if (responseBody.isEmpty()) {
                        throw Exception("Null Response")
                    }
                    val response = parser.parse(responseBody).array
                    response.map { jsonToSearch(it.obj) }
                }

    }

    private fun jsonToSearch(obj: JsonObject): TrackSearch {
        return TrackSearch.create(Trackanager.SHIKOORI).apply {
            media_id = obj["id"].asInt
            title = obj["russian"].asString
            total_chapters = obj["chapters"].asInt
            cover_url = baseUrl + obj["image"].obj["preview"].asString
            summary = ""
            tracking_url = baseUrl + obj["url"].asString
            publishing_status = obj["status"].asString
            publishing_type = obj["kind"].asString
            start_date = obj["aired_on"].asString
        }
    }

    private fun jsonToTrack(obj: JsonObject): Track {
        return Track.create(Trackanager.SHIKOORI).apply {
            media_id = obj["id"].asInt
            title = ""
            last_chapter_read = obj["chapters"].asInt
            total_chapters = obj["chapters"].asInt
            score = (obj["score"].asInt).toFloat()
            status = toTrackStatus(obj["status"].asString)
        }
    }

    fun findLibanga(track: Track, user_id: String): Observable<Track?> {
        val url = Uri.parse("$apiUrl/v2/user_rates").buildUpon()
                .appendQueryParameter("user_id", user_id)
                .appendQueryParameter("target_id", track.media_id.toString())
                .appendQueryParameter("target_type", "anga")
                .build()
        val request = Request.Builder()
                .url(url.toString())
                .get()
                .build()
        return authClient.newCall(request)
                .asObservableSuccess()
                .map { netResponse ->
                    val responseBody = netResponse.body()?.string().orEmpty()
                    if (responseBody.isEmpty()) {
                        throw Exception("Null Response")
                    }
                    val response = parser.parse(responseBody).array
                    if (response.size() > 1) {
                        throw Exception("Too much mangas in response")
                    }
                    val entry = response.map {
                        jsonToTrack(it.obj)
                    }
                    entry.firstOrNull()
                }
    }

    fun getCurrentUser(): Int {
        val user = authClient.newCall(GET("$apiUrl/users/whoami")).execute().body()?.string()
        return parser.parse(user).obj["id"].asInt
    }

    fun accessToken(code: String): Observable<OAuth> {
        return client.newCall(accessTokenRequest(code)).asObservableSuccess().map { netResponse ->
            val responseBody = netResponse.body()?.string().orEmpty()
            if (responseBody.isEmpty()) {
                throw Exception("Null Response")
            }
            gson.fromJson(responseBody, OAuth::class.java)
        }
    }

    private fun accessTokenRequest(code: String) = POST(oauthUrl,
            body = FormBody.Builder()
                    .add("grant_type", "authorization_code")
                    .add("client_id", clientId)
                    .add("client_secret", clientSecret)
                    .add("code", code)
                    .add("redirect_uri", redirectUrl)
                    .build()
    )


    companion object {
        private const val clientId = "1aaf4cf232372708e98b5abc813d795b539c5a916dbbfe9ac61bf02a360832cc"
        private const val clientSecret = "229942c742dd4cde803125d17d64501d91c0b12e14cb1e5120184d77d67024c0"

        private const val baseUrl = "https://shikimori.org"
        private const val apiUrl = "https://shikimori.org/api"
        private const val oauthUrl = "https://shikimori.org/oauth/token"
        private const val loginUrl = "https://shikimori.org/oauth/authorize"

        private const val redirectUrl = "tachiyomi://shikimori-auth"
        private const val baseangaUrl = "$apiUrl/mangas"

        fun mangaUrl(remoteId: Int): String {
            return "$baseangaUrl/$remoteId"
        }

        fun authUrl() =
                Uri.parse(loginUrl).buildUpon()
                        .appendQueryParameter("client_id", clientId)
                        .appendQueryParameter("redirect_uri", redirectUrl)
                        .appendQueryParameter("response_type", "code")
                        .build()


        fun refreshTokenRequest(token: String) = POST(oauthUrl,
                body = FormBody.Builder()
                        .add("grant_type", "refresh_token")
                        .add("client_id", clientId)
                        .add("client_secret", clientSecret)
                        .add("refresh_token", token)
                        .build())

    }

}