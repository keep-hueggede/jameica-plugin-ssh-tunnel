# Jameica SSH-Tunnel

Jameica-Plugin, das beim Start einen SSH-Tunnel mit lokalem Port-Forwarding
aufbaut. Damit kann z.B. eine entfernte MySQL/MariaDB-Datenbank verschluesselt
angesprochen werden. Konfiguriert wird der Tunnel unter
*Datei > Einstellungen > SSH-Tunnel*.

## Installation

In der Plugin-Verwaltung von Jameica ueber *Neues Repository hinzufuegen...*
diese URL eintragen:

```
https://keep-hueggede.github.io/jameica-plugin-ssh-tunnel/jameica-repository
```

Das Plugin ist signiert. Beim ersten Zugriff fragt Jameica, ob dem Zertifikat
des Repositories vertraut werden soll. Bitte nur bestaetigen, wenn der
SHA-256-Fingerabdruck exakt uebereinstimmt:

```
F9:B3:4C:5C:85:23:5E:17:F6:33:F1:05:6E:48:88:01:F4:25:2E:BB:83:43:F5:3A:F1:2B:50:7C:40:19:0D:89
```

Jameica nimmt das Zertifikat in seinen Zertifikatsspeicher auf und vertraut
ihm danach auch fuer andere HTTPS-Verbindungen.

## Bauen

```
powershell -ExecutionPolicy Bypass -File build\build.ps1
```

bzw. `sh build/build.sh`. Erwartet ein gebautes Jameica-Repository unter
`../jameica`. Ergebnis: `target/ssh-tunnel-<version>.zip`.

## Plugins, die den Tunnel nutzen

Jameica initialisiert Plugins nur dann in einer festen Reihenfolge, wenn sie
Abhaengigkeiten deklarieren. Ein Plugin, das seine Datenbank ueber den Tunnel
anspricht, muss deshalb in seiner `plugin.xml` eine **optionale** Abhaengigkeit
auf `ssh-tunnel` angeben. Sonst kann es vor dem Tunnel starten und die
Verbindung schlaegt fehl:

```xml
<requires jameica="2.10.0+">
  <import plugin="ssh-tunnel" required="false"/>
</requires>
```

Durch `required="false"` startet das Plugin auch ohne installierten Tunnel.

Ausserdem sollte im Tunnel ein fester lokaler Port eingetragen werden (nicht
`0`), da die Datenbank-Konfiguration des Plugins auf diesen Port zeigt.

## Migration aus dem Jameica-Core

War der SSH-Tunnel vorher im Jameica-Core konfiguriert, uebernimmt das Plugin
beim ersten Start Einstellungen und gespeicherte Passwoerter automatisch.
