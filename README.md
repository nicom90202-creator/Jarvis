# Jarvis – persönlicher Android-Assistent

Eine native Android-App (Kotlin + Jetpack Compose) mit Sprachsteuerung,
animierter weiß/blauer Kugel und Anbindung an die Google-Gemini-API.

## Funktionen

- **Weißes UI mit animierter weiß/blauer Kugel** (`ui/OrbView.kt`), die je nach
  Zustand (Idle, Zuhören, Denken, Antworten, Fehler) unterschiedlich pulsiert
  und rotiert.
- **Spracheingabe** über Android's `SpeechRecognizer` (`voice/SpeechRecognizerManager.kt`).
- **Sprachausgabe** über Android's `TextToSpeech` (`voice/TextToSpeechManager.kt`).
- **Gemini-Anbindung** über einen schlanken REST-Client (`data/GeminiRepository.kt`),
  inkl. Gesprächsverlauf für kontextbezogene Antworten.
- **Sicherer API-Key-Speicher** über `EncryptedSharedPreferences`
  (`data/ApiKeyStore.kt`) – der Schlüssel wird verschlüsselt auf dem Gerät
  abgelegt, nicht im Code.
- **System-Assistent-Anbindung (Best Effort)**: Ein `VoiceInteractionService`
  (`assistant/`) registriert Jarvis dafür, dass er unter
  *Einstellungen > Apps > Standard-Apps > Digitaler Assistent* ausgewählt
  werden kann. Das Verhalten hängt stark vom Gerätehersteller und der
  Android-Version ab – bei manchen Geräten (v. a. Samsung, manche OEMs) ist
  diese Rolle auf vorinstallierte Apps beschränkt.
- **Text-Eingabe** als Alternative zur Spracheingabe, unten am Bildschirm.
- **Geräte-Fähigkeiten via Gemini Function Calling** (`actions/`):
  Google-Suche für aktuelle Infos, Kalender lesen/anlegen, Wecker/Timer
  stellen, Apps öffnen, Kontakte suchen, Taschenlampe, Lautstärke, Karten
  öffnen. **Anrufe und SMS werden nur vorbereitet** (Dialer/SMS-App öffnet
  sich mit ausgefülltem Empfänger/Text) – Jarvis sendet nie automatisch,
  der letzte Tap liegt immer beim Nutzer.

### Welche Berechtigungen Jarvis anfragt und warum

| Berechtigung | Wofür |
|---|---|
| Mikrofon | Spracheingabe |
| Kontakte (lesen) | Kontakte für Anruf/SMS/Termine nachschlagen |
| Kalender (lesen/schreiben) | Termine anzeigen und anlegen |

Anrufe/SMS benötigen bewusst **keine** `CALL_PHONE`/`SEND_SMS`-Berechtigung:
Jarvis öffnet nur die Telefon- bzw. SMS-App mit vorausgefülltem Inhalt: Nutzung
über System-Intents (`ACTION_DIAL`, `ACTION_SENDTO`), nicht über eine
Direktversand-API. Wecker/Timer laufen ebenfalls über die System-Uhr-App per
Intent. Alle Berechtigungen lassen sich über das Zahnrad in der App
("Zugriff auf Kontakte & Kalender erlauben") oder jederzeit in den
Android-Systemeinstellungen widerrufen.

## Installation direkt aufs Handy (ohne PC)

Ein GitHub-Actions-Workflow (`.github/workflows/build-apk.yml`) baut bei
jedem Push automatisch eine installierbare Debug-APK:

1. Im GitHub-Repo auf **Actions** gehen, den neuesten Lauf von
   **"Build APK"** öffnen (läuft automatisch nach jedem Push auf diesen
   Branch, dauert ca. 3–5 Minuten).
2. Unten bei **Artifacts** die Datei `jarvis-debug-apk` herunterladen
   (ZIP mit der `app-debug.apk` darin) – das geht auch direkt im
   Handy-Browser.
