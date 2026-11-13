using System.Text.Json;
using System.Text.Json.Serialization;

namespace KabenSatuData.Agent.Contracts;

public sealed record AgentCommand(
    [property: JsonPropertyName("uuid")] string Uuid,
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("version")] int Version,
    [property: JsonPropertyName("executor")] string Executor,
    [property: JsonPropertyName("payload")] JsonElement Payload,
    [property: JsonPropertyName("sensitive")] bool Sensitive,
    [property: JsonPropertyName("reason")] string? Reason,
    [property: JsonPropertyName("expires_at")] DateTimeOffset ExpiresAt,
    [property: JsonPropertyName("sent_at")] DateTimeOffset? SentAt);

public sealed record CommandExecutionResult(
    bool Success,
    IReadOnlyDictionary<string, object?> Result,
    string? ErrorCode = null,
    string? ErrorMessage = null,
    string? ArtifactPath = null,
    string? ArtifactType = null)
{
    public static CommandExecutionResult Completed(
        IReadOnlyDictionary<string, object?>? result = null,
        string? artifactPath = null,
        string? artifactType = null) =>
        new(true, result ?? new Dictionary<string, object?>(), ArtifactPath: artifactPath, ArtifactType: artifactType);

    public static CommandExecutionResult Failed(string code, string message) =>
        new(false, new Dictionary<string, object?>(), code, message);
}

public sealed record CompanionRequest(AgentCommand Command);

public sealed record CompanionResponse(
    bool Success,
    Dictionary<string, object?> Result,
    string? ErrorCode,
    string? ErrorMessage,
    string? ArtifactPath,
    string? ArtifactType);
