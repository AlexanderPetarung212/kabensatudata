using System.Security.Cryptography;
using System.Text;
using KabenSatuData.Agent.Core;

namespace KabenSatuData.Agent.Service;

internal sealed class DpapiSecretProtector : ISecretProtector
{
    private static readonly byte[] Entropy = Encoding.UTF8.GetBytes("KabenSatuData.Agent.v1");

    public string Protect(string plainText)
    {
        if (!OperatingSystem.IsWindows())
        {
            throw new PlatformNotSupportedException("DPAPI hanya tersedia pada Windows.");
        }

        var encrypted = ProtectedData.Protect(
            Encoding.UTF8.GetBytes(plainText),
            Entropy,
            DataProtectionScope.LocalMachine);

        return Convert.ToBase64String(encrypted);
    }

    public string Unprotect(string protectedText)
    {
        if (!OperatingSystem.IsWindows())
        {
            throw new PlatformNotSupportedException("DPAPI hanya tersedia pada Windows.");
        }

        var decrypted = ProtectedData.Unprotect(
            Convert.FromBase64String(protectedText),
            Entropy,
            DataProtectionScope.LocalMachine);

        return Encoding.UTF8.GetString(decrypted);
    }
}
