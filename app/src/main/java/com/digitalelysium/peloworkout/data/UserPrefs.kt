package com.digitalelysium.peloworkout.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// 1) SINGLE top-level DataStore instance for the whole app.
//    Do NOT duplicate this in another file with the same name.
val Context.userPrefsDataStore by preferencesDataStore(
    name = "user_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

enum class Gender { Male, Female, Unspecified }

data class UserProfile(
    val massKg: Double?,
    val age: Int?,
    val gender: Gender
)

object UserPrefs {
    private val KEY_MASS = doublePreferencesKey("mass_kg")
    private val KEY_AGE = intPreferencesKey("age")
    private val KEY_GENDER = stringPreferencesKey("gender")

    fun profileFlow(ctx: Context): Flow<UserProfile> =
        // 2) Use applicationContext and guard the flow with catch
        ctx.applicationContext.userPrefsDataStore.data
            .catch { e ->
                if (e is IOException) emit(emptyPreferences()) else throw e
            }
            .map { p ->
                val mass = p[KEY_MASS]
                val age = p[KEY_AGE]
                val gender = when (p[KEY_GENDER]) {
                    Gender.Male.name -> Gender.Male
                    Gender.Female.name -> Gender.Female
                    else -> Gender.Unspecified
                }
                UserProfile(massKg = mass, age = age, gender = gender)
            }

    suspend fun save(ctx: Context, profile: UserProfile) {
        // 3) Use applicationContext for writes as well
        ctx.applicationContext.userPrefsDataStore.edit { p ->
            if (profile.massKg != null) p[KEY_MASS] = profile.massKg else p.remove(KEY_MASS)
            if (profile.age != null) p[KEY_AGE] = profile.age else p.remove(KEY_AGE)
            p[KEY_GENDER] = profile.gender.name
        }
        android.util.Log.d("UserPrefs", "profile=$profile")
    }
}
