param(
    [string]$Repository = "guiyanghai183/claude-link-android",
    [string]$SigningDirectory = (Join-Path $PSScriptRoot "..\release-signing")
)

$ErrorActionPreference = "Stop"
Get-Command gh -ErrorAction Stop | Out-Null
$directory = [IO.Path]::GetFullPath($SigningDirectory)
$keystore = Join-Path $directory "claude-link-release.jks"
$credentialsPath = Join-Path $directory "github-secrets.txt"
if (!(Test-Path -LiteralPath $keystore) -or !(Test-Path -LiteralPath $credentialsPath)) {
    throw "Run scripts/create-release-key.ps1 first."
}

$credentials = @{}
Get-Content -LiteralPath $credentialsPath -Encoding utf8 | ForEach-Object {
    if ($_ -match '^([^=]+)=(.*)$') { $credentials[$matches[1]] = $matches[2] }
}
$base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore))

$base64 | gh secret set ANDROID_KEYSTORE_BASE64 --repo $Repository
$credentials["ANDROID_KEYSTORE_PASSWORD"] | gh secret set ANDROID_KEYSTORE_PASSWORD --repo $Repository
$credentials["ANDROID_KEY_ALIAS"] | gh secret set ANDROID_KEY_ALIAS --repo $Repository
$credentials["ANDROID_KEY_PASSWORD"] | gh secret set ANDROID_KEY_PASSWORD --repo $Repository

Write-Host "GitHub Actions signing secrets configured for $Repository."
