package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Player.class)
public class RiptideTridentMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void noellesroles$checkRiptideCollision(CallbackInfo ci) {
        if (SRE.isLobby)
            return;

        Player player = (Player) (Object) this;
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        ServerLevel serverLevel = serverPlayer.serverLevel();

        // 检查是否是海王或水鬼角色
        boolean isSeaKing = SREGameWorldComponent.KEY.get(serverLevel).isRole(player.getUUID(), ModRoles.SEA_KING);
        boolean isWaterGhost = SREGameWorldComponent.KEY.get(serverLevel).isRole(player.getUUID(),
                ModRoles.WATER_GHOST);

        boolean isUsingRiptide = player.isAutoSpinAttack();

        // 海王：进入水中自动获得海豚的恩惠
        if (isSeaKing && (player.isInWater() || player.isUnderWater())) {
            player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 40, 0, true, false, true));
        }

        // 潜水靴深海探索者3附魔 - 所有玩家检查脚部装备
        ItemStack feetItem = player.getItemBySlot(EquipmentSlot.FEET);
        if (feetItem.is(ModItems.DIVING_BOOTS)) {
            boolean hasDepthStrider = false;
            for (java.util.Map.Entry<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>, Integer> entry : feetItem
                    .getEnchantments().entrySet()) {
                String enchantmentId = entry.getKey().unwrapKey().map(key -> key.location().toString()).orElse("");
                if (enchantmentId.contains("minecraft:depth_strider")) {
                    hasDepthStrider = true;
                    // 检查等级是否为3，如果不是则更新
                    if (entry.getValue() != 3) {
                        feetItem.remove(DataComponents.ENCHANTMENTS);
                        hasDepthStrider = false;
                    }
                    break;
                }
            }
            if (!hasDepthStrider) {
                // 没有深海探索者附魔，或者等级不对，添加深海探索者3
                feetItem.enchant(serverLevel.registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders()
                        .filter(holder -> {
                            return holder.is((Enchantments.DEPTH_STRIDER));
                        }).findFirst().get(), 3);
            }
        }

        if (!isSeaKing && !isWaterGhost)
            return;

        // 检查主手是否持有三叉戟
        ItemStack mainHandItem = player.getMainHandItem();
        if (!mainHandItem.is(Items.TRIDENT))
            return;

        // 统一处理海王和水鬼的附魔
        if (isSeaKing) {
            // 海王：忠诚3
            boolean hasLoyalty = false;
            for (java.util.Map.Entry<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>, Integer> entry : mainHandItem
                    .getEnchantments().entrySet()) {
                String enchantmentId = entry.getKey().unwrapKey().map(key -> key.location().toString()).orElse("");
                if (enchantmentId.contains("minecraft:loyalty")) {
                    hasLoyalty = true;
                    break;
                }
            }
            if (!hasLoyalty) {
                mainHandItem.enchant(serverLevel.registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders()
                        .filter(holder -> {
                            return holder.is((Enchantments.LOYALTY));
                        }).findFirst().get(), 3);
            }
        } else if (isWaterGhost) {
            // 水鬼：激流2（先检查是否已有激流附魔）
            boolean hasRiptide = false;
            for (java.util.Map.Entry<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>, Integer> entry : mainHandItem
                    .getEnchantments().entrySet()) {
                String enchantmentId = entry.getKey().unwrapKey().map(key -> key.location().toString()).orElse("");
                if (enchantmentId.contains("minecraft:riptide")) {
                    hasRiptide = true;
                    // 检查等级是否为2，如果不是则更新
                    if (entry.getValue() != 2) {
                        mainHandItem.remove(DataComponents.ENCHANTMENTS);
                        hasRiptide = false;
                    }
                    break;
                }
            }
            if (!hasRiptide) {
                // 没有激流附魔，或者等级不对，添加激流2
                mainHandItem.enchant(serverLevel.registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders()
                        .filter(holder -> {
                            return holder.is((Enchantments.RIPTIDE));
                        }).findFirst().get(), 2);
            }
        }
        // 检查三叉戟是否有激流附魔
        boolean hasRiptide = false;
        for (java.util.Map.Entry<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>, Integer> entry : mainHandItem
                .getEnchantments().entrySet()) {
            String enchantmentId = entry.getKey().unwrapKey().map(key -> key.location().toString()).orElse("");
            if (enchantmentId.contains("minecraft:riptide")) {
                hasRiptide = true;
                break;
            }
        }

        if (!hasRiptide)
            return;

        // 激流状态：在水中/雨中
        boolean isInWaterOrRain = player.isInWaterOrRain();
        if (!isInWaterOrRain)
            return;

        // 检查玩家是否正在使用激流技能
        // 只有在使用激流技能时才进行碰撞检测

        if (!isUsingRiptide)
            return;

        // 在激流状态期间持续检测碰撞
        // 检测碰撞 - 使用扩大的碰撞箱，参考波纹勋章的实现方式
        AABB hitBox = player.getBoundingBox().inflate(1.5);
        List<ServerPlayer> nearbyPlayers = serverLevel.getEntitiesOfClass(
                ServerPlayer.class,
                hitBox);

        // 遍历所有附近的玩家，可以连续击杀多个
        for (ServerPlayer target : nearbyPlayers) {
            if (target != player && !target.isSpectator() && !target.isCreative()) {
                // 检查目标是否在激流撞击范围内
                double distance = player.position().distanceTo(target.position());
                if (distance < 2.5 && GameUtils.isPlayerAliveAndSurvival(target)) { // 激流撞击范围
                    GameUtils.killPlayer(target, true, serverPlayer, SRE.id("trident"));
                    if (isWaterGhost) {
                        // 水鬼：激流三叉戟击杀后进入30秒冷却
                        player.getCooldowns().addCooldown(Items.TRIDENT, 20 * 30);
                    }
                }
            }
        }
    }
}
