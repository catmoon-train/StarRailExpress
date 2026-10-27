package org.agmas.noellesroles.mixin;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.command_block.Error404Role;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.wifi.starrailexpress.api.GameMode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** 强制死亡忽略所有否决事件，只能在入口保护高维；也避免给被取消的死亡计分。 */
@Mixin(GameMode.class)
public abstract class Error404HighDimensionMixin {
    @Inject(method = "killPlayer", at = @At("HEAD"), cancellable = true, remap = false)
    private void error404$protectDimension(Player victim, boolean spawnBody, Player killer,
            ResourceLocation reason, boolean forced, CallbackInfo ci) {
        if (Error404Role.isHighDimension(victim)) {
            ci.cancel();
            return;
        }
        // 傀儡追击和空间湮灭记在主人头上，不是高维本体攻击。
        if (Error404Role.isHighDimension(killer) && !Error404Role.VOID_DEATH.equals(reason)
                && !Noellesroles.id("error404_puppet").equals(reason)) ci.cancel();
    }
}
