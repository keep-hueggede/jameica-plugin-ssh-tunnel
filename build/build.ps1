# ----------------------------------------------------------------------------
# Baut das Jameica-Plugin "ssh-tunnel".
#
# Da die Jameica-Core-Artefakte (jameica, de_willuhn_util, de_willuhn_ds) nicht
# auf Maven Central verfuegbar sind, werden sie vor dem eigentlichen Build aus
# dem lokalen Jameica-Repository in das lokale Maven-Repository installiert.
#
# Aufruf:
#   powershell -ExecutionPolicy Bypass -File build\build.ps1
#
# Optionen:
#   -JameicaDir <Pfad>   Pfad zum Jameica-Core-Repository (Default: ..\..\jameica)
#   -JameicaVersion <V>  Version der lokal installierten Core-Artefakte
#   -SkipInstall         Core-Artefakte nicht erneut installieren
# ----------------------------------------------------------------------------
#Requires -Version 5.1
param(
  [string]$JameicaDir = "",
  [string]$JameicaVersion = "2.13.0-nightly",
  [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"

$pluginRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrEmpty($JameicaDir))
{
  $JameicaDir = Join-Path (Split-Path -Parent $pluginRoot) "jameica"
}
$JameicaDir = (Resolve-Path -LiteralPath $JameicaDir).Path

Write-Host "Plugin-Verzeichnis : $pluginRoot"
Write-Host "Jameica-Verzeichnis: $JameicaDir"

# ---------------------------------------------------------------------------
# Hilfsfunktion: externes Kommando ausfuehren, ohne dass dessen Ausgaben auf
# stderr von $ErrorActionPreference="Stop" als Fehler behandelt werden.
# ---------------------------------------------------------------------------
function Invoke-External
{
  param(
    [Parameter(Mandatory=$true)][string]$FilePath,
    [Parameter(Mandatory=$false)][string[]]$ArgumentList = @()
  )

  $eap = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try
  {
    $output = & $FilePath @ArgumentList 2>&1
    $code = $LASTEXITCODE
  }
  finally
  {
    $ErrorActionPreference = $eap
  }

  return [pscustomobject]@{ Output = $output; ExitCode = $code }
}

# ---------------------------------------------------------------------------
# JAVA_HOME ermitteln (wird vom Maven-Wrapper und von jar.exe benoetigt).
# ---------------------------------------------------------------------------
function Get-JavaHome
{
  if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME "bin\jar.exe")))
  {
    return $env:JAVA_HOME
  }

  $javaCmd = Get-Command java -ErrorAction SilentlyContinue
  if ($null -eq $javaCmd)
  {
    throw "Kein 'java' auf dem PATH gefunden und JAVA_HOME ist nicht gesetzt."
  }

  # Bevorzugt aus java.home ableiten (z.B. "C:\Program Files\Java\jdk-26").
  $res = Invoke-External -FilePath $javaCmd.Source -ArgumentList @("-XshowSettings:properties","-version")
  $match = ($res.Output | Out-String | Select-String -Pattern "java\.home\s*=\s*(.+)" | Select-Object -First 1)
  if ($match)
  {
    $value = $match.Matches[0].Groups[1].Value.Trim()
    if (Test-Path -LiteralPath (Join-Path $value "bin\jar.exe"))
    {
      return $value
    }
  }

  # Fallback: jar.exe neben java.exe suchen.
  $dir = Split-Path -Parent $javaCmd.Source
  if (Test-Path -LiteralPath (Join-Path $dir "jar.exe"))
  {
    return $dir.TrimEnd('\')
  }

  throw "Konnte JAVA_HOME nicht ermitteln (kein jar.exe gefunden)."
}

$javaHome = Get-JavaHome
$env:JAVA_HOME = $javaHome
$jarExe = Join-Path $javaHome "bin\jar.exe"
Write-Host "JAVA_HOME          : $javaHome"

# ---------------------------------------------------------------------------
# Core-Artefakte in das lokale Maven-Repository installieren.
# ---------------------------------------------------------------------------
$mvnw = Join-Path $pluginRoot "mvnw.cmd"

function Invoke-Maven
{
  param([string[]]$MavenArgs)
  $res = Invoke-External -FilePath $mvnw -ArgumentList $MavenArgs
  $res.Output | ForEach-Object { Write-Host $_ }
  if ($res.ExitCode -ne 0)
  {
    throw "Maven-Aufruf fehlgeschlagen: mvn $($MavenArgs -join ' ')"
  }
}

if (-not $SkipInstall)
{
  $coreBin = Join-Path $JameicaDir "bin"
  if (-not (Test-Path -LiteralPath $coreBin))
  {
    throw "Jameica-Core ist nicht gebaut: '$coreBin' fehlt. Bitte zuerst den Core bauen."
  }

  $utilJar = Join-Path $JameicaDir "lib\de_willuhn_util\de_willuhn_util.jar"
  $dsJar   = Join-Path $JameicaDir "lib\de_willuhn_ds\de_willuhn_ds.jar"
  foreach ($f in @($utilJar,$dsJar))
  {
    if (-not (Test-Path -LiteralPath $f))
    {
      throw "Benoetigtes Core-Jar fehlt: '$f'"
    }
  }

  $staging = Join-Path $env:TEMP ("jameica-bootstrap-" + [System.Guid]::NewGuid().ToString("N"))
  New-Item -ItemType Directory -Path $staging | Out-Null
  try
  {
    Write-Host "Packe Jameica-Core-Klassen aus '$coreBin' ..."
    $coreJar = Join-Path $staging "jameica.jar"
    $resJar = Invoke-External -FilePath $jarExe -ArgumentList @("--create","--file",$coreJar,"-C",$coreBin,".")
    if ($resJar.ExitCode -ne 0)
    {
      $resJar.Output | ForEach-Object { Write-Host $_ }
      throw "jar.exe konnte jameica.jar nicht erzeugen."
    }

    Write-Host "Installiere Core-Artefakte in das lokale Maven-Repository ..."
    Invoke-Maven @("install:install-file","-q",
      "-Dfile=$coreJar","-DgroupId=de.willuhn.jameica","-DartifactId=jameica",
      "-Dversion=$JameicaVersion","-Dpackaging=jar")
    Invoke-Maven @("install:install-file","-q",
      "-Dfile=$utilJar","-DgroupId=de.willuhn","-DartifactId=de_willuhn_util",
      "-Dversion=$JameicaVersion","-Dpackaging=jar")
    Invoke-Maven @("install:install-file","-q",
      "-Dfile=$dsJar","-DgroupId=de.willuhn","-DartifactId=de_willuhn_ds",
      "-Dversion=$JameicaVersion","-Dpackaging=jar")
  }
  finally
  {
    Remove-Item -LiteralPath $staging -Recurse -Force -ErrorAction SilentlyContinue
  }
}

# ---------------------------------------------------------------------------
# Plugin bauen.
# ---------------------------------------------------------------------------
Write-Host "Baue Plugin ..."
Invoke-Maven @("clean","package")

Write-Host ""
Write-Host "Fertig. Ergebnis:"
Get-ChildItem -LiteralPath (Join-Path $pluginRoot "target") -Filter "ssh-tunnel-*.zip" |
  ForEach-Object { Write-Host ("  " + $_.FullName) }
