using KabenSatuData.Agent.Core;
using KabenSatuData.Agent.Service;
using KabenSatuData.Agent.Companion;
using KabenSatuData.Agent.Unified;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;

// ─── Mode Detection ─────────────────────────────────────────────────────────
// No args        = installer mode (first-time self-install + auto-enroll)
// --service      = Windows Service mode (heartbeat, commands, file browser)
// --companion    = Companion mode (screen, camera, audio in user session)
// ─────────────────────────────────────────────────────────────────────────────

var mode = args.Length > 0 ? args[0].ToLowerInvariant().TrimStart('-') : "install";

switch (mode)
{
    case "service":
        await RunServiceAsync(args);
        break;

    case "companion":
        await RunCompanionAsync(args);
        break;

    default:
        return await SilentInstaller.RunAsync();
}

return 0;

// ─── Service Mode ────────────────────────────────────────────────────────────
static async Task RunServiceAsync(string[] args)
{
    Directory.CreateDirectory(AgentPaths.DataDirectory);
    Directory.CreateDirectory(AgentPaths.LogsDirectory);
    Directory.CreateDirectory(AgentPaths.WorkDirectory);

    var builder = Host.CreateApplicationBuilder(args.Skip(1).ToArray());
    builder.Services.AddWindowsService(options => options.ServiceName = HardcodedConfig.ServiceDisplayName);
    builder.Logging.AddEventLog(settings => settings.SourceName = HardcodedConfig.ServiceDisplayName);
    builder.Services.AddSingleton<ISecretProtector, DpapiSecretProtector>();
    builder.Services.AddSingleton(provider => new AgentSettingsStore(
        AgentPaths.SettingsFile,
        provider.GetRequiredService<ISecretProtector>()));
    builder.Services.AddSingleton<WindowsInventory>();
    builder.Services.AddSingleton<SystemMetricsCollector>();
    builder.Services.AddSingleton<ServiceCommandExecutor>();
    builder.Services.AddSingleton<CompanionCommandBridge>();
    builder.Services.AddHostedService<AgentWorker>();

    await builder.Build().RunAsync();
}

// ─── Companion Mode ──────────────────────────────────────────────────────────
static async Task RunCompanionAsync(string[] _)
{
    using var cts = new CancellationTokenSource();

    Console.CancelKeyPress += (_, e) =>
    {
        e.Cancel = true;
        cts.Cancel();
    };

    var loop = new CompanionLoop();
    await loop.RunAsync(cts.Token);
}
