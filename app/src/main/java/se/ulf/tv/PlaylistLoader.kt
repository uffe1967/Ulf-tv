package se.ulf.tv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object PlaylistLoader {

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val logoRegex = Regex("tvg-logo=\"([^\"]*)\"")
    private val groupRegex = Regex("group-title=\"([^\"]*)\"")

    suspend fun loadM3u(url: String): Result<List<Channel>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "UlfTV/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                val body = response.body ?: return@withContext Result.failure(Exception("Tomt svar"))
                val channels = ArrayList<Channel>(256)
                BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8)).use { reader ->
                    var currentName = ""
                    var currentGroup = "Live TV"
                    var currentLogo = ""
                    var index = 0

                    while (true) {
                        val line = reader.readLine() ?: break
                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) continue

                        if (trimmed.startsWith("#EXTINF", ignoreCase = true)) {
                            currentLogo = logoRegex.find(trimmed)?.groupValues?.getOrNull(1) ?: ""
                            currentGroup = groupRegex.find(trimmed)?.groupValues?.getOrNull(1)
                                ?.takeIf { it.isNotBlank() } ?: "Live TV"
                            val commaIdx = trimmed.lastIndexOf(',')
                            currentName = if (commaIdx >= 0 && commaIdx + 1 < trimmed.length) {
                                trimmed.substring(commaIdx + 1).trim()
                            } else {
                                "Kanal ${index + 1}"
                            }
                        } else if (!trimmed.startsWith("#") &&
                            (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true))
                        ) {
                            index++
                            channels.add(
                                Channel(
                                    id = "m3u_${index}_${currentName.hashCode()}",
                                    name = currentName.ifBlank { "Kanal $index" },
                                    url = trimmed,
                                    group = currentGroup,
                                    logo = currentLogo
                                )
                            )
                            currentName = ""
                            currentLogo = ""
                            if (channels.size >= 15000) break
                        }
                    }
                }
                Result.success(channels)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadXtream(server: String, user: String, pass: String): Result<List<Channel>> =
        withContext(Dispatchers.IO) {
            try {
                val base = server.trimEnd('/')
                val catMap = mutableMapOf<String, String>()

                try {
                    val catReq = Request.Builder()
                        .url("$base/player_api.php?username=$user&password=$pass&action=get_live_categories")
                        .header("User-Agent", "UlfTV/1.0")
                        .build()
                    httpClient.newCall(catReq).execute().use { res ->
                        if (res.isSuccessful) {
                            val arr = JSONArray(res.body?.string() ?: "[]")
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                catMap[obj.optString("category_id")] = obj.optString("category_name", "Live TV")
                            }
                        }
                    }
                } catch (_: Exception) {
                }

                val streamReq = Request.Builder()
                    .url("$base/player_api.php?username=$user&password=$pass&action=get_live_streams")
                    .header("User-Agent", "UlfTV/1.0")
                    .build()

                httpClient.newCall(streamReq).execute().use { res ->
                    if (!res.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${res.code}"))
                    }
                    val arr = JSONArray(res.body?.string() ?: "[]")
                    val channels = ArrayList<Channel>(arr.length().coerceAtMost(15000))
                    val limit = arr.length().coerceAtMost(15000)
                    for (i in 0 until limit) {
                        val obj = arr.getJSONObject(i)
                        val streamId = obj.optString("stream_id")
                        if (streamId.isBlank()) continue
                        val name = obj.optString("name", "Kanal $streamId")
                        val logo = obj.optString("stream_icon", "")
                        val catId = obj.optString("category_id", "")
                        val group = catMap[catId] ?: "Live TV"
                        channels.add(
                            Channel(
                                id = "xt_$streamId",
                                name = name,
                                url = "$base/live/$user/$pass/$streamId.ts",
                                group = group,
                                logo = logo
                            )
                        )
                    }
                    Result.success(channels)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
