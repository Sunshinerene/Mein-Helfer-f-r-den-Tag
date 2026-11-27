package de.meinhelfer.sunny.poc

import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import de.meinhelfer.sunny.poc.emotion.AvatarPreview
import de.meinhelfer.sunny.poc.emotion.EmotionEngine
import de.meinhelfer.sunny.poc.emotion.EmotionGroup
import de.meinhelfer.sunny.poc.emotion.EmotionMediaCatalog
import de.meinhelfer.sunny.poc.emotion.MediaBinding
import de.meinhelfer.sunny.poc.emotion.FrameColor
import de.meinhelfer.sunny.poc.emotion.FrameShape
import de.meinhelfer.sunny.poc.emotion.PresentationMode
import de.meinhelfer.sunny.poc.voice.AndroidVoiceOutput
import de.meinhelfer.sunny.poc.voice.VoicePhase
import de.meinhelfer.sunny.poc.voice.VoiceSample
import de.meinhelfer.sunny.poc.voice.VoiceSession
import de.meinhelfer.sunny.poc.voice.VoiceState
import java.util.Locale

internal class EmotionVoicePanel(context: Context) : LinearLayout(context) {
    private val engine = EmotionEngine()
    // Owner-approved original stills, explicitly bound to stable emotion IDs.
    private val media = EmotionMediaCatalog(engine, listOf(
        MediaBinding("su01.webp", R.drawable.sunny_su01),
        MediaBinding("su02.webp", R.drawable.sunny_su02),
        MediaBinding("su03.webp", R.drawable.sunny_su03),
        MediaBinding("su04.webp", R.drawable.sunny_su04),
        MediaBinding("su05.webp", R.drawable.sunny_su05),
        MediaBinding("su06.webp", R.drawable.sunny_su06),
        MediaBinding("su07.webp", R.drawable.sunny_su07),
        MediaBinding("su08.webp", R.drawable.sunny_su08),
        MediaBinding("su09.webp", R.drawable.sunny_su09),
        MediaBinding("su10.webp", R.drawable.sunny_su10),
        MediaBinding("su11.webp", R.drawable.sunny_su11),
        MediaBinding("su12.webp", R.drawable.sunny_su12),
        MediaBinding("su13.webp", R.drawable.sunny_su13),
        MediaBinding("su14.webp", R.drawable.sunny_su14),
        MediaBinding("su15.webp", R.drawable.sunny_su15),
        MediaBinding("su16.webp", R.drawable.sunny_su16),
        MediaBinding("su17.webp", R.drawable.sunny_su17),
        MediaBinding("su18.webp", R.drawable.sunny_su18),
        MediaBinding("su19.webp", R.drawable.sunny_su19),
        MediaBinding("su20.webp", R.drawable.sunny_su20),
        MediaBinding("su21.webp", R.drawable.sunny_su21),
        MediaBinding("su22.webp", R.drawable.sunny_su22),
        MediaBinding("su23.webp", R.drawable.sunny_su23),
        MediaBinding("su24.webp", R.drawable.sunny_su24),
        MediaBinding("su25.webp", R.drawable.sunny_su25),
        MediaBinding("su26.webp", R.drawable.sunny_su26),
        MediaBinding("su27.webp", R.drawable.sunny_su27),
        MediaBinding("su28.webp", R.drawable.sunny_su28),
        MediaBinding("su29.webp", R.drawable.sunny_su29),
        MediaBinding("su30.webp", R.drawable.sunny_su30),
        MediaBinding("su31.webp", R.drawable.sunny_su31),
        MediaBinding("su32.webp", R.drawable.sunny_su32),
        MediaBinding("su33.webp", R.drawable.sunny_su33),
        MediaBinding("su34.webp", R.drawable.sunny_su34),
        MediaBinding("su35.webp", R.drawable.sunny_su35),
    ))
    private var emotion = EmotionGroup.Friendly
    private var variant = 0
    private var mode = PresentationMode.Auto
    private var shape = FrameShape.Rounded
    private var color = FrameColor.Green
    private var sample = VoiceSample.Greeting
    private var foreground = false

    private val preview = AvatarPreview(context)
    private val emotionLabel = liveText()
    private val videoStatus = textView()
    private val speechText = textView()
    private val speechStatus = liveText()
    private val voice = VoiceSession(AndroidVoiceOutput(context), ::renderVoice)
    private val start = button("Testtext sprechen") { if (foreground) voice.play(sample) }
    private val stop = button("Sprachausgabe stoppen", voice::stop)

