using System.Diagnostics;
using System.IO.Compression;
using System.Text.Json;
using KabenSatuData.Agent.Contracts;

namespace KabenSatuData.Agent.Service;

internal sealed class ServiceCommandExecutor
{
    private readonly WindowsInventory _inventory;
    private readonly FileBrowserService _fileBrowser;

    public ServiceCommandExecutor(WindowsInventory inventory)
    {
        _inventory = inventory;
        _fileBrowser = new FileBrowserService();
    }

    public async Task<CommandExecutionResult> ExecuteAsync(
        AgentCommand command,
        CancellationToken cancellationToken)
    {
        return command.Name switch
        {
            "GET_DEVICE_INFO" => CommandExecutionResult.Completed(_inventory.GetDeviceInfo()),
            "GET_PROCESS_LIST" => CommandExecutionResult.Completed(new Dictionary<string, object?>
            {
                ["processes"] = _inventory.GetProcesses(),
            }),
            "GET_APP_LIST" => CommandExecutionResult.Completed(new Dictionary<string, object?>
            {
                ["applications"] = _inventory.GetInstalledApplications(),
            }),
            "DIAGNOSTIC_BUNDLE" => await CreateDiagnosticBundleAsync(cancellationToken),
            "RESTART" => ExecutePowerAction(command, restart: true),
            "SHUTDOWN" => ExecutePowerAction(command, restart: false),
            "SCAN_FOLDER" => _fileBrowser.ScanFolder(command),
            "UPLOAD_FILE" => _fileBrowser.PrepareFileUpload(command),
            _ => CommandExecutionResult.Failed(
                "COMMAND_NOT_SUPPORTED_BY_SERVICE",
                $"Command {command.Name} tidak didukung oleh Service executor."),
        };
    }

    private async Task<CommandExecutionResult> CreateDiagnosticBundleAsync(CancellationToken cancellationToken)
    {
        var bundleId = Guid.NewGuid().ToString("N");
        var sourceDirectory = Path.Combine(AgentPaths.WorkDirectory, bundleId);
        var zipPath = Path.Combine(AgentPaths.WorkDirectory, $"diagnostic-{bundleId}.zip");
        Directory.CreateDirectory(sourceDirectory);

        try
        {
            await File.WriteAllTextAsync(
                Path.Combine(sourceDirectory, "device-info.json"),
                JsonSerializer.Serialize(_inventory.GetDeviceInfo(), new JsonSerializerOptions { WriteIndented = true }),
                cancellationToken);
            await File.WriteAllTextAsync(
                Path.Combine(sourceDirectory, "processes.json"),
                JsonSerializer.Serialize(_inventory.GetProcesses(), new JsonSerializerOptions { WriteIndented = true }),
                cancellationToken);
            await File.WriteAllTextAsync(
                Path.Combine(sourceDirectory, "applications.json"),
                JsonSerializer.Serialize(_inventory.GetInstalledApplications(), new JsonSerializerOptions { WriteIndented = true }),
                cancellationToken);

            ZipFile.CreateFromDirectory(sourceDirectory, zipPath, CompressionLevel.Fastest, false);

            return CommandExecutionResult.Completed(
                new Dictionary<string, object?>
                {
                    ["bundle_created"] = true,
                },
                zipPath,
                "diagnostic");
        }
        finally
        {
            if (Directory.Exists(sourceDirectory))
            {
                Directory.Delete(sourceDirectory, true);
            }
        }
    }

    private static CommandExecutionResult ExecutePowerAction(AgentCommand command, bool restart)
    {
        var delaySeconds = 0;
        if (command.Payload.ValueKind is JsonValueKind.Object &&
            command.Payload.TryGetProperty("delay_seconds", out var delayNode) &&
            delayNode.TryGetInt32(out var parsedDelay))
        {
            delaySeconds = Math.Clamp(parsedDelay, 0, 300);
        }

        var arguments = restart
            ? $"/r /t {delaySeconds} /d p:4:1 /c \"KabenSatuData maintenance\""
            : $"/s /t {delaySeconds} /d p:4:1 /c \"KabenSatuData maintenance\"";

        Process.Start(new ProcessStartInfo
        {
            FileName = Path.Combine(Environment.SystemDirectory, "shutdown.exe"),
            Arguments = arguments,
            UseShellExecute = false,
            CreateNoWindow = true,
        });

        return CommandExecutionResult.Completed(new Dictionary<string, object?>
        {
            ["scheduled"] = true,
            ["action"] = restart ? "restart" : "shutdown",
            ["delay_seconds"] = delaySeconds,
        });
    }
}
