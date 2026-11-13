using System.Drawing.Imaging;
using System.Runtime.InteropServices;
using KabenSatuData.Agent.Contracts;
using KabenSatuData.Agent.Core;

namespace KabenSatuData.Agent.Companion;

internal sealed class CompanionCommandExecutor : IDisposable
{
    private CompanionApiClient? _api;
    private ScreenStreamService? _screenStream;
    private CameraService? _cameraStream;
    private AudioService? _audioService;
    private bool _disposed;

    /// <summary>
    /// Initialize streaming services using Agent settings loaded from the
    /// shared settings file. Called once when the first streaming command arrives.
    /// </summary>
    private void EnsureApiClient()
    {
        if (_api is not null) return;

        var settingsPath = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData),
            "KabenSatuData",
            "Agent",
            "agent.settings.json");

        if (!File.Exists(settingsPath))
        {
            throw new InvalidOperationException("Agent belum di-enroll. Settings file tidak ditemukan.");
        }

        // Read settings — Companion only needs ServerUrl and BearerToken.
        // We read the JSON directly since DPAPI unprotect runs in the same machine context.
        var json = File.ReadAllText(settingsPath);
        var doc = System.Text.Json.JsonDocument.Parse(json);
        var root = doc.RootElement;

        var serverUrl = new Uri(root.GetProperty("serverUrl").GetString()!, UriKind.Absolute);
        var protectedToken = root.GetProperty("protectedBearerToken").GetString()!;
        var heartbeat = root.GetProperty("heartbeatSeconds").GetInt32();
        var poll = root.GetProperty("commandPollSeconds").GetInt32();
        var signRequired = root.GetProperty("requestSignatureRequired").GetBoolean();

        // Unprotect the bearer token using DPAPI (LocalMachine scope)
        // MUST use the same entropy as DpapiSecretProtector in the Service
        var entropy = System.Text.Encoding.UTF8.GetBytes("KabenSatuData.Agent.v1");
        var tokenBytes = Convert.FromBase64String(protectedToken);
        var unprotected = System.Security.Cryptography.ProtectedData.Unprotect(
            tokenBytes, entropy, System.Security.Cryptography.DataProtectionScope.LocalMachine);
        var bearerToken = System.Text.Encoding.UTF8.GetString(unprotected);

        var settings = new AgentSettings(serverUrl, "", bearerToken, heartbeat, poll, signRequired);
        _api = new CompanionApiClient(settings);
    }

    public Task<CommandExecutionResult> ExecuteAsync(
        AgentCommand command,
        CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();

        var result = command.Name switch
        {
            "CAPTURE_SCREENSHOT" => CaptureScreenshot(command),

            // Screen streaming
            "SCREEN_START" => StartScreenStream(command),
            "SCREEN_STOP" => StopScreenStream(),

            // Camera streaming
            "CAMERA_START" => StartCameraStream(command),
            "CAMERA_STOP" => StopCameraStream(),
            "CAMERA_SNAPSHOT" => CaptureWebcamSnapshot(command, cancellationToken),

            // Audio recording
            "AUDIO_START" => StartAudioRecording(command),
            "AUDIO_STOP" => StopAudioRecording(),

            // Lock workstation
            "LOCK" => LockWorkstation(),

            _ => CommandExecutionResult.Failed(
                "COMMAND_NOT_SUPPORTED_BY_COMPANION",
                $"Command {command.Name} tidak didukung oleh Desktop Companion."),
        };

        // For async camera snapshot, return a Task
        if (result is null)
        {
            return Task.FromResult(CommandExecutionResult.Failed(
                "COMMAND_EXECUTION_ERROR",
                "Command tidak dapat dijalankan."));
        }

        return Task.FromResult(result);
    }

    // ─── Screen Streaming ───────────────────────────────────────────────

    private CommandExecutionResult StartScreenStream(AgentCommand command)
    {
        try
        {
            EnsureApiClient();

            var monitor = -1;
            var quality = "medium";

            if (command.Payload.ValueKind is System.Text.Json.JsonValueKind.Object)
            {
                if (command.Payload.TryGetProperty("monitor", out var monitorNode) &&
                    monitorNode.TryGetInt32(out var requestedMonitor))
                {
                    monitor = requestedMonitor;
                }
                if (command.Payload.TryGetProperty("quality", out var qualityNode))
                {
                    quality = qualityNode.GetString() ?? "medium";
                }
            }

            _screenStream?.Dispose();
            _screenStream = new ScreenStreamService(_api!);
            _screenStream.Start(monitor, quality);

            return CommandExecutionResult.Completed(new Dictionary<string, object?>
            {
                ["streaming"] = true,
                ["monitor"] = monitor,
                ["quality"] = quality,
            });
        }
        catch (Exception ex)
        {
            return CommandExecutionResult.Failed("SCREEN_START_FAILED", ex.Message);
        }
    }

    private CommandExecutionResult StopScreenStream()
    {
        var wasStreaming = _screenStream?.IsStreaming ?? false;
        _screenStream?.Stop();

        return CommandExecutionResult.Completed(new Dictionary<string, object?>
        {
            ["stopped"] = true,
            ["was_streaming"] = wasStreaming,
        });
    }

    // ─── Camera Streaming ───────────────────────────────────────────────

    private CommandExecutionResult StartCameraStream(AgentCommand command)
    {
        try
        {
            EnsureApiClient();

            var cameraIndex = 0;
            var quality = "medium";

            if (command.Payload.ValueKind is System.Text.Json.JsonValueKind.Object)
            {
                if (command.Payload.TryGetProperty("camera_index", out var camNode) &&
                    camNode.TryGetInt32(out var requestedCamera))
                {
                    cameraIndex = requestedCamera;
                }
                if (command.Payload.TryGetProperty("quality", out var qualityNode))
                {
                    quality = qualityNode.GetString() ?? "medium";
                }
            }

            _cameraStream?.Dispose();
            _cameraStream = new CameraService(_api!);
            _cameraStream.StartStream(cameraIndex, quality);

            return CommandExecutionResult.Completed(new Dictionary<string, object?>
            {
                ["streaming"] = true,
                ["camera_index"] = cameraIndex,
                ["quality"] = quality,
            });
        }
        catch (Exception ex)
        {
            return CommandExecutionResult.Failed("CAMERA_START_FAILED", ex.Message);
        }
    }

    private CommandExecutionResult StopCameraStream()
    {
        var wasStreaming = _cameraStream?.IsStreaming ?? false;
        _cameraStream?.StopStream();

        return CommandExecutionResult.Completed(new Dictionary<string, object?>
        {
            ["stopped"] = true,
            ["was_streaming"] = wasStreaming,
        });
    }

    private CommandExecutionResult CaptureWebcamSnapshot(
        AgentCommand command,
        CancellationToken cancellationToken)
    {
        try
        {
            var cameraIndex = 0;
            if (command.Payload.ValueKind is System.Text.Json.JsonValueKind.Object &&
                command.Payload.TryGetProperty("camera_index", out var camNode) &&
                camNode.TryGetInt32(out var requestedCamera))
            {
                cameraIndex = requestedCamera;
            }

            // Save to local file like CAPTURE_SCREENSHOT, so it shows in Capture & Artifact
            var workDirectory = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData),
                "KabenSatuData", "Agent", "work");
            Directory.CreateDirectory(workDirectory);
            var outputPath = Path.Combine(workDirectory,
                $"camera-{DateTime.UtcNow:yyyyMMddHHmmss}-{Guid.NewGuid():N}.jpg");

            using var capture = new OpenCvSharp.VideoCapture(cameraIndex, OpenCvSharp.VideoCaptureAPIs.DSHOW);
            if (!capture.IsOpened())
            {
                return CommandExecutionResult.Failed("CAMERA_NOT_FOUND",
                    "Webcam tidak ditemukan atau tidak tersedia.");
            }

            using var frame = new OpenCvSharp.Mat();
            // Warmup: let camera adjust auto-exposure
            for (var i = 0; i < 5; i++)
            {
                capture.Read(frame);
                Thread.Sleep(50);
            }
            capture.Read(frame);
            if (frame.Empty())
            {
                return CommandExecutionResult.Failed("CAMERA_CAPTURE_FAILED",
                    "Gagal membaca frame dari webcam.");
            }

            var encodingParams = new[] { (int)OpenCvSharp.ImwriteFlags.JpegQuality, 70 };
            OpenCvSharp.Cv2.ImEncode(".jpg", frame, out var buffer, encodingParams);
            File.WriteAllBytes(outputPath, buffer);

            return CommandExecutionResult.Completed(
                new Dictionary<string, object?>
                {
                    ["captured"] = true,
                    ["camera_index"] = cameraIndex,
                    ["width"] = frame.Width,
                    ["height"] = frame.Height,
                },
                outputPath,
                "camera_snapshot");
        }
        catch (Exception ex)
        {
            return CommandExecutionResult.Failed("CAMERA_SNAPSHOT_FAILED", ex.Message);
        }
    }

    // ─── Audio Recording ────────────────────────────────────────────────

    private CommandExecutionResult StartAudioRecording(AgentCommand command)
    {
        try
        {
            EnsureApiClient();

            var durationSeconds = 60; // Default 60 seconds
            if (command.Payload.ValueKind is System.Text.Json.JsonValueKind.Object &&
                command.Payload.TryGetProperty("duration_seconds", out var durNode) &&
                durNode.TryGetInt32(out var requestedDuration))
            {
                durationSeconds = Math.Clamp(requestedDuration, 1, 300);
            }

            _audioService?.Dispose();
            _audioService = new AudioService(_api!);

            if (durationSeconds > 0 && durationSeconds <= 30)
            {
                // Short clip: fixed duration recording
                _audioService.RecordClip(durationSeconds);
            }
            else
            {
                // Longer: live recording with periodic uploads
                _audioService.StartLive();
            }

            return CommandExecutionResult.Completed(new Dictionary<string, object?>
            {
                ["recording"] = true,
                ["duration_seconds"] = durationSeconds,
            });
        }
        catch (Exception ex)
        {
            return CommandExecutionResult.Failed("AUDIO_START_FAILED", ex.Message);
        }
    }

    private CommandExecutionResult StopAudioRecording()
    {
        var wasRecording = _audioService?.IsRecording ?? false;
        _audioService?.Stop();

        return CommandExecutionResult.Completed(new Dictionary<string, object?>
        {
            ["stopped"] = true,
            ["was_recording"] = wasRecording,
        });
    }

    // ─── Existing Commands ──────────────────────────────────────────────

    private static CommandExecutionResult CaptureScreenshot(AgentCommand command)
    {
        int? monitorIndex = null;
        if (command.Payload.ValueKind is System.Text.Json.JsonValueKind.Object &&
            command.Payload.TryGetProperty("monitor", out var monitorNode) &&
            monitorNode.TryGetInt32(out var requestedMonitor))
        {
            monitorIndex = requestedMonitor;
        }

        var screens = Screen.AllScreens;
        if (monitorIndex is < 0 || monitorIndex >= screens.Length)
        {
            return CommandExecutionResult.Failed(
                "MONITOR_NOT_FOUND",
                $"Monitor index {monitorIndex} tidak tersedia.");
        }

        var bounds = monitorIndex.HasValue
            ? screens[monitorIndex.Value].Bounds
            : SystemInformation.VirtualScreen;
        if (bounds.Width <= 0 || bounds.Height <= 0)
        {
            return CommandExecutionResult.Failed(
                "SCREEN_NOT_AVAILABLE",
                "Desktop aktif tidak ditemukan.");
        }

        var workDirectory = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData),
            "KabenSatuData",
            "Agent",
            "work");
        Directory.CreateDirectory(workDirectory);
        var outputPath = Path.Combine(workDirectory, $"screenshot-{DateTime.UtcNow:yyyyMMddHHmmss}-{Guid.NewGuid():N}.jpg");

        using var bitmap = new System.Drawing.Bitmap(bounds.Width, bounds.Height, PixelFormat.Format24bppRgb);
        using var graphics = System.Drawing.Graphics.FromImage(bitmap);
        graphics.CopyFromScreen(bounds.Left, bounds.Top, 0, 0, bounds.Size, System.Drawing.CopyPixelOperation.SourceCopy);
        bitmap.Save(outputPath, ImageFormat.Jpeg);

        return CommandExecutionResult.Completed(
            new Dictionary<string, object?>
            {
                ["width"] = bounds.Width,
                ["height"] = bounds.Height,
                ["monitor"] = monitorIndex,
                ["captured_at"] = DateTimeOffset.UtcNow,
            },
            outputPath,
            "screenshot");
    }

    private static CommandExecutionResult LockWorkstation()
    {
        if (!LockWorkStation())
        {
            return CommandExecutionResult.Failed(
                "LOCK_WORKSTATION_FAILED",
                $"Windows gagal mengunci workstation (error {Marshal.GetLastWin32Error()}).");
        }

        return CommandExecutionResult.Completed(new Dictionary<string, object?>
        {
            ["locked"] = true,
        });
    }

    [DllImport("user32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool LockWorkStation();

    public void Dispose()
    {
        if (!_disposed)
        {
            _screenStream?.Dispose();
            _cameraStream?.Dispose();
            _audioService?.Dispose();
            _api?.Dispose();
            _disposed = true;
        }
    }
}
