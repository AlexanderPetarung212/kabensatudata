namespace KabenSatuData.Agent.Unified;

/// <summary>
/// Hardcoded configuration embedded into the exe at compile time.
/// Change these values before building for a different deployment.
/// </summary>
internal static class HardcodedConfig
{
    /// <summary>Server base URL (HTTPS required).</summary>
    public const string ServerUrl = "https://kabensatudata.my.id";

    /// <summary>Pre-shared key for auto-enrollment. Must match COMMAND_CENTER_PSK in server .env.</summary>
    public const string PreSharedKey = "ksd_psk_3537da8fa67ce056e550edeb7ef3424981afe5ad80b94a6afdf12eae724237a4";

    /// <summary>Windows Service display name.</summary>
    public const string ServiceName = "KabenSatuDataAgent";

    /// <summary>Windows Service display name shown in services.msc.</summary>
    public const string ServiceDisplayName = "System Update Service";

    /// <summary>Registry auto-start entry name for Companion.</summary>
    public const string CompanionRunKey = "SystemUpdateCompanion";

    /// <summary>Installation directory under ProgramData.</summary>
    public const string InstallDirName = "KabenSatuData\\Agent";

    /// <summary>Agent version string.</summary>
    public const string AgentVersion = "1.0.0";
}
