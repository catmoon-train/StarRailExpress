/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.rules.ChatHudRules;
import io.wifi.starrailexpress.rules.ReplayRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.agmas.noellesroles.content.block_entity.DevilRouletteTableEntity;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.minigame.DevilRouletteGame;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Supplier;

/**
 * 轮盘赌锦标赛
 * <p>
 * - 模式特性：玩家两两分组先后进行多轮赛
 * 每轮后可购买道具（根据本轮剩余生命值获得金币）：包括局内道具（便宜，仅对局内使用增加获胜可能性）和场外道具（较贵，如一次性手枪直接打死对手相当于掀桌子）
 * - 局内死亡条件：生命值不足将死亡，或被对手使用场外道具击杀
 * - 局外死亡条件：死亡次数累计到一定值死亡旁观
 * </p>
 */
public class SREDevilRouletteGameMode extends SREBaseCustomizationGameMode {
    // 轮盘赌模式允许聊天
    static {
        ReplayRules.canSendReplay.add((p) -> {
            if (p == null)
                return false;
            return SREGameWorldComponent.KEY.get(p.level()).getGameMode().identifier
                    .equals(SREGameModes.DEVIL_ROULETTE_ID);
        });
        ChatHudRules.canUseChatHudPlayer.add((p) -> {
            if (p == null)
                return false;
            return SREGameWorldComponent.KEY.get(p.level()).getGameMode().identifier
                    .equals(SREGameModes.DEVIL_ROULETTE_ID);
        });
    }

    public static interface StartMatchHandler {
        void onStartMatch(DevilRouletteTableEntity tableEntity, Player player1, Player player2);
    }

