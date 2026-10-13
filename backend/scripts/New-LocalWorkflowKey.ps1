$ErrorActionPreference = 'Stop'

$taskBackendPath = Split-Path -Parent $PSScriptRoot
$taskKeyDirectory = Join-Path $taskBackendPath '.local\keys'
$taskKeyPath = Join-Path $taskKeyDirectory 'workflows-fingerprint.key'

if (Test-Path -LiteralPath $taskKeyPath) {
    if ((Get-Item -LiteralPath $taskKeyPath).Length -lt 32) {
        throw 'Existing workflow fingerprint key must contain at least 32 bytes.'
    }
    Write-Output 'The existing local workflow fingerprint key was preserved.'
    exit 0
}

[System.IO.Directory]::CreateDirectory($taskKeyDirectory) | Out-Null
$taskKeyBytes = New-Object byte[] 32
$taskRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $taskRandom.GetBytes($taskKeyBytes)
} finally {
    $taskRandom.Dispose()
}

$taskKeyFile = [System.IO.File]::Open(
    $taskKeyPath,
    [System.IO.FileMode]::CreateNew,
    [System.IO.FileAccess]::Write,
    [System.IO.FileShare]::None
)
try {
    $taskKeyFile.Write($taskKeyBytes, 0, $taskKeyBytes.Length)
} finally {
    $taskKeyFile.Dispose()
}

Write-Output 'Created backend/.local/keys/workflows-fingerprint.key. The .local directory is ignored by Git.'
