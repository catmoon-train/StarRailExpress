package org.agmas.noellesroles.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import org.agmas.noellesroles.game.roles.killer.skincrawler.SkincrawlerPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(AbstractClientPlayer.class)
public abstract class SkincrawlerSkinMixin {
    @Unique
    private static final ThreadLocal<Boolean> resolvingSkin = ThreadLocal.withInitial(() -> false);

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void applyStolenSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        if (Boolean.TRUE.equals(resolvingSkin.get())) return;
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        UUID replacementId = null;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return;

        var comp = SkincrawlerPlayerComponent.KEY.get(self);
        if (comp != null && comp.stolenSkin != null && !comp.stolenSkin.equals(self.getUUID())) {
            replacementId = comp.stolenSkin;
        }
        if (replacementId == null) return;

        AbstractClientPlayer replacement = null;
        for (var player : client.level.players()) {
            if (player instanceof AbstractClientPlayer acp && replacementId.equals(acp.getUUID())) {
                replacement = acp;
                break;
            }
        }
        if (replacement == null || replacement == self) return;

        try {
            resolvingSkin.set(true);
            cir.setReturnValue(replacement.getSkin());
        } finally {
            resolvingSkin.set(false);
        }
    }
}
