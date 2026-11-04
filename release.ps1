# Publishes a new build so installed apps offer it as an update.
#   1. Raise versionCode (+1) and versionName (BIG.MAJOR.MINOR) in app/build.gradle.kts
#   2. .\release.ps1 -Notes "What's new"
# The APK goes on a GitHub Release of the web repository (BudGet-Goblin-web, cloned next to this
# one), and only that repository's app/latest.json changes; Vercel redeploys the page from it.
# The APK is signed with this PC's debug key, the same key Android Studio installs with, so it
# updates over existing installs and keeps the registered SHA-1 / App Check setup working.
param([string]$Notes = "")
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$repo = "BLIZZARD2306/BudGet-Goblin-web"
$web = Join-Path (Split-Path $PSScriptRoot) "BudGet-Goblin-web"
$gh = (Get-Command gh -ErrorAction SilentlyContinue).Source
if (-not $gh) { $gh = "C:\Program Files\GitHub CLI\gh.exe" }
if (-not (Test-Path "$web\.git")) { throw "Clone https://github.com/$repo next to this folder first." }

git -C $web pull --ff-only -q
if ($LASTEXITCODE -ne 0) { throw "Couldn't update $web" }

$gradle = Get-Content app/build.gradle.kts -Raw
$code = [int]([regex]::Match($gradle, 'versionCode\s*=\s*(\d+)').Groups[1].Value)
$name = [regex]::Match($gradle, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
$latestFile = "$web\app\latest.json"
$live = if (Test-Path $latestFile) { Get-Content $latestFile -Raw | ConvertFrom-Json } else { $null }
if ($live -and $live.versionCode -ge $code) {
    throw "versionCode $code isn't newer than the published $($live.versionCode). Raise it in app/build.gradle.kts first."
}

.\gradlew.bat assembleDebug
if ($LASTEXITCODE -ne 0) { throw "Build failed" }

$apk = Join-Path $env:TEMP "budget.apk"
Copy-Item app/build/outputs/apk/debug/app-debug.apk $apk -Force
& $gh release create "v$name" $apk --repo $repo --title "BudGet $name" --notes $(if ($Notes) { $Notes } else { "BudGet $name" })
if ($LASTEXITCODE -ne 0) { throw "Couldn't create the GitHub release" }

@{
    versionCode = $code
    versionName = $name
    notes = $Notes
    apk = "https://github.com/$repo/releases/download/v$name/budget.apk"
    sizeMB = [math]::Round((Get-Item $apk).Length / 1MB, 1)
} | ConvertTo-Json | Set-Content $latestFile -Encoding utf8NoBOM
git -C $web add app/latest.json
git -C $web commit -q -m "Release $name"
git -C $web push -q
if ($LASTEXITCODE -ne 0) { throw "Couldn't push latest.json" }
Write-Host "Published $name ($code). Vercel updates the page in a minute."
