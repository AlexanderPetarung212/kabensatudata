using KabenSatuData.Agent.Core;
using Xunit;

namespace KabenSatuData.Agent.Tests;

public sealed class AgentSettingsStoreTests
{
    [Fact]
    public async Task Store_round_trips_settings_without_plain_token_on_disk()
    {
        var directory = Path.Combine(Path.GetTempPath(), "ksd-agent-tests", Guid.NewGuid().ToString("N"));
        var path = Path.Combine(directory, "settings.json");
        var store = new AgentSettingsStore(path, new ReverseProtector());
        var expected = new AgentSettings(
            new Uri("https://example.test"),
            Guid.NewGuid().ToString(),
            "device.secret-value",
            60,
            5,
            true);

        await store.SaveAsync(expected);
        var raw = await File.ReadAllTextAsync(path);
        var actual = await store.LoadAsync();

        Assert.DoesNotContain(expected.BearerToken, raw);
        Assert.Equal(expected, actual);
    }

    private sealed class ReverseProtector : ISecretProtector
    {
        public string Protect(string plainText) => new(plainText.Reverse().ToArray());
        public string Unprotect(string protectedText) => new(protectedText.Reverse().ToArray());
    }
}
