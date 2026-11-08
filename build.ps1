# Compila GlassHome su Windows senza Gradle (aapt2 + javac + d8 + apksigner). Vedi README.md.
#   .\build.ps1
# Servono JDK 17 e Android SDK (platforms;android-34, build-tools;34.0.0).
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$tc = Join-Path $PSScriptRoot 'toolchain'

if (-not $env:JAVA_HOME -and (Test-Path "$tc\jdk17")) { $env:JAVA_HOME = "$tc\jdk17" }
if ($env:JAVA_HOME) { $env:Path = "$env:JAVA_HOME\bin;$env:Path" }
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk -and (Test-Path "$tc\sdk")) { $sdk = "$tc\sdk" }
if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$bt = "$sdk\build-tools\34.0.0"; $jar = "$sdk\platforms\android-34\android.jar"
if (-not (Test-Path $jar) -or -not (Test-Path $bt)) { throw 'Android SDK non trovato (platforms;android-34, build-tools;34.0.0).' }
function Run([string]$exe) { & $exe @args; if ($LASTEXITCODE -ne 0) { throw "Errore in $exe" } }

$manifest = Get-Content 'app\AndroidManifest.xml' -Raw
$version = [regex]::Match($manifest, 'versionName="([^"]+)"').Groups[1].Value

Remove-Item -Recurse -Force out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force out\classes, out\dex, dist | Out-Null
Run "$bt\aapt2.exe" compile --dir app\res -o out\res.zip
Run "$bt\aapt2.exe" link -o out\base.apk -I $jar --manifest app\AndroidManifest.xml -A app\assets `
    --min-sdk-version 28 --target-sdk-version 34 out\res.zip
$sources = Get-ChildItem -Recurse app\src -Filter *.java | ForEach-Object { $_.FullName }
Run javac -nowarn -Xlint:-options -encoding UTF-8 -source 1.8 -target 1.8 -bootclasspath "$jar;$bt\core-lambda-stubs.jar" -d out\classes @sources
$classes = Get-ChildItem -Recurse out\classes -Filter *.class | ForEach-Object { $_.FullName }
Run "$bt\d8.bat" --min-api 28 --release --lib $jar --output out\dex @classes

# Aggiunge classes.dex all'APK (al posto di "zip").
Copy-Item out\base.apk out\unsigned.apk
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open((Resolve-Path out\unsigned.apk), 'Update')
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Resolve-Path out\dex\classes.dex), 'classes.dex') | Out-Null
$zip.Dispose()
Run "$bt\zipalign.exe" -f 4 out\unsigned.apk out\aligned.apk

# Firma di release: keystore in %USERPROFILE%\keystores, password in keystore.properties (escluso da git).
$props = 'keystore.properties'
if (-not (Test-Path $props)) {
    New-Item -ItemType Directory -Force "$env:USERPROFILE\keystores" | Out-Null
    $pass = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 24 | ForEach-Object { [char]$_ })
    $ks = "$env:USERPROFILE\keystores\glasshome-release.jks" -replace '\\', '/'
    [IO.File]::WriteAllLines((Join-Path $PWD $props), @("storeFile=$ks", "storePassword=$pass", 'keyAlias=glasshome', "keyPassword=$pass"))
}
$p = @{}; Get-Content $props | ForEach-Object { $k, $v = $_ -split '=', 2; $p[$k] = $v }
if (-not (Test-Path $p.storeFile)) {
    Run keytool -genkeypair -keystore $p.storeFile -storepass $p.storePassword -keypass $p.keyPassword -alias $p.keyAlias `
        -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=GlassHome'
    Write-Host "Creato keystore $($p.storeFile)"
}
$out = "dist\GlassHome-$version.apk"
Run "$bt\apksigner.bat" sign --ks $p.storeFile --ks-pass "pass:$($p.storePassword)" --ks-key-alias $p.keyAlias --key-pass "pass:$($p.keyPassword)" --out $out out\aligned.apk
Run "$bt\apksigner.bat" verify $out
Write-Host "APK pronto: $out ($([int]((Get-Item $out).Length / 1KB)) KB)"
