package io.wifi.starrailexpress.game;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SRETrainWorldComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@SuppressWarnings("deprecation")
public class WTLooseEndsGameMode extends GameMode {
    public static final List<Supplier<ItemStack>> looseEndsItems = new ArrayList<>();

    static {
        // 防御试剂
        looseEndsItems.add(() -> {
            final var defenseVial = TMMItems.DEFENSE_VIAL;
            if (defenseVial != Item.byBlock(net.minecraft.world.level.block.Blocks.AIR)) {
                return defenseVial.getDefaultInstance();
            }
            return null;
        });
    }

    public WTLooseEndsGameMode(ResourceLocation identifier) {
        super(identifier, 10, 2);
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        SRETrainWorldComponent.KEY.get(serverWorld).setTimeOfDay(SRETrainWorldComponent.TimeOfDay.SUNDOWN);

        for (ServerPlayer player : players) {
            player.getInventory().clearContent();

            ItemStack derringer = new ItemStack(TMMItems.DERRINGER);
            ItemStack knife = new ItemStack(TMMItems.KNIFE);

            int cooldown = GameConstants.getInTicks(0, 10);
            ItemCooldowns itemCooldownManager = player.getCooldowns();
            itemCooldownManager.addCooldown(TMMItems.DERRINGER, cooldown);
            itemCooldownManager.addCooldown(TMMItems.KNIFE, cooldown);

            player.addItem(new ItemStack(TMMItems.CROWBAR));
            player.addItem(derringer);
            player.addItem(knife);

            // 添加亡命徒模式专属物品
            for (Supplier<ItemStack> itemSupplier : looseEndsItems) {
                ItemStack itemStack = itemSupplier.get();
                if (itemStack != null && !itemStack.isEmpty()) {
                    player.addItem(itemStack);
                }
            }

            gameWorldComponent.addRole(player, TMMRoles.LOOSE_END);

            ServerPlayNetworking.send(player,
                    new AnnounceWelcomePayload(TMMRoles.LOOSE_END.identifier().toString(), -1, -1));
        }
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
            var modifiedStatus = AllowGameEnd.EVENT.invoker().allowGameEnd(serverWorld, winStatus, true);
            if (!modifiedStatus.equals(GameUtils.WinStatus.NONE)) {
                if (!modifiedStatus.equals(GameUtils.WinStatus.NOT_MODIFY)) {
                    winStatus = modifiedStatus;
                }
                SREGameRoundEndComponent.KEY.get(serverWorld).setRoundEndData(serverWorld.players(), winStatus);
                GameUtils.stopGame(serverWorld);
            }
        }
    }
}
