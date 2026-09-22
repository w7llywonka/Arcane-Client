# Arcane's opt-in local Spotify metadata bridge for Windows PowerShell 5.1.
# Read-only SMTC calls; no credentials, network, artwork or playback controls.
# https://learn.microsoft.com/en-us/uwp/api/windows.media.control.globalsystemmediatransportcontrolssessionmanager
# https://learn.microsoft.com/en-us/uwp/api/windows.media.control.globalsystemmediatransportcontrolssessiontimelineproperties
# https://learn.microsoft.com/en-us/dotnet/api/system.windowsruntimesystemextensions.astask
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$InformationPreference = 'SilentlyContinue'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)

function Write-BridgeResult {
    param([hashtable]$Record)
    [Console]::Out.WriteLine(($Record | ConvertTo-Json -Compress -Depth 3))
    [Console]::Out.Flush()
}

function Short-Text {
    param([object]$Value, [int]$Limit)
    $text = [string]$Value
    $text = $text -replace '[\x00-\x1F\x7F]', ' '
    if ($text.Length -gt $Limit) { $text = $text.Substring(0, $Limit) }
    return $text
}

function Read-WinRtResult {
    param([object]$Operation, [type]$ResultType)
    $method = $script:asTaskMethod.MakeGenericMethod([type[]]@($ResultType))
    $task = $method.Invoke($null, [object[]]@($Operation))
    if (-not $task.Wait(4500)) {
        try { $Operation.Cancel() } catch { }
        throw [TimeoutException]::new('Windows media operation timed out')
    }
    return $task.Result
}

function Test-SpotifySource {
    param([string]$Source)
    return $Source -ieq 'Spotify' -or $Source -ieq 'Spotify.exe' -or
        $Source -match '(^|[\\/])Spotify\.exe$' -or $Source -like 'SpotifyAB.SpotifyMusic_*!*'
}

try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
    $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
    $script:asTaskMethod = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.IsGenericMethodDefinition -and
        $_.GetGenericArguments().Length -eq 1 -and $_.GetParameters().Length -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    } | Select-Object -First 1
    if ($null -eq $script:asTaskMethod) { throw 'WinRT task adapter unavailable' }
    $manager = Read-WinRtResult ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
} catch {
    $reason = 'api_unavailable'
    if ($_.Exception -is [TimeoutException]) { $reason = 'timed_out' }
    elseif ($_.Exception -is [UnauthorizedAccessException]) { $reason = 'access_denied' }
    Write-BridgeResult @{ status = 'error'; reason = $reason }
    exit 1
}

while ($true) {
    try {
        $spotify = $null
        $seen = 0
        foreach ($session in $manager.GetSessions()) {
            $seen++
            if ($seen -gt 64) { break }
            if (-not (Test-SpotifySource ([string]$session.SourceAppUserModelId))) { continue }
            if ($null -eq $spotify) { $spotify = $session }
            if ([string]$session.GetPlaybackInfo().PlaybackStatus -eq 'Playing') {
                $spotify = $session
                break
            }
        }
        if ($null -eq $spotify) {
            Write-BridgeResult @{ status = 'no_session'; title = ''; artist = ''; playback = 'Closed'; position = 0; duration = 0 }
        } else {
            $media = Read-WinRtResult ($spotify.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
            $playback = $spotify.GetPlaybackInfo()
            $state = [string]$playback.PlaybackStatus
            $timeline = $spotify.GetTimelineProperties()
            $duration = [Math]::Max(0.0, ($timeline.EndTime - $timeline.StartTime).TotalMilliseconds)
            $position = [Math]::Max(0.0, ($timeline.Position - $timeline.StartTime).TotalMilliseconds)
            if ($state -eq 'Playing' -and $duration -gt 0) {
                # SMTC Position is current at LastUpdatedTime, not at this query.
                $updated = [DateTimeOffset]$timeline.LastUpdatedTime
                $age = [Math]::Max(0.0, ([DateTimeOffset]::UtcNow - $updated).TotalMilliseconds)
                $rate = 1.0
                if ($null -ne $playback.PlaybackRate -and $playback.PlaybackRate -gt 0 -and $playback.PlaybackRate -le 4) {
                    $rate = [double]$playback.PlaybackRate
                }
                $position += $age * $rate
            }
            $duration = [Math]::Min(604800000.0, $duration)
            if ($duration -gt 0) { $position = [Math]::Min($position, $duration) }
            else { $position = [Math]::Min(604800000.0, $position) }
            Write-BridgeResult @{
                status = 'ok'
                title = (Short-Text $media.Title 256)
                artist = (Short-Text $media.Artist 192)
                playback = $state
                position = [long][Math]::Round($position)
                duration = [long][Math]::Round($duration)
            }
        }
    } catch {
        $reason = 'read_failed'
        if ($_.Exception -is [TimeoutException]) { $reason = 'timed_out' }
        elseif ($_.Exception -is [UnauthorizedAccessException]) { $reason = 'access_denied' }
        Write-BridgeResult @{ status = 'error'; reason = $reason }
        exit 1
    }
    Start-Sleep -Milliseconds 2000
}
