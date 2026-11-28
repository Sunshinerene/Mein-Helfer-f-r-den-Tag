# CODEX-7A · Modulregistry für Sunny

Entwurf zur Prüfung durch Projektzentrale 00/1. Integrationsbasis `entwicklung/sunny-2` bei `e69eebbacc80125165c1b53ca6e9e000a9052be0`.

## Zuständigkeiten

Sunny Core besitzt wiederverwendbare, appunabhängige Fähigkeiten. Das MeinHelfer-Modul beschreibt MeinHelfer-spezifische Fähigkeiten, etwa heutige Aufgaben. Ein vergleichbares DAW-Modul beschreibt projektspezifische Fähigkeiten seines Hosts. Die jeweilige Host-App behält Datenhoheit und setzt ihre eigenen Zugriffsregeln über einen fest eingebundenen Adapter durch. MeinHelfer bleibt ohne Sunny eigenständig benutzbar. Bestehende Funktionen bleiben bis zur separaten Abnahme eines Ersatzes erhalten.

Die Registry verwaltet geprüfte Manifeste, Kompatibilität und den Aktivierungszustand. **Sunny Connect** definiert den versionierten Aufrufvertrag zwischen Sunny und einem Host einschließlich Schema, Abweisung und Antwort. **Guard** entscheidet unabhängig vom Modell über Datenzugriff und Aktionen. Modulmanifest, Registry-Eintrag und erfolgreicher Connect-Schemaabgleich sind drei Eingaben für die Prüfung. Die Freigabe stammt vom zuständigen Host und gilt nur für den ausdrücklich erlaubten Zugriff.

In Version 1 verweist jede beschriebene Fähigkeit auf einen vom Host vorkonfigurierten Adapter. Der Registry-Vertrag enthält keine Installation, kein Laden dynamischen Codes, keine direkte Host-Datenbankverbindung, kein Netzwerk und keine automatische Berechtigungserweiterung. Modelltext und generierte Manifestvorschläge sind untrusted. Sie können weder eine vertrauenswürdige Herkunft festlegen noch eine Fähigkeit aufrufen oder aktivieren.

## Manifest Version 1

| Feld | Vertrag |
| --- | --- |
| `manifestVersion` | Pflichtfeld mit Ganzzahlwert `1`. Unbekannte Versionen und unbekannte Felder werden abgewiesen. |
| `moduleId`, `moduleVersion` | Stabile, qualifizierte Modul-ID und getrennte SemVer-Version. Beide bilden die Identität eines Katalogeintrags. Die IDs `sunny.*` sind für Plattformkomponenten reserviert. |
| `hostCompatibility` | Explizite Host-ID und unterstützter Bereich der Host-API. Die Registry vergleicht gegen den vom Host gelieferten Iststand. |
| `connectApi` | Unterstützter Bereich des gemeinsamen Sunny-Connect-Vertrags. Capability-Version und Modulversion sind davon unabhängig. |
| `dependencies` | Eindeutige Modul-IDs mit jeweils einem zulässigen Versionsbereich. `sunny.core` bezeichnet die vom Host gemeldete Plattformkomponente. Andere Abhängigkeiten müssen im geplanten aktiven Zustand ihre Bereiche erfüllen. |
| `capabilities` | Global eindeutige aktive Capability-ID im Namensraum des Moduls, positive Capability-Version, Eingabe- und Ausgabeschema, Typ `read` oder `action`, Datenklasse und erforderliche Berechtigungs-IDs. Verbindung zum Adapter über diese Identität aus der vertrauenswürdigen Host-Konfiguration. |
| `permissionRequirements` | Eindeutige Host-Berechtigungs-IDs mit Zweck und `requiredForActivation`. Capability-Referenzen müssen hier deklariert und vom Host einzeln interpretierbar sein. Aktivierungspflichten gelten für die jeweilige Umgebung. Jeder Aufruf prüft sämtliche Rechte der konkreten Capability erneut. |
| `provenance` | Deklarierte Herausgeber-ID und unveränderliche Quellreferenz. Die Registry erhält die geprüfte Verbindung von Herausgeber, Modul-ID, Version und Manifest-Digest über eine getrennte, vertrauenswürdige Host-Konfiguration. Gleichlautende Angaben im Manifest begründen kein Vertrauen. |

