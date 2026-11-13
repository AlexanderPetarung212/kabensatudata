using System.Text;
using KabenSatuData.Agent.Core;
using Xunit;

namespace KabenSatuData.Agent.Tests;

public sealed class AgentRequestSignerTests
{
    [Fact]
    public void Sign_creates_stable_lowercase_hmac()
    {
        var signature = AgentRequestSigner.Sign(
            HttpMethod.Post,
            "/api/agent/v1/heartbeat",
            Encoding.UTF8.GetBytes("{\"uptime_seconds\":10}"),
            "secret-value",
            1_700_000_000,
            "nonce-value-123456");

        Assert.Equal(64, signature.Length);
        Assert.Equal(signature.ToLowerInvariant(), signature);
        Assert.Equal(
            signature,
            AgentRequestSigner.Sign(
                HttpMethod.Post,
                "/api/agent/v1/heartbeat",
                Encoding.UTF8.GetBytes("{\"uptime_seconds\":10}"),
                "secret-value",
                1_700_000_000,
                "nonce-value-123456"));
    }

    [Fact]
    public void Sign_changes_when_body_changes()
    {
        var first = AgentRequestSigner.Sign(
            HttpMethod.Post,
            "/api/agent/v1/heartbeat",
            Encoding.UTF8.GetBytes("{}"),
            "secret",
            1,
            "nonce-1234567890");
        var second = AgentRequestSigner.Sign(
            HttpMethod.Post,
            "/api/agent/v1/heartbeat",
            Encoding.UTF8.GetBytes("{\"changed\":true}"),
            "secret",
            1,
            "nonce-1234567890");

        Assert.NotEqual(first, second);
    }
}
