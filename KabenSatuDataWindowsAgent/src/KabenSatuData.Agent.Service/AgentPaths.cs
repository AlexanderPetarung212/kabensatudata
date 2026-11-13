namespace KabenSatuData.Agent.Service;

internal static class AgentPaths
{
    public static string DataDirectory =>
        Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData),
            "KabenSatuData",
            "Agent");

    public static string SettingsFile => Path.Combine(DataDirectory, "agent.settings.json");
    public static string LogsDirectory => Path.Combine(DataDirectory, "logs");
    public static string WorkDirectory => Path.Combine(DataDirectory, "work");
}
