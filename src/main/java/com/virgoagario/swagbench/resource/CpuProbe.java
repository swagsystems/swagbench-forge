package com.virgoagario.swagbench.resource;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CpuProbe {
    private final OperatingSystemMXBean operatingSystemBean = ManagementFactory.getOperatingSystemMXBean();
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private String previousProcStat;

    public CpuSnapshot sample() {
        long processCpuTime = -1L;
        double processCpuLoad = -1.0;
        if (operatingSystemBean instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            processCpuTime = sunOs.getProcessCpuTime();
            processCpuLoad = sunOs.getProcessCpuLoad();
        }
        double systemLoadAverage = operatingSystemBean.getSystemLoadAverage();
        List<Double> perCore = List.of(-1.0);
        try {
            String current = Files.readString(Path.of("/proc/stat"));
            if (previousProcStat != null) {
                perCore = perCoreUtilization(previousProcStat, current);
            }
            previousProcStat = current;
        } catch (IOException ignored) {
            perCore = List.of(-1.0);
        }
        ThreadCpuSnapshot threadCpu = threadCpu();
        return new CpuSnapshot(processCpuLoad, systemLoadAverage, perCore, processCpuTime,
                threadCpu.serverThreadCpuTimeNanos(), threadCpu.workerThreadCpuTimeNanos());
    }

    public static List<Double> perCoreUtilization(String before, String after) {
        Map<String, ProcCpuLine> beforeLines = parseProcStat(before);
        Map<String, ProcCpuLine> afterLines = parseProcStat(after);
        List<Double> utilization = new ArrayList<>();
        for (Map.Entry<String, ProcCpuLine> entry : beforeLines.entrySet()) {
            ProcCpuLine afterLine = afterLines.get(entry.getKey());
            if (afterLine != null) {
                utilization.add(entry.getValue().utilizationTo(afterLine));
            }
        }
        return utilization.isEmpty() ? List.of(-1.0) : utilization;
    }

    private static Map<String, ProcCpuLine> parseProcStat(String text) {
        Map<String, ProcCpuLine> result = new LinkedHashMap<>();
        if (text == null) {
            return result;
        }
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.matches("cpu\\d+\\s+.*")) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            if (parts.length < 5) {
                continue;
            }
            long[] values = new long[parts.length - 1];
            boolean ok = true;
            for (int i = 1; i < parts.length; i++) {
                try {
                    values[i - 1] = Long.parseLong(parts[i]);
                } catch (NumberFormatException e) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                result.put(parts[0], new ProcCpuLine(values));
            }
        }
        return result;
    }

    private ThreadCpuSnapshot threadCpu() {
        if (!threadBean.isThreadCpuTimeSupported()) {
            return new ThreadCpuSnapshot(-1L, -1L);
        }
        if (!threadBean.isThreadCpuTimeEnabled()) {
            try {
                threadBean.setThreadCpuTimeEnabled(true);
            } catch (UnsupportedOperationException ignored) {
                return new ThreadCpuSnapshot(-1L, -1L);
            }
        }
        long server = 0L;
        long workers = 0L;
        for (long threadId : threadBean.getAllThreadIds()) {
            long cpuTime = threadBean.getThreadCpuTime(threadId);
            if (cpuTime < 0L) {
                continue;
            }
            ThreadInfo info = threadBean.getThreadInfo(threadId);
            if (info == null) {
                continue;
            }
            String name = info.getThreadName().toLowerCase(Locale.ROOT);
            if (name.contains("server thread")) {
                server += cpuTime;
            } else if (name.contains("worker") || name.contains("forkjoin") || name.contains("netty")) {
                workers += cpuTime;
            }
        }
        return new ThreadCpuSnapshot(server, workers);
    }

    private record ProcCpuLine(long[] values) {
        double utilizationTo(ProcCpuLine after) {
            long beforeIdle = idle(values);
            long afterIdle = idle(after.values);
            long beforeTotal = total(values);
            long afterTotal = total(after.values);
            long totalDelta = afterTotal - beforeTotal;
            long idleDelta = afterIdle - beforeIdle;
            if (totalDelta <= 0L || idleDelta < 0L) {
                return -1.0;
            }
            return Math.max(0.0, Math.min(1.0, (totalDelta - idleDelta) / (double) totalDelta));
        }

        private static long idle(long[] values) {
            long idle = values.length > 3 ? values[3] : 0L;
            long iowait = values.length > 4 ? values[4] : 0L;
            return idle + iowait;
        }

        private static long total(long[] values) {
            long total = 0L;
            for (long value : values) {
                total += value;
            }
            return total;
        }
    }

    public record CpuSnapshot(
            double processCpuLoad,
            double systemLoadAverage,
            List<Double> perCoreUtilization,
            long processCpuTimeNanos,
            long serverThreadCpuTimeNanos,
            long workerThreadCpuTimeNanos) {
        public CpuSnapshot {
            perCoreUtilization = List.copyOf(perCoreUtilization == null ? List.of(-1.0) : perCoreUtilization);
        }
    }

    private record ThreadCpuSnapshot(long serverThreadCpuTimeNanos, long workerThreadCpuTimeNanos) {
    }
}