IDs verwenden Kleinbuchstaben, Ziffern und durch Punkte getrennte Segmente. Versionswerte bestehen in v1 aus drei nicht negativen, numerisch verglichenen SemVer-Komponenten ohne führende Nullen. Bereiche dieser Version verwenden die Form `>=Untergrenze <Obergrenze` mit inklusiver Unter- und exklusiver Obergrenze. Ungültige oder leere Bereiche, doppelte JSON-Schlüssel, doppelte Capability-IDs und widersprüchliche Deklarationen werden bei der Registrierung abgelehnt. Vor einer produktiven Verwendung muss die Implementierung die maximale Manifestgröße, die Feldgrenzen und die kanonische Digest-Bildung verbindlich festlegen.

Die Beispiele verwenden synthetische Hosts, Quellen, Berechtigungen und Schemata. Beide Fähigkeiten lesen begrenzte Fixtures über einen fiktiven, fest eingebundenen Adapter.

### MeinHelfer

```json
{
  "manifestVersion": 1,
  "moduleId": "meinhelfer",
  "moduleVersion": "1.0.0",
  "hostCompatibility": {"hostId": "meinhelfer.fixture", "api": ">=1.0.0 <2.0.0"},
  "connectApi": ">=1.0.0 <2.0.0",
  "dependencies": [{"moduleId": "sunny.core", "versions": ">=1.0.0 <2.0.0"}],
  "capabilities": [
    {
      "id": "meinhelfer.today.tasks",
      "version": 1,
      "inputSchema": "fixture.today.v1",
      "outputSchema": "fixture.tasks.v1",
      "kind": "read",
      "dataClass": "synthetic",
      "permissions": ["meinhelfer.tasks.read"]
    }
  ],
  "permissionRequirements": [
    {"id": "meinhelfer.tasks.read", "purpose": "Heutige Beispielaufgaben lesen", "requiredForActivation": true}
  ],
  "provenance": {"publisherId": "fixture.meinhelfer", "sourceRef": "fixture:meinhelfer@1.0.0"}
}
```

### DAW

```json
{
  "manifestVersion": 1,
  "moduleId": "daw",
  "moduleVersion": "1.0.0",
  "hostCompatibility": {"hostId": "daw.fixture", "api": ">=2.0.0 <3.0.0"},
  "connectApi": ">=1.0.0 <2.0.0",
  "dependencies": [{"moduleId": "sunny.core", "versions": ">=1.0.0 <2.0.0"}],
  "capabilities": [
    {
      "id": "daw.project.tracks",
      "version": 1,
      "inputSchema": "fixture.track.query.v1",
      "outputSchema": "fixture.tracks.v1",
      "kind": "read",
      "dataClass": "synthetic",
      "permissions": ["daw.tracks.read"]
    }
  ],
  "permissionRequirements": [
    {"id": "daw.tracks.read", "purpose": "Spurliste eines Beispiels lesen", "requiredForActivation": false}
  ],
  "provenance": {"publisherId": "fixture.daw", "sourceRef": "fixture:daw@1.0.0"}
}
```

## Registry-Vertrag und Zustände

- **Registrieren.** Prüft Struktur, Manifestversion und Herkunft vor Aufnahme in den Katalog. Ein wiederholter identischer, geprüfter Eintrag kann idempotent bestätigt werden. Dieselbe Modul-ID und Version mit anderem Digest ist ein Konflikt. Ein gültiger Eintrag wird als `Inaktiv` registriert.
- **Planen.** Prüft für eine ausdrücklich angeforderte Aktivierung oder einen Versionswechsel einen Snapshot aus Host, Connect-Version, aktivem Bestand und Kandidat. Fehlende Abhängigkeiten werden gemeldet. Die Prüfung auf Zyklen läuft über die Kandidaten und ihre auflösbaren Abhängigkeitskanten, bevor der Plan fehlende Aktivierungen meldet. Bei einem Zyklus gehört der betroffene Modulpfad zum internen Diagnoseergebnis. Die Registry sucht keine Ersatzversion und aktiviert keine Abhängigkeit automatisch. Verträglichkeit, eindeutige Capability-IDs und Versionsbereiche gelten für den gesamten resultierenden aktiven Graphen.
- **Aktivieren.** Eine autorisierte Host-Anforderung wechselt `Inaktiv` zu `Aktiv`. Sie prüft die Planrevision, alle Pflichtrechte, lokale Adapterbindung und Kompatibilität erneut. Ablehnung oder veraltete Planrevision lässt den aktiven Snapshot unverändert. Genau eine Version je Modul-ID kann aktiv sein.
- **Deaktivieren.** Setzt `Aktiv` auf `Inaktiv`, entfernt die Einträge aus der Aufrufauflösung und verwirft aktive Aufrufgrants für diese Version. Bei aktiven Abhängigen lehnt die Registry eine reguläre Deaktivierung ab, bis diese zuerst deaktiviert wurden.
- **Sperren.** Ein entzogener Herkunftsnachweis, ein entzogenes Pflichtrecht oder ein inkompatibel gewordener Host sperrt das betroffene Modul und transitiv seine aktiven Abhängigen. Gesperrte Fähigkeiten werden in derselben Registry-Revision aus der Aufrufauflösung entfernt. Freigaben werden bei Wiederherstellung nicht wiederverwendet. Nach erneuter Prüfung und expliziter Aktivierung kann ein gesperrtes Modul wieder aktiv werden. Der Entzug eines einzelnen optionalen Capability-Rechts sperrt dessen Aufruf, während das Modul aktiv bleibt.
- **Upgraden oder zurückwechseln.** Prüft die Zielversion wie eine neue Aktivierung. Ein Plan schlägt fehl, wenn aktive Abhängige die Zielversion nicht akzeptieren, Rechte fehlen oder der aktuelle Host inkompatibel ist. Bei Erfolg werden Adapterbindung, aktive Version und Fähigkeitsindex atomar ausgetauscht. Eine vorbereitete, fehlgeschlagene Änderung unterbricht die noch zulässige alte Version nicht. Der Wechsel auf die zuvor geprüfte Version ist ebenfalls eine autorisierte Transaktion mit Prüfung gegen den aktuellen Host und aktuellen Berechtigungsstand. Es werden keine alten Grants reaktiviert. Datenmigrationen fallen außerhalb dieses Vertrags.

