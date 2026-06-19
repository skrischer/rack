# Beta-Test-Protokoll — `rack` Trainings-App (MCP)

**Tester:** Claude (als Beta-Tester der App, agierend über die MCP-Schnittstelle)
**Datum:** 19. Juni 2026
**Testkontext:** Übertragung eines bestehenden, manuell ausgearbeiteten 5-Tage-Trainingsplans (Upper/Lower/Push/Pull/Legs, Recomp-Ausrichtung) aus einem HTML-Artefakt in die App. Der Plan umfasst 5 Trainingstage mit insgesamt 31 Übungen, jeweils mit Satz-/Wiederholungsschema, RIR-Vorgabe, Ausführungshinweis und Superset- bzw. Zirkel-Gruppierung.

> Dieses Dokument beschreibt ausschließlich den durchlaufenen Ablauf und die dabei beobachteten Reibungspunkte. Es enthält bewusst keine Lösungsvorschläge, Priorisierungen oder Umsetzungsaufträge.

---

## 1. Testszenario

Ziel des Testlaufs war, ob sich ein vollständig vordefinierter Trainingsplan allein über die bereitgestellten MCP-Tools in der App abbilden lässt — inklusive der strukturellen Eigenschaften des Plans (Tagesgliederung, Übungsreihenfolge, Intensitätsvorgaben, Gruppierung von Übungen zu Supersätzen und Zirkeln).

---

## 2. Durchlaufener Ablauf (chronologisch)

1. **Server-Entdeckung.** Der `rack`-Server tauchte in der Tool-Umgebung auf. Die zugehörigen Tools waren nicht sofort aufrufbar, sondern mussten zunächst über eine Tool-Suche geladen werden.

2. **Erreichbarkeitsprüfung.** Über `list_plans` wurde der Server angesprochen. Er antwortete unmittelbar und lieferte einen bereits vorhandenen Plan ("Push / Pull / Legs", `kind: recomp`, `source: agent`) zurück. Damit war bestätigt, dass der Server läuft und Daten enthält.

3. **Schema-Erkundung.** In mehreren Tool-Suchläufen wurden die Schreib- und Lese-Tools geladen und ihre Parameter inspiziert: `create_plans`, `create_plan_days`, `create_plan_exercises`, `search_exercises`, `get_plan` sowie die Set-Log-Tools. Dabei wurde die Datenstruktur sichtbar: ein Plan enthält Tage (`plan_days`), ein Tag enthält Übungen (`plan_exercises`), eine Übung verweist über eine Pflicht-ID (`exerciseId`) auf einen globalen Übungskatalog.

4. **Plan-Anlage.** Über `create_plans` wurde ein neuer Plan "5er Split — Upper/Lower/Push/Pull/Legs" angelegt. Die Antwort enthielt eine generierte `id`, `source: agent` und Zeitstempel.

5. **Katalog-Auflösung.** Da `plan_exercises` eine Katalog-ID verlangt, wurden die geplanten (deutschsprachigen) Übungsnamen über `search_exercises` gegen den (englischsprachigen) Katalog aufgelöst. Dies erforderte mehrere Suchanläufe mit unterschiedlichen Suchbegriffen.

6. **Tag-Anlage.** Über `create_plan_days` wurde der erste Trainingstag ("Oberkörper (Mo)", `position: 1`, `tag: push`, `focus`-Text) angelegt und die zurückgegebene `dayId` notiert.

7. **Übungs-Anlage.** Über sieben einzelne `create_plan_exercises`-Aufrufe wurden die Übungen des Montags angelegt — jeweils mit `position`, `target` (Satz × Wdh), `rir`, `cue` und `supersetLabel`. Alle sieben Aufrufe wurden bestätigt zurückgegeben.

8. **Verifikation (abgebrochen).** Der abschließende `get_plan`-Aufruf zur Kontrolle des Ergebnisses lief nach rund vier Minuten in einen Timeout; der Server reagierte an dieser Stelle nicht mehr. Die vorausgegangenen Schreibvorgänge waren davon nicht betroffen.

**Stand bei Abbruch:** Tag 1 (Montag) vollständig angelegt (7 Übungen). Tage 2–5 (24 Übungen) noch nicht übertragen. Verifikation des geschriebenen Stands per `get_plan` nicht erfolgreich.

---

