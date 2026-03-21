package io.wifi.starrailexpress.api;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;

/**
 * 角色方法调度器，用于调用角色的各个方法
 */
public class RoleMethodDispatcher {

    /**
     * 调用玩家角色的 onDeath 方法
     */
    public static void callOnDeath(Player victim, boolean spawnBody, @Nullable Player killer,
            ResourceLocation deathReason) {
        SRERole role = getCurrentRole(victim);
        if (role != null) {
            role.onDeath(victim, spawnBody, killer, deathReason);
        }
    }

    /**
     * 调用玩家角色的 onKill 方法
     */
    public static void callOnKill(Player victim, boolean spawnBody, @Nullable Player killer,
            ResourceLocation deathReason) {
        SRERole role = getCurrentRole(killer);
        if (role != null) {
            role.onKill(victim, spawnBody, killer, deathReason);
        }
    }

    /**
     * 调用玩家角色的 onFinishQuest 方法
     */
    public static void callOnFinishQuest(Player player, String quest) {
        SRERole role = getCurrentRole(player);
        if (role != null) {
            if (role.isInnocent()) {
                SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(player);
                shopComponent.addToBalance(50);
            } else if (role.isNeutrals()) {
                SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(player);
                shopComponent.addToBalance(50);
            } else if (role.canUseKiller()) {
                player.level().players().forEach(
                        a -> {
                            if (role.canUseKiller()) {
                                SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(a);
                                shopComponent.addToBalance(5);
                            }
                        });
            }
            role.onFinishQuest(player, quest);
        }
    }

    public static void onStartGame(ServerLevel serverLevel) {
        serverLevel.players().forEach(
                player -> {
                    TMMRoles.COMPONENT_KEYS.forEach(
                            componentKey -> {
                                RoleComponent roleComponent = componentKey.get(player);
                                if (roleComponent != null) {
                                    roleComponent.clear();
                                    componentKey.sync(player);
                                }
                            });
                });
    }

    public static void onEndGame(ServerLevel serverLevel) {
        serverLevel.players().forEach(
                player -> {
                    TMMRoles.COMPONENT_KEYS.forEach(
                            componentKey -> {
                                RoleComponent roleComponent = componentKey.get(player);
                                if (roleComponent != null) {
                                    roleComponent.clear();
                                    componentKey.sync(player);
                                }
                            });
                });
    }

    /**
     * 调用玩家角色的 onPickupItem 方法
     */
    public static boolean callOnPickupItem(Player player, Item item) {
        SRERole role = getCurrentRole(player);
        if (role != null) {
            return !role.cantPickupItem(player).test(item);
        }
        return true;
    }

    /**
     * 调用玩家角色的 serverTick 方法
     */

    public static void callServerTick(ServerPlayer player) {
        var gameComponent = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(player.level());
        callServerTick(player, gameComponent);
    }

    public static void callServerTick(ServerPlayer player, SREGameWorldComponent gameComponent) {
        SRERole role = gameComponent.getRole(player);
        if (role != null) {
            role.serverGameTickEvent(player, gameComponent);
        }
    }

    public static void onInit(SRERole role, MinecraftServer minecraftServer, ServerPlayer player) {
        role.onInit(minecraftServer, player);
        if (role.isAutoReset()) {
            ComponentKey<? extends RoleComponent> componentKey = role.getComponentKey();
            if (componentKey != null) {
                componentKey.get(player).init();
            }
        }
    }

    /**
     * 调用玩家角色的 clientTick 方法
     */
    public static void callClientTick(Player player) {
        var gameComponent = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(player.level());
        SRERole role = gameComponent.getRole(player);
        if (role != null) {
            role.clientGameTickEvent(player, gameComponent);
        }
    }

    /**
     * 调用玩家角色的 rightClickEntity 方法
     */
    public static void callRightClickEntity(Player player, Entity victim) {
        SRERole role = getCurrentRole(player);
        if (role != null) {
            role.rightClickEntity(player, victim);
        }
    }

    /**
     * 调用玩家角色的 leftClickEntity 方法
     */
    public static void callLeftClickEntity(Player player, Entity victim) {
        SRERole role = getCurrentRole(player);
        if (role != null) {
            role.leftClickEntity(player, victim);
        }
    }

    /**
     * 调用玩家角色的 onItemUse 方法
     */
    public static InteractionResultHolder<ItemStack> callOnItemUse(Player player, Level world, InteractionHand hand) {
        SRERole role = getCurrentRole(player);
        if (role != null) {
            return role.onItemUse(player, world, hand);
        }
        return InteractionResultHolder.pass(ItemStack.EMPTY);
    }

    /**
     * 调用玩家角色的 onAbilityUse 方法
     */
    public static void callOnAbilityUse(Player player) {
        SRERole role = getCurrentRole(player);
        if (role != null) {
            role.onAbilityUse(player);
        }
    }

    /**
     * 调用玩家角色的 onUseBlock 方法
     */
    public static InteractionResult callOnUseBlock(Player player, Level world, InteractionHand hand,
            BlockHitResult hitResult) {
        SRERole role = getCurrentRole(player);
        if (role != null) {
            return role.onUseBlock(player, world, hand, hitResult);
        }
        return InteractionResult.PASS;
    }

    /**
     * 获取玩家当前的角色
     */
    private static SRERole getCurrentRole(Player player) {
        if (player.level() == null) {
            return null;
        }

        var gameComponent = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(player.level());
        return gameComponent.getRole(player);
    }
}