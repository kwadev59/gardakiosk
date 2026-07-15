package com.example.kioskdeviceowner.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class KioskNotificationListener : NotificationListenerService() {
    
    companion object {
        val activeNotificationsList = mutableListOf<StatusBarNotification>()
        var listener: NotificationUpdateListener? = null
    }

    interface NotificationUpdateListener {
        fun onNotificationsUpdated()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        updateActiveNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        updateActiveNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        updateActiveNotifications()
    }

    private fun updateActiveNotifications() {
        try {
            val active = activeNotifications?.toList() ?: emptyList()
            synchronized(activeNotificationsList) {
                activeNotificationsList.clear()
                activeNotificationsList.addAll(active)
            }
            // Trigger update on Main Thread
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                listener?.onNotificationsUpdated()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