Guard behandelt unbekannte oder derzeit inaktive Fähigkeiten, nicht passende Versionen, abgelaufene oder fehlende Grants sowie Fehler bei der Prüfung als ausdrückliche Abweisung. Grants sind an authentifizierten Aufrufer, Host, Daten- beziehungsweise Personenscope, Modul-ID und Version, Capability-ID und Version, Zweck und die einzelnen Rechte gebunden. Connect prüft Eingabe und Ausgabe gegen die jeweils festgelegten Schemata. Die Host-Seite prüft Registry-Revision und aktuellen Berechtigungsstand unmittelbar vor der Datenfreigabe oder Aktion. Entzüge und Adapterwirkungen benötigen dafür einen gemeinsamen atomaren Prüfschritt oder eine gleichwertige Host-Sperre. Ein veralteter Freigabestand führt zur Abweisung, selbst wenn das betroffene Modul aktiv bleibt. Bereits autorisiert übergebene Daten können nachträglich nicht zurückgeholt werden. Protokolle enthalten feste Fehlercodes und zulässige technische Metadaten. Anfragen, Ergebnisdaten, Secrets und private Inhalte bleiben aus Logs heraus.

## Testmatrix für eine spätere Implementierung

| Fall | Synthetische Prüfung | Erwartung |
| --- | --- | --- |
| REG-01 | Beide Beispiele mit jeweils passendem Host, Core und Connect registrieren | Beide stehen in ihren Host-Katalogen als inaktiv. Kein Adapteraufruf. |
| REG-02 | Unbekannte Manifestversion, doppelter JSON-Schlüssel oder unbekanntes Feld | Strukturfehler, kein Eintrag. |
| REG-03 | Identischen geprüften Digest erneut registrieren, dann anderen Digest unter derselben ID und Version | Erste Wiederholung ist idempotent. Zweiter Versuch meldet Versionskonflikt. |
| REG-04 | Capability-ID zweimal im Manifest oder außerhalb des Modulnamensraums deklarieren | Ablehnung bei Registrierung. Kein Konflikt im aktiven Index. |
| REG-05 | Host-API genau auf exklusiver Obergrenze, inkompatibles Connect oder inkompatibles Core | Typisierte Inkompatibilität. Modul bleibt inaktiv. |
| REG-06 | Abhängigkeit fehlt oder passende Version ist nur registriert | Kein Aktivieren. Fehler unterscheidet fehlende von inaktiver Abhängigkeit. |
| REG-07 | Synthetische Abhängigkeiten A → B → A planen | Zyklusfehler mit internem Modulpfad. Beide bleiben inaktiv. |
| REG-08 | MeinHelfer-Pflichtrecht verweigern, dann ausdrücklich erteilen und aktivieren | Ablehnung ohne Adapteraufruf. Erst nach erneuter, autorisierter Aktivierung ist ein autorisierter Aufruf möglich. |
| REG-09 | DAW aktivieren, optionales Leserecht verweigern | Aktivierung möglich, Lesecapability antwortet auf Anfrage mit `Denied`. Adapter bleibt unbehelligt. |
| REG-10 | Passenden MeinHelfer-Aufruf autorisieren, danach Auftrag mit falscher Capability-Version oder abweichendem Personenscope schicken | Nur der passende Aufruf erreicht den Adapter. Beide Abweichungen werden abgewiesen. |
| REG-11 | Unbestätigte Herkunft, fehlende Adapterbindung oder fehlerhaftes Ausgabeschema simulieren | Registrierung, Aktivierung beziehungsweise Ausgabe wird am zuständigen Prüfschritt abgewiesen. |
| REG-12 | Berechtigung nach erfolgreicher Planung und vor Adapterwirkung entziehen | Alte Revision führt zu Abweisung. Kein Zugriff und keine Wirkung. |
| REG-13 | Modul mit aktiven Abhängigen deaktivieren, dann Pflichtrecht entziehen | Reguläre Deaktivierung meldet Konflikt. Rechteentzug sperrt Modul und Abhängige gemeinsam. |
| REG-14 | Kompatible neue Version mit neuen Rechten vorbereiten, Rechte verweigern, anschließend autorisiert wechseln | Solange Rechte fehlen, bleibt die zulässige alte Version aktiv. Erfolgreicher Wechsel publiziert genau einen neuen Snapshot. |
| REG-15 | Rollback zuerst bei inkompatiblem Host oder entzogenem Grant, anschließend mit passender neuer Freigabe | Erster Wechsel scheitert ohne Zustandsänderung. Zweiter Wechsel nutzt aktuelle Freigaben und genau eine aktive Version. |
| REG-16 | Modelltext enthält eine Capability-ID, einen Aktionswunsch oder sein eigenes „Grant“ | Kein Registry-Zustandswechsel, keine Adapterbindung, kein Zugriff. |