    private val emotionPicker = picker(EmotionGroup.entries.map { it.label }) { position ->
        val selected = EmotionGroup.entries[position]
        if (selected != emotion) {
            emotion = selected
            variant = 0
            renderPreview()
        }
    }
    private val modePicker = picker(PresentationMode.entries.map { it.label }) { position ->
        mode = PresentationMode.entries[position]
        renderPreview()
    }
    private val shapePicker = picker(FrameShape.entries.map { it.label }) { position ->
        shape = FrameShape.entries[position]
        preview.setFrame(shape, color)
    }
    private val colorPicker = picker(FrameColor.entries.map { it.label }) { position ->
        color = FrameColor.entries[position]
        preview.setFrame(shape, color)
    }
    private val samplePicker = picker(VoiceSample.entries.map { it.title }) { position ->
        sample = VoiceSample.entries[position]
        speechText.text = sample.text
    }

    init {
        orientation = VERTICAL
        addView(preview, LayoutParams(LayoutParams.MATCH_PARENT, dp(102)))
        addView(emotionLabel)
        addView(videoStatus)
        addSelector("Emotion für die Vorschau", emotionPicker)
        addView(button("Nächste Variante") {
            variant++
            renderPreview()
        })
        addSelector("Darstellung", modePicker)
        addSelector("Rahmenform", shapePicker)
        addSelector("Rahmenfarbe", colorPicker)
        addSelector("Synthetischer Testtext", samplePicker)
        addView(speechText)
        addView(start)
        addView(stop)
        addView(speechStatus)
        reset()
    }

    fun setActive(active: Boolean) {
        foreground = active
        if (!active) voice.stop()
        renderVoice(voice.state)
    }

    fun reset() {
        emotion = EmotionGroup.Friendly
        variant = 0
        mode = PresentationMode.Auto
        shape = FrameShape.Rounded
        color = FrameColor.Green
        sample = VoiceSample.Greeting
        emotionPicker.setSelection(emotion.ordinal)
        modePicker.setSelection(mode.ordinal)
        shapePicker.setSelection(shape.ordinal)
        colorPicker.setSelection(color.ordinal)
        samplePicker.setSelection(sample.ordinal)
        speechText.text = sample.text
        preview.setFrame(shape, color)
        voice.reset()
    }

    private fun renderVoice(state: VoiceState) {
        speechStatus.text = when (state.phase) {
            VoicePhase.Idle -> "Sprachausgabe bereit für einen Test."
            VoicePhase.Preparing -> "Die lokale Stimme wird vorbereitet."
            VoicePhase.Speaking -> "Sprachausgabe läuft."
            VoicePhase.Finished -> "Sprachausgabe beendet."
            VoicePhase.Stopped -> "Sprachausgabe gestoppt."
            VoicePhase.Unavailable -> "Keine deutsche Offline-Stimme verfügbar. Der Testtext bleibt sichtbar."
            VoicePhase.Error -> "Sprachausgabe fehlgeschlagen. Der Testtext bleibt sichtbar."
        }
        start.isEnabled = foreground && !state.phase.busy
        stop.isEnabled = foreground && state.phase.busy
        samplePicker.isEnabled = !state.phase.busy
        speechText.text = (state.sample?.takeIf { state.phase.busy } ?: sample).text
        renderPreview()
    }

    private fun renderPreview() {
        val speaking = voice.state.phase == VoicePhase.Speaking
        val frame = engine.displayed(engine.select(emotion, variant), speaking)
        val selected = media.resolve(frame.id, mode, ValueAnimator.areAnimatorsEnabled())
        val visible = if (foreground) selected else selected.copy(video = null)
        val summary = frame.group.label + " · " + frame.id.code.uppercase(Locale.ROOT)
        if (emotionLabel.text.toString() != summary) emotionLabel.text = summary
        val videoMissing = mode == PresentationMode.Video && selected.video == null
        videoStatus.visibility = if (videoMissing) VISIBLE else GONE
        if (videoMissing) videoStatus.text = "Für diese Variante ist kein freigegebenes Video verfügbar."
        preview.show(frame, visible, loop = speaking)
    }

    private fun addSelector(label: String, picker: Spinner) {
        addView(textView().apply {
            text = label
            labelFor = picker.id
            setPadding(0, dp(8), 0, 0)
        })
        addView(picker)
    }

    private fun picker(labels: List<String>, select: (Int) -> Unit) = Spinner(context).apply {
        id = View.generateViewId()
        isSaveEnabled = false
        minimumHeight = dp(48)
        adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, labels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) =
                select(position)

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }
    }

    private fun button(label: String, onClick: () -> Unit) = Button(context).apply {
        text = label
        isAllCaps = false
        minHeight = dp(48)
        setOnClickListener { onClick() }
    }

    private fun liveText() = textView().apply { accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE }

    private fun textView() = TextView(context).apply {
        textSize = 15f
        setTextColor(0xff1d2923.toInt())
        setPadding(0, dp(6), 0, dp(4))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
