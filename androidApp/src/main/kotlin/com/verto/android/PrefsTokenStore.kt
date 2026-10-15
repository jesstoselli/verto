package com.verto.android

import android.content.Context
import com.verto.data.remote.Session
import com.verto.data.remote.TokenStore
import kotlinx.serialization.json.Json

/**
 * App-private and excluded from backup (see the backup and data extraction rules in res/xml): a restored token on a new phone
 * would be a reused refresh token, which makes the server revoke the whole session family.
 */
class PrefsTokenStore(context: Context) : TokenStore {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(): Session? = prefs.getString(KEY, null)?.let {
        try {
            Json.decodeFromString(Session.serializer(), it)
        } catch (_: Exception) {
            null
        }
    }

    override fun save(session: Session?) {
        val editor = prefs.edit()
        if (session == null) editor.remove(KEY) else editor.putString(KEY, Json.encodeToString(Session.serializer(), session))
        editor.apply()
    }

    companion object {
        const val FILE = "verto_session"
        private const val KEY = "session"
    }
}
