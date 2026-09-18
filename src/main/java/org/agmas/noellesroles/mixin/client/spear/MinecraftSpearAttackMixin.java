package org.agmas.noellesroles.mixin.client.spear;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.packet.SpearStabC2SPacket;
import org.agmas.noellesroles.spear.SpearConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.jetbrains.annotations.Nullable;

/**
 * 手持矛时左键不再是原版近战，而是发一个「直刺」包交给服务端结算（可穿透多个目标）。
 */
@Mixin(Minecraft.class)
public class MinecraftSpearAttackMixin {

    @Shadow
    @Nullable
    public LocalPlayer player;

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void spear$stab(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer player = this.player;
        if (player == null) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!SpearConfig.isSpear(stack)) {
            return;
        }
        // 矛必须满蓄力才能直刺
        if (player.getAttackStrengthScale(0.0F) < SpearConfig.MINIMUM_ATTACK_CHARGE) {
            cir.setReturnValue(false);
            return;
        }
        // 耐久耗尽无法攻击
        if (stack.getMaxDamage() > 0 && stack.getDamageValue() >= stack.getMaxDamage()) {
            cir.setReturnValue(false);
            return;
        }
        SpearConfig.piercing().playSound(player);
        ClientPlayNetworking.send(new SpearStabC2SPacket(player.getId()));
        player.swing(InteractionHand.MAIN_HAND);
        player.resetAttackStrengthTicker();
        cir.setReturnValue(true);
    }
}
