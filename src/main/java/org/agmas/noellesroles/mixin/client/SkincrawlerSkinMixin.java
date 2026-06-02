package org.agmas.noellesroles.mixin.client;

import io.wifi.starrailexpress.client.util.ClientSkinCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import org.agmas.noellesroles.client.ClientEmbalmerState;
import org.agmas.noellesroles.client.ClientSkincrawlerState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replace player skin for Skincrawler (stolen skin) and Embalmer (masquerade). */
@Mixin(AbstractClientPlayer.class)
public abstract class SkincrawlerSkinMixin {
    @Unique
    private static final ThreadLocal<Boolean> resolving = ThreadLocal.withInitial(() -> false);

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void applySkinSwap(CallbackInfoReturnable<PlayerSkin> cir) {
        if (Boolean.TRUE.equals(resolving.get())) return;
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) return;
        java.util.UUID targetId = ClientEmbalmerState.replacement(self.getUUID());
        if (targetId == null) targetId = ClientSkincrawlerState.stolenSkinFor(self.getUUID());
        if (targetId == null || targetId.equals(self.getUUID())) return;
        // 优先使用 ClientSkinCache 获取完整皮肤数据（含有双层），回退到玩家列表
        PlayerInfo info = ClientSkinCache.getCachedPlayerInfo(targetId);
        if (info == null) {
            info = client.getConnection().getPlayerInfo(targetId);
        }
        if (info != null && info.getSkin() != null) {
            try {
                resolving.set(true);
                cir.setReturnValue(info.getSkin());
            } finally {
                resolving.set(false);
            }
        }
    }
}
