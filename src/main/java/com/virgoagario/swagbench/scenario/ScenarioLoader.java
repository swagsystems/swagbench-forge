package com.virgoagario.swagbench.scenario;

import com.virgoagario.swagbench.load.ClientAnchor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

public final class ScenarioLoader {
    private static final BlockPos ORIGIN = new BlockPos(0, 80, 0);
    private static final int RADIUS = 16;
    private static final int ENTITY_CLEANUP_RADIUS = 64;
    private static final int FORCE_CHUNK_RADIUS = 2;
    private static final int HOPPER_FURNACE_ROWS = 8;
    private static final int HOPPER_FURNACE_COLUMNS = 12;
    private static final int ENTITY_GRID_WIDTH = 16;
    private static final int ENTITY_LAYER_SIZE = ENTITY_GRID_WIDTH * ENTITY_GRID_WIDTH;
    private static final int ZOMBIE_COUNT = 512;
    private static final int ITEM_ENTITY_COUNT = 512;
    private static final int ARROW_COUNT = 256;
    private static final int CALIBRATION_CPU_ITERATIONS = 2_000_000;
    private static final int POPULATED_RADIUS = 96;
    private static final int POPULATED_CLIENT_ANCHORS = 8;
    private static volatile long calibrationSink;

    public LoadedScenario load(ScenarioSpec spec, ServerLevel level) {
        try {
            switch (spec.id()) {
                case ScenarioSpec.MIXED_V1 -> loadMixedScenario(spec, level);
                case ScenarioSpec.CALIBRATION_V1 -> loadCalibrationScenario(spec, level);
                case ScenarioSpec.POPULATED_V1 -> loadPopulatedScenario(spec, level);
                default -> throw new IllegalArgumentException("unsupported scenario: " + spec.id());
            }
        } catch (RuntimeException e) {
            try {
                cleanupScenarioState(level);
            } catch (RuntimeException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
        // TODO(Section 12): tune and lock exact mixed-v1 counts/shape after repeated
        // low-MAD runs on the target server pack. Changing these values increments
        // the scenario id because old reports are not comparable.
        return new LoadedScenario(
                spec.id(),
                spec.seed(),
                level.dimension().location().toString(),
                clientAnchorsFor(spec));
    }

    public void reprimeDynamicState(ScenarioSpec spec, ServerLevel level) {
        switch (spec.id()) {
            case ScenarioSpec.MIXED_V1 -> seedContraptionInventories(level);
            case ScenarioSpec.CALIBRATION_V1 -> {
            }
            case ScenarioSpec.POPULATED_V1 -> seedContraptionInventories(level);
            default -> throw new IllegalArgumentException("unsupported scenario: " + spec.id());
        }
    }

    public void runTickWorkload(ScenarioSpec spec, int tickIndex) {
        switch (spec.id()) {
            case ScenarioSpec.MIXED_V1 -> {
            }
            case ScenarioSpec.CALIBRATION_V1 -> runCalibrationCpuWork(tickIndex);
            case ScenarioSpec.POPULATED_V1 -> {
            }
            default -> throw new IllegalArgumentException("unsupported scenario: " + spec.id());
        }
    }

    private static void runCalibrationCpuWork(int tickIndex) {
        long value = 0x9E3779B97F4A7C15L ^ tickIndex;
        for (int i = 0; i < CALIBRATION_CPU_ITERATIONS; i++) {
            value ^= value << 13;
            value ^= value >>> 7;
            value ^= value << 17;
            value += 0xBF58476D1CE4E5B9L + i;
        }
        calibrationSink = value;
    }

    private static void loadMixedScenario(ScenarioSpec spec, ServerLevel level) {
        forceChunks(level);
        clearArea(level);
        buildPerimeter(level);
        buildRoof(level);
        loadBundledStructure(spec, level);
        seedContraptionInventories(level);
        spawnEntityCensus(level);
    }

    private static void loadCalibrationScenario(ScenarioSpec spec, ServerLevel level) {
        forceChunks(level);
        clearArea(level);
        buildPerimeter(level);
        buildRoof(level);
        buildCalibrationStaticArea(level);
    }

    private static void loadPopulatedScenario(ScenarioSpec spec, ServerLevel level) {
        forceChunks(level);
        clearPopulatedArea(level);
        buildPopulatedClientRegion(level);
        loadBundledStructure(spec, level);
        seedContraptionInventories(level);
        spawnEntityCensus(level);
    }

    private static void buildPopulatedClientRegion(ServerLevel level) {
        for (int x = -POPULATED_RADIUS; x <= POPULATED_RADIUS; x++) {
            for (int z = -POPULATED_RADIUS; z <= POPULATED_RADIUS; z++) {
                if ((Math.abs(x) % 16 == 0) || (Math.abs(z) % 16 == 0) || ((x + z) & 7) == 0) {
                    BlockPos floor = ORIGIN.offset(x, -1, z);
                    level.setBlock(floor, Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
                    level.setBlock(floor.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    level.setBlock(floor.above(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        for (ClientAnchor anchor : clientAnchorsFor(ScenarioSpec.populatedV1(0L))) {
            BlockPos base = new BlockPos(anchor.x(), anchor.y() - 1, anchor.z());
            for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, 0, -2), base.offset(2, 0, 2))) {
                level.setBlock(pos, Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(pos.above(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private static void buildCalibrationStaticArea(ServerLevel level) {
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                BlockPos floor = ORIGIN.offset(x, -1, z);
                if (((x + z) & 1) == 0) {
                    level.setBlock(floor, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
                } else {
                    level.setBlock(floor, Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    private static void forceChunks(ServerLevel level) {
        ChunkPos center = new ChunkPos(ORIGIN);
        for (int dx = -FORCE_CHUNK_RADIUS; dx <= FORCE_CHUNK_RADIUS; dx++) {
            for (int dz = -FORCE_CHUNK_RADIUS; dz <= FORCE_CHUNK_RADIUS; dz++) {
                int chunkX = center.x + dx;
                int chunkZ = center.z + dz;
                level.setChunkForced(chunkX, chunkZ, true);
                level.getChunk(chunkX, chunkZ);
            }
        }
        level.setDefaultSpawnPos(ORIGIN, 0.0F);
        level.setDayTime(6_000L);
        level.setWeatherParameters(0, 0, false, false);
    }

    private static void cleanupScenarioState(ServerLevel level) {
        clearArea(level);
        clearPopulatedArea(level);
        unforceChunks(level);
    }

    private static void clearPopulatedArea(ServerLevel level) {
        AABB box = new AABB(
                ORIGIN.getX() - POPULATED_RADIUS,
                level.getMinBuildHeight(),
                ORIGIN.getZ() - POPULATED_RADIUS,
                ORIGIN.getX() + POPULATED_RADIUS,
                level.getMaxBuildHeight(),
                ORIGIN.getZ() + POPULATED_RADIUS);
        List<Entity> entitiesToDiscard = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity != null && box.contains(entity.position())) {
                entitiesToDiscard.add(entity);
            }
        }
        for (Entity entity : entitiesToDiscard) {
            entity.discard();
        }
        for (BlockPos pos : BlockPos.betweenClosed(
                ORIGIN.offset(-POPULATED_RADIUS, -1, -POPULATED_RADIUS),
                ORIGIN.offset(POPULATED_RADIUS, 3, POPULATED_RADIUS))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void unforceChunks(ServerLevel level) {
        ChunkPos center = new ChunkPos(ORIGIN);
        for (int dx = -FORCE_CHUNK_RADIUS; dx <= FORCE_CHUNK_RADIUS; dx++) {
            for (int dz = -FORCE_CHUNK_RADIUS; dz <= FORCE_CHUNK_RADIUS; dz++) {
                int chunkX = center.x + dx;
                int chunkZ = center.z + dz;
                level.setChunkForced(chunkX, chunkZ, false);
            }
        }
    }

    private static void clearArea(ServerLevel level) {
        AABB box = new AABB(
                ORIGIN.getX() - ENTITY_CLEANUP_RADIUS,
                level.getMinBuildHeight(),
                ORIGIN.getZ() - ENTITY_CLEANUP_RADIUS,
                ORIGIN.getX() + ENTITY_CLEANUP_RADIUS,
                level.getMaxBuildHeight(),
                ORIGIN.getZ() + ENTITY_CLEANUP_RADIUS);
        List<Entity> entitiesToDiscard = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity != null && box.contains(entity.position())) {
                entitiesToDiscard.add(entity);
            }
        }
        for (Entity entity : entitiesToDiscard) {
            entity.discard();
        }
        for (BlockPos pos : BlockPos.betweenClosed(
                ORIGIN.offset(-RADIUS, 0, -RADIUS),
                ORIGIN.offset(RADIUS, 8, RADIUS))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        for (BlockPos pos : BlockPos.betweenClosed(
                ORIGIN.offset(-RADIUS, -1, -RADIUS),
                ORIGIN.offset(RADIUS, -1, RADIUS))) {
            level.setBlock(pos, Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void buildPerimeter(ServerLevel level) {
        int minX = ORIGIN.getX() - RADIUS;
        int maxX = ORIGIN.getX() + RADIUS;
        int minZ = ORIGIN.getZ() - RADIUS;
        int maxZ = ORIGIN.getZ() + RADIUS;
        for (int x = minX; x <= maxX; x++) {
            setWallColumn(level, new BlockPos(x, ORIGIN.getY(), minZ));
            setWallColumn(level, new BlockPos(x, ORIGIN.getY(), maxZ));
        }
        for (int z = minZ; z <= maxZ; z++) {
            setWallColumn(level, new BlockPos(minX, ORIGIN.getY(), z));
            setWallColumn(level, new BlockPos(maxX, ORIGIN.getY(), z));
        }
    }

    private static void setWallColumn(ServerLevel level, BlockPos base) {
        level.setBlock(base, Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(base.above(), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void buildRoof(ServerLevel level) {
        int roofY = ORIGIN.getY() + 4;
        for (BlockPos pos : BlockPos.betweenClosed(
                new BlockPos(ORIGIN.getX() - RADIUS, roofY, ORIGIN.getZ() - RADIUS),
                new BlockPos(ORIGIN.getX() + RADIUS, roofY, ORIGIN.getZ() + RADIUS))) {
            level.setBlock(pos, Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void loadBundledStructure(ScenarioSpec spec, ServerLevel level) {
        StructureTemplate template = level.getServer().getStructureManager().getOrCreate(
                Objects.requireNonNull(ResourceLocation.tryParse("swagbench:mixed_v1")));
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setIgnoreEntities(false)
                .setKnownShape(true)
                .setKeepLiquids(false)
                .setRandom(new Random(spec.seed()));
        boolean placed = template.placeInWorld(
                level,
                ORIGIN.offset(-11, 0, -12),
                ORIGIN.offset(-11, 0, -12),
                settings,
                new Random(spec.seed()),
                Block.UPDATE_ALL);
        if (!placed) {
            throw new IllegalStateException("failed to place bundled structure swagbench:mixed_v1");
        }
    }

    private static void seedContraptionInventories(ServerLevel level) {
        for (int row = 0; row < HOPPER_FURNACE_ROWS; row++) {
            for (int column = 0; column < HOPPER_FURNACE_COLUMNS; column++) {
                BlockPos base = ORIGIN.offset(-11 + (column * 2), 0, -12 + (row * 2));
                if (level.getBlockEntity(base) instanceof HopperBlockEntity hopper) {
                    hopper.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                }
                if (level.getBlockEntity(base.above()) instanceof FurnaceBlockEntity furnace) {
                    furnace.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                    furnace.setItem(1, new ItemStack(Items.COAL, 64));
                }
            }
        }
    }

    private static void spawnEntityCensus(ServerLevel level) {
        for (int i = 0; i < ZOMBIE_COUNT; i++) {
            Zombie zombie = EntityType.ZOMBIE.create(level);
            if (zombie != null) {
                int layer = (i / ENTITY_LAYER_SIZE);
                zombie.moveTo(
                        gridX(i),
                        ORIGIN.getY() + layer,
                        gridZ(i),
                        0.0F,
                        0.0F);
                zombie.setBaby(false);
                zombie.setDeltaMovement(0.0, 0.0, 0.0);
                zombie.setNoGravity(true);
                zombie.setNoAi(true);
                zombie.setSilent(true);
                zombie.setInvulnerable(true);
                zombie.setCanPickUpLoot(false);
                zombie.setCanBreakDoors(false);
                zombie.setLeftHanded(false);
                zombie.setAggressive(false);
                zombie.setHealth(zombie.getMaxHealth());
                zombie.setPersistenceRequired();
                level.addFreshEntity(zombie);
            }
        }
        for (int i = 0; i < ITEM_ENTITY_COUNT; i++) {
            int layer = (i / ENTITY_LAYER_SIZE);
            ItemEntity item = new ItemEntity(
                    level,
                    gridX(i),
                    ORIGIN.getY() + 3 + layer,
                    gridZ(i),
                    new ItemStack(Items.COBBLESTONE, 1));
            item.setDeltaMovement(0.0, 0.0, 0.0);
            item.setNoGravity(true);
            item.noPhysics = true;
            item.setNeverPickUp();
            item.setOwner(null);
            item.setThrower(null);
            item.setUnlimitedLifetime();
            level.addFreshEntity(item);
        }
        for (int i = 0; i < ARROW_COUNT; i++) {
            Arrow arrow = EntityType.ARROW.create(level);
            if (arrow != null) {
                int layer = (i / ENTITY_LAYER_SIZE);
                arrow.moveTo(
                        gridX(i),
                        ORIGIN.getY() + 5 + layer,
                        gridZ(i),
                        0.0F,
                        0.0F);
                arrow.setDeltaMovement(0.0, 0.0, 0.0);
                arrow.setNoGravity(true);
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                arrow.setOwner(null);
                arrow.setBaseDamage(0.0D);
                arrow.setKnockback(0);
                arrow.setCritArrow(false);
                arrow.setPierceLevel((byte) 0);
                arrow.setNoPhysics(true);
                arrow.setShotFromCrossbow(false);
                level.addFreshEntity(arrow);
            }
        }
    }

    private static int gridX(int index) {
        return ORIGIN.getX() - 15 + ((index % ENTITY_GRID_WIDTH) * 2);
    }

    private static int gridZ(int index) {
        return ORIGIN.getZ() - 15 + (((index / ENTITY_GRID_WIDTH) % ENTITY_GRID_WIDTH) * 2);
    }

    public static List<ClientAnchor> clientAnchorsFor(ScenarioSpec spec) {
        List<ClientAnchor> clientAnchors = new ArrayList<>(POPULATED_CLIENT_ANCHORS);
        Random random = new Random(spec.seed());
        for (int i = 0; i < POPULATED_CLIENT_ANCHORS; i++) {
            double angle = (Math.PI * 2.0D * i) / POPULATED_CLIENT_ANCHORS;
            double radius = 32.0D + random.nextInt(32);
            clientAnchors.add(new ClientAnchor(
                    ORIGIN.getX() + Math.cos(angle) * radius + 0.5D,
                    ORIGIN.getY() + 1.0D,
                    ORIGIN.getZ() + Math.sin(angle) * radius + 0.5D,
                    (float) Math.toDegrees(angle + Math.PI),
                    0.0F));
        }
        return List.copyOf(clientAnchors);
    }

    public record LoadedScenario(String id, long seed, String dimension, List<ClientAnchor> clientAnchors) {
        public LoadedScenario {
            clientAnchors = List.copyOf(clientAnchors == null ? List.of() : clientAnchors);
        }
    }
}
