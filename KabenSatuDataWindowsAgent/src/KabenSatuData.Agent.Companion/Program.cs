using KabenSatuData.Agent.Companion;

ApplicationConfiguration.Initialize();
await new CompanionLoop().RunAsync(CancellationToken.None);
