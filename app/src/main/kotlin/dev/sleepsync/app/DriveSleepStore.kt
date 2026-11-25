package dev.sleepsync.app

import dev.sleepsync.core.SleepStore
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.YearMonth
import org.json.JSONArray
import org.json.JSONObject

/**
 * Production sleep store: Drive REST with a `drive.file` access token. Call from a background thread.
 * Files live in an app-created folder; with this scope the app can only see files it created.
 */
class DriveSleepStore(private val accessToken: String) : SleepStore {
    private val folderId: String by lazy {
        findId("name = '$FOLDER' and mimeType = '$FOLDER_MIME' and trashed = false")
            ?: JSONObject(
                call("POST", "$API/files", "application/json", JSONObject().put("name", FOLDER).put("mimeType", FOLDER_MIME).toString()),
            ).getString("id")
    }

    override fun read(month: YearMonth): String? =
        findMonth(month)?.let { call("GET", "$API/files/$it?alt=media") }

    override fun write(month: YearMonth, csv: String) {
        val name = "sleep-$month.csv"
        val existing = findMonth(month)
        if (existing != null) {
            call("POST", "$UPLOAD/files/$existing?uploadType=media", "text/csv", csv, override = "PATCH")
        } else {
            val meta = JSONObject().put("name", name).put("parents", JSONArray().put(folderId))
            val body = "--$BOUNDARY\r\nContent-Type: application/json\r\n\r\n$meta\r\n" +
                "--$BOUNDARY\r\nContent-Type: text/csv\r\n\r\n$csv\r\n--$BOUNDARY--"
            call("POST", "$UPLOAD/files?uploadType=multipart", "multipart/related; boundary=$BOUNDARY", body)
        }
    }

    private fun findMonth(month: YearMonth): String? =
        findId("name = 'sleep-$month.csv' and '$folderId' in parents and trashed = false")

    private fun findId(query: String): String? {
        val q = URLEncoder.encode(query, "UTF-8")
        val files = JSONObject(call("GET", "$API/files?q=$q&fields=files(id)")).getJSONArray("files")
        return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
    }

    private fun call(method: String, url: String, contentType: String? = null, body: String? = null, override: String? = null): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.setRequestProperty("Authorization", "Bearer $accessToken")
            override?.let { c.setRequestProperty("X-HTTP-Method-Override", it) }
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "$contentType; charset=UTF-8")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            if (c.responseCode !in 200..299) {
                error("Drive ${c.responseCode}: ${c.errorStream?.bufferedReader()?.readText()}")
            }
            return c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }

    private companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val FOLDER = "SleepSync"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val BOUNDARY = "sleepsync_boundary"
    }
}
