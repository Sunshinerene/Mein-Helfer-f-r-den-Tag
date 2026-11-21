# Archiv der Nutzungsbedingungen

Jede Fassung der Nutzungsbedingungen, der in der App zugestimmt wird, liegt hier im genauen Wortlaut
(UTF-8, Zeilenende LF). Die App berechnet über genau diesen Text eine SHA-256-Prüfsumme und speichert sie
im Nachweis der Zustimmung („Prüfsumme des Textes“). So lässt sich ein geteilter Nachweis eindeutig dem
Wortlaut zuordnen, dem zugestimmt wurde.

| Fassung | Stand | In der App ab | Datei | SHA-256 |
|---|---|---|---|---|
| 1 | 21.11.2026 | v0.38.0 | [fassung-1.txt](fassung-1.txt) | `e7ce7649d51af1a619201ef78283a988d05463c0147057f1e81b0c7b79d34f3f` |

## Prüfen

Prüfsumme einer Datei selbst berechnen und mit dem Nachweis vergleichen:

```
sha256sum fassung-1.txt
```

Beim Bauen jeder Version prüft ein automatischer Test (`TermsArchiveTest`), dass der Text in der App
Zeichen für Zeichen mit der hier abgelegten Fassung übereinstimmt. Wird der Text geändert, ohne die
Fassung zu erhöhen und hier abzulegen, schlägt der Build fehl und es entsteht keine neue Version.

Neue Fassung: `Terms.VERSION` erhöhen, `fassung-<n>.txt` hier ablegen, Tabelle ergänzen. Alte Fassungen
werden nie geändert oder gelöscht. Die Git-Historie belegt, seit wann welche Fassung gilt.
