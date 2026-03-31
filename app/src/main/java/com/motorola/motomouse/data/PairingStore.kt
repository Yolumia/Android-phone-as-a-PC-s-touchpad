package com.motorola.motomouse.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "motomouse_pairing")

class PairingStore(private val context: Context) {
    suspend fun load(): PairingInfo? {
        return context.dataStore.data
            .map { preferences ->
                preferences[PAIRING_JSON]?.let(PairingInfo::fromStoredJson)
            }
            .firstOrNull()
    }

    suspend fun save(pairingInfo: PairingInfo) {
        context.dataStore.edit { preferences ->
            preferences[PAIRING_JSON] = pairingInfo.toJson()
        }
    }

    suspend fun clear() {
        context.dataStore.edit { preferences ->
            preferences.remove(PAIRING_JSON)
        }
    }

    private companion object {
        val PAIRING_JSON = stringPreferencesKey("pairing_json")
    }
}

