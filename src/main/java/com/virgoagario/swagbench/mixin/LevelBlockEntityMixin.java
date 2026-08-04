package com.virgoagario.swagbench.mixin;

import com.virgoagario.swagbench.core.SubsystemBucket;
import com.virgoagario.swagbench.probe.SubsystemProbes;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
abstract class LevelBlockEntityMixin {
    @Inject(method = "tickBlockEntities()V", at = @At("HEAD"))
    private void swagbench$enterBlockEntityTicks(CallbackInfo callbackInfo) {
        SubsystemProbes.enter(SubsystemBucket.BLOCK_ENTITIES);
    }

    @Inject(method = "tickBlockEntities()V", at = @At("RETURN"))
    private void swagbench$exitBlockEntityTicks(CallbackInfo callbackInfo) {
        SubsystemProbes.exit(SubsystemBucket.BLOCK_ENTITIES);
    }
}
