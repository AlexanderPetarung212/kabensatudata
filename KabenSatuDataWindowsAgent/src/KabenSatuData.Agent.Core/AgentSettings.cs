using System.Text.Json;

namespace KabenSatuData.Agent.Core;

public sealed record AgentSettings(
    Uri ServerUrl,
    string DeviceUuid,
    string BearerToken,
    int HeartbeatSeconds,
    int CommandPollSeconds,
    bool RequestSignatureRequired);

public interface ISecretProtector
{
    string Protect(string plainText);
    string Unprotect(string protectedText);
}

public sealed class AgentSettingsStore
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web)
    {
        WriteIndented = true,
    };

    private readonly string _path;
    private readonly ISecretProtector _protector;

    public AgentSettingsStore(string path, ISecretProtector protector)
    {
        _path = path;
        _protector = protector;
    }

    public bool Exists => File.Exists(_path);

    public async Task SaveAsync(AgentSettings settings, CancellationToken cancellationToken = default)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(_path)!);
        var persisted = new PersistedAgentSettings(
            settings.ServerUrl.ToString(),
            settings.DeviceUuid,
            _protector.Protect(settings.BearerToken),
            settings.HeartbeatSeconds,
            settings.CommandPollSeconds,
            settings.RequestSignatureRequired);
        var json = JsonSerializer.Serialize(persisted, JsonOptions);
        await File.WriteAllTextAsync(_path, json, cancellationToken);
    }

    public async Task<AgentSettings> LoadAsync(CancellationToken cancellationToken = default)
    {
        var json = await File.ReadAllTextAsync(_path, cancellationToken);
        var persisted = JsonSerializer.Deserialize<PersistedAgentSettings>(json, JsonOptions)
            ?? throw new InvalidDataException("Agent settings tidak dapat dibaca.");

        return new AgentSettings(
            new Uri(persisted.ServerUrl, UriKind.Absolute),
            persisted.DeviceUuid,
            _protector.Unprotect(persisted.ProtectedBearerToken),
            persisted.HeartbeatSeconds,
            persisted.CommandPollSeconds,
            persisted.RequestSignatureRequired);
    }

    private sealed record PersistedAgentSettings(
        string ServerUrl,
        string DeviceUuid,
        string ProtectedBearerToken,
        int HeartbeatSeconds,
        int CommandPollSeconds,
        bool RequestSignatureRequired);
}
