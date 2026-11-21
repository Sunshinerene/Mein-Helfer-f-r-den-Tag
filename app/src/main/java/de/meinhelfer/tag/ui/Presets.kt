package de.meinhelfer.tag.ui

/** Vorauswahlen fuer die Eingabedialoge. Alle Felder bleiben frei ueberschreibbar. */

val DOSAGE_PRESETS = listOf(
    "1 Tablette",
    "½ Tablette",
    "2 Tabletten",
    "1 Kapsel",
    "2 Kapseln",
    "5 ml",
    "10 ml",
    "10 Tropfen",
    "1 Hub",
    "1 Beutel",
    "1 Pflaster",
    "1 Spritze",
)

val SPECIALTIES = listOf(
    // Ärzte
    "Hausarzt",
    "Internist",
    "Kardiologe",
    "Neurologe",
    "Orthopäde",
    "Augenarzt",
    "HNO-Arzt",
    "Urologe",
    "Gynäkologe",
    "Hautarzt",
    "Zahnarzt",
    "Psychiater / Psychotherapeut",
    "Geriater",
    "Notdienst / Bereitschaftsdienst",
    // Therapie
    "Physiotherapie",
    "Ergotherapie",
    "Logopädie",
    "Fußpflege / Podologie",
    // Pflege und Hilfe im Alltag
    "Pflegedienst",
    "Tagespflege",
    "Kurzzeitpflege",
    "Pflegeheim",
    "Haushaltshilfe / Alltagshilfe",
    "Betreuungsdienst",
    "Essen auf Rädern",
    "Hausnotruf",
    "Fahrdienst",
    "Palliativ / Hospiz",
    "Pflegeberatung / Pflegestützpunkt",
    "Selbsthilfegruppe",
    "Seelsorge",
    // Versorgung
    "Apotheke",
    "Sanitätshaus",
    "Hörakustiker / Optiker",
    "Krankenhaus",
    // Ämter, Kassen, Recht
    "Pflegekasse / Krankenkasse",
    "Medizinischer Dienst",
    "Sozialamt",
    "Rechtlicher Betreuer",
    "Sonstiges",
)

val APPOINTMENT_TITLES = listOf(
    "Arztbesuch",
    "Kontrolltermin",
    "Blutabnahme",
    "Physiotherapie",
    "Besuch Pflegedienst",
    "Krankenhaus",
    "Pflegebegutachtung",
    "Rezept abholen",
    "Fußpflege",
)

/** Erinnerung vor dem Termin: Minuten -> Beschriftung (negativ = keine). */
val REMINDER_OPTIONS = listOf(
    -1 to "Keine Erinnerung",
    15 to "15 Minuten vorher",
    30 to "30 Minuten vorher",
    60 to "1 Stunde vorher",
    120 to "2 Stunden vorher",
    1440 to "1 Tag vorher",
)
