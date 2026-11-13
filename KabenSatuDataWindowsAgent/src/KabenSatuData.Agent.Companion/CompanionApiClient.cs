using System.Net.Http.Headers;
using System.Text;
using KabenSatuData.Agent.Core;

namespace KabenSatuData.Agent.Companion;

/// <summary>
/// Lightweight HTTP client for the Companion to upload streaming data
/// (screen frames, camera frames, audio) directly to the Agent API,
/// bypassing the Service → Named Pipe bridge for performance.
/// </summary>
internal sealed class CompanionApiClient : IDisposable
{
    private readonly HttpClient _http;
    private readonly AgentSettings _settings;
    private readonly string _secret;
    private bool _disposed;

    public CompanionApiClient(AgentSettings settings)
    {
        _settings = settings;
        _secret = settings.BearerToken.Split('.', 2) is [_, var secret]
            ? secret
            : throw new InvalidDataException("Format bearer token Agent tidak valid.");
        _http = new HttpClient { Timeout = TimeSpan.FromSeconds(30) };
    }

    /// <summary>
    /// Upload a JPEG frame (screen capture or camera) to the server.
    /// </summary>
    public async Task<bool> UploadFrameAsync(
        byte[] jpegData,
        string frameType,
        CancellationToken cancellationToken)
    {
        try
        {
            var content = new ByteArrayContent(jpegData);
            content.Headers.ContentType = new MediaTypeHeaderValue("image/jpeg");

            using var request = CreateRequest(
                HttpMethod.Post,
                "/api/agent/v1/stream/frame",
                content);
            request.Headers.Add("X-Frame-Type", frameType);

            using var response = await _http.SendAsync(request, cancellationToken);
            return response.IsSuccessStatusCode;
        }
        catch (Exception)
        {
            return false;
        }
    }

    /// <summary>
    /// Upload an audio file (AAC recording) to the server.
    /// </summary>
    public async Task<bool> UploadAudioAsync(
        string filePath,
        string audioType,
        CancellationToken cancellationToken)
    {
        try
        {
            var boundary = "ksd-" + Guid.NewGuid().ToString("N");
            using var multipart = new MultipartFormDataContent(boundary);
            multipart.Add(new StringContent(audioType, Encoding.UTF8), "type");

            var bytes = await File.ReadAllBytesAsync(filePath, cancellationToken);
            var fileContent = new ByteArrayContent(bytes);
            fileContent.Headers.ContentType = new MediaTypeHeaderValue("audio/aac");
            multipart.Add(fileContent, "audio", Path.GetFileName(filePath));

            using var request = CreateRequest(
                HttpMethod.Post,
                "/api/agent/v1/stream/audio",
                multipart);

            using var response = await _http.SendAsync(request, cancellationToken);
            return response.IsSuccessStatusCode;
        }
        catch (Exception)
        {
            return false;
        }
    }

    /// <summary>
    /// Upload file scan results to the server.
    /// </summary>
    public async Task<bool> UploadFileScanAsync(
        string path,
        IReadOnlyList<object> files,
        CancellationToken cancellationToken)
    {
        try
        {
            var payload = new { path, files };
            var json = System.Text.Json.JsonSerializer.Serialize(payload);
            var content = new StringContent(json, Encoding.UTF8, "application/json");

            using var request = CreateRequest(
                HttpMethod.Post,
                "/api/agent/v1/stream/file-scan",
                content);

            using var response = await _http.SendAsync(request, cancellationToken);
            return response.IsSuccessStatusCode;
        }
        catch (Exception)
        {
            return false;
        }
    }

    /// <summary>
    /// Upload a file from the local filesystem to the server.
    /// </summary>
    public async Task<bool> UploadFileContentAsync(
        string filePath,
        CancellationToken cancellationToken)
    {
        try
        {
            var boundary = "ksd-" + Guid.NewGuid().ToString("N");
            using var multipart = new MultipartFormDataContent(boundary);
            multipart.Add(new StringContent(filePath, Encoding.UTF8), "file_path");

            var bytes = await File.ReadAllBytesAsync(filePath, cancellationToken);
            var fileContent = new ByteArrayContent(bytes);
            fileContent.Headers.ContentType = new MediaTypeHeaderValue("application/octet-stream");
            multipart.Add(fileContent, "file", Path.GetFileName(filePath));

            using var request = CreateRequest(
                HttpMethod.Post,
                "/api/agent/v1/stream/file",
                multipart);

            using var response = await _http.SendAsync(request, cancellationToken);
            return response.IsSuccessStatusCode;
        }
        catch (Exception)
        {
            return false;
        }
    }

    private HttpRequestMessage CreateRequest(
        HttpMethod method,
        string pathAndQuery,
        HttpContent? content)
    {
        var uri = new Uri(_settings.ServerUrl, pathAndQuery);
        var request = new HttpRequestMessage(method, uri);
        request.Content = content;
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", _settings.BearerToken);
        request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));

        if (_settings.RequestSignatureRequired && content is not null)
        {
            var body = content.ReadAsByteArrayAsync().GetAwaiter().GetResult();
            var timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
            var nonce = AgentRequestSigner.CreateNonce();
            var signature = AgentRequestSigner.Sign(
                method, uri.AbsolutePath, body, _secret, timestamp, nonce);

            request.Headers.Add("X-Device-Timestamp",
                timestamp.ToString(System.Globalization.CultureInfo.InvariantCulture));
            request.Headers.Add("X-Device-Nonce", nonce);
            request.Headers.Add("X-Device-Signature", signature);
        }

        return request;
    }

    public void Dispose()
    {
        if (!_disposed)
        {
            _http.Dispose();
            _disposed = true;
        }
    }
}
