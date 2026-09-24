package com.mobileclaude.app.handoff

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.ScanOptions

internal fun claudeLinkHandoffScanOptions(): ScanOptions =
    ScanOptions()
        .setCaptureActivity(HandoffCaptureActivity::class.java)
        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        .setPrompt("扫描 Codex 或 ChatGPT 生成的 Claude Link 接力二维码")
        .setBeepEnabled(false)
        .setBarcodeImageEnabled(false)
        .setOrientationLocked(false)

class HandoffCaptureActivity : CaptureActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val horizontalPadding = 18.dp
        val verticalPadding = 10.dp
        val backButton = TextView(this).apply {
            text = "‹  返回"
            contentDescription = "返回 Codex 窗口"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            background = GradientDrawable().apply {
                setColor(Color.argb(190, 0, 0, 0))
                cornerRadius = 22.dp.toFloat()
                setStroke(1.dp, Color.argb(150, 255, 255, 255))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { cancelScan() }
        }
        val layout = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            marginStart = 16.dp
            topMargin = 16.dp
        }
        addContentView(backButton, layout)
        backButton.setOnApplyWindowInsetsListener { view, insets ->
            val params = view.layoutParams as FrameLayout.LayoutParams
            params.topMargin = insets.systemWindowInsetTop + 12.dp
            view.layoutParams = params
            insets
        }
        backButton.requestApplyInsets()
    }

    @Deprecated("Handled explicitly so scan cancellation always returns to Codex")
    override fun onBackPressed() {
        cancelScan()
    }

    private fun cancelScan() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
