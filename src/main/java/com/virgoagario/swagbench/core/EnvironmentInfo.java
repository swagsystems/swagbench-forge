package com.virgoagario.swagbench.core;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public record EnvironmentInfo(
        String mcVersion,
        String forge,
        String java,
        List<String> jvmArgs,
        String os,
        String cpu,
        long heapMax,
        double systemLoadAverage,
        long totalPhysicalMemory,
        long freePhysicalMemory,
        long totalSwap,
        long freeSwap,
        long cgroupMemoryCurrent,
        long cgroupMemoryMax,
        long cgroupSwapCurrent,
        long cgroupSwapMax,
        List<String> mods) {
    public EnvironmentInfo {
        jvmArgs = List.copyOf(jvmArgs == null ? List.of() : jvmArgs);
        mods = List.copyOf(mods == null ? List.of() : mods);
    }

    public static EnvironmentInfo synthetic() {
        return new EnvironmentInfo(
                "1.18.2",
                "40.3.12",
                System.getProperty("java.version", "17"),
                List.of("-Xmx2G"),
                System.getProperty("os.name", "Linux"),
                "amd64 4 cores",
                2_147_483_648L,
                0.0,
                12_884_901_888L,
                6_442_450_944L,
                2_147_483_648L,
                2_147_483_648L,
                0L,
                -1L,
                0L,
                -1L,
                List.of("swagbench@1.0.0"));
    }

    public static EnvironmentInfo runtime(String forgeVersion, List<String> mods) {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        long totalPhysicalMemory = -1L;
        long freePhysicalMemory = -1L;
        long totalSwap = -1L;
        long freeSwap = -1L;
        if (osBean instanceof com.sun.management.OperatingSystemMXBean extendedOsBean) {
            totalPhysicalMemory = extendedOsBean.getTotalMemorySize();
            freePhysicalMemory = extendedOsBean.getFreeMemorySize();
            totalSwap = extendedOsBean.getTotalSwapSpaceSize();
            freeSwap = extendedOsBean.getFreeSwapSpaceSize();
        }
        return new EnvironmentInfo(
                "1.18.2",
                forgeVersion,
                System.getProperty("java.version", "unknown"),
                ManagementFactory.getRuntimeMXBean().getInputArguments(),
                System.getProperty("os.name", "unknown"),
                System.getProperty("os.arch", "unknown") + " " + Runtime.getRuntime().availableProcessors() + " cores",
                Runtime.getRuntime().maxMemory(),
                osBean.getSystemLoadAverage(),
                totalPhysicalMemory,
                freePhysicalMemory,
                totalSwap,
                freeSwap,
                cgroupLong("/sys/fs/cgroup/memory.current"),
                cgroupLong("/sys/fs/cgroup/memory.max"),
                cgroupLong("/sys/fs/cgroup/memory.swap.current"),
                cgroupLong("/sys/fs/cgroup/memory.swap.max"),
                mods);
    }

    private static long cgroupLong(String path) {
        try {
            String value = Files.readString(Path.of(path)).trim();
            if (value.equals("max")) {
                return -1L;
            }
            return Long.parseLong(value);
        } catch (IOException | NumberFormatException e) {
            return -1L;
        }
    }
}
