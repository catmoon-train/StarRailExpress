package org.agmas.noellesroles.mixin.roles.traitor;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.original.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * 绝境信徒修饰符 - 刀的物品冷却减半（只能触发一次）
 */
@Mixin(KnifeStabPayload.Receiver.class)
public class DesperateFaithKnifeCooldownMixin {

    @Inject(method = "receive", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemCooldowns;addCooldown(Lnet/minecraft/world/item/Item;I)V",
            ordinal = 0), locals = LocalCapture.CAPTURE_FAILHARD)
    private void onKnifeCooldown(KnifeStabPayload payload, ServerPlayNetworking.Context context, CallbackInfo ci,
            ServerPlayer player) {
        // 检查玩家是否有绝境信徒修饰符
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.level());
        if (modifiers.isModifier(player.getUUID(), TraitorAndModifiers.DESPERATE_FAITH)) {
            // 获取当前冷却时间并减半
            int baseCooldown = GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.KNIFE, 1200);
            int halvedCooldown = baseCooldown / 2;

            // 直接修改cooldowns
            player.getCooldowns().addCooldown(TMMItems.KNIFE, halvedCooldown);

            // 取消原始的cooldown添加
            ci.cancel();
        }
    }
}
