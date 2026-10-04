package com.clearoo.app.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.pm.PackageInfoCompat

/** Opens an email to support with the details needed to look into a problem already filled in. */
object Feedback {
    const val EMAIL = "roolabs.support+clearoo@gmail.com"

    fun send(context: Context) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "Clearoo feedback")
            putExtra(Intent.EXTRA_TEXT, body(context))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // No email app: hand over the address instead.
            runCatching {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("Clearoo support", EMAIL))
            }
            Toast.makeText(context, "No email app found. Address copied: $EMAIL", Toast.LENGTH_LONG).show()
        }
    }

    private fun body(context: Context): String {
        val version = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
        }.getOrDefault("unknown")
        val space = Device.storage()?.let { "${Fmt.bytes(it.freeBytes)} free of ${Fmt.bytes(it.totalBytes)}" } ?: "unknown"
        return buildString {
            append("Tell Roo what happened or what you'd like:\n\n\n\n")
            append("— Please keep this, it helps us fix things —\n")
            append("Phone: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            append("Clearoo: $version\n")
            append("Storage: $space\n")
            append("Low memory phone: ${if (Device.isLowMemory(context)) "yes" else "no"}\n")
        }
    }
}