## 3. Was im Testlauf reibungslos lief

- **Erreichbarkeit & Lesezugriff zu Beginn:** `list_plans` antwortete sofort, ein bestehender Plan war abrufbar.
- **Schreib-Tools:** `create_plans`, `create_plan_days` und `create_plan_exercises` lieferten konsistente Antworten mit vollständigem Datensatz (generierte `id`, alle Eingabefelder, `source`, Zeitstempel).
- **Feldübernahme:** `target`, `rir`, `cue` und `supersetLabel` wurden exakt so gespeichert und zurückgegeben, wie sie übergeben wurden.
- **Unicode in Freitextfeldern:** Sonderzeichen wie `×` und `–` im `target`-Feld (z. B. `3 × 6–8`) wurden akzeptiert und korrekt zurückgegeben.
- **Schema-Klarheit:** Die Tool-Parameter waren klar typisiert (UUID-Muster, Datums-Muster, Wertebereiche), was die Eingabe eindeutig machte.

---

## 4. Beobachtete Pain Points

### 4.1 Übungskatalog und Suchverhalten (zentraler Reibungspunkt)

- **Kein Relevanz-Ranking.** `search_exercises` lieferte Treffer in alphabetischer Reihenfolge. In Kombination mit dem `limit`-Parameter führte das dazu, dass passende Einträge abgeschnitten wurden: Eine Suche nach `press` lieferte nur Einträge im Bereich A–F; "Leg Press", "Shoulder Press"-Varianten und "Incline …" fielen aus dem Ergebnis.
- **Mehrwort-Suchen ergaben leere Trefferlisten.** Suchbegriffe wie `bent over row barbell` oder `shoulder press dumbbell` lieferten null Treffer, obwohl thematisch passende Einträge im Katalog vorhanden waren. Erst die Reduktion auf einzelne Kernbegriffe (`row`, `shoulder press`) brachte Ergebnisse.
- **Keine kanonischen Grundübungen.** Der Katalog enthielt für viele Bewegungen keine schlichte Standardform, sondern ausschließlich Spezialvarianten. Beispiele: keine einfache "Lateral Raise" (stattdessen u. a. "45° lateral raises", "Behind the Back Cable Lateral Raise"), keine einfache "Triceps Pushdown" (stattdessen "Drag Pushdown", "Rocking Triceps Pushdown", "Single-arm cable pushdown"), keine einfache "Lat Pulldown" (stattdessen "Neutral Grip Lat Pulldown", "Single-Arm Lat Pulldown" u. a.).
- **Geräte-/Equipment-Auswahl schwer zu treffen.** Die geplante Gerätevariante (Kurzhantel, Langhantel, Kabel, Maschine) ließ sich über die Namenssuche nicht zuverlässig ansteuern. Für "Schulterdrücken (KH, sitzend)" war in den Treffern nur eine Langhantel-Variante ("Shoulder Press, Barbell") verfügbar.
- **Auswirkung auf das Ergebnis:** Für mehrere Zielübungen musste eine Ersatzvariante verlinkt werden, die nicht der im Plan vorgesehenen Übung entspricht (siehe Abschnitt 5).

### 4.2 Abbildung der Plan-Semantik im Datenmodell

- **Pflicht-Verweis auf den Katalog.** `plan_exercises` verlangt eine Katalog-`exerciseId`; ein freies Namens- oder Aliasfeld stand nicht zur Verfügung. Übungen ohne passenden Katalogeintrag konnten daher nur über eine Ersatzvariante abgebildet werden.
- **RIR nur als Einzelwert.** Das `rir`-Feld nimmt eine einzelne Ganzzahl. Geplante RIR-*Bereiche* (z. B. "1–2", "0–1") ließen sich nicht als Bereich hinterlegen und wurden auf einen Einzelwert reduziert.
- **`target` als unstrukturierter Freitext.** Das Satz-/Wiederholungsschema ließ sich als String gut unterbringen (`3 × 6–8`), liegt damit aber als unstrukturierter Text vor und nicht als getrennt auswertbare Felder (Satzanzahl, Wdh-Untergrenze, Wdh-Obergrenze).
- **Superset/Zirkel nur implizit.** Die Gruppierung wird allein über ein gemeinsames `supersetLabel` (String) ausgedrückt. Ob es sich um einen Zweier-Superset oder einen Dreier-Zirkel handelt, ergibt sich nur indirekt aus der Anzahl gleich gelabelter Übungen; eine zugehörige Pausenvorgabe ist im Modell nicht vorgesehen.

