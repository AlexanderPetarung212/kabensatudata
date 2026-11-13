using NAudio.Wave;

namespace KabenSatuData.Agent.Companion;

/// <summary>
/// Records audio from the default microphone using NAudio and uploads
/// recordings to the server. Supports fixed-duration clips and
/// continuous live recording with periodic chunk uploads.
/// </summary>
internal sealed class AudioService : IDisposable
{
    /// <summary>Maximum recording duration.</summary>
    private static readonly TimeSpan MaxRecordingDuration = TimeSpan.FromMinutes(5);

    /// <summary>Chunk interval for live recording uploads.</summary>
    private static readonly TimeSpan LiveChunkInterval = TimeSpan.FromSeconds(10);

    private readonly CompanionApiClient _api;
    private CancellationTokenSource? _cts;
    private Task? _recordingTask;
    private bool _disposed;

    public bool IsRecording => _recordingTask is { IsCompleted: false };

    public AudioService(CompanionApiClient api)
    {
        _api = api;
    }

    /// <summary>
    /// Start continuous recording with periodic uploads.
    /// </summary>
    public void StartLive()
    {
        if (IsRecording)
        {
            return;
        }

        _cts = new CancellationTokenSource();
        _recordingTask = Task.Run(() => LiveRecordLoopAsync(_cts.Token));
    }

    /// <summary>
    /// Record a fixed-duration clip and upload it.
    /// </summary>
    public void RecordClip(int durationSeconds)
    {
        if (IsRecording)
        {
            return;
        }

        var duration = TimeSpan.FromSeconds(Math.Clamp(durationSeconds, 1, 300));
        _cts = new CancellationTokenSource();
        _recordingTask = Task.Run(() => RecordClipAsync(duration, _cts.Token));
    }

    /// <summary>
    /// Stop any active recording.
    /// </summary>
    public void Stop()
    {
        _cts?.Cancel();
        _cts?.Dispose();
        _cts = null;
    }

    private async Task RecordClipAsync(TimeSpan duration, CancellationToken cancellationToken)
    {
        var tempPath = GetTempPath("clip");

        try
        {
            await RecordToFileAsync(tempPath, duration, cancellationToken);

            if (File.Exists(tempPath))
            {
                await _api.UploadAudioAsync(tempPath, "clip", cancellationToken);
            }
        }
        catch (OperationCanceledException)
        {
            // Recording was stopped early — upload whatever we have.
            if (File.Exists(tempPath))
            {
                try
                {
                    await _api.UploadAudioAsync(tempPath, "clip", CancellationToken.None);
                }
                catch (Exception)
                {
                    // Best effort upload of partial recording.
                }
            }
        }
        finally
        {
            TryDeleteFile(tempPath);
        }
    }

    private async Task LiveRecordLoopAsync(CancellationToken cancellationToken)
    {
        var sessionStart = DateTimeOffset.UtcNow;

        while (!cancellationToken.IsCancellationRequested)
        {
            if (DateTimeOffset.UtcNow - sessionStart >= MaxRecordingDuration)
            {
                break;
            }

            var tempPath = GetTempPath("live");

            try
            {
                await RecordToFileAsync(tempPath, LiveChunkInterval, cancellationToken);

                if (File.Exists(tempPath))
                {
                    await _api.UploadAudioAsync(tempPath, "live", cancellationToken);
                }
            }
            catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
            {
                if (File.Exists(tempPath))
                {
                    try
                    {
                        await _api.UploadAudioAsync(tempPath, "live", CancellationToken.None);
                    }
                    catch (Exception)
                    {
                        // Best effort.
                    }
                }
                break;
            }
            catch (Exception)
            {
                // Recording chunk failed — retry next interval.
                await Task.Delay(TimeSpan.FromSeconds(2), cancellationToken);
            }
            finally
            {
                TryDeleteFile(tempPath);
            }
        }
    }

    /// <summary>
    /// Record audio to a WAV file for the specified duration.
    /// WAV is used because it's the most reliable format for NAudio.
    /// The server re-encodes if needed.
    /// </summary>
    private static async Task RecordToFileAsync(
        string outputPath,
        TimeSpan duration,
        CancellationToken cancellationToken)
    {
        if (WaveIn.DeviceCount == 0)
        {
            return;
        }

        using var waveIn = new WaveInEvent
        {
            DeviceNumber = 0,
            WaveFormat = new WaveFormat(16000, 16, 1), // 16kHz mono for bandwidth savings
        };

        await using var writer = new WaveFileWriter(outputPath, waveIn.WaveFormat);
        var tcs = new TaskCompletionSource<bool>();

        waveIn.DataAvailable += (_, e) =>
        {
            if (!cancellationToken.IsCancellationRequested)
            {
                writer.Write(e.Buffer, 0, e.BytesRecorded);
            }
        };

        waveIn.RecordingStopped += (_, _) => tcs.TrySetResult(true);
        waveIn.StartRecording();

        try
        {
            await Task.Delay(duration, cancellationToken);
        }
        catch (OperationCanceledException)
        {
            // Normal cancellation — stop recording gracefully.
        }

        waveIn.StopRecording();
        await tcs.Task;
    }

    private static string GetTempPath(string type)
    {
        var dir = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData),
            "KabenSatuData",
            "Agent",
            "work");
        Directory.CreateDirectory(dir);
        return Path.Combine(dir, $"audio-{type}-{DateTime.UtcNow:yyyyMMddHHmmss}-{Guid.NewGuid():N}.wav");
    }

    private static void TryDeleteFile(string path)
    {
        try
        {
            if (File.Exists(path))
            {
                File.Delete(path);
            }
        }
        catch (Exception)
        {
            // Cleanup is best-effort.
        }
    }

    public void Dispose()
    {
        if (!_disposed)
        {
            Stop();
            _disposed = true;
        }
    }
}
