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

# ---------- Generate backup filename with UTC timestamp ----------
$timestamp = [System.DateTime]::UtcNow.ToString('yyyyMMdd_HHmmss')
$backupFile = "${Database}_${timestamp}.sql"
$backupPath = Join-Path -Path $outputPath -ChildPath $backupFile

# ---------- Execute mysqldump ----------
Write-Host "Backing up database $Database to $backupPath ..." -ForegroundColor Cyan

# Execute mysqldump inside container:
# - Use MYSQL_PWD env var so the password never appears on the mysqldump command line.
# - Write a temp .sh script (ASCII, no BOM) and docker cp into the container,
#   then execute it. This avoids all PowerShell/native-command quoting and encoding issues.
# --single-transaction: InnoDB consistent read
# --routines: include stored procedures
# --events: include events
# --triggers: include triggers
$tempSh = Join-Path ([System.IO.Path]::GetTempPath()) "backup_$([guid]::NewGuid().ToString('N')).sh"
$errorPath = "$backupPath.stderr"

try {
    # Write dump script as ASCII (no BOM) to temp file
    $dumpScript = "MYSQL_PWD=`"`$MYSQL_ROOT_PASSWORD`" mysqldump -uroot --single-transaction --routines --events --triggers $Database"
    [System.IO.File]::WriteAllText($tempSh, $dumpScript, [System.Text.ASCIIEncoding]::new())

    # Copy script into container and execute
    $containerScript = '/tmp/loldatahub_backup.sh'
    docker cp $tempSh "${containerName}:${containerScript}" 2> $errorPath
    if ($LASTEXITCODE -ne 0) {
        throw "docker cp failed with code ${LASTEXITCODE}"
    }

    docker exec $containerName sh $containerScript > $backupPath 2> $errorPath
    $dumpExitCode = $LASTEXITCODE

    # Cleanup script inside container
    docker exec $containerName rm -f $containerScript 2> $null

    if ($dumpExitCode -ne 0) {
        $errorText = if (Test-Path $errorPath) { (Get-Content $errorPath -Raw).Trim() } else { 'unknown mysqldump error' }
        throw "mysqldump exited with code ${dumpExitCode}: $errorText"
    }

    # Check backup file size
    $fileInfo = Get-Item -Path $backupPath
    if ($fileInfo.Length -lt 1024) {
        Write-Host "[WARNING] Backup file is too small ($($fileInfo.Length) bytes), backup may have failed." -ForegroundColor Yellow
    }

    $sizeMB = [math]::Round($fileInfo.Length / 1MB, 2)
    Write-Host "[SUCCESS] Backup completed: $backupPath ($sizeMB MB)" -ForegroundColor Green
    Remove-Item -Path $errorPath -Force -ErrorAction SilentlyContinue
    exit 0
} catch {
    Write-Host "[ERROR] Backup failed: $_" -ForegroundColor Red
    # Clean up partial files
    if (Test-Path -Path $backupPath) {
        Remove-Item -Path $backupPath -Force -ErrorAction SilentlyContinue
    }
    if (Test-Path -Path $errorPath) {
        Remove-Item -Path $errorPath -Force -ErrorAction SilentlyContinue
    }
    exit 1
} finally {
    # Always cleanup temp file
    if (Test-Path -Path $tempSh) {
        Remove-Item -Path $tempSh -Force -ErrorAction SilentlyContinue
    }
}
