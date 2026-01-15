package com.example.apphipertension

import android.app.Application

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 1. Crear canales de notificación
        NotificationUtils.createNotificationChannels(this)

        // 2. Programar la alarma diaria (se ejecutará una vez al iniciar la app)
        AlarmUtils.scheduleDailyTipAlarm(this)
        AlarmUtils.scheduleAllReminders(this)
    }
}