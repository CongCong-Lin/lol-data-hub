#Requires -Version 5.1
<#
.SYNOPSIS
    Backup MySQL database using running lol-datahub-mysql container.
.DESCRIPTION
    Executes mysqldump via docker exec, using container environment variable MYSQL_ROOT_PASSWORD for authentication.
    Password does not appear in command line or output. Read-only export only.
.PARAMETER OutputDirectory
    Backup output directory, default 'backups'.
.PARAMETER Database
    Database name, default reads from container MYSQL_DATABASE.
.EXAMPLE
    .\scripts\backup-mysql.ps1
    .\scripts\backup-mysql.ps1 -OutputDirectory "D:\backups"
    .\scripts\backup-mysql.ps1 -Database "mydb" -OutputDirectory "backups"
#>
param(
    [string]$OutputDirectory = 'backups',
    [string]$Database = ''
)

$ErrorActionPreference = 'Stop'

# ---------- Constants ----------
$containerName = 'lol-datahub-mysql'

# ---------- Check container status ----------
Write-Host "Checking container $containerName status..." -ForegroundColor Cyan

try {
    $containerState = docker inspect -f '{{.State.Running}}' $containerName 2>&1
    if ($containerState -ne 'true') {
        Write-Host "[ERROR] Container $containerName is not running." -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "[ERROR] Cannot check container status: $_" -ForegroundColor Red
    exit 1
}

# ---------- Get database name ----------
if ([string]::IsNullOrEmpty($Database)) {
    Write-Host "Reading MYSQL_DATABASE from container environment..." -ForegroundColor Cyan
    try {
        $Database = docker exec $containerName printenv MYSQL_DATABASE 2>&1
        if ([string]::IsNullOrEmpty($Database)) {
            Write-Host "[ERROR] Cannot read MYSQL_DATABASE from container." -ForegroundColor Red
            exit 1
        }
        Write-Host "Database: $Database" -ForegroundColor Green
    } catch {
        Write-Host "[ERROR] Cannot read container environment: $_" -ForegroundColor Red
        exit 1
    }
}

if ($Database -notmatch '^[A-Za-z0-9_]+$') {
    Write-Host "[ERROR] Database name may contain only letters, digits, and underscores." -ForegroundColor Red
    exit 1
}

# ---------- Create output directory ----------
if ([System.IO.Path]::IsPathRooted($OutputDirectory)) {
    $outputPath = [System.IO.Path]::GetFullPath($OutputDirectory)
} else {
    $outputPath = [System.IO.Path]::GetFullPath((Join-Path (Join-Path $PSScriptRoot '..') $OutputDirectory))
}

if (-not (Test-Path -Path $outputPath)) {
    Write-Host "Creating backup directory: $outputPath" -ForegroundColor Cyan
    New-Item -ItemType Directory -Path $outputPath -Force | Out-Null
}

# ---------- Generate a unique backup filename ----------
$timestamp = [System.DateTime]::UtcNow.ToString('yyyyMMdd_HHmmss_fff')
$operationId = [guid]::NewGuid().ToString('N')
$backupFile = "${Database}_${timestamp}_$($operationId.Substring(0, 8)).sql"
$backupPath = Join-Path -Path $outputPath -ChildPath $backupFile

# ---------- Execute mysqldump ----------
Write-Host "Backing up database $Database to $backupPath ..." -ForegroundColor Cyan

# The dump is written to a file inside the container and copied byte-for-byte with docker cp.
# Do not redirect mysqldump stdout in Windows PowerShell 5.1: native stdout would be decoded
# and re-encoded as UTF-16, which can silently corrupt a UTF-8 SQL dump.
$tempSh = Join-Path ([System.IO.Path]::GetTempPath()) "backup_${operationId}.sh"
$containerScript = "/tmp/loldatahub_backup_${operationId}.sh"
$containerDump = "/tmp/loldatahub_backup_${operationId}.sql"

try {
    # Write an ASCII shell script without BOM. Database is restricted to [A-Za-z0-9_].
    $dumpScript = "MYSQL_PWD=`"`$MYSQL_ROOT_PASSWORD`" mysqldump -uroot --no-tablespaces --single-transaction --routines --events --triggers --default-character-set=utf8mb4 --result-file=`"${containerDump}`" $Database"
    [System.IO.File]::WriteAllText($tempSh, $dumpScript, [System.Text.ASCIIEncoding]::new())

    $copyScriptOutput = docker cp $tempSh "${containerName}:${containerScript}" 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "docker cp script failed: $($copyScriptOutput -join [Environment]::NewLine)"
    }

    $dumpOutput = docker exec $containerName sh $containerScript 2>&1
    $dumpExitCode = $LASTEXITCODE
    if ($dumpExitCode -ne 0) {
        throw "mysqldump exited with code ${dumpExitCode}: $($dumpOutput -join [Environment]::NewLine)"
    }

    $copyDumpOutput = docker cp "${containerName}:${containerDump}" $backupPath 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "docker cp dump failed: $($copyDumpOutput -join [Environment]::NewLine)"
    }

    $fileInfo = Get-Item -Path $backupPath
    if ($fileInfo.Length -lt 1024) {
        throw "Backup file is too small ($($fileInfo.Length) bytes)."
    }

    $stream = [System.IO.File]::OpenRead($backupPath)
    try {
        $headerBytes = New-Object byte[] ([Math]::Min(512, $fileInfo.Length))
        $readLength = $stream.Read($headerBytes, 0, $headerBytes.Length)
        $header = [System.Text.Encoding]::UTF8.GetString($headerBytes, 0, $readLength)
    } finally {
        $stream.Dispose()
    }
    if ($header -notmatch '(?i)(MySQL|MariaDB).*dump') {
        throw 'Backup header is not a recognizable SQL dump.'
    }

    $sha256 = (Get-FileHash -LiteralPath $backupPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $sizeMB = [math]::Round($fileInfo.Length / 1MB, 2)
    Write-Host "[SUCCESS] Backup completed: $backupPath ($sizeMB MB)" -ForegroundColor Green
    Write-Host "SHA-256: $sha256" -ForegroundColor Green
    exit 0
} catch {
    Write-Host "[ERROR] Backup failed: $_" -ForegroundColor Red
    if (Test-Path -Path $backupPath) {
        Remove-Item -Path $backupPath -Force -ErrorAction SilentlyContinue
    }
    exit 1
} finally {
    docker exec $containerName rm -f $containerScript $containerDump 2>$null | Out-Null
    if (Test-Path -Path $tempSh) {
        Remove-Item -Path $tempSh -Force -ErrorAction SilentlyContinue
    }
}
