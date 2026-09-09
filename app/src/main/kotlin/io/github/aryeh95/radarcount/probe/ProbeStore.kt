package io.github.aryeh95.radarcount.probe

import android.content.Context

/**
 * Scratch state for the connection probe: the test server URL, the PKCE
 * verifier waiting for a redirect, and whatever the last redirect or
 * ride-end ping produced. Plain SharedPreferences; this is throwaway.
 */
class ProbeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("radarcount_probe", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString("server_url", null) ?: io.github.aryeh95.radarcount.BuildConfig.PROBE_DEFAULT_URL
        set(v) { prefs.edit().putString("server_url", v.trim().trimEnd('/')).apply() }

    var pkceVerifier: String?
        get() = prefs.getString("pkce_verifier", null)
        set(v) { prefs.edit().putString("pkce_verifier", v).apply() }

    var pkceState: String?
        get() = prefs.getString("pkce_state", null)
        set(v) { prefs.edit().putString("pkce_state", v).apply() }

    /** Raw redirect URI received by MainActivity, if any. */
    var lastRedirect: String?
        get() = prefs.getString("last_redirect", null)
        set(v) { prefs.edit().putString("last_redirect", v).apply() }

    /** Outcome of the last ride-end ping sent from the service. */
    var lastRideEndPing: String?
        get() = prefs.getString("last_ride_end_ping", null)
        set(v) { prefs.edit().putString("last_ride_end_ping", v).apply() }
}
