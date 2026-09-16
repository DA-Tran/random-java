<#
    Compiles the whole 128-project suite into build\classes.

        .\build.ps1

    Prefers the JDK bundled in openJdk-25\, falling back to javac on PATH.
    Set $env:JAVAC to force a particular compiler.
#>

$ErrorActionPreference = "Stop"
Set-Location -Path $PSScriptRoot

$javac = $env:JAVAC
if (-not $javac) {
    if (Test-Path ".\openJdk-25\bin\javac.exe") {
        $javac = (Resolve-Path ".\openJdk-25\bin\javac.exe").Path
    } else {
        $javac = "javac"
    }
}

if (-not (Get-Command $javac -ErrorAction SilentlyContinue) -and -not (Test-Path $javac)) {
    Write-Error "No Java compiler found. Install a JDK 17 or newer, or set `$env:JAVAC."
    exit 1
}

$out = "build\classes"
# Best effort clean. A locked file (an editor or a running JVM holding a class)
# should not abort the build, since javac overwrites what it produces anyway.
if (Test-Path $out) {
    Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
    if (Test-Path $out) { Write-Host "  (could not fully clean $out, continuing)" }
}
New-Item -ItemType Directory -Force -Path $out | Out-Null

Write-Host "Compiling with $(& $javac -version 2>&1)"

# One compilation unit for the lot: the generated Catalog references every
# project, so they all have to be on the same javac invocation anyway.
$sources = Get-ChildItem -Path "lib", "projects" -Recurse -Filter "*.java" |
    ForEach-Object { $_.FullName }
$argFile = "build\sources.txt"
$sources | Set-Content -Path $argFile -Encoding UTF8
Write-Host "  $($sources.Count) source files"

& $javac -encoding UTF-8 --release 17 -nowarn -d $out "@$argFile"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Compilation failed."
    exit $LASTEXITCODE
}

# Each project's ui.html has to sit beside its class on the classpath, because
# Project.uiFragment() loads it as a resource relative to the class.
$copied = 0
foreach ($ui in Get-ChildItem -Path "projects" -Recurse -Filter "ui.html") {
    $javaFile = Get-ChildItem -Path $ui.Directory.FullName -Filter "*.java" |
        Select-Object -First 1
    if (-not $javaFile) { continue }
    $packageLine = Select-String -Path $javaFile.FullName -Pattern '^package\s+(.+);' |
        Select-Object -First 1
    if (-not $packageLine) { continue }
    $package = $packageLine.Matches[0].Groups[1].Value.Trim()
    $dest = Join-Path $out ($package -replace '\.', '\')
    New-Item -ItemType Directory -Force -Path $dest | Out-Null
    Copy-Item $ui.FullName (Join-Path $dest "ui.html") -Force
    $copied++
}

Write-Host "  $copied ui.html resources copied"
Write-Host ""
Write-Host "Built. Try:"
Write-Host "  .\run.ps1              interactive terminal menu"
Write-Host "  .\run.ps1 web          browser hub on http://localhost:8080"
Write-Host "  .\run.ps1 text 1       run project 1 in the terminal"
