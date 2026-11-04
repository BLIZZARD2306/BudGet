# Publishes a new build to the Vercel download page (website/) so installed apps offer it as an update.
#   1. Raise versionCode (and versionName) in app/build.gradle.kts
#   2. .\release.ps1 -Notes "What's new"
# The APK is signed with this PC's debug key, the same key Android Studio installs with, so it
# updates over existing installs and keeps the registered SHA-1 / App Check setup working.
param([string]$Notes = "")
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$gradle = Get-Content app/build.gradle.kts -Raw
$code = [int]([regex]::Match($gradle, 'versionCode\s*=\s*(\d+)').Groups[1].Value)
$name = [regex]::Match($gradle, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value

try { $live = Invoke-RestMethod "https://budget-goblin.vercel.app/app/latest.json?t=$(Get-Date -UFormat %s)" } catch { $live = $null }
if ($live -and $live.versionCode -ge $code) {
    throw "versionCode $code isn't newer than the published $($live.versionCode). Raise it in app/build.gradle.kts first."
}

.\gradlew.bat assembleDebug
if ($LASTEXITCODE -ne 0) { throw "Build failed" }

$out = "website/app"
New-Item -ItemType Directory -Force $out | Out-Null
Copy-Item app/build/outputs/apk/debug/app-debug.apk "$out/budget.apk" -Force
$mb = [math]::Round((Get-Item "$out/budget.apk").Length / 1MB, 1)
@{ versionCode = $code; versionName = $name; notes = $Notes; apk = "budget.apk"; sizeMB = $mb } |
    ConvertTo-Json | Set-Content "$out/latest.json" -Encoding utf8NoBOM

Push-Location website
npx -y vercel@64.7.0 deploy --prod --yes
Pop-Location
if ($LASTEXITCODE -ne 0) { throw "Deploy failed" }
Write-Host "Published $name ($code)."
