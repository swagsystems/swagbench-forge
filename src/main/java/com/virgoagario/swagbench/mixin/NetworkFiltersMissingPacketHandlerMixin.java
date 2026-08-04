package com.virgoagario.swagbench.mixin;

import net.minecraft.network.Connection;
import net.minecraftforge.network.filters.NetworkFilters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NetworkFilters.class, remap = false)
abstract class NetworkFiltersMissingPacketHandlerMixin {
    @Inject(method = "injectIfNecessary", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void swagbench$skipMissingPacketHandler(Connection connection, CallbackInfo callbackInfo) {
        if (connection == null
                || connection.channel() == null
                || connection.channel().pipeline().get("packet_handler") == null) {
            callbackInfo.cancel();
        }
    }
}
