#!/bin/sh
# ----------------------------------------------------------------------------
# Baut das Jameica-Plugin "ssh-tunnel".
#
# Da die Jameica-Core-Artefakte (jameica, de_willuhn_util, de_willuhn_ds) nicht
# auf Maven Central verfuegbar sind, werden sie vor dem eigentlichen Build aus
# dem lokalen Jameica-Repository in das lokale Maven-Repository installiert.
#
# Aufruf:
#   sh build/build.sh
#
# Umgebungsvariablen:
#   JAMEICA_DIR      Pfad zum Jameica-Core-Repository (Default: ../jameica)
#   JAMEICA_VERSION  Version der lokal installierten Core-Artefakte
#   SKIP_INSTALL     "1" = Core-Artefakte nicht erneut installieren
# ----------------------------------------------------------------------------
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
plugin_root=$(CDPATH= cd -- "$script_dir/.." && pwd)

JAMEICA_DIR=${JAMEICA_DIR:-"$(CDPATH= cd -- "$plugin_root/../jameica" && pwd)"}
JAMEICA_VERSION=${JAMEICA_VERSION:-2.13.0-nightly}
SKIP_INSTALL=${SKIP_INSTALL:-0}

echo "Plugin-Verzeichnis : $plugin_root"
echo "Jameica-Verzeichnis: $JAMEICA_DIR"

# ---------------------------------------------------------------------------
# JAVA_HOME ermitteln.
# ---------------------------------------------------------------------------
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/jar" ]; then
  if ! command -v java >/dev/null 2>&1; then
    echo "Fehler: Kein 'java' auf dem PATH gefunden und JAVA_HOME ist nicht gesetzt." >&2
    exit 1
  fi
  JAVA_HOME=$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*java\.home[[:space:]]*=[[:space:]]*//p' | head -n 1)
  if [ -z "$JAVA_HOME" ]; then
    echo "Fehler: Konnte JAVA_HOME nicht ermitteln." >&2
    exit 1
  fi
  export JAVA_HOME
fi
JAR_EXE="$JAVA_HOME/bin/jar"
echo "JAVA_HOME          : $JAVA_HOME"

# ---------------------------------------------------------------------------
# Core-Artefakte in das lokale Maven-Repository installieren.
# ---------------------------------------------------------------------------
install_artifacts()
{
  core_bin="$JAMEICA_DIR/bin"
  if [ ! -d "$core_bin" ]; then
    echo "Fehler: Jameica-Core ist nicht gebaut: '$core_bin' fehlt." >&2
    exit 1
  fi

  util_jar="$JAMEICA_DIR/lib/de_willuhn_util/de_willuhn_util.jar"
  ds_jar="$JAMEICA_DIR/lib/de_willuhn_ds/de_willuhn_ds.jar"
  for f in "$util_jar" "$ds_jar"; do
    if [ ! -f "$f" ]; then
      echo "Fehler: Benoetigtes Core-Jar fehlt: '$f'" >&2
      exit 1
    fi
  done

  staging=$(mktemp -d)
  trap 'rm -rf "$staging"' EXIT INT TERM
  core_jar="$staging/jameica.jar"

  echo "Packe Jameica-Core-Klassen aus '$core_bin' ..."
  "$JAR_EXE" --create --file "$core_jar" -C "$core_bin" .

  echo "Installiere Core-Artefakte in das lokale Maven-Repository ..."
  "$plugin_root/mvnw" -q install:install-file \
    -Dfile="$core_jar" -DgroupId=de.willuhn.jameica -DartifactId=jameica \
    -Dversion="$JAMEICA_VERSION" -Dpackaging=jar
  "$plugin_root/mvnw" -q install:install-file \
    -Dfile="$util_jar" -DgroupId=de.willuhn -DartifactId=de_willuhn_util \
    -Dversion="$JAMEICA_VERSION" -Dpackaging=jar
  "$plugin_root/mvnw" -q install:install-file \
    -Dfile="$ds_jar" -DgroupId=de.willuhn -DartifactId=de_willuhn_ds \
    -Dversion="$JAMEICA_VERSION" -Dpackaging=jar
}

if [ "$SKIP_INSTALL" != "1" ]; then
  install_artifacts
fi

echo "Baue Plugin ..."
"$plugin_root/mvnw" clean package

echo ""
echo "Fertig. Ergebnis:"
for f in "$plugin_root"/target/ssh-tunnel-*.zip; do
  [ -f "$f" ] && echo "  $f"
done
