param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot "..\release-signing"),
    [string]$Alias = "claude-link"
)

$ErrorActionPreference = "Stop"
$keytool = Get-Command keytool -ErrorAction Stop
$output = [IO.Path]::GetFullPath($OutputDirectory)
$keystore = Join-Path $output "claude-link-release.jks"
$credentials = Join-Path $output "github-secrets.txt"

if (Test-Path -LiteralPath $keystore) {
    throw "Signing key already exists: $keystore"
}

$alphabet = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789!@#%_-"
$bytes = New-Object byte[] 40
$random = [Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $random.GetBytes($bytes)
} finally {
    $random.Dispose()
}
$password = -join ($bytes | ForEach-Object { $alphabet[$_ % $alphabet.Length] })

New-Item -ItemType Directory -Path $output -Force | Out-Null
& $keytool.Source -genkeypair -v `
    -keystore $keystore `
    -storetype PKCS12 `
    -alias $Alias `
    -keyalg RSA `
    -keysize 4096 `
    -validity 10000 `
    -dname "CN=Claude Link, O=Claude Link, C=CN" `
    -storepass $password `
    -keypass $password
if ($LASTEXITCODE -ne 0) { throw "keytool failed with exit code $LASTEXITCODE" }

$content = @"
ANDROID_KEYSTORE_PASSWORD=$password
ANDROID_KEY_ALIAS=$Alias
ANDROID_KEY_PASSWORD=$password
"@
[IO.File]::WriteAllText($credentials, $content, [Text.UTF8Encoding]::new($false))

Write-Host "Release signing key created: $keystore"
Write-Host "Secret values created: $credentials"
Write-Warning "Back up both files securely. Never commit or send them in chat. Losing this key prevents future in-place updates."
