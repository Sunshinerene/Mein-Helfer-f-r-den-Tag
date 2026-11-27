package de.meinhelfer.sunny.poc

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import de.meinhelfer.sunny.core.FallbackReason
import de.meinhelfer.sunny.core.Reply
import de.meinhelfer.sunny.core.Route
import de.meinhelfer.sunny.core.SunnyResponse
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class MainActivity : Activity() {
    private val core = CoreDemo()
    private val connect = MockConnect()
    private lateinit var input: EditText
    private lateinit var coreStatus: TextView
    private lateinit var connectStatus: TextView
    private lateinit var connectResult: TextView
    private lateinit var grantButton: Button
    private lateinit var revokeButton: Button
    private lateinit var choices: RadioGroup
    private lateinit var selected: CapabilityDescriptor
    private var initialChoice = View.NO_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(24))
            isFocusableInTouchMode = true
            addView(label("EXPERIMENTELL · OFFLINE", 13, Green, bold = true))
            addView(label(getString(R.string.app_name), 30, Ink, bold = true), margins(top = 6))
            addView(label("Avatar-Platzhalter", 14, Green).apply {
                background = surface(PaleGreen)
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }, margins(top = 12, width = LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(buildCoreSection(), margins(top = 20))
            addView(buildConnectSection(), margins(top = 16))
            addView(button("Hilfe", ::showHelp), margins(top = 12))
            addView(button("Testsitzung zurücksetzen", ::resetSession))
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isSaveEnabled = false
            addView(page)
            setOnApplyWindowInsetsListener { view, insets ->
                applyInsets(view, insets)
                insets
            }
        }
        // Android 15 enforces edge-to-edge for targetSdk 35. Opt in on Android 11–14.
        if (Build.VERSION.SDK_INT in 30..34) {
            @Suppress("DEPRECATION")
            window.setDecorFitsSystemWindows(false)
        }
        setContentView(scroll)
        page.requestFocus()
        scroll.requestApplyInsets()
    }

    private fun buildCoreSection(): LinearLayout = section("Sunny Core").apply {
        addView(label("Simulierte Laufzeit · feste Antworten", 15, Green))
        input = EditText(context).apply {
            hint = "Synthetischen Testtext eingeben"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            filters = arrayOf(InputFilter.LengthFilter(4_096))
            maxLines = 4
            isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            if (Build.VERSION.SDK_INT >= 29) {
                importantForContentCapture = View.IMPORTANT_FOR_CONTENT_CAPTURE_NO
            }
        }
        addView(input, margins(top = 8))
        addView(button("Simulation ausführen") { submit(simulateMissingRuntime = false) })
        addView(button("Fehlende Laufzeit simulieren") { submit(simulateMissingRuntime = true) })
        coreStatus = status("Bereit für einen Test.")
        addView(coreStatus, margins(top = 8))
    }

    private fun buildConnectSection(): LinearLayout = section("Sunny Connect").apply {
        addView(label("MeinHelfer-Simulation · feste Beispieldaten · nur Lesen", 15, Green))
        val capabilities = connect.capabilities()
        selected = capabilities.first()
        choices = RadioGroup(context).apply {
            orientation = RadioGroup.VERTICAL
            isSaveEnabled = false
        }
        for (capability in capabilities) {
            val choice = RadioButton(context).apply {
                id = View.generateViewId()
                isSaveEnabled = false
                text = capability.title + "\n" + capability.id + " · v" + capability.version
                setTextColor(Ink)
                textSize = 15f
                minHeight = dp(56)
            }
            choices.addView(choice)
            if (capability == selected) initialChoice = choice.id
            choice.setOnClickListener {
                selected = capability
                updateGrantStatus()
                connectResult.text = "Bereit für eine Abfrage."
            }
        }
        choices.check(initialChoice)
        addView(choices, margins(top = 8))
        connectStatus = status("")
        addView(connectStatus, margins(top = 8))
        grantButton = button("Auswahl für diesen Test freigeben") {
            connect.grantForTest(selected.id, selected.version)
            updateGrantStatus()
            connectResult.text = "Bereit für eine Abfrage."
        }
        revokeButton = button("Freigabe der Auswahl widerrufen") {
            connect.revokeForTest(selected.id, selected.version)
            updateGrantStatus()
            connectResult.text = "Bereit für eine Abfrage."
        }
        addView(grantButton, margins(top = 8))
        addView(revokeButton)
        addView(button("Auswahl abfragen", ::readSelection))
        connectResult = status("Bereit für eine Abfrage.")
        addView(connectResult, margins(top = 8))
        updateGrantStatus()
    }

    private fun submit(simulateMissingRuntime: Boolean) {
        val text = input.text.toString()
        closeKeyboard()
        // Both POC runtimes finish inline without blocking work or an Android dependency in the core.
        suspend { core.respond(text, simulateMissingRuntime) }
            .startCoroutine(Continuation(EmptyCoroutineContext) { result ->
                coreStatus.text = result.fold(::describeCore, { "Die Simulation konnte nicht abgeschlossen werden." })
            })
    }

    private fun describeCore(response: SunnyResponse): String = when (response) {
        SunnyResponse.InvalidInput -> "Eingabe ungültig. Bitte einen synthetischen Testtext eingeben."
        is SunnyResponse.Completed -> when {
            response.fallback == FallbackReason.ModelMissing ->
                "Route: lokal (simuliert) · Ersatzantwort: feste Regel bei fehlender Laufzeit."
            response.fallback != null -> "Ersatzantwort: feste Regel bei ungültiger Laufzeitantwort."
            response.selectedRoute == Route.OnDevice && response.reply == Reply.LocalAcknowledgement ->
                "Route: lokal (simuliert) · Antwort: feste Bestätigung."
            response.selectedRoute == Route.OnDevice && response.reply == Reply.LocalClarification ->
                "Route: lokal (simuliert) · Antwort: feste Rückfrage."
            else -> "Antwort: feste Regel."
        }
    }

    private fun updateGrantStatus() {
        val granted = connect.isGranted(selected.id, selected.version)
        connectStatus.text = if (granted) "Testfreigabe aktiv." else "Keine Testfreigabe."
        grantButton.isEnabled = !granted
        revokeButton.isEnabled = granted
    }

    private fun readSelection() {
        val request = ConnectRequest(selected.id, selected.version, mapOf("day" to "today", "limit" to "3"))
        connectResult.text = when (val result = connect.request(request)) {
            is ConnectResult.Read -> when (val payload = result.payload) {
                is TodayPayload.Tasks -> payload.items.joinToString("\n") {
                    it.title + " · " + if (it.completed) "erledigt" else "offen"
                }
                is TodayPayload.Appointments -> payload.items.joinToString("\n") { it.time + " · " + it.title }
            }
            is ConnectResult.Rejected -> when (result.reason) {
                Rejection.Unauthorized -> "Abgelehnt. Für diese Auswahl fehlt die Testfreigabe."
                Rejection.UnknownCapability -> "Abgelehnt. Die Fähigkeit ist unbekannt."
                Rejection.IncompatibleVersion -> "Abgelehnt. Die Version wird nicht unterstützt."
                Rejection.MalformedInput -> "Abgelehnt. Die Anfrage ist ungültig."
                Rejection.InvalidResponse -> "Abgelehnt. Die Antwort entspricht nicht dem Vertrag."
            }
        }
    }

    private fun resetSession() {
        closeKeyboard()
        connect.reset()
        input.text.clear()
        input.clearFocus()
        selected = connect.capabilities().first()
        choices.check(initialChoice)
        updateGrantStatus()
        coreStatus.text = "Testsitzung zurückgesetzt."
        connectResult.text = "Bereit für eine Abfrage."
    }

    private fun closeKeyboard() {
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(input.windowToken, 0)
    }

    private fun showHelp() {
        val capabilityHelp = connect.capabilities().joinToString("\n") { it.title + " · " + it.help }
        AlertDialog.Builder(this)
            .setTitle("Hilfe zur Testsitzung")
            .setMessage(
                "Verwende ausschließlich synthetischen Testtext. Sunny Core liefert feste Antworten. " +
                    "Über „Fehlende Laufzeit simulieren“ lässt sich die Regelantwort prüfen.\n\n" +
                    "Wähle eine Fähigkeit und rufe sie vor der Freigabe ab, um die Ablehnung zu sehen. " +
                    "Die Testfreigabe gilt nur für die Auswahl. Nach einem Widerruf ist der Abruf wieder gesperrt.\n\n" +
                    capabilityHelp + "\n\nBeim Zurücksetzen werden Testtext, Antworten und alle Testfreigaben gelöscht.",
            )
            .setPositiveButton("Schließen", null)
            .show()
    }

    private fun section(title: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = surface(Color.WHITE)
        setPadding(dp(16), dp(14), dp(16), dp(16))
        addView(label(title, 21, Ink, bold = true), margins(top = 0).apply { bottomMargin = dp(6) })
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun status(value: String) = label(value, 15, Ink).apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        background = surface(PaleGreen)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        isAllCaps = false
        minHeight = dp(48)
        backgroundTintList = ColorStateList.valueOf(PaleGreen)
        setTextColor(
            ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(Muted, Green),
            ),
        )
        setOnClickListener { action() }
    }

    private fun surface(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(12).toFloat()
    }

    private fun margins(top: Int, width: Int = LinearLayout.LayoutParams.MATCH_PARENT) =
        LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top)
        }

    private fun applyInsets(view: View, insets: WindowInsets) {
        if (Build.VERSION.SDK_INT >= 30) {
            val safe = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime()
            )
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        val Ink = Color.rgb(29, 41, 35)
        val Green = Color.rgb(36, 91, 73)
        val PaleGreen = Color.rgb(232, 242, 235)
        val Muted = Color.rgb(100, 112, 106)
    }
}
