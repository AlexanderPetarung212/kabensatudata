using System.Security.Cryptography;
using System.Text;

namespace KabenSatuData.Agent.Core;

public static class AgentRequestSigner
{
    public static string Sign(
        HttpMethod method,
        string absolutePath,
        ReadOnlySpan<byte> body,
        string secret,
        long timestamp,
        string nonce)
    {
        var bodyHash = Convert.ToHexStringLower(SHA256.HashData(body));
        var canonical = string.Join(
            "\n",
            method.Method.ToUpperInvariant(),
            absolutePath,
            timestamp.ToString(System.Globalization.CultureInfo.InvariantCulture),
            nonce,
            bodyHash);

        return Convert.ToHexStringLower(
            HMACSHA256.HashData(
                Encoding.UTF8.GetBytes(secret),
                Encoding.UTF8.GetBytes(canonical)));
    }

    public static string CreateNonce()
    {
        return Convert.ToBase64String(RandomNumberGenerator.GetBytes(24))
            .TrimEnd('=')
            .Replace('+', '-')
            .Replace('/', '_');
    }
}
