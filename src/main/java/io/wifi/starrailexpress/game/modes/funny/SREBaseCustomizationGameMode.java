package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.*;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.util.TickTimer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SREBaseCustomizationGameMode extends GameMode {
    /** 共有物品 */
    public final List<Supplier<ItemStack>> sharedItems = new ArrayList<>();
    /** 游戏模式运行中的定时器 */
    public final List<TickTimer> tickTimers = new ArrayList<>();
    /**
     * @param identifier       游戏的id
     */
    public SREBaseCustomizationGameMode(ResourceLocation identifier) {
        this(identifier, 10, 2);
    }
    public SREBaseCustomizationGameMode(ResourceLocation identifier, int defaultStartTime, int minPlayerCount) {
        super(identifier, defaultStartTime, minPlayerCount);
        constructItemList();
    }
    protected void constructItemList() {
        // 初始化模式物品列表
//        sharedItems.add(TMMItems.CROWBAR::getDefaultInstance);
    }

    @Override
    public boolean shouldRecordPlayerStats() {
        return false;
    }

    public static void broadcastMessageTo(ServerPlayer player, Component messageComponent) {
        BroadcastCommand.BroadcastMessage(player, messageComponent);
    }
    /** 触发角色初始化事件 */
    public static void triggerRoleAssignedEvent(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : players) {
            var role = gameWorldComponent.getRole(player);
            // 触发角色初始化事件
            if (role != null) {
                if (role.canUseKiller()) {
                    SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(player);
                    if (playerShopComponent.balance < GameConstants.getMoneyStart())
                        playerShopComponent.setBalance(GameConstants.getMoneyStart());
                }
                ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, role);
            }
        }
    }

    protected void initCoolDownItems(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {

    }

    /** 初始化物品 */
    protected void initPlayerItems(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : players) {
            player.getInventory().clearContent();
            // 添加模式物品
            for (Supplier<ItemStack> itemSupplier : sharedItems) {
                ItemStack itemStack = itemSupplier.get();
                if (itemStack != null && !itemStack.isEmpty()) {
                    player.addItem(itemStack);
                }
            }
        }
    }

    protected void initRoles(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : players)
            gameWorldComponent.addRole(player, TMMRoles.LOOSE_END);
    }

    protected void sendWelcomePackets(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : players) {
            var role = gameWorldComponent.getRole(player);
            if (role == null)
                continue;
            RoleUtils.sendWelcomeAnnouncement(player, role.identifier(), 0);
        }
    }

    protected void assignModdedRole(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : players) {
            var role = gameWorldComponent.getRole(player);
            ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, role);
        }
    }

    /** 初始化游戏模式定时器：游戏每次初始化时清空并重新填入 */
    protected void initTickTimers(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent, List<ServerPlayer> players) {

    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
                               List<ServerPlayer> players) {
        SRETrainWorldComponent.KEY.get(serverWorld).setTimeOfDay(SRETrainWorldComponent.TimeOfDay.SUNDOWN);

        // 先分配职业再发物品：可以根据职业来分配
        initRoles(players, gameWorldComponent);
        initCoolDownItems(players, gameWorldComponent);
        initPlayerItems(players, gameWorldComponent);
        sendWelcomePackets(players, gameWorldComponent);

        tickTimers.clear();
        initTickTimers(serverWorld, gameWorldComponent, players);
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        super.tickServerGameLoop(serverWorld, gameWorldComponent);
        tickTimers.forEach(TickTimer::tick);
    }
}
