package com.tqhit.adlib.sdk.firebase

import android.util.Log
import androidx.annotation.XmlRes
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseRemoteConfigHelper @Inject constructor(
    val firebaseRemoteConfig: FirebaseRemoteConfig
) {
    companion object {
        private const val TAG = "FirebaseRemoteConfig"
    }

    fun fetchAndActivate(
        onFetchComplete: ((Boolean) -> Unit)? = null,
        @XmlRes defaultConfig: Int? = null
    ) {
        defaultConfig?.let {
            try {
                firebaseRemoteConfig.setDefaultsAsync(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting Remote Config defaults: ${e.message}")
            }
        }

        firebaseRemoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "Remote Config fetch and activate succeeded")
                onFetchComplete?.invoke(true)
            } else {
                Log.e(TAG, "Remote Config fetch and activate failed: ${task.exception?.message}")
                onFetchComplete?.invoke(false)
            }
        }
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return try {
            val value = firebaseRemoteConfig.getString(key)
            if (value.isNotEmpty()) value else defaultValue
        } catch (e: Exception) {
            Log.e(TAG, "Error getting string for key $key: ${e.message}")
            defaultValue
        }
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return try {
            firebaseRemoteConfig.getBoolean(key)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting boolean for key $key: ${e.message}")
            defaultValue
        }
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return try {
            firebaseRemoteConfig.getLong(key)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting long for key $key: ${e.message}")
            defaultValue
        }
    }

    fun getDouble(key: String, defaultValue: Double = 0.0): Double {
        return try {
            firebaseRemoteConfig.getDouble(key)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting double for key $key: ${e.message}")
            defaultValue
        }
    }
}
