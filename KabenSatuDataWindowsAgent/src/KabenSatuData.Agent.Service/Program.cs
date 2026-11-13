using KabenSatuData.Agent.Core;
using KabenSatuData.Agent.Service;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;

if (args.Length > 0 && args[0].Equals("enroll", StringComparison.OrdinalIgnoreCase))
{
    return await EnrollmentRunner.RunAsync(args.Skip(1).ToArray());
}

Directory.CreateDirectory(AgentPaths.DataDirectory);
Directory.CreateDirectory(AgentPaths.LogsDirectory);
Directory.CreateDirectory(AgentPaths.WorkDirectory);

var builder = Host.CreateApplicationBuilder(args);
builder.Services.AddWindowsService(options => options.ServiceName = "KabenSatuData Agent");
builder.Logging.AddEventLog(settings => settings.SourceName = "KabenSatuData Agent");
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
return 0;
