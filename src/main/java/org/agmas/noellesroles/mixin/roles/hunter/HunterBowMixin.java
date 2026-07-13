package org.agmas.noellesroles.mixin.roles.hunter;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class HunterBowMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void noellesroles$hunterBowPowerEnchant(CallbackInfo ci) {
        if (SRE.isLobby)
            return;

        Player player = (Player) (Object) this;
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        ServerLevel serverLevel = serverPlayer.serverLevel();

        // 检查是否是猎人角色
        if (!SREGameWorldComponent.KEY.get(serverLevel).isRole(player.getUUID(), ModRoles.HUNTER))
            return;

        // 检查主手是否持有弓
        ItemStack mainHandItem = player.getMainHandItem();
        if (!mainHandItem.is(Items.BOW))
            return;

        // 检查是否已有力量V附魔
        boolean hasPower = false;
        for (java.util.Map.Entry<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>, Integer> entry : mainHandItem
                .getEnchantments().entrySet()) {
            String enchantmentId = entry.getKey().unwrapKey().map(key -> key.location().toString()).orElse("");
            if (enchantmentId.contains("minecraft:power")) {
                hasPower = true;
                // 检查等级是否为5，如果不是则更新
                if (entry.getValue() != 5) {
                    mainHandItem.remove(DataComponents.ENCHANTMENTS);
                    hasPower = false;
                }
                break;
            }
        }
        if (!hasPower) {
            // 没有力量附魔或者等级不对，添加力量5
            mainHandItem.enchant(serverLevel.registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders()
                    .filter(holder -> {
                        return holder.is((Enchantments.POWER));
                    }).findFirst().get(), 5);
        }
    }
}
