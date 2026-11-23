package de.meinhelfer.tag.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

/** Ein gespeichertes eigenes Design: alles aus „Darstellung“ zusammen (ohne Vorlesen). */
data class DesignPreset(
    val name: String,
    val theme: String,
    val style: String,
    val tweak: DesignTweak,
    val corners: String,
    val effects: Effects,
    val font: Float,
    val scale: Float,
)

object DesignPresets {
    private fun prefs(context: Context) = context.getSharedPreferences("eigene_designs", Context.MODE_PRIVATE)

    fun list(context: Context): List<DesignPreset> = runCatching {
        val arr = JSONArray(prefs(context).getString("liste", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            fun long(k: String) = if (o.has(k)) o.getLong(k) else null
            DesignPreset(
                name = o.getString("name"),
                theme = o.optString("theme", "system"),
                style = o.optString("style", "schlicht"),
                tweak = DesignTweak(long("a"), long("b"), long("c")),
                corners = o.optString("corners", "design"),
                effects = Effects(
                    cardAlpha = o.optDouble("alpha", -1.0).toFloat(),
                    glow = o.optDouble("glow", -1.0).toFloat(),
                    backdrop = o.optDouble("backdrop", -1.0).toFloat(),
                    motion = o.optBoolean("motion"),
                    animations = o.optBoolean("animations"),
                    gloss = o.optBoolean("gloss"),
                ),
                font = o.optDouble("font", 1.0).toFloat(),
                scale = o.optDouble("scale", 1.0).toFloat(),
            )
        }
    }.getOrDefault(emptyList())

    private fun save(context: Context, list: List<DesignPreset>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("name", p.name)
                    put("theme", p.theme)
                    put("style", p.style)
                    p.tweak.a?.let { put("a", it) }
                    p.tweak.b?.let { put("b", it) }
                    p.tweak.c?.let { put("c", it) }
                    put("corners", p.corners)
                    put("alpha", p.effects.cardAlpha.toDouble())
                    put("glow", p.effects.glow.toDouble())
                    put("backdrop", p.effects.backdrop.toDouble())
                    put("motion", p.effects.motion)
                    put("animations", p.effects.animations)
                    put("gloss", p.effects.gloss)
                    put("font", p.font.toDouble())
                    put("scale", p.scale.toDouble())
                },
            )
        }
        prefs(context).edit().putString("liste", arr.toString()).apply()
    }

    /** Aktuelle Darstellung unter [name] speichern (gleicher Name wird ersetzt). */
    fun saveCurrent(context: Context, vm: MainViewModel, name: String) {
        val style = vm.designStyle.value
        val p = DesignPreset(
            name = name.trim(),
            theme = vm.themeMode.value,
            style = style.key,
            tweak = vm.designTweaks.value[style] ?: DesignTweak(),
            corners = vm.cornerStyle.value.key,
            effects = vm.effects.value,
            font = vm.fontFactor.value,
            scale = vm.uiScale.value,
        )
        save(context, list(context).filterNot { it.name.equals(p.name, ignoreCase = true) } + p)
    }

    fun delete(context: Context, name: String) = save(context, list(context).filterNot { it.name == name })

    fun apply(vm: MainViewModel, p: DesignPreset) {
        val style = DesignStyle.fromKey(p.style)
        vm.setThemeMode(p.theme)
        vm.setDesignStyle(style)
        vm.setDesignTweak(style, p.tweak)
        vm.setCornerStyle(CornerStyle.fromKey(p.corners))
        vm.setEffects(p.effects)
        vm.setFontFactor(p.font)
        vm.setUiScale(p.scale)
    }
}

/** Karte „Meine Designs“: aktuelles Design speichern, gespeicherte anwenden oder löschen. */
@Composable
fun DesignPresetsCard(vm: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var list by remember { mutableStateOf(DesignPresets.list(context)) }
    var name by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<String?>(null) }
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Meine Designs", style = MaterialTheme.typography.titleMedium)
            Text(
                "Speichert alles aus „Darstellung“ zusammen: Hell/Dunkel, Design, eigene Farben, Ecken, Effekte, Schrift- und Bediengröße. So kannst du ausprobieren und jederzeit zurück.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text("Name, z. B. Abends") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(shape = buttonShape(), enabled = name.isNotBlank(), onClick = {
                    DesignPresets.saveCurrent(context, vm, name)
                    info = "„${name.trim()}“ gespeichert."
                    name = ""
                    list = DesignPresets.list(context)
                }) { Text("Speichern") }
            }
            info?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            if (list.isEmpty()) {
                Text("Noch keine eigenen Designs gespeichert.", style = MaterialTheme.typography.bodySmall)
            }
            list.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            DesignStyle.fromKey(p.style).label + " · " + when (p.theme) { "light" -> "Hell"; "dark" -> "Dunkel"; else -> "wie Handy" },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = {
                        DesignPresets.apply(vm, p)
                        info = "„${p.name}“ angewendet."
                    }) { Text("Anwenden") }
                    TextButton(onClick = {
                        DesignPresets.delete(context, p.name)
                        list = DesignPresets.list(context)
                    }) { Text("Löschen") }
                }
            }
        }
    }
}
