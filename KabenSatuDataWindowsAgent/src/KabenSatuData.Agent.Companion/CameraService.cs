using OpenCvSharp;

namespace KabenSatuData.Agent.Companion;

/// <summary>
/// Captures frames from a webcam using OpenCvSharp4 and uploads them to the server.
/// Supports camera enumeration, snapshot, and continuous streaming.
/// </summary>
internal sealed class CameraService : IDisposable
{
    /// <summary>Maximum session duration (auto-stop).</summary>
    private static readonly TimeSpan MaxSessionDuration = TimeSpan.FromMinutes(5);

    /// <summary>Delay between frames (~8fps for cPanel hosting).</summary>
    private static readonly TimeSpan FrameInterval = TimeSpan.FromMilliseconds(125);

    private readonly CompanionApiClient _api;
    private CancellationTokenSource? _cts;
    private Task? _captureTask;
    private int _cameraIndex;
    private int _quality = 50;
    private bool _disposed;

    public bool IsStreaming => _captureTask is { IsCompleted: false };

    public CameraService(CompanionApiClient api)
    {
        _api = api;
    }

    /// <summary>
    /// Start webcam streaming loop.
    /// </summary>
    public void StartStream(int cameraIndex = 0, string quality = "medium")
    {
        if (IsStreaming)
        {
            return;
        }

        _cameraIndex = cameraIndex;
        _quality = quality switch
        {
            "low" => 30,
            "high" => 80,
            _ => 50,
        };
        _cts = new CancellationTokenSource();
        _captureTask = Task.Run(() => StreamLoopAsync(_cts.Token));
    }

    /// <summary>
    /// Stop webcam streaming.
    /// </summary>
    public void StopStream()
    {
        _cts?.Cancel();
        _cts?.Dispose();
        _cts = null;
    }

    /// <summary>
    /// Capture a single snapshot from the webcam and upload it.
    /// Returns true if successful.
    /// </summary>
    public async Task<bool> CaptureSnapshotAsync(
        int cameraIndex,
        CancellationToken cancellationToken)
    {
        try
        {
            using var capture = new VideoCapture(cameraIndex, VideoCaptureAPIs.DSHOW);
            if (!capture.IsOpened())
            {
                return false;
            }

            using var frame = new Mat();
            // Read a few frames to let the camera adjust auto-exposure
            for (var warmup = 0; warmup < 5; warmup++)
            {
                capture.Read(frame);
                await Task.Delay(50, cancellationToken);
            }

            capture.Read(frame);
            if (frame.Empty())
            {
                return false;
            }

            var jpeg = FrameToJpeg(frame, 70);
            return await _api.UploadFrameAsync(jpeg, "snapshot", cancellationToken);
        }
        catch (Exception)
        {
            return false;
        }
    }

    private async Task StreamLoopAsync(CancellationToken cancellationToken)
    {
        var sessionStart = DateTimeOffset.UtcNow;

        using var capture = new VideoCapture(_cameraIndex, VideoCaptureAPIs.DSHOW);
        if (!capture.IsOpened())
        {
            return;
        }

        // Set reasonable resolution for streaming
        capture.Set(VideoCaptureProperties.FrameWidth, 640);
        capture.Set(VideoCaptureProperties.FrameHeight, 480);

        using var frame = new Mat();

        while (!cancellationToken.IsCancellationRequested)
        {
            if (DateTimeOffset.UtcNow - sessionStart >= MaxSessionDuration)
            {
                break;
            }

            try
            {
                capture.Read(frame);
                if (frame.Empty())
                {
                    await Task.Delay(FrameInterval, cancellationToken);
                    continue;
                }

                var jpeg = FrameToJpeg(frame, _quality);
                await _api.UploadFrameAsync(jpeg, "camera", cancellationToken);
            }
            catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
            {
                break;
            }
            catch (Exception)
            {
                // Frame capture/upload failed — retry after a short delay.
            }

            await Task.Delay(FrameInterval, cancellationToken);
        }
    }

    private static byte[] FrameToJpeg(Mat frame, int quality)
    {
        var encodingParams = new[] { (int)ImwriteFlags.JpegQuality, quality };
        Cv2.ImEncode(".jpg", frame, out var buffer, encodingParams);
        return buffer;
    }

    public void Dispose()
    {
        if (!_disposed)
        {
            StopStream();
            _disposed = true;
        }
    }
}
