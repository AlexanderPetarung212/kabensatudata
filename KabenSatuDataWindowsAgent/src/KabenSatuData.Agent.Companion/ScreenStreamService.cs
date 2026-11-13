using System.Drawing;
using System.Drawing.Imaging;

namespace KabenSatuData.Agent.Companion;

/// <summary>
/// Captures the screen in a loop and uploads JPEG frames to the server.
/// Used for live screen streaming from Windows PCs to the Command Center.
/// </summary>
internal sealed class ScreenStreamService : IDisposable
{
    /// <summary>Maximum session duration (auto-stop).</summary>
    private static readonly TimeSpan MaxSessionDuration = TimeSpan.FromMinutes(5);

    /// <summary>Delay between frames (~10fps for cPanel hosting).</summary>
    private static readonly TimeSpan FrameInterval = TimeSpan.FromMilliseconds(100);

    private readonly CompanionApiClient _api;
    private CancellationTokenSource? _cts;
    private Task? _captureTask;
    private int _monitorIndex = -1; // -1 = all screens
    private int _quality = 50; // JPEG quality %
    private bool _disposed;

    public bool IsStreaming => _captureTask is { IsCompleted: false };

    public ScreenStreamService(CompanionApiClient api)
    {
        _api = api;
    }

    /// <summary>
    /// Start screen capture loop.
    /// </summary>
    public void Start(int monitorIndex = -1, string quality = "medium")
    {
        if (IsStreaming)
        {
            return;
        }

        _monitorIndex = monitorIndex;
        _quality = quality switch
        {
            "low" => 30,
            "high" => 80,
            _ => 50,
        };
        _cts = new CancellationTokenSource();
        _captureTask = Task.Run(() => CaptureLoopAsync(_cts.Token));
    }

    /// <summary>
    /// Stop screen capture loop.
    /// </summary>
    public void Stop()
    {
        _cts?.Cancel();
        _cts?.Dispose();
        _cts = null;
    }

    private async Task CaptureLoopAsync(CancellationToken cancellationToken)
    {
        var sessionStart = DateTimeOffset.UtcNow;

        while (!cancellationToken.IsCancellationRequested)
        {
            // Auto-stop after max session duration
            if (DateTimeOffset.UtcNow - sessionStart >= MaxSessionDuration)
            {
                break;
            }

            try
            {
                var jpeg = CaptureScreenAsJpeg();
                if (jpeg is not null)
                {
                    await _api.UploadFrameAsync(jpeg, "screen", cancellationToken);
                }
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

    private byte[]? CaptureScreenAsJpeg()
    {
        var screens = Screen.AllScreens;
        Rectangle bounds;

        if (_monitorIndex >= 0 && _monitorIndex < screens.Length)
        {
            bounds = screens[_monitorIndex].Bounds;
        }
        else
        {
            bounds = SystemInformation.VirtualScreen;
        }

        if (bounds.Width <= 0 || bounds.Height <= 0)
        {
            return null;
        }

        using var bitmap = new Bitmap(bounds.Width, bounds.Height, PixelFormat.Format24bppRgb);
        using var graphics = Graphics.FromImage(bitmap);
        graphics.CopyFromScreen(bounds.Left, bounds.Top, 0, 0, bounds.Size, CopyPixelOperation.SourceCopy);

        // Downscale for bandwidth (cPanel-friendly): max 1280px wide
        using var resized = ResizeIfNeeded(bitmap, 1280);

        using var stream = new MemoryStream();
        var encoder = ImageCodecInfo.GetImageDecoders()
            .FirstOrDefault(c => c.FormatID == ImageFormat.Jpeg.Guid);
        if (encoder is null)
        {
            resized.Save(stream, ImageFormat.Jpeg);
        }
        else
        {
            var encoderParams = new EncoderParameters(1);
            encoderParams.Param[0] = new EncoderParameter(Encoder.Quality, (long)_quality);
            resized.Save(stream, encoder, encoderParams);
        }

        return stream.ToArray();
    }

    private static Bitmap ResizeIfNeeded(Bitmap source, int maxWidth)
    {
        if (source.Width <= maxWidth)
        {
            // Return a clone so the caller can safely dispose the original.
            return (Bitmap)source.Clone();
        }

        var ratio = (double)maxWidth / source.Width;
        var newWidth = maxWidth;
        var newHeight = (int)(source.Height * ratio);

        var resized = new Bitmap(newWidth, newHeight, PixelFormat.Format24bppRgb);
        using var g = Graphics.FromImage(resized);
        g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.Bilinear;
        g.DrawImage(source, 0, 0, newWidth, newHeight);

        return resized;
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
