package com.virgoagario.swagbench.scenario;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ScenarioLoaderSourceShapeTest {
    @Test
    void cleanupUsesFullHeightEntityVolumeAndScenarioHasPerimeter() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/scenario/ScenarioLoader.java"));

        assertTrue(source.contains("ENTITY_CLEANUP_RADIUS = 64"));
        assertTrue(source.contains("level.getMinBuildHeight()"));
        assertTrue(source.contains("level.getMaxBuildHeight()"));
        assertTrue(source.contains("buildPerimeter(level)"));
        assertTrue(source.contains("buildRoof(level)"));
        assertTrue(source.contains("loadBundledStructure(spec, level)"));
        assertTrue(source.contains("getStructureManager().getOrCreate"));
        assertTrue(source.contains("ResourceLocation.tryParse(\"swagbench:mixed_v1\")"));
        assertTrue(source.contains("template.placeInWorld"));
        assertTrue(source.contains("ZOMBIE_COUNT = 512"));
        assertTrue(source.contains("ITEM_ENTITY_COUNT = 512"));
        assertTrue(source.contains("ARROW_COUNT = 256"));
        assertTrue(source.contains("zombie.setNoAi(true)"));
        assertTrue(source.contains("zombie.setSilent(true)"));
        assertTrue(source.contains("zombie.setInvulnerable(true)"));
        assertTrue(source.contains("item.setUnlimitedLifetime()"));
    }

    @Test
    void scenarioLoadFailuresClearPartialBenchmarkAreaBeforeRethrow() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/scenario/ScenarioLoader.java"));

        assertTrue(source.contains("catch (RuntimeException e)"));
        assertTrue(source.contains("try {"));
        assertTrue(source.contains("cleanupScenarioState(level);"));
        assertTrue(source.contains("catch (RuntimeException cleanupFailure)"));
        assertTrue(source.contains("e.addSuppressed(cleanupFailure);"));
        assertTrue(source.contains("throw e;"));
    }

    @Test
    void failureCleanupUnforcesBenchmarkChunks() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/scenario/ScenarioLoader.java"));

        assertTrue(source.contains("private static void cleanupScenarioState(ServerLevel level)"));
        assertTrue(source.contains("clearArea(level);"));
        assertTrue(source.contains("unforceChunks(level);"));
        assertTrue(source.contains("private static void unforceChunks(ServerLevel level)"));
        assertTrue(source.contains("level.setChunkForced(chunkX, chunkZ, false);"));
    }

    @Test
    void cleanupSnapshotsAndNullChecksEntitiesBeforeDiscarding() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/scenario/ScenarioLoader.java"));

        assertTrue(source.contains("List<Entity> entitiesToDiscard = new ArrayList<>()"));
        assertTrue(source.contains("entity != null && box.contains(entity.position())"));
        assertTrue(source.contains("entitiesToDiscard.add(entity)"));
        assertTrue(source.contains("for (Entity entity : entitiesToDiscard)"));
        assertTrue(source.contains("entity.discard()"));
    }

    @Test
    void dynamicContraptionStateCanBeReprimedAfterWarmup() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/scenario/ScenarioLoader.java"));

        assertTrue(source.contains("public void reprimeDynamicState(ScenarioSpec spec, ServerLevel level)"));
        assertTrue(source.contains("case ScenarioSpec.MIXED_V1 -> seedContraptionInventories(level);"));
        assertTrue(source.contains("case ScenarioSpec.CALIBRATION_V1 -> {"));
    }

    @Test
    void calibrationScenarioUsesStaticNonRandomWorkload() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/scenario/ScenarioLoader.java"));

        assertTrue(source.contains("case ScenarioSpec.CALIBRATION_V1 -> loadCalibrationScenario(spec, level);"));
        assertTrue(source.contains("private static void loadCalibrationScenario(ScenarioSpec spec, ServerLevel level)"));
        assertTrue(source.contains("buildCalibrationStaticArea(level);"));
        assertTrue(source.contains("Blocks.OBSIDIAN.defaultBlockState()"));
        assertTrue(source.contains("Blocks.SMOOTH_STONE.defaultBlockState()"));
    }

    @Test
    void calibrationScenarioAddsDeterministicMeasuredWorkload() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/scenario/ScenarioLoader.java"));

        assertTrue(source.contains("public void runTickWorkload(ScenarioSpec spec, int tickIndex)"));
        assertTrue(source.contains("case ScenarioSpec.MIXED_V1 -> {"));
        assertTrue(source.contains("case ScenarioSpec.CALIBRATION_V1 -> runCalibrationCpuWork(tickIndex);"));
        assertTrue(source.contains("private static volatile long calibrationSink"));
        assertTrue(source.contains("CALIBRATION_CPU_ITERATIONS = 2_000_000"));
    }

    @Test
    void populatedScenarioProvidesDeterministicClientAnchorsInBenchmarkDimension() throws Exception {
        String spec = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/scenario/ScenarioSpec.java"));
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/scenario/ScenarioLoader.java"));

        assertTrue(spec.contains("POPULATED_V1 = \"populated-v1\""));
        assertTrue(source.contains("case ScenarioSpec.POPULATED_V1 -> loadPopulatedScenario(spec, level);"));
        assertTrue(source.contains("clientAnchors"));
        assertTrue(source.contains("clientAnchorsFor(spec)"));
        assertTrue(source.contains("POPULATED_CLIENT_ANCHORS"));
        assertTrue(source.contains("buildPopulatedClientRegion(level)"));
        assertTrue(source.contains("new LoadedScenario("));
        assertTrue(source.contains("level.dimension().location().toString()"));
    }

    @Test
    void spawnedEntitiesPinTickRelevantState() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/scenario/ScenarioLoader.java"));

        assertTrue(source.contains("zombie.setDeltaMovement(0.0, 0.0, 0.0)"));
        assertTrue(source.contains("zombie.setCanPickUpLoot(false)"));
        assertTrue(source.contains("zombie.setCanBreakDoors(false)"));
        assertTrue(source.contains("zombie.setLeftHanded(false)"));
        assertTrue(source.contains("zombie.setAggressive(false)"));
        assertTrue(source.contains("zombie.setHealth(zombie.getMaxHealth())"));
        assertTrue(source.contains("item.setDeltaMovement(0.0, 0.0, 0.0)"));
        assertTrue(source.contains("item.setNeverPickUp()"));
        assertTrue(source.contains("item.setOwner(null)"));
        assertTrue(source.contains("item.setThrower(null)"));
        assertTrue(source.contains("item.noPhysics = true"));
        assertTrue(source.contains("arrow.pickup = AbstractArrow.Pickup.DISALLOWED"));
        assertTrue(source.contains("arrow.setOwner(null)"));
        assertTrue(source.contains("arrow.setBaseDamage(0.0D)"));
        assertTrue(source.contains("arrow.setKnockback(0)"));
        assertTrue(source.contains("arrow.setCritArrow(false)"));
        assertTrue(source.contains("arrow.setPierceLevel((byte) 0)"));
        assertTrue(source.contains("arrow.setNoPhysics(true)"));
        assertTrue(source.contains("arrow.setShotFromCrossbow(false)"));
    }

    @Test
    void entityBallastIncreasesStableMeasuredWorkWithoutScalingPhaseyContraption() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/scenario/ScenarioLoader.java"));

        assertTrue(source.contains("ZOMBIE_COUNT = 512"));
        assertTrue(source.contains("ITEM_ENTITY_COUNT = 512"));
        assertTrue(source.contains("ARROW_COUNT = 256"));
        assertTrue(source.contains("zombie.setNoGravity(true)"));
        assertTrue(source.contains("ENTITY_LAYER_SIZE"));
        assertTrue(source.contains("(i / ENTITY_LAYER_SIZE)"));
        assertTrue(source.contains("HOPPER_FURNACE_ROWS = 8"));
        assertTrue(source.contains("HOPPER_FURNACE_COLUMNS = 12"));
    }
}
