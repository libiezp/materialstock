package com.materialstock.mixin;

import com.materialstock.scan.ContainerProbe;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundTagQueryPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截方块实体 NBT 查询响应，交给 ContainerProbe 解析容器内容。
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "handleTagQueryPacket", at = @At("HEAD"))
    private void materialstock_onHandleTagQuery(ClientboundTagQueryPacket packet, CallbackInfo ci) {
        ContainerProbe.getInstance().onTagQueryResponse(packet);
    }
}
