package com.virgoagario.swagbench.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.management.GarbageCollectionNotificationInfo;
import java.lang.management.MemoryUsage;
import java.util.Map;
import javax.management.Notification;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;
import org.junit.jupiter.api.Test;

class GcMonitorTest {
    @Test
    void convertsSyntheticGcNotificationIntoNanoTimeEvent() throws OpenDataException {
        GcMonitor monitor = new GcMonitor(1_000_000_000L, 250L, false);

        monitor.accept(syntheticGcNotification("G1 Young Generation", 260L, 264L), null);

        var events = monitor.drain();
        assertEquals(1, events.size());
        assertEquals("G1 Young Generation", events.get(0).collectorName());
        assertEquals(1_010_000_000L, events.get(0).startNanos());
        assertEquals(1_014_000_000L, events.get(0).endNanos());
        assertEquals(1L, monitor.postGcLiveSetBytes());
    }

    @Test
    void tracksLatestPostGcLiveSetInsteadOfHistoricalMinimum() throws OpenDataException {
        GcMonitor monitor = new GcMonitor(1_000_000_000L, 250L, false);

        monitor.accept(syntheticGcNotification("G1 Old Generation", 260L, 264L, 10L), null);
        monitor.accept(syntheticGcNotification("G1 Old Generation", 360L, 364L, 20L), null);

        assertEquals(20L, monitor.postGcLiveSetBytes());
    }

    private static Notification syntheticGcNotification(String gcName, long startMillis, long endMillis)
            throws OpenDataException {
        return syntheticGcNotification(gcName, startMillis, endMillis, 1L);
    }

    private static Notification syntheticGcNotification(String gcName, long startMillis, long endMillis, long usedBytes)
            throws OpenDataException {
        Notification notification = new Notification(
                GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION,
                "test",
                1L);
        notification.setUserData(gcNotificationData(gcName, startMillis, endMillis, usedBytes));
        return notification;
    }

    private static CompositeData gcNotificationData(String gcName, long startMillis, long endMillis, long usedBytes)
            throws OpenDataException {
        CompositeType type = new CompositeType(
                "sun.management.BaseGarbageCollectionNotifInfoCompositeType",
                "CompositeType for Base GarbageCollectionNotificationInfo",
                new String[]{"gcName", "gcAction", "gcCause", "gcInfo"},
                new String[]{"gcName", "gcAction", "gcCause", "gcInfo"},
                new OpenType[]{SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, gcInfoType()});
        return new CompositeDataSupport(
                type,
                new String[]{"gcName", "gcAction", "gcCause", "gcInfo"},
                new Object[]{gcName, "end of minor GC", "test", gcInfoData(startMillis, endMillis, usedBytes)});
    }

    private static CompositeData gcInfoData(long startMillis, long endMillis, long usedBytes) throws OpenDataException {
        CompositeType type = gcInfoType();
        return new CompositeDataSupport(
                type,
                new String[]{
                        "id",
                        "startTime",
                        "endTime",
                        "duration",
                        "memoryUsageBeforeGc",
                        "memoryUsageAfterGc"},
                new Object[]{
                        1L,
                        startMillis,
                        endMillis,
                        endMillis - startMillis,
                        memoryUsageTable(usedBytes),
                        memoryUsageTable(usedBytes)});
    }

    private static CompositeType gcInfoType() throws OpenDataException {
        return new CompositeType(
                "sun.management.BaseGcInfoCompositeType",
                "CompositeType for Base GcInfo",
                new String[]{
                        "id",
                        "startTime",
                        "endTime",
                        "duration",
                        "memoryUsageBeforeGc",
                        "memoryUsageAfterGc"},
                new String[]{
                        "id",
                        "startTime",
                        "endTime",
                        "duration",
                        "memoryUsageBeforeGc",
                        "memoryUsageAfterGc"},
                new OpenType[]{
                        SimpleType.LONG,
                        SimpleType.LONG,
                        SimpleType.LONG,
                        SimpleType.LONG,
                        memoryUsageMapType(),
                        memoryUsageMapType()});
    }

    private static TabularData memoryUsageTable(long usedBytes) throws OpenDataException {
        TabularDataSupport table = new TabularDataSupport(memoryUsageMapType());
        CompositeType rowType = memoryUsageRowType();
        CompositeData row = new CompositeDataSupport(
                rowType,
                new String[]{"key", "value"},
                new Object[]{"heap", memoryUsageData(new MemoryUsage(0, usedBytes, usedBytes, Math.max(usedBytes, 1L)))});
        table.put(row);
        return table;
    }

    private static TabularType memoryUsageMapType() throws OpenDataException {
        return new TabularType(
                Map.class.getName(),
                "MemoryUsage map",
                memoryUsageRowType(),
                new String[]{"key"});
    }

    private static CompositeType memoryUsageRowType() throws OpenDataException {
        return new CompositeType(
                Map.Entry.class.getName(),
                "MemoryUsage map entry",
                new String[]{"key", "value"},
                new String[]{"key", "value"},
                new OpenType[]{SimpleType.STRING, memoryUsageType()});
    }

    private static CompositeData memoryUsageData(MemoryUsage usage) throws OpenDataException {
        return new CompositeDataSupport(
                memoryUsageType(),
                new String[]{"init", "used", "committed", "max"},
                new Object[]{usage.getInit(), usage.getUsed(), usage.getCommitted(), usage.getMax()});
    }

    private static CompositeType memoryUsageType() throws OpenDataException {
        return new CompositeType(
                MemoryUsage.class.getName(),
                "MemoryUsage",
                new String[]{"init", "used", "committed", "max"},
                new String[]{"init", "used", "committed", "max"},
                new OpenType[]{SimpleType.LONG, SimpleType.LONG, SimpleType.LONG, SimpleType.LONG});
    }
}
