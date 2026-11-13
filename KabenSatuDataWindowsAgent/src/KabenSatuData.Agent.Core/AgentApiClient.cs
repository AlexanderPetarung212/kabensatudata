using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
using KabenSatuData.Agent.Contracts;

namespace KabenSatuData.Agent.Core;

public sealed class AgentApiClient
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);
    private readonly HttpClient _http;
    private readonly AgentSettings _settings;
    private readonly string _secret;

    public AgentApiClient(HttpClient http, AgentSettings settings)
    {
        _http = http;
        _settings = settings;
        _secret = settings.BearerToken.Split('.', 2) is [_, var secret]
            ? secret
            : throw new InvalidDataException("Format bearer token Agent tidak valid.");
    }

    public static async Task<AgentSettings> EnrollAsync(
        HttpClient http,
        Uri serverUrl,
        object enrollmentPayload,
        CancellationToken cancellationToken = default)
    {
        var endpoint = new Uri(serverUrl, "/api/agent/v1/enroll");
        using var response = await http.PostAsJsonAsync(endpoint, enrollmentPayload, JsonOptions, cancellationToken);
        var json = await ReadJsonAsync(response, cancellationToken);
        EnsureSuccess(response, json);

        var data = json.RootElement.GetProperty("data");
        var configuration = data.GetProperty("configuration");

        return new AgentSettings(
            serverUrl,
            data.GetProperty("device_uuid").GetString()!,
            data.GetProperty("bearer_token").GetString()!,
            configuration.GetProperty("heartbeat_seconds").GetInt32(),
            configuration.GetProperty("command_poll_seconds").GetInt32(),
            configuration.GetProperty("request_signature_required").GetBoolean());
    }

    public async Task HeartbeatAsync(object payload, CancellationToken cancellationToken)
    {
        using var response = await SendJsonSignedAsync(HttpMethod.Post, "/api/agent/v1/heartbeat", payload, cancellationToken);
        await EnsureSuccessAsync(response, cancellationToken);
    }

    public async Task<AgentCommand?> GetNextCommandAsync(string executor, CancellationToken cancellationToken)
    {
        using var response = await SendSignedAsync(
            HttpMethod.Get,
            $"/api/agent/v1/commands/next?executor={Uri.EscapeDataString(executor)}",
            null,
            cancellationToken);
        var json = await ReadJsonAsync(response, cancellationToken);
        EnsureSuccess(response, json);
        var data = json.RootElement.GetProperty("data");

        return data.ValueKind is JsonValueKind.Null
            ? null
            : data.Deserialize<AgentCommand>(JsonOptions);
    }

    public async Task UpdateCommandAsync(
        string commandUuid,
        string state,
        object? payload,
        CancellationToken cancellationToken)
    {
        using var response = await SendJsonSignedAsync(
            HttpMethod.Post,
            $"/api/agent/v1/commands/{Uri.EscapeDataString(commandUuid)}/{state}",
            payload ?? new { },
            cancellationToken);
        await EnsureSuccessAsync(response, cancellationToken);
    }

    public async Task<string> UploadArtifactAsync(
        string commandUuid,
        string type,
        string filePath,
        CancellationToken cancellationToken)
    {
        var boundary = "ksd-"+Guid.NewGuid().ToString("N");
        using var multipart = new MultipartFormDataContent(boundary);
        multipart.Add(new StringContent(type, Encoding.UTF8), "type");
        multipart.Add(new StringContent(commandUuid, Encoding.UTF8), "command_uuid");

        var bytes = await File.ReadAllBytesAsync(filePath, cancellationToken);
        var fileContent = new ByteArrayContent(bytes);
        fileContent.Headers.ContentType = new MediaTypeHeaderValue(GuessMimeType(filePath));
        multipart.Add(fileContent, "file", Path.GetFileName(filePath));

        using var response = await SendSignedAsync(
            HttpMethod.Post,
            "/api/agent/v1/artifacts",
            multipart,
            cancellationToken);
        var json = await ReadJsonAsync(response, cancellationToken);
        EnsureSuccess(response, json);

        return json.RootElement.GetProperty("data").GetProperty("uuid").GetString()!;
    }

    private async Task<HttpResponseMessage> SendJsonSignedAsync(
        HttpMethod method,
        string path,
        object payload,
        CancellationToken cancellationToken)
    {
        var json = JsonSerializer.Serialize(payload, JsonOptions);
        var content = new StringContent(json, Encoding.UTF8, "application/json");

        return await SendSignedAsync(method, path, content, cancellationToken);
    }

    private async Task<HttpResponseMessage> SendSignedAsync(
        HttpMethod method,
        string pathAndQuery,
        HttpContent? content,
        CancellationToken cancellationToken)
    {
        var uri = new Uri(_settings.ServerUrl, pathAndQuery);
        var request = new HttpRequestMessage(method, uri);
        var body = Array.Empty<byte>();

        if (content is not null)
        {
            body = await content.ReadAsByteArrayAsync(cancellationToken);
            var replayable = new ByteArrayContent(body);
            foreach (var header in content.Headers)
            {
                replayable.Headers.TryAddWithoutValidation(header.Key, header.Value);
            }
            request.Content = replayable;
        }

        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", _settings.BearerToken);
        request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));

        if (_settings.RequestSignatureRequired)
        {
            var timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
            var nonce = AgentRequestSigner.CreateNonce();
            var signature = AgentRequestSigner.Sign(method, uri.AbsolutePath, body, _secret, timestamp, nonce);
            request.Headers.Add("X-Device-Timestamp", timestamp.ToString(System.Globalization.CultureInfo.InvariantCulture));
            request.Headers.Add("X-Device-Nonce", nonce);
            request.Headers.Add("X-Device-Signature", signature);
        }

        return await _http.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
    }

    private static async Task EnsureSuccessAsync(HttpResponseMessage response, CancellationToken cancellationToken)
    {
        var json = await ReadJsonAsync(response, cancellationToken);
        EnsureSuccess(response, json);
    }

    private static async Task<JsonDocument> ReadJsonAsync(HttpResponseMessage response, CancellationToken cancellationToken)
    {
        var content = await response.Content.ReadAsStringAsync(cancellationToken);
        if (string.IsNullOrWhiteSpace(content))
        {
            return JsonDocument.Parse("{}");
        }

        try
        {
            return JsonDocument.Parse(content);
        }
        catch (JsonException)
        {
            throw new AgentApiException(
                $"Server mengembalikan response non-JSON (HTTP {(int)response.StatusCode}).",
                "NON_JSON_RESPONSE",
                (int)response.StatusCode);
        }
    }

    private static void EnsureSuccess(HttpResponseMessage response, JsonDocument json)
    {
        if (response.IsSuccessStatusCode)
        {
            return;
        }

        var root = json.RootElement;
        var message = root.TryGetProperty("message", out var messageNode)
            ? messageNode.GetString()
            : $"HTTP {(int)response.StatusCode}";
        var code = root.TryGetProperty("error_code", out var codeNode)
            ? codeNode.GetString()
            : "HTTP_ERROR";

        // Include validation errors for debugging
        if (root.TryGetProperty("errors", out var errorsNode))
        {
            var details = new System.Text.StringBuilder();
            foreach (var prop in errorsNode.EnumerateObject())
            {
                foreach (var err in prop.Value.EnumerateArray())
                {
                    details.Append($" [{prop.Name}: {err.GetString()}]");
                }
            }
            if (details.Length > 0)
            {
                message = $"{message} ->{details}";
            }
        }

        throw new AgentApiException(message ?? "Request Agent gagal.", code ?? "HTTP_ERROR", (int)response.StatusCode);
    }

    private static string GuessMimeType(string filePath)
    {
        return Path.GetExtension(filePath).ToLowerInvariant() switch
        {
            ".jpg" or ".jpeg" => "image/jpeg",
            ".png" => "image/png",
            ".json" => "application/json",
            ".zip" => "application/zip",
            ".txt" or ".log" => "text/plain",
            ".aac" => "audio/aac",
            ".mp3" => "audio/mpeg",
            ".mp4" => "video/mp4",
            _ => "application/octet-stream",
        };
    }
}

public sealed class AgentApiException : Exception
{
    public AgentApiException(string message, string errorCode, int statusCode)
        : base(message)
    {
        ErrorCode = errorCode;
        StatusCode = statusCode;
    }

    public string ErrorCode { get; }
    public int StatusCode { get; }
}
