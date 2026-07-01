param(
    [string]$Title = "",
    [string]$Objective = "",
    [string]$Completed = "[]",
    [string]$Pending = "[]",
    [string]$Decisions = "[]",
    [string]$Knowledge = "[]",
    [string]$Risks = "[]",
    [string]$NextSteps = "[]",
    [string]$ContextSummary = "",
    [string]$Status = "in_progress",
    [int]$Progress = 0,
    [switch]$Auto,
    [switch]$OnError,
    [string]$ErrorType = "",
    [string]$ErrorMessage = "",
    [string]$ToolUsed = "",
    [string]$ActionsBeforeError = "[]",
    [string]$RecoveryInstruction = "",
    [string]$ErrorException = ""
)

$projectRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$checkpointsDir = Join-Path $projectRoot "checkpoints"
if (-not (Test-Path $checkpointsDir)) { New-Item -ItemType Directory -Path $checkpointsDir -Force | Out-Null }

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$isoNow = Get-Date -Format "yyyy-MM-ddTHH:mm:ssK"

# Auto-title for error checkpoints
if ($OnError -and -not $Title) {
    $Title = "Error Recovery"
}
if ($OnError) {
    $Status = "error"
}

$slug = if ($Title) { $Title.ToLower() -replace '[^a-z0-9]+', '-' -replace '^-|-$', '' } else { "checkpoint" }

# Auto sequence: find last checkpoint with same slug and increment
$seq = 1
$existingFiles = Get-ChildItem -Path $checkpointsDir -Filter "wp-$timestamp-*" -ErrorAction SilentlyContinue
$slugPrefix = "wp-$timestamp-$slug"
if ((Get-ChildItem -Path $checkpointsDir -Filter "wp-*$slug*.json" -ErrorAction SilentlyContinue).Count -gt 0) {
    $lastFile = Get-ChildItem -Path $checkpointsDir -Filter "wp-*$slug*.json" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    $baseName = [System.IO.Path]::GetFileNameWithoutExtension($lastFile.Name)
    if ($baseName -match '-(\d+)$') {
        $seq = [int]$Matches[1] + 1
    }
}

$id = "wp-$timestamp-$slug"
$filename = "$id.json"
if ($Auto -or $OnError) {
    $filename = "wp-$timestamp-$slug-$( '{0:D3}' -f $seq ).json"
    $id = "wp-$timestamp-$slug-$seq"
}

$filePath = Join-Path $checkpointsDir $filename

$wp = @{
    schema = "hermes-work-package-v1"
    id = $id
    sequence = $seq
    createdAt = $isoNow
    updatedAt = $isoNow
    title = $Title
    status = $Status
    progress = $Progress
    objective = $Objective
    scope = ""
    completed = if ($Completed -eq "[]") { @() } else { $Completed | ConvertFrom-Json }
    pending = if ($Pending -eq "[]") { @() } else { $Pending | ConvertFrom-Json }
    artifacts = @()
    agents = @()
    decisions = if ($Decisions -eq "[]") { @() } else { $Decisions | ConvertFrom-Json }
    knowledge = if ($Knowledge -eq "[]") { @() } else { $Knowledge | ConvertFrom-Json }
    assumptions = @()
    risks = if ($Risks -eq "[]") { @() } else { $Risks | ConvertFrom-Json }
    next_steps = if ($NextSteps -eq "[]") { @() } else { $NextSteps | ConvertFrom-Json }
    dependencies = @()
    open_questions = @()
    memory_snapshot = @()
    context_summary = $ContextSummary
    tags = @()
}

# Populate error_info for OnError checkpoints
if ($OnError) {
    $errorInfo = @{}
    $errorInfo.error_type = if ($ErrorType) { $ErrorType } else { "unknown" }
    $errorInfo.error_message = if ($ErrorMessage) { $ErrorMessage } else { "No error message captured" }
    $errorInfo.error_timestamp = $isoNow
    $errorInfo.tool_used = if ($ToolUsed) { $ToolUsed } else { "" }
    $errorInfo.actions_before_error = if ($ActionsBeforeError -ne "[]") { $ActionsBeforeError | ConvertFrom-Json } else { @() }
    $errorInfo.recovery_instruction = if ($RecoveryInstruction) { $RecoveryInstruction } else { "Review error and retry with caution" }

    # Capture last PowerShell error if available
    if (-not $ErrorMessage -and $global:Error.Count -gt 0) {
        $lastErr = $global:Error[0]
        if (-not $ErrorException) { $errorInfo.stack_trace = $lastErr.ToString() }
    }
    if ($ErrorException) {
        $errorInfo.stack_trace = $ErrorException.ToString()
    }

    $wp.error_info = $errorInfo
}

$wpJson = $wp | ConvertTo-Json -Depth 10
Set-Content -Path $filePath -Value $wpJson -Encoding UTF8

if ($OnError) {
    Write-Output "[error-checkpoint] $id"
} elseif ($Auto) {
    Write-Output "[auto-checkpoint] $id"
} else {
    Write-Output "Checkpoint saved:"
    Write-Output (Resolve-Path $filePath -Relative).TrimStart('.\')
}
