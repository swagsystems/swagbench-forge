package com.virgoagario.swagbench.mixin;

import com.virgoagario.swagbench.core.SubsystemBucket;
import com.virgoagario.swagbench.probe.SubsystemProbes;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkCache.class)
abstract class ServerChunkCacheMixin {
    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;Z)V", at = @At("HEAD"))
    private void swagbench$enterChunkSourceTick(BooleanSupplier hasTimeLeft, boolean tickChunks, CallbackInfo callbackInfo) {
        SubsystemProbes.enter(SubsystemBucket.CHUNK);
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;Z)V", at = @At("RETURN"))
    private void swagbench$exitChunkSourceTick(BooleanSupplier hasTimeLeft, boolean tickChunks, CallbackInfo callbackInfo) {
        SubsystemProbes.exit(SubsystemBucket.CHUNK);
    }
}
