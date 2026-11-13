using System.IO.Pipes;
using System.Security.AccessControl;
using System.Security.Principal;
using System.Text.Json;
using KabenSatuData.Agent.Contracts;

namespace KabenSatuData.Agent.Service;

internal sealed class CompanionCommandBridge
{
    public const string PipeName = "KabenSatuData.Agent.Command.v1";
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    public async Task<CommandExecutionResult> ExecuteAsync(
        AgentCommand command,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var pipe = CreatePipe();
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
            timeout.CancelAfter(TimeSpan.FromSeconds(20));
            await pipe.WaitForConnectionAsync(timeout.Token);

            await using var writer = new StreamWriter(pipe, leaveOpen: true) { AutoFlush = true };
            using var reader = new StreamReader(pipe, leaveOpen: true);
            await writer.WriteLineAsync(JsonSerializer.Serialize(new CompanionRequest(command), JsonOptions));
            var responseLine = await reader.ReadLineAsync(timeout.Token);

            if (string.IsNullOrWhiteSpace(responseLine) || responseLine.Length > 1_048_576)
            {
                return CommandExecutionResult.Failed(
                    "COMPANION_RESPONSE_INVALID",
                    "Desktop Companion mengirim response yang tidak valid.");
            }

            var response = JsonSerializer.Deserialize<CompanionResponse>(responseLine, JsonOptions);
            if (response is null)
            {
                return CommandExecutionResult.Failed(
                    "COMPANION_RESPONSE_INVALID",
                    "Desktop Companion tidak dapat diproses.");
            }

            return new CommandExecutionResult(
                response.Success,
                response.Result,
                response.ErrorCode,
                response.ErrorMessage,
                response.ArtifactPath,
                response.ArtifactType);
        }
        catch (OperationCanceledException) when (!cancellationToken.IsCancellationRequested)
        {
            return CommandExecutionResult.Failed(
                "COMPANION_UNAVAILABLE",
                "Desktop Companion tidak aktif pada user session.");
        }
        catch (Exception exception)
        {
            return CommandExecutionResult.Failed(
                "COMPANION_BRIDGE_ERROR",
                exception.Message);
        }
    }

    private static NamedPipeServerStream CreatePipe()
    {
        var security = new PipeSecurity();
        security.AddAccessRule(new PipeAccessRule(
            new SecurityIdentifier(WellKnownSidType.LocalSystemSid, null),
            PipeAccessRights.FullControl,
            AccessControlType.Allow));
        security.AddAccessRule(new PipeAccessRule(
            new SecurityIdentifier(WellKnownSidType.BuiltinAdministratorsSid, null),
            PipeAccessRights.FullControl,
            AccessControlType.Allow));
        security.AddAccessRule(new PipeAccessRule(
            new SecurityIdentifier(WellKnownSidType.AuthenticatedUserSid, null),
            PipeAccessRights.ReadWrite,
            AccessControlType.Allow));

        return NamedPipeServerStreamAcl.Create(
            PipeName,
            PipeDirection.InOut,
            1,
            PipeTransmissionMode.Byte,
            PipeOptions.Asynchronous | PipeOptions.WriteThrough,
            64 * 1024,
            64 * 1024,
            security);
    }
}
