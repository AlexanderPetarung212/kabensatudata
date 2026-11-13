using KabenSatuData.Agent.Contracts;
using KabenSatuData.Agent.Core;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;

namespace KabenSatuData.Agent.Service;

internal sealed class AgentWorker : BackgroundService
{
    private readonly AgentSettingsStore _settingsStore;
    private readonly WindowsInventory _inventory;
    private readonly SystemMetricsCollector _metrics;
    private readonly ServiceCommandExecutor _serviceExecutor;
    private readonly CompanionCommandBridge _companionBridge;
    private readonly ILogger<AgentWorker> _logger;

    public AgentWorker(
        AgentSettingsStore settingsStore,
        WindowsInventory inventory,
        SystemMetricsCollector metrics,
        ServiceCommandExecutor serviceExecutor,
        CompanionCommandBridge companionBridge,
        ILogger<AgentWorker> logger)
    {
        _settingsStore = settingsStore;
        _inventory = inventory;
        _metrics = metrics;
        _serviceExecutor = serviceExecutor;
        _companionBridge = companionBridge;
        _logger = logger;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        if (!_settingsStore.Exists)
        {
            _logger.LogError(
                "Agent belum di-enroll. Jalankan KabenSatuData.Agent.Service.exe enroll --server https://server.");
            return;
        }

        AgentSettings settings;
        try
        {
            settings = await _settingsStore.LoadAsync(stoppingToken);
        }
        catch (Exception exception)
        {
            _logger.LogCritical(exception, "Konfigurasi Agent tidak dapat dibaca.");
            return;
        }

        using var http = new HttpClient { Timeout = TimeSpan.FromMinutes(2) };
        var api = new AgentApiClient(http, settings);
        var lastHeartbeat = DateTimeOffset.MinValue;

        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                if (DateTimeOffset.UtcNow - lastHeartbeat >= TimeSpan.FromSeconds(settings.HeartbeatSeconds))
                {
                    await SendHeartbeatAsync(api, stoppingToken);
                    lastHeartbeat = DateTimeOffset.UtcNow;
                }

                await PollAndExecuteAsync(api, "service", stoppingToken);
                await PollAndExecuteAsync(api, "companion", stoppingToken);
            }
            catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
            {
                break;
            }
            catch (AgentApiException exception)
            {
                _logger.LogWarning(
                    "Agent API error {ErrorCode} (HTTP {StatusCode}): {Message}",
                    exception.ErrorCode,
                    exception.StatusCode,
                    exception.Message);
            }
            catch (Exception exception)
            {
                _logger.LogError(exception, "Agent loop gagal.");
            }

            await Task.Delay(TimeSpan.FromSeconds(settings.CommandPollSeconds), stoppingToken);
        }
    }

    private async Task SendHeartbeatAsync(AgentApiClient api, CancellationToken cancellationToken)
    {
        var enrollment = _inventory.BuildEnrollmentPayload("redacted");
        var payload = new
        {
            uptime_seconds = Environment.TickCount64 / 1000,
            user_session = (string?)null,
            screen_locked = (bool?)null,
            agent_version = typeof(AgentWorker).Assembly.GetName().Version?.ToString() ?? "0.1.0",
            capabilities = enrollment.GetType().GetProperty("capabilities")?.GetValue(enrollment),
            permissions_state = enrollment.GetType().GetProperty("permissions_state")?.GetValue(enrollment),
            health = new
            {
                service = "healthy",
                companion = "session_dependent",
            },
            metrics = _metrics.Collect(),
        };

        await api.HeartbeatAsync(payload, cancellationToken);
    }

    private async Task PollAndExecuteAsync(
        AgentApiClient api,
        string executor,
        CancellationToken cancellationToken)
    {
        var command = await api.GetNextCommandAsync(executor, cancellationToken);
        if (command is null)
        {
            return;
        }

        _logger.LogInformation(
            "Menjalankan command {CommandName} {CommandUuid} melalui {Executor}.",
            command.Name,
            command.Uuid,
            executor);

        await api.UpdateCommandAsync(command.Uuid, "delivered", null, cancellationToken);
        await api.UpdateCommandAsync(command.Uuid, "started", null, cancellationToken);

        CommandExecutionResult execution;
        try
        {
            execution = executor == "companion"
                ? await _companionBridge.ExecuteAsync(command, cancellationToken)
                : await _serviceExecutor.ExecuteAsync(command, cancellationToken);

            var result = new Dictionary<string, object?>(execution.Result);
            if (execution.Success &&
                !string.IsNullOrWhiteSpace(execution.ArtifactPath) &&
                !string.IsNullOrWhiteSpace(execution.ArtifactType))
            {
                var safeArtifactPath = ArtifactPathValidator.Validate(
                    AgentPaths.WorkDirectory,
                    execution.ArtifactPath);
                var artifactUuid = await api.UploadArtifactAsync(
                    command.Uuid,
                    execution.ArtifactType,
                    safeArtifactPath,
                    cancellationToken);
                result["artifact_uuid"] = artifactUuid;
                TryDeleteWorkArtifact(safeArtifactPath);
            }

            if (execution.Success)
            {
                await api.UpdateCommandAsync(
                    command.Uuid,
                    "completed",
                    new { result },
                    cancellationToken);
            }
            else
            {
                await api.UpdateCommandAsync(
                    command.Uuid,
                    "failed",
                    new
                    {
                        error_code = execution.ErrorCode ?? "COMMAND_FAILED",
                        error_message = execution.ErrorMessage,
                        result,
                    },
                    cancellationToken);
            }
        }
        catch (Exception exception)
        {
            _logger.LogError(exception, "Command {CommandUuid} gagal.", command.Uuid);
            await api.UpdateCommandAsync(
                command.Uuid,
                "failed",
                new
                {
                    error_code = "AGENT_EXECUTION_EXCEPTION",
                    error_message = exception.Message,
                    result = new { },
                },
                cancellationToken);
        }
    }

    private static void TryDeleteWorkArtifact(string path)
    {
        try
        {
            var fullPath = Path.GetFullPath(path);
            var workPath = Path.GetFullPath(AgentPaths.WorkDirectory) + Path.DirectorySeparatorChar;
            if (fullPath.StartsWith(workPath, StringComparison.OrdinalIgnoreCase) && File.Exists(fullPath))
            {
                File.Delete(fullPath);
            }
        }
        catch (Exception)
        {
            // Retention cleanup can remove a leftover work file later.
        }
    }
}
