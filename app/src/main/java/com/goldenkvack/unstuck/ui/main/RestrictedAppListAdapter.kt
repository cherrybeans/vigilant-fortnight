package com.goldenkvack.unstuck.ui.main

import android.graphics.Color
import android.os.CountDownTimer
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.goldenkvack.unstuck.R

class RestrictedAppListAdapter(
    private var appList: List<AppDisplayListItem>
) :
    RecyclerView.Adapter<RestrictedAppListAdapter.AppViewHolder>() {

//    private var appList = blockedAppList

    class AppViewHolder(appListItem: View) : RecyclerView.ViewHolder(appListItem) {
        var appName: TextView = appListItem.findViewById(R.id.appItemName)
        var appTime: Chronometer = appListItem.findViewById(R.id.appItemTime)
        var appIcon: ImageView = appListItem.findViewById(R.id.appItemIcon)
        var blockedText: TextView = appListItem.findViewById(R.id.appItemBlockedText)
        var totalUsage: TextView = appListItem.findViewById(R.id.appItemTotalUsageTime)
        var remainingUsage: TextView = appListItem.findViewById(R.id.appItemRemainingUsage)
    }

    // Create new app blocking views invoked by layout manager
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val appListItem = LayoutInflater.from(parent.context)
            .inflate(R.layout.app_blocking_item, parent, false)
        // Layout settings for each list item
        appListItem.setPadding(20, 10, 10, 10)
        return AppViewHolder(appListItem)
    }

    // Replace contents of view invoked by layout manager
    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.appName.text = appList[position].displayName
        val finishTimeStamp = appList[position].blockTimeStamp
        holder.appIcon.setImageDrawable(appList[position].icon)

        val isAppBlocked = finishTimeStamp != null
        if (isAppBlocked) {
            holder.blockedText.visibility = View.VISIBLE
            holder.appTime.visibility = View.VISIBLE
            holder.totalUsage.visibility = View.INVISIBLE
            holder.remainingUsage.visibility = View.GONE

            if (finishTimeStamp != null && System.currentTimeMillis() < finishTimeStamp) {
                getBlockCountdown(finishTimeStamp, holder.appTime, holder).start()
            } else if (finishTimeStamp != null && finishTimeStamp <= System.currentTimeMillis()) {
                holder.appTime.text = "00:00"
                holder.appTime.setTextColor(Color.parseColor("#8bc34a"))
            }
        } else {
            holder.totalUsage.text = appList[position].todayUsageString
            holder.remainingUsage.text = appList[position].remainingUsage
        }
    }

    override fun getItemCount(): Int = appList.size

    fun setAppList(newAppList: List<AppDisplayListItem>) {
        this.appList = newAppList
        notifyDataSetChanged()
    }


    // Get countdown timer for each app in adapter list
    private fun getBlockCountdown(
        countDownFromTime: Long,
        chrono: Chronometer,
        holder: AppViewHolder
    ): CountDownTimer {
        val msToFinish = countDownFromTime - System.currentTimeMillis()
        chrono.base = SystemClock.elapsedRealtime() + msToFinish
        chrono.start()

        return object : CountDownTimer(msToFinish, 1000) {
            override fun onTick(millisUntilFinished: Long) {
            }

            override fun onFinish() {
                holder.appTime.text = "00:00"
                holder.appTime.setTextColor(Color.parseColor("#8bc34a"))
                chrono.stop()
            }
        }
    }
}