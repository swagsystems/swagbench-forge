package com.virgoagario.swagbench.mixin;

import com.virgoagario.swagbench.network.NetworkProbe;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
abstract class ConnectionNetworkProbeMixin {
    @Inject(method = "channelActive(Lio/netty/channel/ChannelHandlerContext;)V", at = @At("TAIL"), require = 1)
    private void swagbench$installNetworkProbe(ChannelHandlerContext ctx, CallbackInfo callbackInfo) {
        NetworkProbe.install(ctx);
    }
}
