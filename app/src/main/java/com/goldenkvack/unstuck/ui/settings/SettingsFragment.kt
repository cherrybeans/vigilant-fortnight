package com.goldenkvack.unstuck.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.preference.*
import com.goldenkvack.unstuck.MainForegroundService
import com.goldenkvack.unstuck.R

class SettingsFragment : PreferenceFragmentCompat() {

    private lateinit var preference: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preference = PreferenceManager.getDefaultSharedPreferences(requireContext())

        checkIfActiveAppBlock()
        editor = preference.edit()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        val profilePreference = findPreference<EditTextPreference>(getString(R.string.profile))
        profilePreference?.summary = "Display Name"

        val appBlockPreference =
            preferenceManager.findPreference<SwitchPreference>(getString(R.string.appblock))
        // App block monitoring starts automatically when toggled on
        appBlockPreference?.setOnPreferenceChangeListener { preference, newValue ->
            if (!appBlockPreference.isChecked) {
                // Toggle on: MainForegroundService.startService(context!!, "Monitoring.. ")
                MainForegroundService.startService(context!!, "Monitoring.. ")

                Toast.makeText(
                    activity!!.applicationContext,
                    "App blocking enabled",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                // Toggle off: MainForegroundService.stopService(context!!)
                MainForegroundService.stopService(context!!)

                Toast.makeText(
                    activity!!.applicationContext,
                    "App blocking disabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
            true
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference?) {
        if (preference is RestrictAppsPreference) {
            val dialogFragment: DialogFragment =
                RestrictAppsPreferenceFragmentCompat.newInstance(preference.key)
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(fragmentManager!!, null)
        } else super.onDisplayPreferenceDialog(preference)
    }

    private fun checkIfActiveAppBlock() {
        val appBlockingSetting =
            findPreference<SwitchPreference>(getString(R.string.appblock))

        val blockedAppsJson = preference.getString("currentlyBlockedApps", "{}")
        if (blockedAppsJson!! != "{}") {
            appBlockingSetting!!.isEnabled = false
            appBlockingSetting.summaryOn =
                "App blocking enabled. Cannot disable when there is an active app block."
        }
    }
}