### 4.3 Workflow und Granularität

- **Stark normalisierte Einzelanlage.** Plan, Tag und Übung werden über getrennte Aufrufe erzeugt. Für den Gesamtplan (5 Tage, 31 Übungen) summiert sich das, zusammen mit den vorgelagerten Katalogsuchen, auf eine hohe Zahl an Einzelaufrufen. Ein Sammel-/Bulk-Aufruf zum Anlegen eines kompletten Tages oder Plans stand nicht zur Verfügung.
- **Sequenzielle ID-Abhängigkeit.** Die IDs müssen kettenförmig weitergereicht werden (`planId` → `dayId` → Übung). Zwischen den Schritten muss jeweils auf die Antwort gewartet werden, bevor der nächste Schritt möglich ist.

### 4.4 Tool-Verfügbarkeit / Discovery

- Die `rack`-Tools waren nicht unmittelbar nutzbar, sondern mussten erst über Tool-Suchen geladen werden. Bis die für die Aufgabe nötige Tool-Auswahl (Plan-, Tag-, Übungs-, Such- und Lese-Tools) beisammen war, waren mehrere Suchläufe nötig. Einzelne Tools (z. B. der Health-Check `ping`) waren in den geladenen Sätzen nicht enthalten.

### 4.5 Stabilität

- Während die Schreibvorgänge durchgehend verzögerungsfrei beantwortet wurden, lief der anschließende **lesende** `get_plan`-Aufruf in einen Timeout (~4 Minuten ohne Antwort). Der Reibungspunkt trat reproduzierbar nach einer Folge von Schreibzugriffen auf.

### 4.6 Lokalisierung / Sprache

- Die Nutzerdaten (Plan- und Tagestitel, `cue`-Texte, `target`) wurden auf Deutsch eingegeben, der Übungskatalog ist englischsprachig. Daraus ergab sich ein durchgehender Sprach-Mismatch zwischen den selbst angelegten Plandaten und den referenzierten Katalogeinträgen.

---

## 5. Konkrete Übung-zu-Katalog-Zuordnungen aus dem Testlauf

Die folgende Tabelle dokumentiert, welche geplante Übung auf welchen tatsächlich verfügbaren Katalogeintrag abgebildet wurde. Sie zeigt die im Testlauf entstandenen Abweichungen.

| Geplante Übung (Plan) | Verlinkter Katalogeintrag | Abweichung |
|---|---|---|
| Bankdrücken (Langhantel) | Bench Press | keine |
| Rudern (LH vorgebeugt / Kabel) | Barbell Row (Overhand) | keine wesentliche |
| Schulterdrücken (KH, sitzend) | Shoulder Press, Barbell | Gerät (LH statt KH) |
| Latzug | Neutral Grip Lat Pulldown | Griffvariante festgelegt |
| Seitheben | 45° lateral raises | Variante festgelegt |
| Bizeps Curl (KH) | Alternating Biceps Curls With Dumbbell | Ausführung festgelegt (alternierend) |
| Trizeps Pushdown | Rocking Triceps Pushdown | Variante festgelegt (Rocking) |

Die Trainingslogik (Satz-/Wiederholungsschema, RIR, Gruppierung) wurde in allen Fällen wie geplant gespeichert; die Abweichungen betreffen ausschließlich den referenzierten Katalogeintrag.

---

## 6. Datengrundlage des Testlaufs

- **Angelegter Plan:** „5er Split — Upper/Lower/Push/Pull/Legs" (`kind: recomp`, `source: agent`)
- **Angelegter Tag:** „Oberkörper (Mo)" (`position: 1`, `tag: push`)
- **Angelegte Übungen:** 7 (Positionen 1–7), gruppiert als Superset A (Bankdrücken, Rudern), Superset B (Schulterdrücken, Latzug) und Zirkel C (Seitheben, Bizeps Curl, Trizeps Pushdown)
- **Nicht übertragen:** Trainingstage 2–5 (Unterkörper, Push, Pull, Legs) mit zusammen 24 Übungen
- **Nicht verifiziert:** Rücklesen des angelegten Stands (`get_plan` per Timeout abgebrochen)
