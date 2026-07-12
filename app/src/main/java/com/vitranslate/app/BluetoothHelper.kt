package com.vitranslate.app

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * Định tuyến micro + loa sang tai nghe Bluetooth (kênh SCO/thoại).
 * - Android 12+ (API 31): dùng setCommunicationDevice (API mới).
 * - Android 10/11: dùng startBluetoothSco (API cũ, vẫn hoạt động).
 */
object BluetoothHelper {

    fun enableHeadsetMic(context: Context): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return if (Build.VERSION.SDK_INT >= 31) {
            val device = am.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
            if (device != null) {
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                am.setCommunicationDevice(device)
            } else false
        } else {
            @Suppress("DEPRECATION")
            run {
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                am.startBluetoothSco()
                am.isBluetoothScoOn = true
                true
            }
        }
    }

    fun disableHeadsetMic(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= 31) {
            am.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            run {
                am.stopBluetoothSco()
                am.isBluetoothScoOn = false
            }
        }
        am.mode = AudioManager.MODE_NORMAL
    }
}
