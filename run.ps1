<#
    Runs the suite, building first if it has not been built yet.

        .\run.ps1                 interactive terminal menu
        .\run.ps1 text 4          run project 4 in the terminal
        .\run.ps1 text bmi        ...by name
        .\run.ps1 web             browser hub on http://localhost:8080
        .\run.ps1 web 9000        ...on another port
        .\run.ps1 list maze       search the catalogue
#>

$ErrorActionPreference = "Stop"
Set-Location -Path $PSScriptRoot

if (-not (Test-Path "build\classes\com\randomjava\Launcher.class")) {
    & "$PSScriptRoot\build.ps1"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

$java = $env:JAVA
if (-not $java) {
    if (Test-Path ".\openJdk-25\bin\java.exe") {
        $java = (Resolve-Path ".\openJdk-25\bin\java.exe").Path
    } else {
        $java = "java"
    }
}

& $java -cp "build\classes" com.randomjava.Launcher @args
exit $LASTEXITCODE
