package com.kvamme.goldenpegasusius.ui.main

import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.text.bold
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.reflect.TypeToken
import com.kvamme.goldenpegasusius.MainActivity
import com.kvamme.goldenpegasusius.MainForegroundService
import com.kvamme.goldenpegasusius.R
import kotlinx.android.synthetic.main.fragment_main.*
import java.util.concurrent.TimeUnit

class AppDisplayListItem(
    val displayName: String?,
    val blockTimeStamp: Long?,
    var icon: Drawable?,
    var todayUsageString: String,
    var remainingUsage: String
)

class MainFragment : Fragment() {
    private lateinit var appOps: AppOpsManager
    private lateinit var appBlockingViewModel: MainViewModel
    private lateinit var usage: UsageStatsManager
    private lateinit var packageManager: PackageManager
    private lateinit var currentlyBlockedApps: MutableMap<String, Long>
    private lateinit var appUsageTimers: MutableMap<String, Long>
    private lateinit var appStepCounters: MutableMap<String, Int>
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var usageStatsMap: MutableMap<String, UsageStats>
    private lateinit var restrictedApps: Set<String>
    private lateinit var mAdapter: RestrictedAppListAdapter

    // Appblock variables
    private lateinit var blockTitle: TextView
    private lateinit var blockedAppName: TextView
    private lateinit var chrono: Chronometer
    private lateinit var blockTimeLabel: TextView
    private lateinit var appIcon: ImageView
    private lateinit var appUsageTime: TextView
    private lateinit var appBlockListTitle: TextView
    private lateinit var totalUsageTime: TextView
    private lateinit var motivationalText: TextView
    private lateinit var divider: View
    private lateinit var divider2: View
    private lateinit var divider3: View
    private var maxTimeLimit: Int = 30 * 60 * 1000 // 30 min in ms
    private var isAppBlockModeEnabled: Boolean = false

    // Strict mode variables
    private lateinit var strictModeLayout: ConstraintLayout
    private lateinit var strictModeTitle: TextView
    private lateinit var strictModeRemainingTime: TextView
    private var isStrictModeActivated = false

    companion object {
        fun newInstance() = MainFragment()
    }

