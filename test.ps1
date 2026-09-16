<#
    Compiles and runs the whole test suite.

        .\test.ps1

    Tests live in tests\ and compile into build\test-classes, which keeps them
    out of the shipped build in build\classes. Exits non-zero on any failure.
#>

$ErrorActionPreference = "Stop"
Set-Location -Path $PSScriptRoot

$javac = $env:JAVAC
if (-not $javac) {
    if (Test-Path ".\openJdk-25\bin\javac.exe") {
        $javac = (Resolve-Path ".\openJdk-25\bin\javac.exe").Path
    } else { $javac = "javac" }
}
$java = $env:JAVA
if (-not $java) {
    if (Test-Path ".\openJdk-25\bin\java.exe") {
        $java = (Resolve-Path ".\openJdk-25\bin\java.exe").Path
    } else { $java = "java" }
}

if (-not (Test-Path "build\classes\com\randomjava\Launcher.class")) {
    & "$PSScriptRoot\build.ps1"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

$out = "build\test-classes"
if (Test-Path $out) { Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue }
New-Item -ItemType Directory -Force -Path $out | Out-Null

$sources = Get-ChildItem -Path "tests" -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
$argFile = "build\test-sources.txt"
$sources | Set-Content -Path $argFile -Encoding UTF8

& $javac -encoding UTF-8 --release 17 -nowarn -cp "build\classes" -d $out "@$argFile"
if ($LASTEXITCODE -ne 0) { Write-Error "Test compilation failed."; exit $LASTEXITCODE }

& $java -Xss8m -cp "build\classes;$out" com.randomjava.test.AllTests
exit $LASTEXITCODE
