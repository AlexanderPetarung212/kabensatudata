using System.Net.NetworkInformation;
using System.Runtime.InteropServices;

namespace KabenSatuData.Agent.Service;

internal sealed class SystemMetricsCollector
{
    private ulong? _lastIdle;
    private ulong? _lastKernel;
    private ulong? _lastUser;

    public Dictionary<string, object?> Collect()
    {
        var memory = ReadMemory();
        var drives = DriveInfo.GetDrives().Where(drive => drive.IsReady).ToArray();
        var diskTotal = drives.Sum(drive => drive.TotalSize);
        var diskUsed = drives.Sum(drive => drive.TotalSize - drive.AvailableFreeSpace);
        var network = ReadNetwork();

        return new Dictionary<string, object?>
        {
            ["cpu_percent"] = ReadCpuPercent(),
            ["memory_percent"] = memory.Total > 0
                ? Math.Round(memory.Used * 100d / memory.Total, 2)
                : null,
            ["memory_used_bytes"] = memory.Used,
            ["memory_total_bytes"] = memory.Total,
            ["disk_used_bytes"] = diskUsed,
            ["disk_total_bytes"] = diskTotal,
            ["network_rx_bytes"] = network.Received,
            ["network_tx_bytes"] = network.Sent,
        };
    }

    private double? ReadCpuPercent()
    {
        if (!GetSystemTimes(out var idle, out var kernel, out var user))
        {
            return null;
        }

        var idleValue = idle.ToUInt64();
        var kernelValue = kernel.ToUInt64();
        var userValue = user.ToUInt64();

        if (_lastIdle is null || _lastKernel is null || _lastUser is null)
        {
            _lastIdle = idleValue;
            _lastKernel = kernelValue;
            _lastUser = userValue;
            return null;
        }

        var idleDelta = idleValue - _lastIdle.Value;
        var kernelDelta = kernelValue - _lastKernel.Value;
        var userDelta = userValue - _lastUser.Value;
        var total = kernelDelta + userDelta;
        _lastIdle = idleValue;
        _lastKernel = kernelValue;
        _lastUser = userValue;

        return total == 0 ? 0 : Math.Round((total - idleDelta) * 100d / total, 2);
    }

    private static (ulong Total, ulong Used) ReadMemory()
    {
        var status = new MemoryStatusEx();
        if (!GlobalMemoryStatusEx(status))
        {
            return (0, 0);
        }

        return (status.TotalPhysical, status.TotalPhysical - status.AvailablePhysical);
    }

    private static (long Received, long Sent) ReadNetwork()
    {
        long received = 0;
        long sent = 0;
        foreach (var networkInterface in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (networkInterface.OperationalStatus != OperationalStatus.Up)
            {
                continue;
            }

            try
            {
                var statistics = networkInterface.GetIPStatistics();
                received += statistics.BytesReceived;
                sent += statistics.BytesSent;
            }
            catch (NetworkInformationException)
            {
                // Interface disappeared while collecting metrics.
            }
        }

        return (received, sent);
    }

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool GetSystemTimes(
        out FileTime idleTime,
        out FileTime kernelTime,
        out FileTime userTime);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool GlobalMemoryStatusEx([In, Out] MemoryStatusEx buffer);

    [StructLayout(LayoutKind.Sequential)]
    private struct FileTime
    {
        public uint Low;
        public uint High;
        public readonly ulong ToUInt64() => ((ulong)High << 32) | Low;
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Auto)]
    private sealed class MemoryStatusEx
    {
        public uint Length = (uint)Marshal.SizeOf<MemoryStatusEx>();
        public uint MemoryLoad;
        public ulong TotalPhysical;
        public ulong AvailablePhysical;
        public ulong TotalPageFile;
        public ulong AvailablePageFile;
        public ulong TotalVirtual;
        public ulong AvailableVirtual;
        public ulong AvailableExtendedVirtual;
    }
}
