package com.virgoagario.swagbench.mixin;

import com.virgoagario.swagbench.core.SubsystemBucket;
import com.virgoagario.swagbench.probe.SubsystemProbes;
import net.minecraft.world.ticks.LevelTicks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelTicks.class)
abstract class LevelTicksMixin {
    @Inject(method = "tick(JILjava/util/function/BiConsumer;)V", at = @At("HEAD"))
    private void swagbench$enterScheduledTick(CallbackInfo callbackInfo) {
        SubsystemProbes.enter(SubsystemBucket.SCHEDULED);
    }

    @Inject(method = "tick(JILjava/util/function/BiConsumer;)V", at = @At("RETURN"))
    private void swagbench$exitScheduledTick(CallbackInfo callbackInfo) {
        SubsystemProbes.exit(SubsystemBucket.SCHEDULED);
    }
}
