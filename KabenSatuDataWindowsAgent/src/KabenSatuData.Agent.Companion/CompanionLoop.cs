using System.IO.Pipes;
using System.Text.Json;
using KabenSatuData.Agent.Contracts;

namespace KabenSatuData.Agent.Companion;

internal sealed class CompanionLoop
{
    private const string PipeName = "KabenSatuData.Agent.Command.v1";
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);
    private readonly CompanionCommandExecutor _executor = new();

    public async Task RunAsync(CancellationToken cancellationToken)
    {
        try
        {
            while (!cancellationToken.IsCancellationRequested)
            {
                try
                {
                    await using var pipe = new NamedPipeClientStream(
                        ".",
                        PipeName,
                        PipeDirection.InOut,
                        PipeOptions.Asynchronous);
                    await pipe.ConnectAsync(5000, cancellationToken);

                    using var reader = new StreamReader(pipe, leaveOpen: true);
                    await using var writer = new StreamWriter(pipe, leaveOpen: true) { AutoFlush = true };
                    var line = await reader.ReadLineAsync(cancellationToken);

                    if (string.IsNullOrWhiteSpace(line) || line.Length > 1_048_576)
                    {
                        continue;
                    }

                    var request = JsonSerializer.Deserialize<CompanionRequest>(line, JsonOptions);
                    if (request is null)
                    {
                        continue;
                    }

                    var execution = await _executor.ExecuteAsync(request.Command, cancellationToken);
                    var response = new CompanionResponse(
                        execution.Success,
                        new Dictionary<string, object?>(execution.Result),
                        execution.ErrorCode,
                        execution.ErrorMessage,
                        execution.ArtifactPath,
                        execution.ArtifactType);
                    await writer.WriteLineAsync(JsonSerializer.Serialize(response, JsonOptions));
                }
                catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
                {
                    break;
                }
                catch (TimeoutException)
                {
                    await Task.Delay(TimeSpan.FromSeconds(2), cancellationToken);
                }
                catch (IOException)
                {
                    await Task.Delay(TimeSpan.FromSeconds(2), cancellationToken);
                }
                catch (Exception)
                {
                    await Task.Delay(TimeSpan.FromSeconds(5), cancellationToken);
                }
            }
        }
        finally
        {
            // Dispose streaming services when loop ends
            _executor.Dispose();
        }
    }
}
