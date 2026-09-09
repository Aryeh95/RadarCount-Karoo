package io.github.aryeh95.radarcount.probe

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPOutputStream

/** Result of one direct HTTP call. */
data class HttpResult(val status: Int, val body: String, val millis: Long, val headers: Map<String, String>)

/**
 * Direct HTTP through Android's own stack (HttpURLConnection), bypassing
 * karoo-ext and its 100 KB body cap. This is the path the real upload
 * would use if the probe shows it works on the Karoo.
 */
object ProbeHttp {
    private const val CONNECT_MS = 15_000
    private const val READ_MS = 60_000

    fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResult =
        exchange("GET", url, headers, null)

    fun post(url: String, headers: Map<String, String>, body: ByteArray, gzip: Boolean = false): HttpResult =
        exchange("POST", url, headers, body, gzip)

    fun postForm(url: String, fields: Map<String, String>): HttpResult {
        val body = fields.entries.joinToString("&") { (k, v) ->
            java.net.URLEncoder.encode(k, "UTF-8") + "=" + java.net.URLEncoder.encode(v, "UTF-8")
        }.toByteArray()
        return post(url, mapOf("Content-Type" to "application/x-www-form-urlencoded"), body)
    }

    /** One file field in a multipart/form-data body. */
    fun postMultipart(
        url: String,
        headers: Map<String, String>,
        fieldName: String,
        fileName: String,
        content: ByteArray,
        extraFields: Map<String, String> = emptyMap(),
    ): HttpResult {
        val boundary = "----RadarCountProbe" + System.nanoTime()
        val out = ByteArrayOutputStream()
        fun line(s: String) = out.write((s + "\r\n").toByteArray())
        extraFields.forEach { (k, v) ->
            line("--$boundary")
            line("Content-Disposition: form-data; name=\"$k\"")
            line("")
            line(v)
        }
        line("--$boundary")
        line("Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$fileName\"")
        line("Content-Type: application/octet-stream")
        line("")
        out.write(content)
        line("")
        line("--$boundary--")
        return post(url, headers + ("Content-Type" to "multipart/form-data; boundary=$boundary"), out.toByteArray())
    }

    private fun exchange(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
        gzip: Boolean = false,
    ): HttpResult {
        val start = System.currentTimeMillis()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_MS
            readTimeout = READ_MS
            instanceFollowRedirects = false
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            setRequestProperty("User-Agent", "RadarCount-Probe")
        }
        try {
            if (body != null) {
                val payload = if (gzip) gzipBytes(body) else body
                if (gzip) conn.setRequestProperty("Content-Encoding", "gzip")
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(payload.size)
                conn.outputStream.use { it.write(payload) }
            }
            val status = conn.responseCode
            val stream = if (status >= 400) conn.errorStream else conn.inputStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            val hdrs = conn.headerFields.filterKeys { it != null }.mapValues { it.value.joinToString(", ") }
            return HttpResult(status, text, System.currentTimeMillis() - start, hdrs)
        } finally {
            conn.disconnect()
        }
    }

    private fun gzipBytes(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        (GZIPOutputStream(out) as OutputStream).use { it.write(data) }
        return out.toByteArray()
    }
}
