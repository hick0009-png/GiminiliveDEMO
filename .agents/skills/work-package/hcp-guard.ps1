param(
    [Parameter(Mandatory=$true)]
    [string]$Command,

    [string]$Context = "",

    [string]$Title = "",
    [string]$Objective = "",
    [string]$Completed = "[]",
    [string]$Pending = "[]",
    [string]$Decisions = "[]",
    [string]$Knowledge = "[]",
    [string]$NextSteps = "[]",
    [string]$RecoveryInstruction = "",

    [switch]$CreateAutoCheckpointOnSuccess,
    [switch]$PassThru
)

[Console]::OutputEncoding = [Text.Encoding]::UTF8
$PSDefaultParameterValues['*:Encoding'] = 'utf8'

$checkpointScript = Join-Path $PSScriptRoot "checkpoint.ps1"

# Resolve context description
$contextDesc = if ($Context) { $Context } else { $Command.Trim() }

Write-Output "[hcp-guard] Running: $contextDesc"

# Capture current error count to detect new errors
$beforeErrorCount = $global:Error.Count

try {
    # Execute the command
    $result = Invoke-Expression $Command 2>&1
    $exitCode = $LASTEXITCODE

    # Check $LASTEXITCODE for native commands
    $hadError = $false
    $errorMessage = ""

    if ($exitCode -ne 0) {
        $hadError = $true
        $errorMessage = "Command exited with code $exitCode"
    }

    # Check for new PowerShell errors
    if ($global:Error.Count -gt $beforeErrorCount) {
        $hadError = $true
        if (-not $errorMessage) {
            $errorMessage = $global:Error[0].ToString()
        }
    }

    if ($hadError) {
        Write-Output "[hcp-guard] ERROR detected. Creating error checkpoint..."

        # Build actions before error
        $actions = @()
        if ($Context) { $actions += $Context }

        # Resolve title
        $cpTitle = $Title
        if (-not $cpTitle) { $cpTitle = "Error Recovery - $contextDesc" }

        # Resolve recovery instruction
        $cpRecovery = $RecoveryInstruction
        if (-not $cpRecovery) { $cpRecovery = "Check the error above and retry the command" }

        & $checkpointScript -Title $cpTitle `
            -Objective $Objective `
            -Completed $Completed `
            -Pending $Pending `
            -Decisions $Decisions `
            -Knowledge $Knowledge `
            -NextSteps $NextSteps `
            -OnError `
            -ErrorType "script_execution_error" `
            -ErrorMessage $errorMessage `
            -ToolUsed $contextDesc `
            -ActionsBeforeError ($actions | ConvertTo-Json -Compress) `
            -RecoveryInstruction $cpRecovery

        if ($PassThru) {
            return @{ success = $false; error = $errorMessage; result = $result }
        }
        exit 1
    }

    # Success
    Write-Output "[hcp-guard] Success."

    if ($CreateAutoCheckpointOnSuccess) {
        $cpTitle = $Title
        if (-not $cpTitle) { $cpTitle = $contextDesc }
        & $checkpointScript -Title $cpTitle `
            -Objective $Objective -Completed $Completed -Pending $Pending `
            -Decisions $Decisions -Knowledge $Knowledge -Status "in_progress" -Auto
    }

    if ($PassThru) {
        return @{ success = $true; result = $result }
    }
    return $result

} catch {
    Write-Output "[hcp-guard] EXCEPTION caught: $_"

    $actions = @()
    if ($Context) { $actions += $Context }

    $cpTitle = $Title
    if (-not $cpTitle) { $cpTitle = "Error Recovery - $contextDesc" }

    $cpRecovery = $RecoveryInstruction
    if (-not $cpRecovery) { $cpRecovery = "Investigate the exception and retry after fixing the issue" }

    & $checkpointScript -Title $cpTitle `
        -OnError -ErrorType "exception" -ErrorMessage $_.ToString() -ToolUsed $contextDesc `
        -ActionsBeforeError ($actions | ConvertTo-Json -Compress) `
        -RecoveryInstruction $cpRecovery `
        -ErrorException $_.ToString()

    if ($PassThru) {
        return @{ success = $false; error = $_.ToString() }
    }
    exit 1
}
