package io.wifi.starrailexpress.event;

import io.wifi.starrailexpress.cca.SREPlayerAFKComponent;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;

/**
 * AFK（挂机）检测事件处理器。
 * 负责监听玩家的操作（使用物品、与方块交互、与实体交互），
 * 并在每次操作时更新 AFK 计时器，同时分发角色方法调用。
 *
 * <p>AFK (Away From Keyboard) event handler.
 * Listens for player actions (item use, block interaction, entity interaction)
 * and updates the AFK timer on each action, while also dispatching role method calls.
 */
public class AFKEventHandler {

    /**
     * 注册所有AFK检测相关的事件处理器。
     *
     * <p>Registers all AFK-detection related event handlers.
     */
    public static void register() {
        // 当玩家使用物品时更新AFK计时器
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player != null) {
                SREPlayerAFKComponent afkComponent = SREPlayerAFKComponent.KEY.maybeGet(player).orElse(null);
                if (afkComponent != null) {
                    afkComponent.updateActivity();
                    // 调用角色的技能使用方法
                    io.wifi.starrailexpress.api.RoleMethodDispatcher.callOnItemUse(player, world, hand);
                }
            }
            return InteractionResultHolder.pass(ItemStack.EMPTY);
        });

        // 当玩家与方块交互时更新AFK计时器
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player != null) {
                SREPlayerAFKComponent afkComponent = SREPlayerAFKComponent.KEY.maybeGet(player).orElse(null);
                if (afkComponent != null) {
                    afkComponent.updateActivity();
                    return io.wifi.starrailexpress.api.RoleMethodDispatcher.callOnUseBlock(player, world, hand, hitResult);
                }
            }
            return InteractionResult.PASS;
        });

        // 当玩家与实体交互时更新AFK计时器
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player != null) {
                SREPlayerAFKComponent afkComponent = SREPlayerAFKComponent.KEY.maybeGet(player).orElse(null);
                if (afkComponent != null) {
                    afkComponent.updateActivity();
                }
            }
            return InteractionResult.PASS;
        });
    }
}