package com.virgoagario.swagbench.mixin;

import com.virgoagario.swagbench.core.SubsystemBucket;
import com.virgoagario.swagbench.probe.SubsystemProbes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLevel.class)
abstract class ServerLevelEntityLoopMixin {
    @Redirect(
            method = "tick(Ljava/util/function/BooleanSupplier;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/EntityTickList;forEach(Ljava/util/function/Consumer;)V"))
    private void swagbench$recordEntityLoop(EntityTickList entities, Consumer<Entity> consumer) {
        SubsystemProbes.enter(SubsystemBucket.ENTITIES);
        try {
            entities.forEach(consumer);
        } finally {
            SubsystemProbes.exit(SubsystemBucket.ENTITIES);
        }
    }
}
