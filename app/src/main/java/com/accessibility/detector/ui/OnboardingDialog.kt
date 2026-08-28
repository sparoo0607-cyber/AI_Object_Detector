package com.accessibility.detector.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Window
import android.widget.Button
import com.accessibility.detector.R

/**
 * First-launch accessible onboarding introduction dialog.
 */
class OnboardingDialog(
    context: Context,
    private val onStartClicked: () -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_onboarding)
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnStart = findViewById<Button>(R.id.btnStartLiveAssist)
        btnStart.setOnClickListener {
            dismiss()
            onStartClicked.invoke()
        }
    }
}
