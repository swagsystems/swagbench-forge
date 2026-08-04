package com.virgoagario.swagbench.forge;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MixinSourceShapeTest {
    @Test
    void blockEntityProbeTargetsConcreteLevelTickLoop() throws IOException {
        String mixinConfig = Files.readString(Path.of("src/main/resources/swagbench.mixins.json"));
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/mixin/LevelBlockEntityMixin.java"));

        assertTrue(mixinConfig.contains("LevelBlockEntityMixin"),
                "mixin config must load the block entity tick-loop probe");
        assertTrue(!mixinConfig.contains("TickingBlockEntityMixin"),
                "mixin config must not target the TickingBlockEntity interface directly");
        assertTrue(source.contains("@Mixin(Level.class)"),
                "block entity probe must target the concrete Level tick loop");
        assertTrue(source.contains("SubsystemBucket.BLOCK_ENTITIES"),
                "block entity probe must record the BLOCK_ENTITIES bucket");
    }

    @Test
    void entityLoopProbeCoversWholeServerLevelEntityTickList() throws IOException {
        String mixinConfig = Files.readString(Path.of("src/main/resources/swagbench.mixins.json"));
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/mixin/ServerLevelEntityLoopMixin.java"));

        assertTrue(mixinConfig.contains("ServerLevelEntityLoopMixin"),
                "mixin config must load the high-level entity loop probe");
        assertTrue(source.contains("@Mixin(ServerLevel.class)"),
                "entity loop probe must target the concrete ServerLevel loop");
        assertTrue(source.contains("EntityTickList"),
                "entity loop probe must target the whole EntityTickList iteration");
        assertTrue(source.contains("method = \"tick(Ljava/util/function/BooleanSupplier;)V\""),
                "entity loop probe must wrap ServerLevel.tick with an exact descriptor");
        assertTrue(source.contains("target = \"Lnet/minecraft/world/level/entity/EntityTickList;forEach"),
                "entity loop probe must redirect the whole EntityTickList.forEach call");
        assertTrue(!source.contains("method = \"tickNonPassenger\""),
                "entity loop probe must not add per-entity timing overhead");
        assertTrue(source.contains("SubsystemBucket.ENTITIES"),
                "entity loop probe must record the ENTITIES bucket");
    }

    @Test
    void chunkSourceProbeCoversServerChunkCacheTick() throws IOException {
        String mixinConfig = Files.readString(Path.of("src/main/resources/swagbench.mixins.json"));
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/mixin/ServerChunkCacheMixin.java"));

        assertTrue(mixinConfig.contains("ServerChunkCacheMixin"),
                "mixin config must load the high-level chunk source probe");
        assertTrue(source.contains("@Mixin(ServerChunkCache.class)"),
                "chunk source probe must target ServerChunkCache");
        assertTrue(source.contains("method = \"tick(Ljava/util/function/BooleanSupplier;Z)V\""),
                "chunk source probe must wrap ServerChunkCache.tick with an exact descriptor");
        assertTrue(source.contains("SubsystemBucket.CHUNK"),
                "chunk source probe must record the CHUNK bucket");
    }

    @Test
    void scheduledTickProbeTargetsConcreteLevelTicksClass() throws IOException {
        String mixinConfig = Files.readString(Path.of("src/main/resources/swagbench.mixins.json"));
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/mixin/LevelTicksMixin.java"));

        assertTrue(mixinConfig.contains("LevelTicksMixin"),
                "mixin config must load the scheduled tick probe");
        assertTrue(source.contains("@Mixin(LevelTicks.class)"),
                "scheduled tick probe must target the concrete LevelTicks class");
        assertTrue(!source.contains("@Pseudo"),
                "scheduled tick probe must fail closed if LevelTicks is not present");
        assertTrue(source.contains("method = \"tick(JILjava/util/function/BiConsumer;)V\""),
                "scheduled tick probe must wrap LevelTicks.tick with an exact descriptor");
        assertTrue(source.contains("SubsystemBucket.SCHEDULED"),
                "scheduled tick probe must record the SCHEDULED bucket");
    }

    @Test
    void coarseSubsystemProbeSetDoesNotLoadLegacyInnerProbes() throws IOException {
        String mixinConfig = Files.readString(Path.of("src/main/resources/swagbench.mixins.json"));

        assertTrue(!mixinConfig.contains("EntityTickMixin"),
                "entity bucket should use the broad ServerLevel entity-list probe only");
        assertTrue(!mixinConfig.contains("ServerLevelMixin"),
                "chunk bucket should use the broad ServerChunkCache.tick probe only");
        assertTrue(Files.notExists(Path.of(
                "src/main/java/com/virgoagario/swagbench/mixin/EntityTickMixin.java")),
                "legacy per-Entity.tick probe should not remain in the source tree");
        assertTrue(Files.notExists(Path.of(
                "src/main/java/com/virgoagario/swagbench/mixin/ServerLevelMixin.java")),
                "legacy ServerLevel.tickChunk probe should not remain in the source tree");
    }

    @Test
    void criticalProbeMixinsFailClosedInsteadOfSilentlyDisappearing() throws IOException {
        String mixinConfig = Files.readString(Path.of("src/main/resources/swagbench.mixins.json"));
        String blockEntities = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/mixin/LevelBlockEntityMixin.java"));
        String scheduled = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/mixin/LevelTicksMixin.java"));
        String chunk = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/mixin/ServerChunkCacheMixin.java"));
        String entities = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/mixin/ServerLevelEntityLoopMixin.java"));

        assertTrue(mixinConfig.contains("\"defaultRequire\": 1"));
        assertTrue(!blockEntities.contains("require = 0"));
        assertTrue(!scheduled.contains("require = 0"));
        assertTrue(!chunk.contains("require = 0"));
        assertTrue(!entities.contains("require = 0"));
    }

    @Test
    void vanillaSubsystemInjectorsUseRefmapRemappingForProductionServers() throws IOException {
        String blockEntities = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/mixin/LevelBlockEntityMixin.java"));
        String scheduled = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/mixin/LevelTicksMixin.java"));
        String chunk = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/mixin/ServerChunkCacheMixin.java"));
        String entities = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/mixin/ServerLevelEntityLoopMixin.java"));

        assertTrue(!blockEntities.contains("remap = false"),
                "Level.tickBlockEntities must be remapped through the refmap for production");
        assertTrue(!scheduled.contains("remap = false"),
                "LevelTicks.tick must be remapped through the refmap for production");
        assertTrue(!chunk.contains("remap = false"),
                "ServerChunkCache.tick must be remapped through the refmap for production");
        assertTrue(!entities.contains("remap = false"),
                "ServerLevel.tick and EntityTickList.forEach selectors must be remapped through the refmap");
    }

    @Test
    void headlessServerHookRemapsVanillaMethodButNotForgeLifecycleTarget() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/mixin/MinecraftServerRunMixin.java"));

        assertTrue(source.contains("method = \"runServer()V\""),
                "headless hook must target MinecraftServer.runServer");
        assertTrue(!source.contains("method = \"runServer()V\",\n            at = @At(")
                        || !source.contains("remap = false,\n            require"),
                "MinecraftServer.runServer must use default remapping");
        assertTrue(source.contains("target = \"Lnet/minecraftforge/server/ServerLifecycleHooks;handleServerStarted"),
                "headless hook should still anchor after Forge's server-started lifecycle callback");
        assertTrue(source.contains("remap = false"),
                "Forge lifecycle callback owner is not a vanilla remapped target");
        assertTrue(!source.contains("require = 0"),
                "headless hook must fail closed if the production injector cannot apply");
    }

    @Test
    void networkProbeMixinInstallsOnVanillaConnectionWithProductionRemapping() throws IOException {
        String mixinConfig = Files.readString(Path.of("src/main/resources/swagbench.mixins.json"));
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/mixin/ConnectionNetworkProbeMixin.java"));
        String probe = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/network/NetworkProbe.java"));

        assertTrue(mixinConfig.contains("ConnectionNetworkProbeMixin"));
        assertTrue(source.contains("@Mixin(Connection.class)"));
        assertTrue(source.contains("method = \"channelActive(Lio/netty/channel/ChannelHandlerContext;)V\""));
        assertTrue(!source.contains("remap = false"));
        assertTrue(!source.contains("require = 0"));
        assertTrue(source.contains("NetworkProbe.install"));
        assertTrue(probe.contains("addBefore(\"splitter\""));
    }

    @Test
    void forgeNetworkFilterGuardTargetsNonRemappedForgeClass() throws IOException {
        String mixinConfig = Files.readString(Path.of("src/main/resources/swagbench.mixins.json"));
        String source = Files.readString(Path.of(
                "src/main/java/com/virgoagario/swagbench/mixin/NetworkFiltersMissingPacketHandlerMixin.java"));

        assertTrue(mixinConfig.contains("NetworkFiltersMissingPacketHandlerMixin"));
        assertTrue(source.contains("NetworkFilters.class"));
        assertTrue(source.contains("remap = false"));
        assertTrue(source.contains("packet_handler"));
        assertTrue(source.contains("callbackInfo.cancel()"));
    }
}
