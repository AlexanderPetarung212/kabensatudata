using System.Diagnostics;
using System.Runtime.InteropServices;
using Microsoft.Win32;

namespace KabenSatuData.Agent.Service;

internal sealed class WindowsInventory
{
    private static readonly string[] Capabilities =
    [
        "heartbeat",
        "metrics",
        "device_info",
        "process_list",
        "application_list",
        "screenshot_on_demand",
        "live_screen",
        "camera",
        "camera_snapshot",
        "audio_capture",
        "file_browser",
        "lock",
        "restart",
        "shutdown",
        "diagnostic_bundle",
    ];

    public object BuildEnrollmentPayload(string enrollmentToken)
    {
        return new
        {
            enrollment_token = enrollmentToken,
            platform = "windows",
            device_name = Environment.MachineName,
            hostname = Environment.MachineName,
            os_name = "Windows",
            os_version = Environment.OSVersion.VersionString,
            architecture = RuntimeInformation.OSArchitecture.ToString().ToLowerInvariant(),
            agent_version = typeof(WindowsInventory).Assembly.GetName().Version?.ToString() ?? "0.2.0",
            installer_version = "0.2.0",
            hardware_fingerprint = ReadMachineGuid(),
            capabilities = Capabilities,
            permissions_state = new
            {
                screenshot = "available",
                camera = "available",
                microphone = "available",
                live_screen = "available",
                file_browser = "available",
            },
        };
    }

    public Dictionary<string, object?> GetDeviceInfo()
    {
        var drives = DriveInfo.GetDrives()
            .Where(drive => drive.IsReady)
            .Select(drive => new
            {
                drive.Name,
                drive.DriveFormat,
                drive.TotalSize,
                drive.AvailableFreeSpace,
            })
            .ToArray();

        return new Dictionary<string, object?>
        {
            ["hostname"] = Environment.MachineName,
            ["os"] = Environment.OSVersion.VersionString,
            ["architecture"] = RuntimeInformation.OSArchitecture.ToString(),
            ["processor_count"] = Environment.ProcessorCount,
            ["system_directory"] = Environment.SystemDirectory,
            ["user_domain"] = Environment.UserDomainName,
            ["uptime_seconds"] = Environment.TickCount64 / 1000,
            ["drives"] = drives,
        };
    }

    public IReadOnlyList<object> GetProcesses()
    {
        return Process.GetProcesses()
            .OrderByDescending(process => TryGetWorkingSet(process))
            .Take(250)
            .Select(process => new
            {
                id = process.Id,
                name = process.ProcessName,
                working_set_bytes = TryGetWorkingSet(process),
                session_id = TryGetSessionId(process),
            })
            .Cast<object>()
            .ToArray();
    }

    public IReadOnlyList<object> GetInstalledApplications()
    {
        var applications = new Dictionary<string, object>(StringComparer.OrdinalIgnoreCase);
        foreach (var (hive, view) in RegistryLocations())
        {
            try
            {
                using var baseKey = RegistryKey.OpenBaseKey(hive, view);
                using var uninstall = baseKey.OpenSubKey(@"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall");
                if (uninstall is null)
                {
                    continue;
                }

                foreach (var subKeyName in uninstall.GetSubKeyNames())
                {
                    using var subKey = uninstall.OpenSubKey(subKeyName);
                    var displayName = subKey?.GetValue("DisplayName") as string;
                    if (string.IsNullOrWhiteSpace(displayName))
                    {
                        continue;
                    }

                    applications[displayName] = new
                    {
                        name = displayName,
                        version = subKey?.GetValue("DisplayVersion") as string,
                        publisher = subKey?.GetValue("Publisher") as string,
                        install_date = subKey?.GetValue("InstallDate") as string,
                    };
                }
            }
            catch (Exception)
            {
                // Inventory is best-effort; inaccessible registry branches are skipped.
            }
        }

        return applications.Values.OrderBy(value => value.ToString()).Take(1000).ToArray();
    }

    private static string ReadMachineGuid()
    {
        try
        {
            using var key = Registry.LocalMachine.OpenSubKey(@"SOFTWARE\Microsoft\Cryptography");
            return key?.GetValue("MachineGuid") as string
                ?? $"{Environment.MachineName}|{Environment.OSVersion.VersionString}";
        }
        catch (Exception)
        {
            return $"{Environment.MachineName}|{Environment.OSVersion.VersionString}";
        }
    }

    private static IEnumerable<(RegistryHive Hive, RegistryView View)> RegistryLocations()
    {
        yield return (RegistryHive.LocalMachine, RegistryView.Registry64);
        yield return (RegistryHive.LocalMachine, RegistryView.Registry32);
        yield return (RegistryHive.CurrentUser, RegistryView.Registry64);
        yield return (RegistryHive.CurrentUser, RegistryView.Registry32);
    }

    private static long TryGetWorkingSet(Process process)
    {
        try { return process.WorkingSet64; }
        catch (Exception) { return 0; }
    }

    private static int? TryGetSessionId(Process process)
    {
        try { return process.SessionId; }
        catch (Exception) { return null; }
    }
}
