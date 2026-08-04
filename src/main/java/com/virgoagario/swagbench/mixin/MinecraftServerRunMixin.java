package com.virgoagario.swagbench.mixin;

import com.virgoagario.swagbench.forge.HeadlessEntry;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
abstract class MinecraftServerRunMixin {
    @Inject(
            method = "runServer()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/server/ServerLifecycleHooks;handleServerStarted(Lnet/minecraft/server/MinecraftServer;)V",
                    shift = At.Shift.AFTER,
                    remap = false),
            require = 1)
    private void swagbench$runHeadlessAfterForgeStarted(CallbackInfo callbackInfo) {
        HeadlessEntry.maybeRun((MinecraftServer) (Object) this);
    }
}