3. Die entpackte `app-debug.apk` öffnen/installieren. Falls Android
   "Installation aus unbekannten Quellen" blockiert: In der Meldung auf
   **Einstellungen** tippen und für den verwendeten Browser/Dateimanager
   erlauben.
4. App öffnen, Mikrofon-Berechtigung erlauben, über das Zahnrad oben
   rechts den Gemini-API-Key eintragen (siehe unten).
5. Auf die Kugel tippen und sprechen – Jarvis transkribiert, fragt
   Gemini und liest die Antwort vor.

## Einrichtung (alternativ mit Android Studio auf einem PC/Mac)

1. **Projekt öffnen**: In Android Studio (Iguana oder neuer) als bestehendes
   Projekt öffnen – `File > Open` auf diesen Ordner zeigen.
2. **Gemini API-Key besorgen**: Kostenlos unter https://aistudio.google.com/apikey
   erstellen.
3. **App starten**: Auf Gerät/Emulator ausführen, Mikrofon-Berechtigung
   erlauben, über das Zahnrad oben rechts den Gemini-API-Key eintragen.
4. **Sprechen**: Auf die Kugel tippen und sprechen – Jarvis transkribiert,
   fragt Gemini und liest die Antwort vor.

### Als Standard-Assistent aktivieren (optional)

1. App einmal öffnen und Mikrofon-Berechtigung erteilen (Pflicht, da die
   Assistent-Session selbst keine Laufzeit-Berechtigung anfordern kann).
2. Unter *Einstellungen > Apps > Standard-Apps > Digitaler Assistent* (Name
   und Pfad variiert je nach Hersteller) "Jarvis" auswählen, falls es dort
   angeboten wird.
3. Assist-Geste auslösen (z. B. Home-Taste lang drücken, je nach Gerät).

## Projektstruktur

```
app/src/main/java/com/jarvis/assistant/
├── MainActivity.kt                  Einstiegspunkt, Berechtigungen
├── JarvisApplication.kt
├── ui/
│   ├── AssistantScreen.kt           Haupt-UI (Kugel, Status, Transcript)
│   ├── AssistantViewModel.kt        Verbindet Speech, Gemini, TTS
│   ├── AssistantUiState.kt
│   ├── OrbView.kt                   Die animierte Kugel
│   ├── SettingsSheet.kt             API-Key-Dialog
│   └── theme/                       Farben, Typografie
├── voice/
│   ├── SpeechRecognizerManager.kt   Spracherkennung
│   └── TextToSpeechManager.kt       Sprachausgabe
├── data/
│   ├── GeminiRepository.kt          REST-Client für Gemini (inkl. Function Calling)
│   ├── GeminiTools.kt               Datenklassen für Tool-Deklarationen
│   ├── ApiKeyStore.kt               Verschlüsselte Key-Ablage
│   └── ChatMessage.kt               Gesprächsverlauf (Text- & Function-Call-Turns)
├── actions/
│   ├── ToolRegistry.kt              JSON-Schema aller Jarvis-Fähigkeiten für Gemini
│   └── DeviceActions.kt             Führt die Fähigkeiten auf dem Gerät aus
└── assistant/
    ├── JarvisVoiceInteractionService.kt
    ├── JarvisVoiceInteractionSessionService.kt
    └── JarvisVoiceInteractionSession.kt
```

## Hinweis zu dieser Umgebung

Diese Session hatte keinen Zugriff auf ein Android SDK und keinen
Netzwerkzugriff auf Googles Maven-Repository (`dl.google.com`), daher konnte
der Build hier nicht kompiliert/getestet werden. Der Code wurde sorgfältig
manuell auf Imports, API-Signaturen und Typkonsistenz geprüft, aber ein
erster Build in Android Studio sollte durchgeführt werden, um letzte
Fehler (z. B. abweichende Bibliotheksversionen) auszuschließen.
