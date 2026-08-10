<#
.SYNOPSIS
    Generates a cryptographically random 48-byte JWT signing secret, base64-encoded.

.DESCRIPTION
    Paste the output into JWT_SECRET in .env. Works on Windows PowerShell 5.1 and
    PowerShell 7+, with no OpenSSL required.

    Do not reuse this value across environments, and never commit it.
#>

$bytes = New-Object byte[] 48
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$secret = [Convert]::ToBase64String($bytes)

Write-Host ''
Write-Host 'Add this line to your .env file:' -ForegroundColor Cyan
Write-Host ''
Write-Host "JWT_SECRET=$secret" -ForegroundColor Green
Write-Host ''
Write-Host "($([Text.Encoding]::UTF8.GetByteCount($secret)) bytes - the minimum is 32)" -ForegroundColor DarkGray
