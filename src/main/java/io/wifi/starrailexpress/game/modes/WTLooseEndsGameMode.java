package io.wifi.starrailexpress.game.modes;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SRETrainWorldComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@SuppressWarnings("deprecation")
public class WTLooseEndsGameMode extends GameMode {
    public final List<Supplier<ItemStack>> looseEndsItems = new ArrayList<>();

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

    public WTLooseEndsGameMode(ResourceLocation identifier) {
        super(identifier, 10, 2);
        initItemList();
    }

    protected void initItemList() {
        // 初始化模式物品列表
        looseEndsItems.add(TMMItems.CROWBAR::getDefaultInstance);
        looseEndsItems.add(TMMItems.DERRINGER::getDefaultInstance);
        looseEndsItems.add(TMMItems.KNIFE::getDefaultInstance);
        // 防御试剂
        looseEndsItems.add(() -> {
            final var defenseVial = TMMItems.DEFENSE_VIAL;
            if (defenseVial != Item.byBlock(net.minecraft.world.level.block.Blocks.AIR)) {
                return defenseVial.getDefaultInstance();
            }
            return null;
        });
    }

    protected void initCoolDownItems(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        int cooldown = GameConstants.getInTicks(0, 10);
        for (ServerPlayer player : players) {
            // 给所有人的武器添加冷却
            ItemCooldowns itemCooldownManager = player.getCooldowns();
            itemCooldownManager.addCooldown(TMMItems.DERRINGER, cooldown);
            itemCooldownManager.addCooldown(TMMItems.KNIFE, cooldown);
        }
    }

    /** 初始化亡命徒物品 */
    protected void initPlayerItems(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : players) {
            player.getInventory().clearContent();
            // 添加亡命徒模式专属物品
            for (Supplier<ItemStack> itemSupplier : looseEndsItems) {
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

    protected void sendWelcomePackets(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent,
            SRERole role) {
        if (role == null)
            return;
        for (ServerPlayer player : players) {
            ServerPlayNetworking.send(player,
                    new AnnounceWelcomePayload(role.identifier().toString(), -1, -1));
        }
    }

    protected void assignModdedRole(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : players) {
            var role = gameWorldComponent.getRole(player);
            ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, role);
        }
    }

    @Override
    public boolean isLooseEndMode() {
        return true;
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        SRETrainWorldComponent.KEY.get(serverWorld).setTimeOfDay(SRETrainWorldComponent.TimeOfDay.SUNDOWN);

        // 先分配职业再发物品：可以根据职业来分配
        initRoles(players, gameWorldComponent);
        initCoolDownItems(players, gameWorldComponent);
        initPlayerItems(players, gameWorldComponent);
        sendWelcomePackets(players, gameWorldComponent, TMMRoles.LOOSE_END);
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        GameUtils.WinStatus winStatus = GameUtils.WinStatus.NONE;

        // check if out of time
        if (!SREGameTimeComponent.KEY.get(serverWorld).hasTime())
            winStatus = GameUtils.WinStatus.TIME;

        // check if last person standing in loose end
        int playersLeft = 0;
        Player lastPlayer = null;
        for (Player player : serverWorld.players()) {
            if (GameUtils.isPlayerAliveAndSurvival(player)) {
                playersLeft++;
                lastPlayer = player;
            }
        }

        if (playersLeft <= 0) {
            var modifiedWinStatus = AllowGameEnd.EVENT.invoker().allowGameEnd(serverWorld, WinStatus.NO_PLAYER, true);
            if (!modifiedWinStatus.equals(WinStatus.NONE)) {
                GameUtils.stopGame(serverWorld);
            }
        }

        if (playersLeft == 1) {
            gameWorldComponent.setLooseEndWinner(lastPlayer.getUUID());
            winStatus = GameUtils.WinStatus.LOOSE_END;
        }

        // game end on win and display
        if (winStatus != GameUtils.WinStatus.NONE
                && gameWorldComponent.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE) {

            SREGameRoundEndComponent.KEY.get(serverWorld).setRoundEndData(serverWorld.players(), winStatus);
            GameUtils.stopGame(serverWorld);
        }
    }

    public boolean hasMood() {
        return false;
    }
}
