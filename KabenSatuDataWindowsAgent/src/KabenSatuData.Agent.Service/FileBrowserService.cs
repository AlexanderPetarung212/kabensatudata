using System.Text.Json;
using KabenSatuData.Agent.Contracts;

namespace KabenSatuData.Agent.Service;

/// <summary>
/// Provides file system browsing and upload capabilities for the Windows Agent.
/// Includes path validation to prevent access to sensitive system directories.
/// </summary>
internal sealed class FileBrowserService
{
    private readonly CompanionApiClientFactory? _apiFactory;

    /// <summary>Directories that are blocked from scanning.</summary>
    private static readonly string[] BlockedPaths =
    [
        @"C:\Windows",
        @"C:\Program Files\KabenSatuData",
        @"C:\ProgramData\KabenSatuData",
    ];

    /// <summary>Maximum file size for upload (50MB).</summary>
    private const long MaxUploadSizeBytes = 50 * 1024 * 1024;

    /// <summary>Maximum files to return per scan.</summary>
    private const int MaxFilesPerScan = 500;

    public FileBrowserService(CompanionApiClientFactory? apiFactory = null)
    {
        _apiFactory = apiFactory;
    }

    /// <summary>
    /// Scan a directory and return file/folder metadata.
    /// </summary>
    public CommandExecutionResult ScanFolder(AgentCommand command)
    {
        var path = GetPayloadString(command, "path") ?? @"C:\";

        if (!IsPathAllowed(path))
        {
            return CommandExecutionResult.Failed(
                "PATH_BLOCKED",
                $"Akses ke path '{path}' tidak diizinkan.");
        }

        if (!Directory.Exists(path))
        {
            return CommandExecutionResult.Failed(
                "PATH_NOT_FOUND",
                $"Direktori '{path}' tidak ditemukan.");
        }

        var files = new List<Dictionary<string, object?>>();

        try
        {
            // List directories first
            foreach (var dir in Directory.GetDirectories(path))
            {
                if (files.Count >= MaxFilesPerScan) break;

                try
                {
                    var info = new DirectoryInfo(dir);
                    files.Add(new Dictionary<string, object?>
                    {
                        ["name"] = info.Name,
                        ["path"] = info.FullName,
                        ["is_directory"] = true,
                        ["size"] = 0,
                        ["mime_type"] = null,
                        ["last_modified"] = info.LastWriteTimeUtc.ToString("yyyy-MM-dd HH:mm:ss"),
                    });
                }
                catch (UnauthorizedAccessException)
                {
                    // Skip inaccessible directories.
                }
            }

            // Then list files
            foreach (var file in Directory.GetFiles(path))
            {
                if (files.Count >= MaxFilesPerScan) break;

                try
                {
                    var info = new FileInfo(file);
                    files.Add(new Dictionary<string, object?>
                    {
                        ["name"] = info.Name,
                        ["path"] = info.FullName,
                        ["is_directory"] = false,
                        ["size"] = info.Length,
                        ["mime_type"] = GuessMimeType(info.Extension),
                        ["last_modified"] = info.LastWriteTimeUtc.ToString("yyyy-MM-dd HH:mm:ss"),
                    });
                }
                catch (UnauthorizedAccessException)
                {
                    // Skip inaccessible files.
                }
            }
        }
        catch (UnauthorizedAccessException)
        {
            return CommandExecutionResult.Failed(
                "ACCESS_DENIED",
                $"Akses ditolak ke direktori '{path}'.");
        }
        catch (Exception ex)
        {
            return CommandExecutionResult.Failed(
                "SCAN_ERROR",
                ex.Message);
        }

        return CommandExecutionResult.Completed(new Dictionary<string, object?>
        {
            ["path"] = path,
            ["file_count"] = files.Count,
            ["files"] = files,
        });
    }

    /// <summary>
    /// Read a file and prepare it for upload as an artifact.
    /// </summary>
    public CommandExecutionResult PrepareFileUpload(AgentCommand command)
    {
        var filePath = GetPayloadString(command, "file_path");
        if (string.IsNullOrWhiteSpace(filePath))
        {
            return CommandExecutionResult.Failed(
                "MISSING_FILE_PATH",
                "Parameter 'file_path' wajib diisi.");
        }

        if (!IsPathAllowed(filePath))
        {
            return CommandExecutionResult.Failed(
                "PATH_BLOCKED",
                $"Akses ke path '{filePath}' tidak diizinkan.");
        }

        if (!File.Exists(filePath))
        {
            return CommandExecutionResult.Failed(
                "FILE_NOT_FOUND",
                $"File '{filePath}' tidak ditemukan.");
        }

        var info = new FileInfo(filePath);
        if (info.Length > MaxUploadSizeBytes)
        {
            return CommandExecutionResult.Failed(
                "FILE_TOO_LARGE",
                $"File terlalu besar ({info.Length / 1024 / 1024}MB). Maksimal {MaxUploadSizeBytes / 1024 / 1024}MB.");
        }

        // Copy file to work directory for safe upload
        var workDir = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData),
            "KabenSatuData",
            "Agent",
            "work");
        Directory.CreateDirectory(workDir);
        var destPath = Path.Combine(workDir, $"download-{Guid.NewGuid():N}{info.Extension}");
        File.Copy(filePath, destPath, true);

        return CommandExecutionResult.Completed(
            new Dictionary<string, object?>
            {
                ["original_path"] = filePath,
                ["file_name"] = info.Name,
                ["file_size"] = info.Length,
                ["mime_type"] = GuessMimeType(info.Extension),
            },
            destPath,
            "file_download");
    }

    /// <summary>
    /// Validate that a path is not in a blocked directory.
    /// </summary>
    private static bool IsPathAllowed(string path)
    {
        var fullPath = Path.GetFullPath(path);
        foreach (var blocked in BlockedPaths)
        {
            if (fullPath.StartsWith(blocked, StringComparison.OrdinalIgnoreCase))
            {
                return false;
            }
        }
        return true;
    }

    private static string? GetPayloadString(AgentCommand command, string key)
    {
        if (command.Payload.ValueKind is JsonValueKind.Object &&
            command.Payload.TryGetProperty(key, out var value) &&
            value.ValueKind is JsonValueKind.String)
        {
            return value.GetString();
        }
        return null;
    }

    private static string GuessMimeType(string extension)
    {
        return extension.ToLowerInvariant() switch
        {
            ".txt" or ".log" or ".csv" => "text/plain",
            ".json" => "application/json",
            ".xml" => "application/xml",
            ".pdf" => "application/pdf",
            ".doc" or ".docx" => "application/msword",
            ".xls" or ".xlsx" => "application/vnd.ms-excel",
            ".ppt" or ".pptx" => "application/vnd.ms-powerpoint",
            ".zip" => "application/zip",
            ".rar" => "application/x-rar-compressed",
            ".jpg" or ".jpeg" => "image/jpeg",
            ".png" => "image/png",
            ".gif" => "image/gif",
            ".mp4" => "video/mp4",
            ".mp3" => "audio/mpeg",
            ".aac" => "audio/aac",
            _ => "application/octet-stream",
        };
    }
}

/// <summary>
/// Placeholder for future Companion API client creation from Service context.
/// </summary>
internal sealed class CompanionApiClientFactory;
