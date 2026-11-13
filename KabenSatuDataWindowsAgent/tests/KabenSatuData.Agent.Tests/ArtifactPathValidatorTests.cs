using KabenSatuData.Agent.Core;
using Xunit;

namespace KabenSatuData.Agent.Tests;

public sealed class ArtifactPathValidatorTests
{
    [Fact]
    public void Validate_accepts_regular_file_inside_work_directory()
    {
        var root = Path.Combine(Path.GetTempPath(), "ksd-artifact-tests", Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(root);
        var artifact = Path.Combine(root, "screen.jpg");
        File.WriteAllText(artifact, "test");

        try
        {
            Assert.Equal(Path.GetFullPath(artifact), ArtifactPathValidator.Validate(root, artifact));
        }
        finally
        {
            Directory.Delete(root, true);
        }
    }

    [Fact]
    public void Validate_rejects_file_outside_work_directory()
    {
        var root = Path.Combine(Path.GetTempPath(), "ksd-artifact-tests", Guid.NewGuid().ToString("N"));
        var work = Path.Combine(root, "work");
        Directory.CreateDirectory(work);
        var outside = Path.Combine(root, "outside.txt");
        File.WriteAllText(outside, "not allowed");

        try
        {
            Assert.Throws<InvalidDataException>(() => ArtifactPathValidator.Validate(work, outside));
        }
        finally
        {
            Directory.Delete(root, true);
        }
    }
}
