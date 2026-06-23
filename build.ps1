$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

if (-not $env:JAVA_HOME -or -not (Test-Path $env:JAVA_HOME)) {
    $env:JAVA_HOME = "C:\Program Files\1C\1CE\components\axiom-jdk-full-17.0.16+12-x86_64"
}
$env:MAVEN_OPTS = "-Xmx2g"

$mvn = Join-Path $root "_tools\apache-maven-3.9.9\bin\mvn.cmd"
if (-not (Test-Path $mvn)) {
    throw "Local Maven not found at $mvn. Download Apache Maven 3.9.x into _tools/."
}

$goals = if ($args.Count -gt 0) { $args } else { @("clean", "verify") }
& $mvn -f (Join-Path $root "pom.xml") -B @goals
exit $LASTEXITCODE
