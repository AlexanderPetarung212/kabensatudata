namespace KabenSatuData.Agent.Core;

public static class ArtifactPathValidator
{
    public static string Validate(string workDirectory, string artifactPath)
    {
        var fullPath = Path.GetFullPath(artifactPath);
        var workPath = Path.GetFullPath(workDirectory)
            .TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar)
            + Path.DirectorySeparatorChar;
        var comparison = OperatingSystem.IsWindows()
            ? StringComparison.OrdinalIgnoreCase
            : StringComparison.Ordinal;

        if (!fullPath.StartsWith(workPath, comparison) ||
            !File.Exists(fullPath) ||
            File.GetAttributes(fullPath).HasFlag(FileAttributes.ReparsePoint))
        {
            throw new InvalidDataException(
                "Artifact berada di luar direktori kerja Agent yang diizinkan.");
        }

        return fullPath;
    }
}
