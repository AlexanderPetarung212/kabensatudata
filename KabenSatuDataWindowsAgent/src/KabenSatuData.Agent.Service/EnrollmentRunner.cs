using KabenSatuData.Agent.Core;

namespace KabenSatuData.Agent.Service;

internal static class EnrollmentRunner
{
    public static async Task<int> RunAsync(string[] args)
    {
        if (!OperatingSystem.IsWindows())
        {
            Console.Error.WriteLine("Enrollment Agent hanya dapat dijalankan pada Windows.");
            return 2;
        }

        var serverValue = ReadOption(args, "--server");
        if (!Uri.TryCreate(serverValue, UriKind.Absolute, out var serverUrl) ||
            serverUrl.Scheme != Uri.UriSchemeHttps)
        {
            Console.Error.WriteLine("Gunakan --server dengan URL HTTPS yang valid.");
            return 2;
        }

        var enrollmentToken = ReadOption(args, "--token");
        if (string.IsNullOrWhiteSpace(enrollmentToken))
        {
            Console.Write("Kode enrollment: ");
            enrollmentToken = Console.ReadLine();
        }

        if (string.IsNullOrWhiteSpace(enrollmentToken))
        {
            Console.Error.WriteLine("Kode enrollment wajib diisi.");
            return 2;
        }

        try
        {
            using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(60) };
            var inventory = new WindowsInventory();
            var settings = await AgentApiClient.EnrollAsync(
                http,
                serverUrl,
                inventory.BuildEnrollmentPayload(enrollmentToken),
                CancellationToken.None);
            var store = new AgentSettingsStore(AgentPaths.SettingsFile, new DpapiSecretProtector());
            await store.SaveAsync(settings);
            Console.WriteLine($"Enrollment berhasil. Device UUID: {settings.DeviceUuid}");
            return 0;
        }
        catch (Exception exception)
        {
            Console.Error.WriteLine($"Enrollment gagal: {exception.Message}");
            return 1;
        }
    }

    private static string? ReadOption(string[] args, string name)
    {
        var index = Array.FindIndex(args, item => item.Equals(name, StringComparison.OrdinalIgnoreCase));
        return index >= 0 && index + 1 < args.Length ? args[index + 1] : null;
    }
}
