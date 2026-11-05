param([string]$OutDir = "$PSScriptRoot", [int]$Iterations = 0)
# SwiftClient now-playing bridge. Reads the Windows system media session (Spotify, Apple Music,
# YouTube Music, browsers) and writes a tiny JSON snapshot plus accepts media commands.
$ErrorActionPreference = 'SilentlyContinue'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
try { Add-Type -AssemblyName System.Drawing } catch { }

# WASAPI loopback bass meter: captures the render endpoint's audio, low-passes to the bass band
# (~150 Hz) and reports a normalised 0-1 level so the client can pulse the screen to the music.
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;

[ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
internal class ScfDeviceEnumerator { }

[ComImport, Guid("A9564D2E-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
internal interface ScfIMMDeviceEnumerator {
    [PreserveSig] int EnumAudioEndpoints(int dataFlow, int stateMask, out IntPtr devices);
    [PreserveSig] int GetDefaultAudioEndpoint(int dataFlow, int role, out IntPtr device);
    [PreserveSig] int GetDevice([MarshalAs(UnmanagedType.LPWStr)] string id, out IntPtr device);
    [PreserveSig] int RegisterEndpointNotificationCallback(IntPtr cb);
    [PreserveSig] int UnregisterEndpointNotificationCallback(IntPtr cb);
}

[ComImport, Guid("D666063F-1587-4E43-81F1-B971E807313F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
internal interface ScfIMMDevice {
    [PreserveSig] int Activate(ref Guid iid, int clsCtx, IntPtr activationParams, [MarshalAs(UnmanagedType.IUnknown)] out object iface);
    [PreserveSig] int OpenPropertyStore(int access, out IntPtr props);
    [PreserveSig] int GetId(out IntPtr id);
    [PreserveSig] int GetState(out int state);
}

[ComImport, Guid("1CB9AD4C-DBFA-4C32-B178-C2F568A703B2"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
internal interface ScfIAudioClient {
    [PreserveSig] int Initialize(int shareMode, uint streamFlags, long bufferDuration, long periodicity, IntPtr format, ref Guid sessionGuid);
    [PreserveSig] int GetBufferSize(out uint bufferSize);
    [PreserveSig] int GetStreamLatency(out long latency);
    [PreserveSig] int GetCurrentPadding(out int padding);
    [PreserveSig] int IsFormatSupported(int mode, IntPtr format, out IntPtr closest);
    [PreserveSig] int GetMixFormat(out IntPtr format);
    [PreserveSig] int GetDevicePeriod(out long defaultPeriod, out long minimumPeriod);
    [PreserveSig] int Start();
    [PreserveSig] int Stop();
    [PreserveSig] int Reset();
    [PreserveSig] int SetEventHandle(IntPtr eventHandle);
    [PreserveSig] int GetService(ref Guid iid, [MarshalAs(UnmanagedType.IUnknown)] out object service);
}

[ComImport, Guid("C8ADBD64-E71E-48A0-A4DE-185C395CD317"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
internal interface ScfIAudioCaptureClient {
    [PreserveSig] int GetBuffer(out IntPtr data, out int numFrames, out uint flags, out long devicePosition, out long qpcPosition);
    [PreserveSig] int ReleaseBuffer(int numFrames);
    [PreserveSig] int GetNextPacketSize(out int numFrames);
}

public class ScfBassMeter {
    ScfIAudioClient client;
    ScfIAudioCaptureClient capture;
    int channels = 2;
    bool isFloat = true;
    double bassState;
    double last;

    public bool Start() {
        try {
            var en = (ScfIMMDeviceEnumerator)(object)new ScfDeviceEnumerator();
            IntPtr devPtr;
            if (en.GetDefaultAudioEndpoint(0, 1, out devPtr) != 0) return false;
            ScfIMMDevice dev = (ScfIMMDevice)Marshal.GetObjectForIUnknown(devPtr);
            Guid clientId = typeof(ScfIAudioClient).GUID;
            object obj;
            if (dev.Activate(ref clientId, 1, IntPtr.Zero, out obj) != 0) return false;
            client = (ScfIAudioClient)obj;
            IntPtr fmt;
            if (client.GetMixFormat(out fmt) != 0) return false;
            short tag = Marshal.ReadInt16(fmt, 0);
            channels = Marshal.ReadInt16(fmt, 2);
            if (channels < 1) channels = 1;
            if (tag == 3) isFloat = true;
            else if (tag == -2) isFloat = (Marshal.ReadInt32(fmt, 24) == 3);
            else isFloat = false;
            Guid session = Guid.Empty;
            if (client.Initialize(0, 0x2, 100000, 0, fmt, ref session) != 0) return false;
            Guid capId = typeof(ScfIAudioCaptureClient).GUID;
            object capObj;
            if (client.GetService(ref capId, out capObj) != 0) return false;
            capture = (ScfIAudioCaptureClient)capObj;
            if (client.Start() != 0) return false;
            return true;
        } catch { return false; }
    }

    public double Read() {
        double sum = 0; int n = 0;
        try {
            int size;
            capture.GetNextPacketSize(out size);
            int guard = 0;
            while (size > 0 && guard++ < 32) {
                IntPtr data; int frames; uint flags; long dp; long qp;
                if (capture.GetBuffer(out data, out frames, out flags, out dp, out qp) != 0) break;
                int total = frames * channels;
                if (isFloat) {
                    float[] buf = new float[total];
                    Marshal.Copy(data, buf, 0, total);
                    for (int i = 0; i < frames; i++) {
                        double s = 0;
                        for (int c = 0; c < channels; c++) s += buf[i * channels + c];
                        s /= channels;
                        bassState += 0.02 * (s - bassState);
                        sum += bassState * bassState; n++;
                    }
                } else {
                    short[] buf = new short[total];
                    Marshal.Copy(data, buf, 0, total);
                    for (int i = 0; i < frames; i++) {
                        double s = 0;
                        for (int c = 0; c < channels; c++) s += buf[i * channels + c] / 32768.0;
                        s /= channels;
                        bassState += 0.02 * (s - bassState);
                        sum += bassState * bassState; n++;
                    }
                }
                capture.ReleaseBuffer(frames);
                capture.GetNextPacketSize(out size);
            }
        } catch { }
        if (n > 0) {
            double rms = Math.Sqrt(sum / n);
            double v = Math.Min(1.0, rms * 7.0);
            if (v > last) last = v; else last += (v - last) * 0.25;
        } else {
            last *= 0.85;
        }
        return last;
    }
}
'@

$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
  $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]
function Await($WinRtTask, $ResultType) {
  $asTask = $asTaskGeneric.MakeGenericMethod($ResultType)
  $netTask = $asTask.Invoke($null, @($WinRtTask))
  # Never block forever: a stalled SMTC call would freeze the whole loop, stop the JSON
  # from updating, and make the in-game lyrics vanish. Time out and let the caller retry.
  if (-not $netTask.Wait(2000)) { throw [System.TimeoutException]::new('SMTC call timed out') }
  $netTask.Result
}

function Clean-Artist($artist, $title) {
  if ([string]::IsNullOrWhiteSpace($artist)) { return '' }
  $clean = $artist.Trim()
  $clean = [regex]::Replace($clean, '\s+[-\u2013\u2014]\s+(Single|EP|Album|Deluxe|Remaster(ed)?|Explicit)\s*$', '', 'IgnoreCase')
  if (-not [string]::IsNullOrWhiteSpace($title)) {
    $clean = [regex]::Replace($clean, '\s+[-\u2013\u2014]\s+' + [regex]::Escape($title.Trim()) + '\s*$', '', 'IgnoreCase')
  }
  return $clean.Trim()
}

function Get-ActiveSession($manager) {
  $current = $manager.GetCurrentSession()
  if ($null -ne $current -and $current.GetPlaybackInfo().PlaybackStatus.ToString() -eq 'Playing') { return $current }
  foreach ($session in $manager.GetSessions()) {
    if ($session.GetPlaybackInfo().PlaybackStatus.ToString() -eq 'Playing') { return $session }
  }
  return $current
}

function Get-Accent($bytes) {
  try {
    $stream = [IO.MemoryStream]::new($bytes)
    $bitmap = [System.Drawing.Bitmap]::new($stream)
    $stepX = [Math]::Max(1, [int]($bitmap.Width / 64))
    $stepY = [Math]::Max(1, [int]($bitmap.Height / 64))
    $best = 0
    $bestScore = 0.0
    for ($x = 0; $x -lt $bitmap.Width; $x += $stepX) {
      for ($y = 0; $y -lt $bitmap.Height; $y += $stepY) {
        $c = $bitmap.GetPixel($x, $y)
        $max = [Math]::Max($c.R, [Math]::Max($c.G, $c.B))
        $min = [Math]::Min($c.R, [Math]::Min($c.G, $c.B))
        if ($max -lt 48) { continue }
        $saturation = ($max - $min) / [double]$max
        if ($saturation -lt 0.25) { continue }
        $score = $saturation * (0.55 + 0.45 * ($max / 255.0))
        if ($score -gt $bestScore) {
          $bestScore = $score
          $best = ($c.R -shl 16) -bor ($c.G -shl 8) -bor $c.B
        }
      }
    }
    $bitmap.Dispose()
    $stream.Dispose()
    if ($best -eq 0) { return $null }
    return ('#{0:X6}' -f $best)
  } catch {
    return $null
  }
}

[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null
try {
  $manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
} catch {
  exit 1
}

$jsonPath = Join-Path $OutDir 'nowplaying.json'
$jsonTmp = Join-Path $OutDir 'nowplaying.json.tmp'
$commandPath = Join-Path $OutDir 'media-command.txt'
$lastKey = ''
$lastAccent = $null
$cachedProps = $null
$iteration = 0

$bassMeter = $null
try {
  $bassMeter = New-Object ScfBassMeter
  if (-not $bassMeter.Start()) { $bassMeter = $null }
} catch { $bassMeter = $null }

while ($true) {
  $iteration++
  $bass = 0.0
  if ($null -ne $bassMeter) { try { $bass = [math]::Round($bassMeter.Read(), 3) } catch { } }
  $session = $null
  try { $session = Get-ActiveSession $manager } catch { }

  if ($null -ne $session) {
    $command = $null
    if (Test-Path -LiteralPath $commandPath) {
      try { $command = ([IO.File]::ReadAllText($commandPath)).Trim() } catch { }
      Remove-Item -LiteralPath $commandPath -Force
      if ($command) {
        try {
          switch ($command) {
            'next' { Await ($session.TrySkipNextAsync()) ([bool]) | Out-Null }
            'prev' { Await ($session.TrySkipPreviousAsync()) ([bool]) | Out-Null }
            'toggle' { Await ($session.TryTogglePlayPauseAsync()) ([bool]) | Out-Null }
          }
        } catch { }
      }
    }
  }

  try {
    if ($null -eq $session) {
      $lastKey = ''
      $lastAccent = $null
      $object = @{
        active = $false
        title = ''
        artist = ''
        album = ''
        app = ''
        playing = $false
        status = 'Stopped'
        positionMs = 0
        durationMs = 0
        accent = $null
        bass = 0
        generatedAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
      }
    } else {
      # Properties is the only async call in the hot path; if it stalls, fall back to the last
      # good metadata so the timeline below still updates and the JSON never goes stale.
      $properties = $null
      try {
        $properties = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
        $cachedProps = $properties
      } catch {
        $properties = $cachedProps
      }
      $timeline = $session.GetTimelineProperties()
      $playback = $session.GetPlaybackInfo()
      $title = if ($null -ne $properties) { [string]$properties.Title } else { '' }
      $albumTitle = if ($null -ne $properties) { [string]$properties.AlbumTitle } else { '' }
      $artistRaw = if ($null -ne $properties) {
        if ([string]::IsNullOrWhiteSpace([string]$properties.Artist)) { [string]$properties.AlbumArtist } else { [string]$properties.Artist }
      } else { '' }
      $artist = Clean-Artist $artistRaw $title
      $key = "$title|$artist|$albumTitle"
      if ($key -ne $lastKey) {
        $lastKey = $key
        $lastAccent = $null
      }
      if ($null -ne $properties -and $null -eq $lastAccent -and $null -ne $properties.Thumbnail) {
        try {
          $thumbnail = Await ($properties.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
          $size = [int]$thumbnail.Size
          if ($size -gt 0 -and $size -lt 6000000) {
            $reader = [Windows.Storage.Streams.DataReader]::new($thumbnail)
            Await ($reader.LoadAsync([uint32]$size)) ([uint32]) | Out-Null
            $bytes = New-Object byte[] $size
            $reader.ReadBytes($bytes)
            $reader.Dispose()
            $thumbnail.Dispose()
            $lastAccent = Get-Accent $bytes
          }
        } catch { }
      }
      $duration = [long]($timeline.EndTime - $timeline.StartTime).TotalMilliseconds
      $playing = ($playback.PlaybackStatus.ToString() -eq 'Playing')
      $position = [long]$timeline.Position.TotalMilliseconds
      if ($playing) {
        # The session timeline is a snapshot from LastUpdatedTime; add the time since then so
        # lyrics line up with what is audible instead of trailing behind the song. The cap only
        # guards against a dead timeline; normal playback updates LastUpdatedTime far more often,
        # so a low cap froze the position and made lyrics lag until the next SMTC update.
        try {
          $elapsed = ([DateTimeOffset]::UtcNow - $timeline.LastUpdatedTime).TotalMilliseconds
          if ($elapsed -gt 0 -and $elapsed -lt 30000) { $position += [long]$elapsed }
        } catch { }
      }
      if ($duration -gt 0) {
        if ($position -lt 0) { $position = 0 }
        if ($position -gt $duration) { $position = $duration }
      }
      $object = @{
        active = $true
        title = $title
        artist = $artist
        album = $albumTitle
        app = [string]$session.SourceAppUserModelId
        playing = $playing
        status = $playback.PlaybackStatus.ToString()
        positionMs = $position
        durationMs = $duration
        accent = $lastAccent
        bass = $bass
        generatedAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
      }
    }
    $json = $object | ConvertTo-Json -Compress -Depth 3
    [IO.File]::WriteAllText($jsonTmp, $json, (New-Object Text.UTF8Encoding($false)))
    Move-Item -LiteralPath $jsonTmp -Destination $jsonPath -Force
  } catch { }

  if ($Iterations -gt 0 -and $iteration -ge $Iterations) { break }
  Start-Sleep -Milliseconds 40
}
