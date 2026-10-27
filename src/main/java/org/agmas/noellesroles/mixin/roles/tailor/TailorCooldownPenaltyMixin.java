package org.agmas.noellesroles.mixin.roles.tailor;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ServerItemCooldowns;
import org.agmas.noellesroles.game.roles.innocence.tailor.TailorClothesHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 「裁缝的衣服」的冷却惩罚结算点。
 *
 * <p>攻击身着衣服的玩家后，其主手物品进入原版物品冷却（{@code addCooldown}）时会额外 +5 秒。
 * 原版 {@code addCooldown} 在写入冷却表后必然调用一次 {@code onCooldownStarted(item, ticks)}，
 * 因此在这里把 ticks 改写为 {@code ticks + 惩罚}：以延长后的时长再走一次 {@code addCooldown}
 * （内部会再次触发本回调，但惩罚已被消费，不会递归），并取消本次原始回调，避免重复发包。</p>
 */
@Mixin(ServerItemCooldowns.class)
public class TailorCooldownPenaltyMixin {

    @Inject(method = "onCooldownStarted", at = @At("HEAD"), cancellable = true)
    private void noellesroles$tailorCooldownPenalty(Item item, int ticks, CallbackInfo ci) {
        if (ticks <= 0) {
            return;
        }
        ItemCooldowns self = (ItemCooldowns) (Object) this;
        int extra = TailorClothesHandler.consumeCooldownPenalty(self, item, self.tickCount);
        if (extra <= 0) {
            return;
        }
        self.addCooldown(item, ticks + extra);
        ci.cancel();
    }
}
