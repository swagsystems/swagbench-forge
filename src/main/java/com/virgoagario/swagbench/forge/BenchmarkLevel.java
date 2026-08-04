package com.virgoagario.swagbench.forge;

import java.util.Objects;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class BenchmarkLevel {
    public static final ResourceKey<Level> KEY = ResourceKey.create(
            Registry.DIMENSION_REGISTRY,
            Objects.requireNonNull(ResourceLocation.tryParse("swagbench:benchmark")));

    private BenchmarkLevel() {
    }

    public static ServerLevel level(MinecraftServer server) {
        ServerLevel level = server.getLevel(KEY);
        if (level == null) {
            throw new IllegalStateException("missing swagbench:benchmark dimension");
        }
        return level;
    }
}
