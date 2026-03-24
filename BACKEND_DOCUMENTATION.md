# Backend-Dokumentation — navbackend

Kurzbeschreibung
- Zweck: Backend für Routing/Navigation (Berechnung von Routen, Verwaltung von Fahrzeugprofilen, Lane Guidance).
- Stack: Java 17, Spring Boot, eigene komprimierte Graph-Implementierung (Package `com.patriot.nav.routing`).
- Hinweis: Es existiert ein separates Frontend, das die API unter `/api/routing` verwendet.

Wichtigste Dateien / Einstiegspunkte
- Main: [src/main/java/com/patriot/nav/NavbackendApplication.java](src/main/java/com/patriot/nav/NavbackendApplication.java#L1)
- REST API Controller: [src/main/java/com/patriot/nav/controller/RoutingController.java](src/main/java/com/patriot/nav/controller/RoutingController.java#L1)
- Routing-Logik: [src/main/java/com/patriot/nav/service/RoutingService.java](src/main/java/com/patriot/nav/service/RoutingService.java#L1)
- Graph-Lade-/Preprocessing: [src/main/java/com/patriot/nav/routing/GraphPreprocessor.java](src/main/java/com/patriot/nav/routing/GraphPreprocessor.java#L1)
- OSM Graph Service (lädt Graph beim Start): [src/main/java/com/patriot/nav/service/OSMGraphService.java](src/main/java/com/patriot/nav/service/OSMGraphService.java#L1)
- Startup / Runtime-Setup: [src/main/java/com/patriot/nav/config/StartupConfig.java](src/main/java/com/patriot/nav/config/StartupConfig.java#L1)
- Konfiguration: [src/main/resources/application.properties](src/main/resources/application.properties#L1)

Projektstruktur (Kurz)
- Dokumentation
**Qualitätskriterien & Checkliste**

Im Folgenden sind Qualitätskriterien und Abgabeanforderungen aufgeführt, die als Leitfaden für die Dokumentation und Abgabe dienen.

Projektergebnis, Dokumentation & Präsentation (Hauptkriterien)
- Fachliche Qualität: Fachlich richtige und begründete Entscheidungen; Nachvollziehbarkeit der Algorithmen (Routing, Gewichtung, Profilbehandlung).
- Konzeption und Diagramme: Use-Case-, Aktivitäts- und Klassendiagramme zur Architektur und Ablaufbeschreibung.
- Umfang der Arbeit: Erklärter Funktionsumfang, realisierter Umfang gegenüber Zielen.
- Ausdruck, Satzbau, Stil, Rechtschreibung: Formale Lesbarkeit der Dokumentation.
- Reflexion der Projektarbeit und Ausblick: Kritische Reflexion, Lessons Learned, Ausblick auf Weiterentwicklung.

Konzeptionelle Qualität / Fachliche Tiefe
- Begründung für Designentscheidungen (z. B. Wahl der Datenstruktur `CompressedGraph`, Dijkstra-Algorithmus, Profil-Merging-Strategie).

Seitenlayout, Design, Übersichtlichkeit
- Inhaltsverzeichnis, Deckblatt, klare Kapitelstruktur, Seitenangaben (falls gedruckt oder als PDF eingereicht).

Optionale Anlagen (falls erforderlich)
- Anhänge wie Testdaten, Messprotokolle, Gantt-Diagramm, Deployment- oder Komponentendiagramm — in `docs/appendices/`.

Zeiterfassung
- Nachweis über aufgewendete Zeiten (z. B. einfache CSV/Markdown-Datei `docs/timesheet.md`).

Quellenangaben & Eigenständigkeitserklärung
- Vollständige Quellenangaben (Weblinks, Bibliotheken) und unterschriebene Eigenständigkeitserklärung.
- Artefakte: `docs/sources.md` und `docs/declaration_of_independence.md`.

Präsentation (Kurz)
- Planung & Durchführung: Struktur der Präsentation, Aufgabenteilung (falls Gruppenarbeit).
- Fachgespräch: Tiefe der fachlichen Diskussion, Beantwortung von Detailfragen.
- Artefakte: `docs/presentation_slides.pdf` (optional) und `docs/presentation_notes.md` mit erwarteten Fragen.

Praktische Checkliste für die Abgabe (was in das Repository gehört)
- Dokumentation
  - `BACKEND_DOCUMENTATION.md` (diese Datei) mit: Projektbeschreibung, Setup-Anleitung, API-Dokumentation, Reflexion, Architekturentscheidungen.
- Diagramme
  - `docs/diagrams/usecase.png` oder `.svg`
  - `docs/diagrams/activity.png` oder `.svg`
  - `docs/diagrams/class.png` oder `.svg`
- Anhang
  - `docs/sources.md` — Quellenangaben
  - `docs/declaration_of_independence.md` — Eigenständigkeitserklärung
  - `docs/timesheet.md` — Zeiterfassung
  - `docs/presentation_slides.pdf` — Präsentationsfolien (falls vorhanden)

Hinweise zur Umsetzung der Anforderungen
- Use-Case- und Aktivitätsdiagramme sollten die Benutzerinteraktion (z. B. Route anfragen, Profil anlegen) und die internen Abläufe (NearestNode-Find, Dijkstra, NavigationService) abbilden.
- Klassendiagramm sollte Kernelemente zeigen: `CompressedGraph`, `CompressedDijkstraRouter`, `RoutingService`, `RouteRequest/RouteResponse`, `VehicleProfile`.
- Reflexion: Beschreibe, welche Anforderungen erfüllt wurden, welche nicht, welche technischen Risiken aufgetreten sind, und wie Du sie adressiert hättest.
- Beispiel: `GET /api/routing/vehicle/car` →

```json
{
  "name":"car",
  "maxSpeed":130,
  "heuristicWeight":1.0,
  "ignoreOneWay":false,
  "wayMultipliers":{"motorway":0.7},
  "tagWeights":{},
  "blockedTags":[],
  "access":{"motorVehicle":true,"foot":false,"bicycle":false}
}
```

4) POST `/api/routing/vehicle/{name}`
- Beschreibung: Speichert/überschreibt ein Fahrzeugprofil mit dem gegebenen `name`.
- Beispiel-Request: JSON wie beim `VehicleProfile`-Beispiel oben. Antwort ist ein einfacher String bei Erfolg.

5) OPTIONS `/api/routing/vehicles`
- Beschreibung: Liefert ein dynamisches Schema und mögliche Werte/Schlüssel für Fahrzeugprofile (wird vom Controller dynamisch generiert).

6) GET `/api/routing/health`
- Beschreibung: Health-Check endpoint. Antwort: einfacher Text `Routing service is running`.

Interne Architektur & Ablauf (Kurz)
- Beim Start initialisiert `OSMGraphService` den Graphen aus der konfigurierten OSM-PBF-Datei.
- `RoutingService.findRoute()` verwendet `CompressedNearestNodeFinder` zum Finden nächster Knoten und `CompressedDijkstraRouter` für die Pfadsuche. `NavigationService` erzeugt Turn-by-Turn-Instruktionen.

Bekannte Probleme & Hinweise
- Logs prüfen: `logging.level.com.patriot.nav=DEBUG` hilft bei der Fehlersuche.
- Wenn beim Laden der OSM-Datei Speicher- oder Performance-Probleme auftreten: PBF-Datei verkleinern, mehr Heap zuweisen oder Vorverarbeitung außerhalb des Produktionsstarts durchführen.

Tests & Entwicklertipps
- Tests unter `src/test/java/com/patriot/nav/` (z. B. `CompressedDijkstraRouterTests`).
- Nützliche Klassen zum Debuggen: `CompressedNearestNodeFinder`, `CompressedDijkstraRouter`, `NavigationService`.

Weiteres
- Siehe Code im Ordner `src/main/java/com/patriot/nav/` für Implementierungsdetails.

---
**Bewertungsschema (Prüfungskriterien)**

Dieses Projekt wird nach dem im Schulungsdokument vorgegebenen Bewertungsschema beurteilt. Im Folgenden sind die Kriterien vollständig aufgeführt und in konkrete Anforderungen für die Projektdokumentation und die Abgabe überführt.

- Gesamtpunktzahl / Skala
  - Maximal 10 Punkte je Kriterienblock (Gesamtbewertungskonzept laut Prüfungsrichtlinie). Zentral ist die fachliche Qualität, die Dokumentation sowie die Präsentation.

- Projektergebnis, Dokumentation & Präsentation (Hauptkriterien)
  - Fachliche Qualität: Fachlich richtige und begründete Entscheidungen; Nachvollziehbarkeit der Algorithmen (Routing, Gewichtung, Profilbehandlung).
    - Artefakte: Ausführliche Beschreibung der Implementierung in dieser Dokumentation; Verweise auf die Implementierung in `src/main/java/com/patriot/nav/`.
  - Konzeption und Diagramme: Use-Case-, Aktivitäts- und Klassendiagramme zur Architektur und Ablaufbeschreibung.
    - Artefakte: UML-Diagramme (.png / .svg) im Ordner `docs/diagrams` oder als eingebettete Bilder in der Dokumentation. Mindestens Use-Case, Aktivität und Klassendiagramm müssen vorhanden sein.
  - Umfang der Arbeit: Erklärter Funktionsumfang, realisierter Umfang gegenüber Zielen.
    - Artefakte: Abschnitt "Umfang" in `BACKEND_DOCUMENTATION.md` mit Liste umgesetzter Features und optionaler Erweiterungen.
  - Ausdruck, Satzbau, Stil, Rechtschreibung: Formale Lesbarkeit der Dokumentation.
    - Artefakt: Korrekte, redaktionell geprüfte `BACKEND_DOCUMENTATION.md`.
  - Reflexion der Projektarbeit und Ausblick: Kritische Reflexion, Lessons Learned, Ausblick auf Weiterentwicklung.
    - Artefakt: Abschnitt "Reflexion" am Ende dieser Datei.

- Konzeptionelle Qualität / Fachliche Tiefe
  - Begründung für Designentscheidungen (z. B. Wahl der Datenstruktur `CompressedGraph`, Dijkstra-Algorithmus, Profil-Merging-Strategie).
    - Artefakt: Abschnitt "Architekturentscheidungen" mit pros/cons.

- Seitenlayout, Design, Übersichtlichkeit
  - Inhaltsverzeichnis, Deckblatt, klare Kapitelstruktur, Seitenangaben (falls gedruckt oder als PDF eingereicht).
    - Artefakte: `docs/cover.md` (oder `docs/cover.png`) und `docs/TOC.md` oder ein generiertes PDF mit TOC.

- Optionale Anlagen (falls erforderlich)
  - Anhänge wie Testdaten, Messprotokolle, Gantt-Diagramm, Deployment- oder Komponentendiagramm — in `docs/appendices/`.

- Zeiterfassung
  - Nachweis über aufgewendete Zeiten (z. B. einfache CSV/Markdown-Datei `docs/timesheet.md`).

- Quellenangaben & Eigenständigkeitserklärung
  - Vollständige Quellenangaben (Weblinks, Bibliotheken) und unterschriebene Eigenständigkeitserklärung.
    - Artefakte: `docs/sources.md` und `docs/declaration_of_independence.md`.

- Präsentation (Prüfung/Verteidigung)
  - Planung & Durchführung: Struktur der Präsentation, Aufgabenteilung (falls Gruppenarbeit).
  - Fachgespräch: Tiefe der fachlichen Diskussion, Beantwortung von Detailfragen.
    - Artefakte: `docs/presentation_slides.pdf` (optional) und ein kurzes `docs/presentation_notes.md` mit erwarteten Fragen.

- Bewertungsskala (Kurzinfo)
  - 10 Punkte: Keine Mängel, Arbeit einwandfrei.
  - 9 Punkte: Sehr geringe Mängel.
  - 8 Punkte: Geringe Mängel.
  - 7 Punkte: Mängel.
  - 5 Punkte: Gerade noch vertretbare Mängel.
  - 0–3 Punkte: Größere bzw. unvertretbare Mängel oder keine abgegebene Prüfungsleistung.

Praktische Checkliste für die Abgabe (was in das Repository gehört)
- Dokumentation
  - `BACKEND_DOCUMENTATION.md` (diese Datei) mit: Projektbeschreibung, Setup-Anleitung, API-Dokumentation, Reflexion, Architekturentscheidungen.
- Diagramme
  - `docs/diagrams/usecase.png` oder `.svg`
  - `docs/diagrams/activity.png` oder `.svg`
  - `docs/diagrams/class.png` oder `.svg`
- Anhang
  - `docs/sources.md` — Quellenangaben
  - `docs/declaration_of_independence.md` — Eigenständigkeitserklärung
  - `docs/timesheet.md` — Zeiterfassung
  - `docs/presentation_slides.pdf` — Präsentationsfolien (falls vorhanden)

Hinweise zur Umsetzung der Prüfanforderungen
- Use-Case- und Aktivitätsdiagramme sollten die Benutzerinteraktion (z. B. Route anfragen, Profil anlegen) und die internen Abläufe (NearestNode-Find, Dijkstra, NavigationService) abbilden.
- Klassendiagramm sollte Kernelemente zeigen: `CompressedGraph`, `CompressedDijkstraRouter`, `RoutingService`, `RouteRequest/RouteResponse`, `VehicleProfile`.
- Reflexion: Beschreibe, welche Anforderungen erfüllt wurden, welche nicht, welche technischen Risiken aufgetreten sind, und wie Du sie adressiert hättest.

Abschluss & nächste Schritte
- Prüfe die Checkliste oben und ergänze fehlende Dateien in `docs/`.
- Soll ich die fehlenden Hilfsdateien (Template für Eigenständigkeitserklärung, TOC, UML-Templates) in `docs/` anlegen oder die Änderungen committen und Tests ausführen?