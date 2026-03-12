package io.wifi.starrailexpress.game;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.GameRoundEndComponent;
import io.wifi.starrailexpress.cca.GameTimeComponent;
import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.cca.TrainWorldComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.game.GameFunctions.WinStatus;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
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
            final var defenseVial = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse("noellesroles:defense_vial"));
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
    public void initializeGame(ServerLevel serverWorld, GameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        TrainWorldComponent.KEY.get(serverWorld).setTimeOfDay(TrainWorldComponent.TimeOfDay.SUNDOWN);

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
    public void tickServerGameLoop(ServerLevel serverWorld, GameWorldComponent gameWorldComponent) {
        GameFunctions.WinStatus winStatus = GameFunctions.WinStatus.NONE;

        // check if out of time
        if (!GameTimeComponent.KEY.get(serverWorld).hasTime())
            winStatus = GameFunctions.WinStatus.TIME;

        // check if last person standing in loose end
        int playersLeft = 0;
        Player lastPlayer = null;
        for (Player player : serverWorld.players()) {
            if (GameFunctions.isPlayerAliveAndSurvival(player)) {
                playersLeft++;
                lastPlayer = player;
            }
        }

        if (playersLeft <= 0) {
            var modifiedWinStatus = AllowGameEnd.EVENT.invoker().allowGameEnd(serverWorld, WinStatus.NO_PLAYER, true);
            if (!modifiedWinStatus.equals(WinStatus.NONE)) {
                GameFunctions.stopGame(serverWorld);
            }
        }

        if (playersLeft == 1) {
            gameWorldComponent.setLooseEndWinner(lastPlayer.getUUID());
            winStatus = GameFunctions.WinStatus.LOOSE_END;
        }

        // game end on win and display
        if (winStatus != GameFunctions.WinStatus.NONE
                && gameWorldComponent.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE) {
            var modifiedStatus = AllowGameEnd.EVENT.invoker().allowGameEnd(serverWorld, winStatus, true);
            if (!modifiedStatus.equals(GameFunctions.WinStatus.NONE)) {
                if (!modifiedStatus.equals(GameFunctions.WinStatus.NOT_MODIFY)) {
                    winStatus = modifiedStatus;
                }
                GameRoundEndComponent.KEY.get(serverWorld).setRoundEndData(serverWorld.players(), winStatus);
                GameFunctions.stopGame(serverWorld);
            }
        }
    }
}
