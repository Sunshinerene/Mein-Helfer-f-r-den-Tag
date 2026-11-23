#!/usr/bin/env python3
"""Prüft die jährliche "Übersicht Leistungsbeträge" des Bundesgesundheitsministeriums und aktualisiert
daten/leistungen.txt, wenn sich Entlastungsbetrag oder Gemeinsamer Jahresbetrag ändern oder ein neues Jahr erscheint.
Übernommen wird nur, wenn beide Beträge gefunden und plausibel sind. Die App fragt vor dem Übernehmen immer nach."""
import datetime, re, subprocess, sys, urllib.request, pathlib

URL = "https://www.bundesgesundheitsministerium.de/fileadmin/Dateien/3_Downloads/P/Pflegeversicherung_Leistungsbeitraege/Uebersicht_Leistungsbetraege_{}.pdf"
FILE = pathlib.Path("daten/leistungen.txt")
RELIEF = re.compile(r"Entlastungsbetrag[^0-9]{0,80}?(\d{2,3}(?:,\d{2})?)\s*(?:Euro|€)", re.I)
YEARLY = re.compile(r"Gemeinsame[nrs]?\s+Jahresbetrag[^0-9]{0,120}?(\d{1,2}\.?\d{3}(?:,\d{2})?)\s*(?:Euro|€)", re.I)

def euro(s):
    return float(s.replace(".", "").replace(",", "."))

def fmt(v):
    return str(int(v)) if v == int(v) else f"{v:.2f}"

def read(year):
    try:
        req = urllib.request.Request(URL.format(year), headers={"User-Agent": "Mozilla/5.0 MeinHelfer-Pruefung"})
        data = urllib.request.urlopen(req, timeout=30).read()
    except Exception as e:
        print(f"{year}: keine Übersicht ({e})")
        return None
    if not data.startswith(b"%PDF"):
        print(f"{year}: keine PDF")
        return None
    pathlib.Path("u.pdf").write_bytes(data)
    text = subprocess.run(["pdftotext", "u.pdf", "-"], capture_output=True, text=True).stdout
    flat = re.sub(r"\s+", " ", text)
    m = next((euro(x) for x in RELIEF.findall(flat) if 50 <= euro(x) <= 500), None)
    j = next((euro(x) for x in YEARLY.findall(flat) if 1000 <= euro(x) <= 10000), None)
    if m is None or j is None:
        print(f"{year}: Beträge nicht eindeutig gefunden (Entlastung={m}, Jahresbetrag={j})")
        return None
    return m, j

def main():
    today = datetime.date.today()
    lines = FILE.read_text(encoding="utf-8").splitlines()
    years = {}
    for i, l in enumerate(lines):
        p = l.split(";")
        if p[0] == "jahr" and len(p) >= 6:
            years[int(p[1])] = (i, p)
    changed = False
    for y in (today.year, today.year + 1):
        found = read(y)
        if not found:
            continue
        m, j = found
        status = "gilt" if y <= today.year else "beschlossen"
        if y in years:
            i, p = years[y]
            if abs(float(p[2]) - m) < 0.005 and abs(float(p[3]) - j) < 0.005 and p[5] in ("gilt", "beschlossen"):
                print(f"{y}: unverändert")
                continue
            carry = p[4]
        else:
            carry = "6"
        new = f"jahr;{y};{fmt(m)};{fmt(j)};{carry};{status};BMG, Übersicht Leistungsbeträge {y} (automatisch geprüft {today.isoformat()})"
        if y in years:
            lines[years[y][0]] = new
        else:
            idx = max([v[0] for v in years.values()], default=len(lines) - 1) + 1
            lines.insert(idx, new)
            years[y] = (idx, new.split(";"))
        print(f"{y}: neu {new}")
        changed = True
    if changed:
        lines = [f"stand;{today.isoformat()}" if l.startswith("stand;") else l for l in lines]
        FILE.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return 0

if __name__ == "__main__":
    sys.exit(main())
