package io.github.aryeh95.radarcount.probe

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Base64
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.coroutines.resume

data class ProbeResult(val name: String, val ok: Boolean?, val detail: String)

/**
 * Answers the questions about the Karoo that the upload feature depends
 * on, against a stand-in server (tools/probe_server.py). Each probe is
 * independent; a failure in one does not stop the rest.
 */
class ProbeRunner(
    private val context: Context,
    private val karoo: KarooSystemService?,
    private val store: ProbeStore,
) {
    private val base get() = store.serverUrl

    suspend fun runAll(emit: (ProbeResult) -> Unit) {
        emit(environment())
        emit(safely("Direct GET /ping") { directPing() })
        emit(safely("karoo-ext POST 50 KB") { karooExtPost(50_000) })
        emit(safely("karoo-ext POST 150 KB") { karooExtPost(150_000) })
        emit(safely("Direct POST 512 KB raw") { directPostLarge(gzip = false) })
        emit(safely("Direct POST 512 KB gzip") { directPostLarge(gzip = true) })
        emit(safely("FIT files on /sdcard") { fitFiles() })
        emit(safely("Upload newest FIT (multipart)") { uploadNewestFit() })
        emit(safely("FIT files via folder picker") { safFiles() })
        emit(safely("Upload newest FIT via picker") { uploadViaSaf() })
        emit(safely("Browser available") { browserAvailable() })
        emit(rideEndPing())
    }

    // ---- individual probes ------------------------------------------------

    private fun environment(): ProbeResult {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val transports = buildList {
            if (caps == null) add("none")
            else {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("wifi")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("cellular")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("bluetooth")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ethernet")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("vpn")
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) add("internet")
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) add("validated")
            }
        }
        return ProbeResult(
            "Environment", null,
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "network: ${transports.joinToString(", ")}"
        )
    }

    private fun directPing(): ProbeResult {
        val r = ProbeHttp.get("$base/ping")
        return ProbeResult("Direct GET /ping", r.status == 200, "HTTP ${r.status} in ${r.millis} ms: ${r.body.take(120)}")
    }

    private suspend fun karooExtPost(size: Int): ProbeResult {
        val name = "karoo-ext POST ${size / 1000} KB"
        val k = karoo ?: return ProbeResult(name, false, "KarooSystemService not available")
        val body = ByteArray(size) { (it % 251).toByte() }
        val request = try {
            OnHttpResponse.MakeHttpRequest(
                method = "POST",
                url = "$base/api/addride?via=karoo-ext",
                headers = mapOf("Content-Type" to "application/octet-stream"),
                body = body,
                waitForConnection = false,
            )
        } catch (e: IllegalStateException) {
            // The SDK refuses bodies over 100 KB before they leave the device.
            return ProbeResult(name, false, "rejected client-side: ${e.message}")
        }
        val start = System.currentTimeMillis()
        val outcome = withTimeoutOrNull(90_000L) {
            suspendCancellableCoroutine<HttpResponseState.Complete> { cont ->
                var id: String? = null
                id = k.addConsumer(request) { event: OnHttpResponse ->
                    val s = event.state
                    if (s is HttpResponseState.Complete) {
                        id?.let { k.removeConsumer(it) }
                        if (cont.isActive) cont.resume(s)
                    }
                }
                cont.invokeOnCancellation { id?.let { k.removeConsumer(it) } }
            }
        } ?: return ProbeResult(name, false, "no response within 90 s")
        val ms = System.currentTimeMillis() - start
        val text = outcome.body?.toString(Charsets.UTF_8)?.take(120) ?: ""
        val err = outcome.error?.let { " error=$it" } ?: ""
        return ProbeResult(name, outcome.statusCode == 200, "HTTP ${outcome.statusCode} in $ms ms$err: $text")
    }

    private fun directPostLarge(gzip: Boolean): ProbeResult {
        val name = "Direct POST 512 KB " + if (gzip) "gzip" else "raw"
        val body = ByteArray(512_000).also { SecureRandom().nextBytes(it) }
        val r = ProbeHttp.post("$base/api/addride?via=direct", mapOf("Content-Type" to "application/octet-stream"), body, gzip)
        val echoed = runCatching { JSONObject(r.body).optInt("bytes", -1) }.getOrDefault(-1)
        return ProbeResult(name, r.status == 200 && echoed == body.size, "HTTP ${r.status} in ${r.millis} ms, server saw $echoed bytes")
    }

    private fun fitDir(): File = File(Environment.getExternalStorageDirectory(), "FitFiles")

    private fun newestFit(): File? = fitDir().listFiles { f -> f.name.endsWith(".fit", ignoreCase = true) }
        ?.maxByOrNull { it.lastModified() }

    /** Newest FIT through the Storage Access Framework tree the user picked, if any. */
    private fun newestFitViaSaf(): Pair<androidx.documentfile.provider.DocumentFile, Int>? {
        val tree = store.safTreeUri?.let { Uri.parse(it) } ?: return null
        val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, tree) ?: return null
        val fits = root.listFiles().filter { it.name?.endsWith(".fit", true) == true }
        return fits.maxByOrNull { it.lastModified() }?.let { it to fits.size }
    }

    private fun safFiles(): ProbeResult {
        val name = "FIT files via folder picker"
        val tree = store.safTreeUri ?: return ProbeResult(name, null, "no folder picked yet — tap 'Pick FitFiles folder'")
        val hit = newestFitViaSaf() ?: return ProbeResult(name, false, "picked $tree but no .fit files listed")
        val (f, n) = hit
        return ProbeResult(name, true, "$n .fit in $tree, newest ${f.name} (${f.length() / 1024} KB)")
    }

    private fun uploadViaSaf(): ProbeResult {
        val name = "Upload newest FIT via picker"
        val (f, _) = newestFitViaSaf() ?: return ProbeResult(name, null, "no folder picked")
        val bytes = context.contentResolver.openInputStream(f.uri)?.use { it.readBytes() } ?: return ProbeResult(name, false, "cannot open ${f.uri}")
        val md5 = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
        val r = ProbeHttp.postMultipart("$base/api/addride?via=saf-multipart", mapOf("Authorization" to "Bearer probe-token"),
            "file", f.name ?: "ride.fit", bytes, mapOf("device" to "karoo"))
        val serverMd5 = runCatching { JSONObject(r.body).optString("md5") }.getOrNull()
        return ProbeResult(name, r.status == 200 && serverMd5 == md5,
            "${f.name} ${bytes.size / 1024} KB in ${r.millis} ms, HTTP ${r.status}, md5 ${if (serverMd5 == md5) "matches" else "MISMATCH"}")
    }

    /** Can the Karoo show the "All files access" settings page? */
    fun requestAllFilesAccess(): ProbeResult {
        val name = "All-files access settings"
        if (Build.VERSION.SDK_INT < 30) return ProbeResult(name, null, "not needed below Android 11")
        if (Environment.isExternalStorageManager()) return ProbeResult(name, true, "already granted")
        val intents = listOf(
            Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")),
            Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
        )
        for (i in intents) {
            val handler = i.resolveActivity(context.packageManager)
            if (handler != null) {
                return try {
                    context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    ProbeResult(name, null, "opened ${handler.packageName}; flip the switch for RadarCount, come back, rerun checks")
                } catch (e: Exception) {
                    ProbeResult(name, false, "${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }
        return ProbeResult(name, false, "no settings page handles the all-files-access intent on this device")
    }

    private fun fitFiles(): ProbeResult {
        val dir = fitDir()
        val granted = context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        val allFiles = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true
        val all = dir.listFiles()
        if (all != null && all.none { it.name.endsWith(".fit", true) }) {
            return ProbeResult(
                "FIT files on /sdcard", false,
                "${dir.path}: no .fit visible (scoped storage). entries: ${all.joinToString { it.name + if (it.isDirectory) "/" else "" }.take(120)}; " +
                    "all-files access=$allFiles, READ_EXTERNAL_STORAGE=$granted"
            )
        }
        if (all == null) {
            return ProbeResult(
                "FIT files on /sdcard", false,
                "${dir.path}: cannot list (exists=${dir.exists()}, canRead=${dir.canRead()}, READ_EXTERNAL_STORAGE=$granted)"
            )
        }
        val fits = all.filter { it.name.endsWith(".fit", ignoreCase = true) }
        val newest = fits.maxByOrNull { it.lastModified() }
        val allFilesNote = if (Build.VERSION.SDK_INT >= 30) " all-files access=${Environment.isExternalStorageManager()}" else ""
        val when_ = newest?.let { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(it.lastModified())) }
        return ProbeResult(
            "FIT files on /sdcard", fits.isNotEmpty(),
            "${dir.path}: ${fits.size} .fit of ${all.size} files, newest ${newest?.name} (${(newest?.length() ?: 0) / 1024} KB, $when_)$allFilesNote"
        )
    }

    private fun uploadNewestFit(): ProbeResult {
        val name = "Upload newest FIT (multipart)"
        val f = newestFit() ?: return ProbeResult(name, false, "no FIT file found")
        val bytes = f.readBytes()
        val md5 = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
        val r = ProbeHttp.postMultipart(
            "$base/api/addride?via=direct-multipart",
            mapOf("Authorization" to "Bearer probe-token"),
            fieldName = "file", fileName = f.name, content = bytes,
            extraFields = mapOf("device" to "karoo"),
        )
        val json = runCatching { JSONObject(r.body) }.getOrNull()
        val serverMd5 = json?.optString("md5")
        val ok = r.status == 200 && serverMd5 == md5
        return ProbeResult(
            name, ok,
            "${f.name} ${bytes.size / 1024} KB in ${r.millis} ms (${bytes.size * 8 / maxOf(r.millis, 1)} kbit/s), HTTP ${r.status}, " +
                "md5 ${if (serverMd5 == md5) "matches" else "MISMATCH $serverMd5"}"
        )
    }

    private fun browserAvailable(): ProbeResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://mybiketraffic.com/"))
        val handlers = context.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }
        return ProbeResult(
            "Browser available", handlers.isNotEmpty(),
            if (handlers.isEmpty()) "nothing handles https:// links — a WebView inside the app would be needed"
            else "handled by: ${handlers.joinToString()}"
        )
    }

    private fun rideEndPing(): ProbeResult {
        val last = store.lastRideEndPing
        return ProbeResult(
            "Ride-end ping from service", if (last == null) null else last.contains("HTTP 200"),
            last ?: "none yet — record and end a ride with the server running, wait ~1 min, then rerun"
        )
    }

    // ---- OAuth flows ---------------------------------------------------------

    /** Opens the fake authorize page. The redirect lands in MainActivity. */
    fun startBrowserLogin(): ProbeResult {
        val verifier = randomToken(64)
        val state = randomToken(16)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        store.pkceVerifier = verifier
        store.pkceState = state
        store.lastRedirect = null
        val url = Uri.parse("$base/oauth/authorize").buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", "radarcount")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .build()
        val intent = Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            ProbeResult("Browser login", null, "opened browser; approve on the page and check that RadarCount comes back")
        } catch (e: Exception) {
            ProbeResult("Browser login", false, "cannot open browser: ${e.javaClass.simpleName} ${e.message}")
        }
    }

    /** Called once MainActivity has received the redirect. */
    suspend fun finishBrowserLogin(): ProbeResult = withContext(Dispatchers.IO) {
        val name = "Browser login"
        val redirect = store.lastRedirect ?: return@withContext ProbeResult(name, false, "no redirect received")
        val uri = Uri.parse(redirect)
        val code = uri.getQueryParameter("code") ?: return@withContext ProbeResult(name, false, "redirect had no code: $redirect")
        if (uri.getQueryParameter("state") != store.pkceState) return@withContext ProbeResult(name, false, "state mismatch")
        val r = ProbeHttp.postForm(
            "$base/oauth/token",
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to REDIRECT_URI,
                "client_id" to "radarcount",
                "code_verifier" to (store.pkceVerifier ?: ""),
            )
        )
        val token = runCatching { JSONObject(r.body).optString("access_token") }.getOrNull()
        ProbeResult(name, r.status == 200 && !token.isNullOrEmpty(), "redirect received, token exchange HTTP ${r.status}: ${r.body.take(120)}")
    }

    /** Device authorisation grant: show a code, poll until the server says approved. */
    suspend fun deviceCodeLogin(emit: (ProbeResult) -> Unit) = withContext(Dispatchers.IO) {
        val name = "Device code login"
        val start = try {
            ProbeHttp.postForm("$base/oauth/device", mapOf("client_id" to "radarcount"))
        } catch (e: Exception) {
            emit(ProbeResult(name, false, "${e.javaClass.simpleName}: ${e.message}")); return@withContext
        }
        val json = runCatching { JSONObject(start.body) }.getOrNull()
        if (start.status != 200 || json == null) {
            emit(ProbeResult(name, false, "HTTP ${start.status}: ${start.body.take(120)}")); return@withContext
        }
        val deviceCode = json.getString("device_code")
        val userCode = json.getString("user_code")
        val verifyUri = json.getString("verification_uri")
        val interval = json.optInt("interval", 5).coerceIn(2, 30)
        emit(ProbeResult(name, null, "On your phone open $verifyUri and enter code $userCode (waiting up to 3 min)"))
        val deadline = System.currentTimeMillis() + 180_000
        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000L)
            val r = ProbeHttp.postForm(
                "$base/oauth/token",
                mapOf("grant_type" to "urn:ietf:params:oauth:grant-type:device_code", "device_code" to deviceCode, "client_id" to "radarcount")
            )
            val body = runCatching { JSONObject(r.body) }.getOrNull()
            when {
                r.status == 200 && body?.has("access_token") == true -> {
                    emit(ProbeResult(name, true, "approved; token received")); return@withContext
                }
                body?.optString("error") == "authorization_pending" -> continue
                else -> { emit(ProbeResult(name, false, "HTTP ${r.status}: ${r.body.take(120)}")); return@withContext }
            }
        }
        emit(ProbeResult(name, false, "timed out waiting for approval"))
    }

    // ---- helpers -------------------------------------------------------------

    private suspend fun safely(name: String, block: suspend () -> ProbeResult): ProbeResult =
        withContext(Dispatchers.IO) {
            try { block() } catch (e: Exception) {
                ProbeResult(name, false, "${e.javaClass.simpleName}: ${e.message}")
            }
        }

    private fun randomToken(bytes: Int): String {
        val b = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    companion object {
        const val REDIRECT_URI = "radarcount://oauth"

        /**
         * Ride-end timeline from the service: the Karoo appears to bring wifi
         * back only after the ride goes idle, so keep trying for a while and
         * record when the network returns and when the newest FIT stops growing.
         */
        fun sendRideEndPing(context: Context, store: ProbeStore) {
            val base = store.serverUrl
            if (base.isEmpty()) return
            Thread {
                val t0 = System.currentTimeMillis()
                val lines = StringBuilder()
                var pingOk = false
                var lastSize = -1L
                var stableSince = -1L
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                fun net(): String {
                    val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return "none"
                    val t = when {
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bt"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cell"
                        else -> "other"
                    }
                    return t + if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "+validated" else ""
                }
                var attempt = 0
                while (System.currentTimeMillis() - t0 < 180_000 && !(pingOk && stableSince >= 0 && System.currentTimeMillis() - stableSince > 20_000)) {
                    val el = (System.currentTimeMillis() - t0) / 1000
                    val newest = File(Environment.getExternalStorageDirectory(), "FitFiles")
                        .listFiles { f -> f.name.endsWith(".fit", true) }?.maxByOrNull { it.lastModified() }
                    val size = newest?.length() ?: -1
                    if (size != lastSize) { lastSize = size; stableSince = System.currentTimeMillis() }
                    val fit = "fit ${size / 1024} KB" + if (size == lastSize && System.currentTimeMillis() - stableSince > 20_000) " (stable)" else ""
                    val result = if (pingOk) "already ok" else try {
                        val r = ProbeHttp.get("$base/ping?event=ride_end&attempt=$attempt&elapsed_s=$el&fit=${Uri.encode(newest?.name ?: "none")}&size=$size")
                        if (r.status == 200) pingOk = true
                        "HTTP ${r.status} in ${r.millis} ms"
                    } catch (e: Exception) {
                        e.javaClass.simpleName
                    }
                    lines.append("+${el}s ${net()}, $fit, $result\n")
                    android.util.Log.i("RadarCountProbe", "ride-end +${el}s ${net()} $fit $result")
                    store.lastRideEndPing = lines.toString().trimEnd()
                    attempt++
                    Thread.sleep(10_000)
                }
                lines.append(if (pingOk) "done: first success after ${lines.lines().indexOfFirst { it.contains("HTTP 200") } * 10}s" else "gave up after 3 min")
                store.lastRideEndPing = lines.toString()
            }.start()        }
    }
}
