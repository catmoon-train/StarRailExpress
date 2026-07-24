package org.agmas.noellesroles.game.roles.innocence.veteran;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.TrainWeapon;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.harpymodloader.events.GameInitializeEvent;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class VeteranKnifeHandler {

    private static final double INITIAL_KNIFE_TRIGGER_RADIUS_SQR = 14.0D * 14.0D;
    private static final int CHECK_INTERVAL_TICKS = 10;
    private static final Set<UUID> grantedInitialKnife = new HashSet<>();
    private static int tickCounter = 0;

    public static void tick(ServerPlayer player) {
        tickCounter++;
        if (tickCounter % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        tryGrantInitialKnife(player);
    }

    public static void register() {
        GameInitializeEvent.EVENT.register((level, gameWorldComponent, readyPlayerList) -> {
            grantedInitialKnife.clear();
            tickCounter = 0;
        });
        OnGameEnd.EVENT.register((world, gameWorldComponent) -> {
            grantedInitialKnife.clear();
            tickCounter = 0;
        });

        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (killer == null)
                return;
            if (victim == null)
                return;
            if (killer.level().isClientSide())
                return;

            // 检查是否是刀击杀
            if (!deathReason.equals(GameConstants.DeathReasons.KNIFE))
                return;

            // 检查击杀者是否是退伍军人
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(killer.level());
            if (!gameWorld.isRole(killer, ModRoles.VETERAN))
                return;
            ConfigWorldComponent.onPlayerUsedSkill((ServerPlayer) killer);
            // 移除玩家手中的刀
            removeKnifeFromPlayer(killer);
        });
    }

    private static void tryGrantInitialKnife(ServerPlayer player) {
        if (grantedInitialKnife.contains(player.getUUID())) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }
        if (!hasNearbyKnifeHolder(player)) {
            return;
        }

        player.addItem(TMMItems.KNIFE.getDefaultInstance().copy());
        grantedInitialKnife.add(player.getUUID());
    }

    private static boolean hasNearbyKnifeHolder(ServerPlayer veteran) {
        for (Player other : veteran.level().players()) {
            if (other == veteran) {
                continue;
            }
            if (!GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(other)) {
                continue;
            }
            if (other.distanceToSqr(veteran) > INITIAL_KNIFE_TRIGGER_RADIUS_SQR) {
                continue;
            }
            if (isHeldKnife(other.getMainHandItem()) || isHeldKnife(other.getOffhandItem())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isHeldKnife(ItemStack stack) {
        return stack.getItem() instanceof TrainWeapon && stack.is(TMMItemTags.GUNS);
    }

    private static void removeSPKnifeFromPlayer(Player player) {
        // 先检查主手
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.is(ModItems.SP_KNIFE)) {
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        if (mainHand.is(TMMItems.KNIFE)) {
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }

        // 再检查副手
        ItemStack offHand = player.getOffhandItem();
        if (offHand.is(ModItems.SP_KNIFE)) {
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        if (offHand.is(TMMItems.KNIFE)) {
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }

    /**
     * 从玩家身上移除刀
     */
    private static void removeKnifeFromPlayer(Player player) {
        // 先检查主手
        removeSPKnifeFromPlayer(player);
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.is(TMMItems.KNIFE)) {
            mainHand.setCount(0);
            return;
        }

        // 再检查副手
        ItemStack offHand = player.getOffhandItem();
        if (offHand.is(TMMItems.KNIFE)) {
            offHand.setCount(0);
            return;
        }

        // // 最后遍历背包移除刀
        // for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
        // ItemStack stack = player.getInventory().getItem(i);
        // if (stack.is(TMMItems.KNIFE)) {
        // stack.setCount(0);
        // return;
        // }
        // }
    }
}
