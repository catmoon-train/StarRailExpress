package org.agmas.noellesroles.item.charge_item;

import io.wifi.starrailexpress.api.ChargeableItem;
import io.wifi.starrailexpress.cca.PlayerPoisonComponent;
import io.wifi.starrailexpress.client.StaminaRenderer;
import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

public class AntidoteChargeItem implements ChargeableItem {
    @Override
    public int getMaxChargeTime(ItemStack itemStack, Player player) {
        return 6;
    }

    @Override
    public boolean hasSpecialVisualEffects(ItemStack stack, Player player) {
        return true;
    }

    @Override
    public float getMaxStamina(ItemStack stack, Player player) {
        return 6;
    }

    @Override
    public void onFullyCharged(ItemStack stack, Player player) {
        // 触发屏幕边缘效果
        StaminaRenderer.triggerScreenEdgeEffect(0xFF0000, 300L, 0.5f);
    }

    @Override
    public float getChargePercentage(ItemStack stack, Player player, int ticksUsingItem) {
        return Math.min((float) ticksUsingItem / getMaxChargeTime(stack, player), 1f);
    }
}