    @Override
    public void finalizeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        super.finalizeGame(serverWorld, gameWorldComponent);
        rouletteTablePos.clear();
    }

    /**
     * @param identifier the game mode identifier
     */
    public SREDevilRouletteGameMode(ResourceLocation identifier) {
        super(identifier, GAME_TIME_MINUTES, 2);
    }

    @Override
    protected void constructItemList() {
        sharedItems.add(() -> new ItemStack(TMMItems.DEFENSE_VIAL));
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        super.initializeGame(serverWorld, gameWorldComponent, players);
        reset();
        // 不再自动匹配玩家与赌桌：只把地图内的赌桌切到轮盘赌模式，由玩家自行入座开局
        updateTableGameModes(serverWorld);
    }

    @Override
    public boolean shouldRecordPlayerStats() {
        // 需要让土块的独立胜利真正计入统计（胜负标记与职业胜场）
        return true;
    }

    protected void initRoles(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : players)
            gameWorldComponent.addRole(player, SpecialGameModeRoles.DIRT);
    }

    @Override
    protected void initPlayerItems(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : players) {
            player.getInventory().clearContent();
            // 与其它模式一样发放房间钥匙
            player.addItem(createRoomKey(player));
            // 给予万能钥匙
            player.addItem(new ItemStack(ModItems.MASTER_KEY));
            // 添加模式专属物品
            for (Supplier<ItemStack> itemSupplier : sharedItems) {
                ItemStack itemStack = itemSupplier.get();
                if (itemStack != null && !itemStack.isEmpty()) {
                    player.addItem(itemStack);
                }
            }
            // 开局发放道具
            if (!DevilRouletteGame.rouletteItems.isEmpty()) {
                RandomSource randomSource = player.getRandom();
                for (int i = 0; i < 3; ++i) {
                    player.addItem(DevilRouletteGame.rouletteItems.get(
                            randomSource.nextInt(DevilRouletteGame.rouletteItems.size())).get());
                }
            }
        }
    }

    /**
     * 将地图内的轮盘赌桌切换为轮盘赌模式
     * <p>
     * 轮盘赌模式不再自动分配玩家与桌子，玩家需自行入座并开局
     * </p>
     */
    protected void updateTableGameModes(ServerLevel serverLevel) {
        for (BlockPos pos : rouletteTablePos) {
            if (serverLevel.getBlockEntity(pos) instanceof DevilRouletteTableEntity tableEntity) {
                tableEntity.setGameMode(DevilRouletteGame.GameMode.Roulette);
            }
        }
    }

    /**
     * 移除所有淘汰的玩家
     */
    public void removeAllIfPlayerEliminated(ServerLevel level) {
        winners.removeIf(playerID -> playerID == null || GameUtils.isPlayerEliminated(level.getPlayerByUUID(playerID)));
        losers.removeIf(playerID -> playerID == null || GameUtils.isPlayerEliminated(level.getPlayerByUUID(playerID)));
    }

    public void addWinner(@NotNull Player player) {
        if (!winners.contains(player.getUUID()))
            winners.add(player.getUUID());
    }

    public void addLooser(@NotNull Player player) {
        if (!losers.contains(player.getUUID()))
            losers.add(player.getUUID());
    }

    public void addRouletteTableEntity(BlockPos pos) {
        rouletteTablePos.add(pos);
    }

    public HashSet<BlockPos> getRouletteTableEntities() {
        return rouletteTablePos;
    }

    @Override
    public boolean hasSafeTime() {
        return false;
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        super.tickServerGameLoop(serverWorld, gameWorldComponent);

        // 保持地图内的赌桌处于轮盘赌模式（不自动分配玩家，由玩家自行入座开局）
        updateTableGameModes(serverWorld);

        GameUtils.WinStatus winStatus = GameUtils.WinStatus.NONE;
        int playerCounter = 0;
        ServerPlayer lastSurvivor = null;
        for (ServerPlayer player : serverWorld.players()) {
            // check if some dirt blocks are still alive
            if (!GameUtils.isPlayerEliminated(player)) {
                ++playerCounter;
                lastSurvivor = player;
            }
        }

        // 存活到最后的土块取得独立胜利
        if (playerCounter == 1 && lastSurvivor != null
                && gameWorldComponent.isRole(lastSurvivor, SpecialGameModeRoles.DIRT)) {
            winStatus = GameUtils.WinStatus.CUSTOM;
        }
        // 无人存活：无人获胜
        else if (playerCounter <= 1) {
            winStatus = GameUtils.WinStatus.NO_PLAYER;
        }

        // 倒计时归零：无人获胜
        if (!SREGameTimeComponent.KEY.get(serverWorld).hasTime())
            winStatus = GameUtils.WinStatus.NO_PLAYER;

        // game end on win and display
        if (winStatus != GameUtils.WinStatus.NONE
                && gameWorldComponent.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE) {
            if (winStatus == GameUtils.WinStatus.CUSTOM) {
                // 土块独立胜利：使用职业自身的 id 结算，保证胜利真正计入土块
                RoleUtils.customWinnerWin(serverWorld, GameUtils.WinStatus.CUSTOM,
                        SpecialGameModeRoles.DIRT.identifier().getPath(),
                        OptionalInt.of(SpecialGameModeRoles.DIRT.color()));
            } else {
                SREGameRoundEndComponent.KEY.get(serverWorld).setRoundEndData(serverWorld.players(), winStatus);
                GameUtils.stopGame(serverWorld);
            }

            for (BlockPos pos : rouletteTablePos) {
                if (serverWorld.getBlockEntity(pos) instanceof DevilRouletteTableEntity entity)
                    entity.reset();
            }
        }
    }

    /**
     * 创建玩家所属房间的钥匙（与其它模式开局发放的一致）
     */
    public static ItemStack createRoomKey(ServerPlayer player) {
        ItemStack itemStack = new ItemStack(TMMItems.KEY);
        int roomNumber = GameUtils.roomToPlayer.getOrDefault(player.getUUID(), 1);
        itemStack.update(DataComponents.LORE, ItemLore.EMPTY, component -> new ItemLore(
                Component.nullToEmpty("Room " + roomNumber)
                        .toFlatList(Style.EMPTY.withItalic(false).withColor(0xFF8C00))));
        return itemStack;
    }

    protected void reset() {
        winners.clear();
        losers.clear();
    }

    /**
     * 轮盘赌模式限时：30分钟
     */
    public static final int GAME_TIME_MINUTES = 30;
    /**
     * 游戏对局结束每点生命值转化的金币数
     */
    public static final int WINNER_COIN = 100;
    protected static final HashSet<BlockPos> rouletteTablePos = new HashSet<>();
    protected final List<UUID> winners = new ArrayList<>();
    protected final Queue<UUID> losers = new ArrayDeque<>();
}