## Offene Entscheidungen

1. Quelle und Aktualisierung des Vertrauenskatalogs sowie kanonische Manifestdarstellung und Digest-Verfahren. RFC 8785 ist ein Kandidat für kanonisches JSON.
2. Verbindliche Größenlimits, dauerhafte Metadaten und Wiederaufnahme nach Prozessabbruch. Snapshot- und Sperrverhalten müssen beim Neustart fail-closed bleiben.
3. Schema-Registry, Datenklassen, Herkunftsnachweise und die konkrete Authentifizierung von Connect-Aufrufern. Reale Host-Anbindung und Cross-App-Zugriff benötigen eigene Freigaben.
4. Inventar bestehender MeinHelfer-Fähigkeiten und Zuordnung zu Core oder Modul. Erst nach Review und Funktionsnachweis können bestehende Pfade ersetzt werden.

## Entwicklungs- und Prüfbericht

- Freigabe aus `docs/PROJECT_STATUS.md` und `docs/CODEX-PARALLEL-2026-11-28.md` geprüft. Der Entwurf wurde anhand von `docs/SUNNY_ARCHITECTURE.md`, `docs/ROADMAP-2026-11-27.md` und des bestehenden synthetischen `sunny-standalone-poc/…/MockConnect.kt` abgeglichen. Die Trennung von Capability-Version und Modulversion ergänzt das vorhandene Connect-Beispiel.
- Der Diff umfasst ausschließlich `docs/CODEX-7A-REGISTRY-DESIGN.md`. Das Verhalten der Registry bleibt bis zur Implementierung ungeprüft.
- Gemeinsame Core-Basis auf der oben genannten SHA geprüft mit `gradle -p sunny-core --no-parallel --max-workers=2 clean test`, Gradle 8.11.1 und JDK 17.0.17. 131 Tests bestanden, 0 Fehler, 0 Ausfälle, 0 übersprungen. Die Matrix REG-01 bis REG-16 enthält geplante Fälle.
- Ein lokaler `python3 -`-Check prüfte die beiden JSON-Blöcke auf Pflichtfelder, doppelte Schlüssel und passende Capability- und Berechtigungsreferenzen. Alle Prüfungen bestanden. Die Matrix enthält 16 eindeutige Fälle. `git diff --cached --check` und der Vergleich mit der Pfadfreigabe prüften den Dokumentationsdiff.
- Rollback vor Abnahme durch Schließen des Draft PR. Bei einer später genehmigten Übernahme kann ein eigener Revert den Dokumentationsstand zurücknehmen.
- Unabhängiger Review durch Projektzentrale 00/1 steht aus. Änderungen an der Registry, Connect-Verhalten und Rechtevergabe benötigen nach dem Konzept eigene Implementierungs- und Abnahmeentscheidungen.
