package com.example.geminimultimodalliveapi.utils

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.example.geminimultimodalliveapi.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

object GoogleSignInHelper {

    private val SCOPES = arrayOf(
        Scope(DriveScopes.DRIVE_FILE),
        Scope("https://www.googleapis.com/auth/calendar"),
        Scope("https://www.googleapis.com/auth/calendar.events")
    )

    fun buildGso(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(SCOPES[0], *SCOPES.drop(1).toTypedArray())
            .build()
    }

    fun getClient(context: Context): GoogleSignInClient {
        return GoogleSignIn.getClient(context, buildGso())
    }
}

fun Activity.showReAuthDialog(
    existingDialog: AlertDialog?,
    onConfirm: () -> Unit
): AlertDialog? {
    if (isFinishing || isDestroyed) return existingDialog
    if (existingDialog?.isShowing == true) return existingDialog

    val newDialog = AlertDialog.Builder(this)
        .setTitle(R.string.reauth_title)
        .setMessage(R.string.reauth_message)
        .setPositiveButton(R.string.reauth_positive) { _, _ ->
            onConfirm()
        }
        .setNegativeButton("ยกเลิก", null)
        .create()
    
    newDialog.show()
    return newDialog
}