    private lateinit var viewModel: MainViewModel

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)

        // Check usage data access permissions
        if (isAppBlockModeEnabled && !hasUsageDataAccessPermission()) {
            // Permission is not granted, show alert dialog to request for permission
            Log.d(
                "gold",
                "Usage data permissions not granted. Showing dialog asking for permissions."
            )
            showAlertDialog()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        sharedPrefs = getDefaultSharedPreferences(context)
        appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        packageManager = context.packageManager
        restrictedApps = sharedPrefs.getStringSet("restricted_apps", mutableSetOf())!!
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        appBlockingViewModel =
            ViewModelProviders.of(this).get(MainViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_main, container, false)

        currentlyBlockedApps = getCurrentlyBlockedApps()
        usageStatsMap = getUsageStatsMap()
        appUsageTimers = getAppUsageTimers()

        // Initialize app block views
        blockTitle = root.findViewById(R.id.block_title)
        blockedAppName = root.findViewById(R.id.blocked_app_name)
        appIcon = root.findViewById(R.id.app_icon)
        appUsageTime = root.findViewById(R.id.app_usage_time)
        chrono = root.findViewById(R.id.view_timer)
        blockTimeLabel = root.findViewById(R.id.block_explanation)
        appBlockListTitle = root.findViewById(R.id.app_list_title)
        divider = root.findViewById(R.id.app_divider)
        totalUsageTime = root.findViewById(R.id.total_usage_time)
        divider2 = root.findViewById(R.id.app_divider2)
        divider3 = root.findViewById(R.id.app_divider3)
        motivationalText = root.findViewById(R.id.motivational_text)

        // Initialize strict mode views
        strictModeLayout = root.findViewById(R.id.strict_mode_layout)
        strictModeTitle = root.findViewById(R.id.strict_title)
        strictModeRemainingTime = root.findViewById(R.id.strict_remaining_time)

        // Check if app blocking enabled in Settings
        isAppBlockModeEnabled = sharedPrefs.getBoolean(getString(R.string.appblock), false)
        maxTimeLimit =
            sharedPrefs.getString("time_limit", "${30 * 60 * 1000}")!!.toInt() // ms
        isStrictModeActivated = sharedPrefs.getBoolean("shouldUseStrictMode", false)

        if (isAppBlockModeEnabled) {
            MainForegroundService.startService(context!!, "Monitoring...")

            totalUsageTime.text =
                "Total usage of restricted apps today: ${getTotalUsageTimeDayAllRestrictedApps()}"

            if (isStrictModeActivated) {
                strictModeLayout.visibility = View.VISIBLE
                strictModeTitle.visibility = View.VISIBLE
                val timeLeftMs =
                    TimeUnit.MILLISECONDS.toMinutes(maxTimeLimit - getTotalAppUsageTime())
                strictModeRemainingTime.text = "Total usage time left: $timeLeftMs min"
                strictModeRemainingTime.visibility = View.VISIBLE
            }

            if (currentlyBlockedApps.entries.count() == 0) {
                hideViews()
            } else {
                blockTitle.text = "ACTIVE APP BLOCK"

                currentlyBlockedApps.forEach { (appPackageName, finishTimeStamp) ->
                    blockedAppName.text = getAppNameFromPackage(appPackageName)
                    appUsageTime.text =
                        "Total usage today: " + getAppTotalUsageTimeDay(appPackageName, true)

                    motivationalText.visibility = View.VISIBLE
                    appIcon.setImageDrawable(getAppIcon(appPackageName))
                    if (System.currentTimeMillis() < finishTimeStamp) {
                        getBlockCountdown(finishTimeStamp, chrono).start()
                    } else if (finishTimeStamp <= System.currentTimeMillis()) {
                        chrono.text = "00:00"
                        chrono.setTextColor(Color.parseColor("#8bc34a"))
                    }
                }
            }
        } else {
            MainForegroundService.stopService(context!!)
            appBlockModeDisabled()
        }
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (isAppBlockModeEnabled) {
            mAdapter = RestrictedAppListAdapter(getAdapterList())
            appblocking_recycler_view.apply {
                setHasFixedSize(true)
                layoutManager = LinearLayoutManager(activity)
                adapter = mAdapter
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isAppBlockModeEnabled) {
            mAdapter = RestrictedAppListAdapter(getAdapterList())
            appblocking_recycler_view.apply {
                setHasFixedSize(true)
                layoutManager = LinearLayoutManager(activity)
                adapter = mAdapter
            }
        }
    }

    private fun hasUsageDataAccessPermission(): Boolean {
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context!!.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun showAlertDialog() {
        val alert = AlertDialog.Builder(activity!!)
        val titleMessage = SpannableStringBuilder()
            .append("Allow ")
            .bold { append("Golden Pegasus ") }
            .append("to access your usage data?")

        alert.setTitle(titleMessage)
        alert.setMessage("In order to use the App Blocking feature, please enable \"Usage Access Permission\" on your device.")

        alert.setPositiveButton("OK") { dialog, which ->
            // Redirect to settings to enable usage access permission
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        alert.setNegativeButton("Cancel") { dialog, which ->
            Toast.makeText(
                activity!!.applicationContext,
                "Permission request denied.",
                Toast.LENGTH_SHORT
            ).show()
            activity!!.onBackPressed()
        }

        val dialog: AlertDialog = alert.create()
        dialog.show() // Display the alert dialog on app interface
    }

    private fun getBlockCountdown(
        countDownFromTime: Long,
        chrono: Chronometer
    ): CountDownTimer {
        val msToFinish = countDownFromTime - System.currentTimeMillis()
        chrono.base = SystemClock.elapsedRealtime() + msToFinish
        chrono.start()
        return object : CountDownTimer(msToFinish, 1000) {
            override fun onTick(millisUntilFinished: Long) {
            }

            override fun onFinish() {
                chrono.text = "00:00"
                chrono.setTextColor(Color.parseColor("#8bc34a"))

                mAdapter.setAppList(getAdapterList())
                chrono.stop()
            }
        }
    }

    private fun getAppNameFromPackage(targetPackageName: String): String {
        val fullAppList = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        fullAppList.forEach { appInfo: ApplicationInfo ->
            if (appInfo.packageName == targetPackageName) {
                return appInfo.loadLabel(packageManager).toString()
            }
        }
        return targetPackageName
    }

    private fun getAppIcon(packageName: String): Drawable? {
        var icon: Drawable? = null
        try {
            icon = packageManager.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return icon
    }

    private fun getCurrentlyBlockedApps(): MutableMap<String, Long> {
        val type = object : TypeToken<MutableMap<String, Long>>() {}.type
        val blockedAppsJson = sharedPrefs.getString("currentlyBlockedApps", null)

        currentlyBlockedApps =
            if (blockedAppsJson !== null) MainActivity.gson.fromJson(
                blockedAppsJson,
                type
            ) else mutableMapOf()
        return currentlyBlockedApps
    }

    private fun getAdapterList(): List<AppDisplayListItem> {
        currentlyBlockedApps = getCurrentlyBlockedApps()
        val blockedAppList: MutableList<AppDisplayListItem> = arrayListOf()

        restrictedApps.forEach { appPackageName ->
            val blockFinishTimeStamp =
                if (currentlyBlockedApps.contains(appPackageName)) currentlyBlockedApps[appPackageName] else null
            blockedAppList.add(
                AppDisplayListItem(
                    getAppNameFromPackage(appPackageName),
                    blockFinishTimeStamp,
                    getAppIcon(appPackageName),
                    getAppTotalUsageTimeDay(appPackageName),
                    getAppUsageToString(appPackageName)
                )
            )
        }
        blockedAppList.sortWith(compareBy { it.displayName })
        return blockedAppList
    }

    private fun hideViews() {
        // Hide app blocking countdown and pedometer views if no currently blocked apps
        blockTitle.text = "NO ACTIVE APP BLOCK"
        val blockTitleParams = blockTitle.layoutParams as ViewGroup.MarginLayoutParams
        blockTitleParams.topMargin = 200
        val dividerParams = divider.layoutParams as ViewGroup.MarginLayoutParams
        dividerParams.topMargin = 200

        blockedAppName.visibility = View.GONE
        appIcon.visibility = View.GONE
        chrono.visibility = View.GONE
        blockTimeLabel.visibility = View.GONE
        appUsageTime.visibility = View.GONE
        divider2.visibility = View.GONE
        motivationalText.visibility = View.GONE
    }

    private fun getUsageStatsMap(): MutableMap<String, UsageStats> {
        val endTime = System.currentTimeMillis()
        val beginTime = endTime - (24 * 60 * 60 * 1000)
        // Usage stats for all apps over the past 24 hours
        return usage.queryAndAggregateUsageStats(beginTime, endTime)
    }

    private fun convertMsToHoursToString(millis: Long, usePretty: Boolean = false): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(hours)

        return String.format(
            if (usePretty) "%dh %dmin" else "%dh %02dmin",
            hours, minutes
        )
    }

    private fun getTotalUsageTimeDayAllRestrictedApps(): String {
        var result: Long = 0
        usageStatsMap.filter { (packageName: String, _) ->
            restrictedApps.contains(packageName)
        }.forEach { (_, usageStats) ->
            result += usageStats.totalTimeInForeground
        }
        return convertMsToHoursToString(result, true) // ms
    }

    private fun getAppTotalUsageTimeDay(
        targetPackageName: String,
        usePretty: Boolean = false
    ): String {
        usageStatsMap.forEach { (_, usageStats) ->
            if (usageStats.packageName == targetPackageName) {
                return convertMsToHoursToString(usageStats.totalTimeInForeground, usePretty)
            }
        }
        return "0h 00min"
    }

    private fun getAppUsageTimers(): MutableMap<String, Long> {
        val type = object : TypeToken<MutableMap<String, Long>>() {}.type
        val appUsageTimersJson = sharedPrefs.getString("appUsageTimers", null)

        appUsageTimers =
            if (appUsageTimersJson !== null) MainActivity.gson.fromJson(
                appUsageTimersJson,
                type
            ) else mutableMapOf()
        return appUsageTimers
    }

    private fun getTotalAppUsageTime(): Long {
        var totalAppUsageTime: Long = 0

        appUsageTimers.forEach {
            totalAppUsageTime += it.value
        }
        return totalAppUsageTime
    }

    private fun getAppUsageToString(targetPackageName: String): String {
        val appUsage = appUsageTimers.getOrDefault(targetPackageName, 0) // ms
        val usageMs = if (isStrictModeActivated) appUsage else maxTimeLimit - appUsage
        val usageMin = TimeUnit.MILLISECONDS.toMinutes(usageMs)

        return String.format("$usageMin min ${if (isStrictModeActivated) "used" else "left"}")
    }

    private fun appBlockModeDisabled() {
        hideViews()
        val message: SpannableStringBuilder = SpannableStringBuilder()
            .bold { append("APP BLOCKING MODE IS DISABLED") }
        blockTitle.text = message
        blockTitle.setTextColor(Color.DKGRAY)
        divider3.visibility = View.GONE
        divider.visibility = View.GONE
        motivationalText.visibility = View.VISIBLE
        motivationalText.text =
            "Please go to the settings to enable the mode if you want to use it."
        appBlockListTitle.visibility = View.GONE
        totalUsageTime.visibility = View.GONE
    }
}


