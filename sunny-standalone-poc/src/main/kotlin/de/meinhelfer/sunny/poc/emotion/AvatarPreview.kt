package de.meinhelfer.sunny.poc.emotion

import android.content.ContentResolver
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView

internal enum class FrameShape(val label: String, val radiusDp: Int) {
    Rounded("Abgerundet", 18), Square("Eckig", 2),
}

internal enum class FrameColor(val label: String, val fill: Int, val border: Int) {
    Green("Salbei", 0xffe8f2eb.toInt(), 0xff567d69.toInt()),
    Blue("Blau", 0xffe9f0f8.toInt(), 0xff546f94.toInt()),
    Copper("Kupfer", 0xfff6ede6.toInt(), 0xff936549.toInt()),
}

internal class AvatarPreview(context: Context) : FrameLayout(context) {
    private val placeholder = TextView(context).apply {
        text = "Avatar-Platzhalter"
        textSize = 16f
        setTextColor(Color.rgb(29, 41, 35))
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(12), dp(10), dp(12))
    }
    private val image = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
    }
    private var video: VideoView? = null
    private var current: Preview? = null

    private data class Preview(val frame: EmotionFrame, val choice: MediaChoice, val loop: Boolean)

    init {
        addView(placeholder, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(image, mediaLayout())
    }

    fun setFrame(shape: FrameShape, color: FrameColor) {
        background = GradientDrawable().apply {
            setColor(color.fill)
            setStroke(dp(2), color.border)
            cornerRadius = dp(shape.radiusDp).toFloat()
        }
    }

    fun show(frame: EmotionFrame, choice: MediaChoice, loop: Boolean) {
        val next = Preview(frame, choice, loop)
        if (next == current) return
        current = next
        stopVideo()

        val hasImage = choice.still?.let { id ->
            runCatching {
                image.setImageResource(id)
                image.drawable != null
            }.getOrDefault(false)
        } ?: false
        if (!hasImage) image.setImageDrawable(null)
        image.visibility = if (hasImage) VISIBLE else GONE
        placeholder.visibility = if (hasImage) GONE else VISIBLE
        choice.video?.let { playVideo(it, loop) }
    }

    private fun playVideo(id: Int, loop: Boolean) {
        val view = VideoView(context).apply {
            setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        video = view
        addView(view, mediaLayout())
        view.setOnPreparedListener { player ->
            if (video === view) {
                runCatching {
                    player.setVolume(0f, 0f)
                    player.isLooping = loop
                    view.start()
                }.onFailure { if (video === view) stopVideo() }
            }
        }
        view.setOnErrorListener { _, _, _ ->
            if (video === view) stopVideo()
            true
        }
        view.setOnCompletionListener {
            if (video === view) stopVideo()
        }
        val uri = Uri.Builder().scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(context.packageName)
            .appendPath(id.toString())
            .build()
        runCatching { view.setVideoURI(uri) }.onFailure { if (video === view) stopVideo() }
    }

    private fun stopVideo() {
        val view = video ?: return
        video = null
        view.suspend()
        removeView(view)
    }

    private fun mediaLayout() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
        val margin = dp(6)
        setMargins(margin, margin, margin, margin)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
