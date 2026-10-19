# Jameica SSH-Tunnel

Jameica-Plugin, das beim Start einen SSH-Tunnel mit lokalem Port-Forwarding
aufbaut. Damit kann z.B. eine entfernte MySQL/MariaDB-Datenbank verschluesselt
angesprochen werden. Konfiguriert wird der Tunnel unter
*Datei > Einstellungen > SSH-Tunnel*.

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
