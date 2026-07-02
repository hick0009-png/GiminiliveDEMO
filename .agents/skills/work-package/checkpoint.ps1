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
    [switch]$Lite,
    [string]$ErrorType = "",
    [string]$ErrorMessage = "",
    [string]$ToolUsed = "",
    [string]$ActionsBeforeError = "[]",
    [string]$RecoveryInstruction = "",
    [string]$ErrorException = ""
)

function Convert-JsonArray {
    param([string]$JsonString)
    $list = New-Object System.Collections.ArrayList
    if ($JsonString -and $JsonString -ne "[]" -and $JsonString -ne "") {
        # Auto-replace single quotes with double quotes to support easy CLI inputs
        if ($JsonString -match "'") {
            $JsonString = $JsonString -replace "'", '"'
        }
        try {
            $raw = $JsonString | ConvertFrom-Json
            if ($raw -is [array]) {
                foreach ($item in $raw) { $list.Add($item) | Out-Null }
            } else {
                $list.Add($raw) | Out-Null
            }
        } catch {
            $list.Add($JsonString) | Out-Null
        }
    }
    return ,$list
}

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

# Deserialize JSON array inputs safely using our helper function
$completedArr = Convert-JsonArray -JsonString $Completed
$pendingArr = Convert-JsonArray -JsonString $Pending
$decisionsArr = Convert-JsonArray -JsonString $Decisions
$knowledgeArr = Convert-JsonArray -JsonString $Knowledge
$risksArr = Convert-JsonArray -JsonString $Risks
$nextStepsArr = Convert-JsonArray -JsonString $NextSteps
$actionsBeforeErrorArr = Convert-JsonArray -JsonString $ActionsBeforeError

# Lite schema activation logic
$isLite = $Lite -or ($Auto -and -not $OnError -and $Decisions -eq "[]" -and $Knowledge -eq "[]" -and $Risks -eq "[]")

if ($isLite) {
    $wp = @{
        schema = "hermes-work-package-v1-lite"
        id = $id
        sequence = $seq
        createdAt = $isoNow
        updatedAt = $isoNow
        title = $Title
        status = $Status
        progress = $Progress
        objective = $Objective
        completed = $completedArr
        pending = $pendingArr
        next_steps = $nextStepsArr
        context_summary = $ContextSummary
    }
} else {
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
        completed = $completedArr
        pending = $pendingArr
        artifacts = New-Object System.Collections.ArrayList
        agents = New-Object System.Collections.ArrayList
        decisions = $decisionsArr
        knowledge = $knowledgeArr
        assumptions = New-Object System.Collections.ArrayList
        risks = $risksArr
        next_steps = $nextStepsArr
        dependencies = New-Object System.Collections.ArrayList
        open_questions = New-Object System.Collections.ArrayList
        memory_snapshot = New-Object System.Collections.ArrayList
        context_summary = $ContextSummary
        tags = New-Object System.Collections.ArrayList
    }
}

# Populate error_info for OnError checkpoints
if ($OnError) {
    $errorInfo = @{}
    $errorInfo.error_type = if ($ErrorType) { $ErrorType } else { "unknown" }
    $errorInfo.error_message = if ($ErrorMessage) { $ErrorMessage } else { "No error message captured" }
    $errorInfo.error_timestamp = $isoNow
    $errorInfo.tool_used = if ($ToolUsed) { $ToolUsed } else { "" }
    $errorInfo.actions_before_error = $actionsBeforeErrorArr
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

# Write/update checkpoints/latest.json
$latestPath = Join-Path $checkpointsDir "latest.json"
$latestObj = @{
    latest_checkpoint_id = $id
    latest_checkpoint_path = "checkpoints/$filename"
    timestamp = $isoNow
    status = $Status
    schema = $wp.schema
}
$latestJson = $latestObj | ConvertTo-Json -Depth 5
Set-Content -Path $latestPath -Value $latestJson -Encoding UTF8

# Enforce Eviction Policy (maxCheckpoints)
$configFile = Join-Path $PSScriptRoot "hcp-config.json"
$maxCheckpoints = 50
if (Test-Path $configFile) {
    try {
        $config = Get-Content $configFile -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($config.storage.maxCheckpoints) {
            $maxCheckpoints = [int]$config.storage.maxCheckpoints
        }
    } catch {}
}

# Find all checkpoint files sorted by creation time
$allFiles = Get-ChildItem -Path $checkpointsDir -Filter "wp-*.json" | Sort-Object LastWriteTime
if ($allFiles.Count -gt $maxCheckpoints) {
    $archiveDir = Join-Path $checkpointsDir "archive"
    if (-not (Test-Path $archiveDir)) { New-Item -ItemType Directory -Path $archiveDir -Force | Out-Null }
    
    $evictCount = $allFiles.Count - $maxCheckpoints
    for ($i = 0; $i -lt $evictCount; $i++) {
        $fileToEvict = $allFiles[$i]
        try {
            Move-Item -Path $fileToEvict.FullName -Destination (Join-Path $archiveDir $fileToEvict.Name) -Force | Out-Null
        } catch {
            Write-Warning "Failed to archive checkpoint $($fileToEvict.Name): $_"
        }
    }
}

if ($OnError) {
    Write-Output "[error-checkpoint] $id"
} elseif ($Auto) {
    Write-Output "[auto-checkpoint] $id"
} else {
    Write-Output "Checkpoint saved:"
    Write-Output (Resolve-Path $filePath -Relative).TrimStart('.\')
}
