package com.virgoagario.swagbench.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virgoagario.swagbench.core.BenchConfig;
import com.virgoagario.swagbench.core.ExitCodes;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HeadlessEntryTest {
    @Test
    void parsesStableInlineConfigForFutureExternalDriver() {
        BenchConfig config = HeadlessEntry.parseConfig("scenario=mixed-v1,seed=42,warmup=12,measure=34");

        assertEquals("mixed-v1", config.scenario());
        assertEquals(42L, config.seed());
        assertEquals(12, config.warmupTicks());
        assertEquals(34, config.measureTicks());
    }

    @Test
    void parsesRepeatedSameJvmRunRequestWithoutChangingBenchConfig() {
        HeadlessRunRequest request = HeadlessEntry.parseRequest(
                "scenario=mixed-v1,seed=42,warmup=12,measure=34,runs=3");

        assertEquals("mixed-v1", request.config().scenario());
        assertEquals(42L, request.config().seed());
        assertEquals(12, request.config().warmupTicks());
        assertEquals(34, request.config().measureTicks());
        assertEquals(3, request.runs());
    }

    @Test
    void defaultsToVerifiedWarmupAndMeasureWindowWhenFieldsAreOmitted() {
        BenchConfig config = HeadlessEntry.parseConfig("scenario=mixed-v1,seed=42");

        assertEquals(2_000, config.warmupTicks());
        assertEquals(2_000, config.measureTicks());
    }

    @Test
    void defaultsToOneSameJvmRunWhenRunCountIsOmitted() {
        HeadlessRunRequest request = HeadlessEntry.parseRequest("scenario=mixed-v1,seed=42");

        assertEquals(1, request.runs());
    }

    @Test
    void keepsResourceSamplerDisabledByDefaultForMarginalRuns() {
        HeadlessRunRequest request = HeadlessEntry.parseRequest("scenario=mixed-v1,seed=42");

        assertFalse(request.resourceSampler().enabled());
        assertEquals(250L, request.resourceSampler().intervalMillis());
        assertFalse(request.resourceSampler().fullTimeline());
    }

    @Test
    void parsesResourceSamplerFlagsFromInlineRunContract() {
        HeadlessRunRequest request = HeadlessEntry.parseRequest(
                "scenario=mixed-v1,seed=42,resource=true,resourceIntervalMillis=125,resourceFull=true");

        assertTrue(request.resourceSampler().enabled());
        assertEquals(125L, request.resourceSampler().intervalMillis());
        assertTrue(request.resourceSampler().fullTimeline());
    }

    @Test
    void parsesRealisticLoadContractWithoutChangingMarginalDefaults() {
        HeadlessRunRequest marginal = HeadlessEntry.parseRequest("scenario=mixed-v1,seed=42");
        assertEquals("marginal", marginal.mode());
        assertFalse(marginal.load().enabled());

        HeadlessRunRequest realistic = HeadlessEntry.parseRequest(
                "mode=realistic,scenario=populated-v1,seed=99,clients=3,rampSeconds=2,holdSeconds=5,moveHz=4");

        assertEquals("realistic", realistic.mode());
        assertEquals("populated-v1", realistic.config().scenario());
        assertEquals(99L, realistic.config().seed());
        assertTrue(realistic.load().enabled());
        assertEquals(3, realistic.load().targetClients());
        assertEquals(2, realistic.load().rampSeconds());
        assertEquals(5, realistic.load().holdSeconds());
        assertEquals(4, realistic.load().moveHz());
        assertTrue(realistic.resourceSampler().enabled(), "realistic mode always enables resource sampling");
    }

    @Test
    void rejectsNonPositiveSameJvmRunCount() {
        assertThrows(IllegalArgumentException.class, () -> HeadlessEntry.parseRequest("runs=0"));
        assertThrows(IllegalArgumentException.class, () -> HeadlessEntry.parseRequest("runs=-1"));
    }

    @Test
    void documentsDistinctHeadlessExitCodes() {
        assertEquals(0, ExitCodes.OK);
        assertEquals(2, ExitCodes.SETUP_FAILURE);
        assertEquals(3, ExitCodes.DEGRADED);
    }

    @Test
    void startsNonDaemonExitThreadWithRequestedCode() throws InterruptedException {
        AtomicInteger observedExitCode = new AtomicInteger(-1);

        Thread exitThread = HeadlessEntry.exitAsync(ExitCodes.DEGRADED, observedExitCode::set);
        exitThread.join(1_000);

        assertEquals("swagbench-headless-exit", exitThread.getName());
        assertEquals(false, exitThread.isDaemon());
        assertEquals(ExitCodes.DEGRADED, observedExitCode.get());
    }
}
