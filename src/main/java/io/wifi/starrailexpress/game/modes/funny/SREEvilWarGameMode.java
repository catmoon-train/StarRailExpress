package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.WTLooseEndsGameMode;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentPool;
import org.agmas.noellesroles.component.StalkerPlayerComponent;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 邪恶战局
 * <p>
 *     模式特性：每 7 狼（开局 1000 金币）对战 1 超级亡命徒（无友军透视，但是必定矮小）
 * </p>
 */
public class SREEvilWarGameMode extends WTLooseEndsGameMode {
    public static final List<SRERole> BANED_ROLES = new ArrayList<>();
    protected static AttributeModifier tinyModifier = new AttributeModifier(
            StupidExpress.id("tiny_modifier"), -0.15, AttributeModifier.Operation.ADD_VALUE);
    public static final int ADD_BALANCE_TIME = 600;
    int curTick = 0;
    public SREEvilWarGameMode(ResourceLocation identifier) {
        super(identifier);
        addBanedRoles();
    }
    protected void addBanedRoles() {
        // 禁用 水鬼，观者，布袋鬼
        BANED_ROLES.add(ModRoles.WATER_GHOST);
        BANED_ROLES.add(ModRoles.WATCHER);
        BANED_ROLES.add(ModRoles.MA_CHEN_XU);
    }
    @Override
    protected void initItemList() {
        super.initItemList();
        looseEndsItems.add(ModItems.PATROLLER_REVOLVER::getDefaultInstance);
        looseEndsItems.add(TMMItems.DEFENSE_VIAL::getDefaultInstance);
    }
    @Override
    protected void initCoolDownItems(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        super.initCoolDownItems(players, gameWorldComponent);
        int cooldown = GameConstants.getInTicks(0, 10);
        for (ServerPlayer player : players) {
            // 给所有人的武器添加冷却
            ItemCooldowns itemCooldownManager = player.getCooldowns();
            itemCooldownManager.addCooldown(ModItems.PATROLLER_REVOLVER, cooldown);
        }
    }
    @Override
    protected void initRoles(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        // 每有一组狼人产生一个超级亡命徒：8人局对应 7 狼 1 亡命徒
        int superLooseEndCount = players.size() / (SREConfig.instance().evilWarKillGroupNumber + 1);
        superLooseEndCount = Math.max(superLooseEndCount, 1);
        if (superLooseEndCount > players.size())
            return;
        // 生成杀手池
        RoleAssignmentPool killerPool = RoleAssignmentPool.createUnlimited("Killer",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        role.canUseKiller() &&
                        !role.isInnocent() &&
                        role != TMMRoles.CIVILIAN &&
                        // 禁用角色
                        !BANED_ROLES.contains(role)
        );
        List<SRERole> assignedKillers = killerPool.selectRoles(players.size() - superLooseEndCount);

        RandomSource random = RandomSource.create();
        // 如果狼池不够，生成普通杀手池索引, 同时生成超级亡命徒所在索引
        List<Integer> numbers = new ArrayList<>();
        for (int i = 1; i <= players.size(); i++) {
            numbers.add(i);
        }

        Collections.shuffle(numbers);
        // 前 superLooseEndeCount 个是亡命徒，剩下的为普通杀手
        List<Integer> otherAssignedIdxGroup = numbers.subList(0, superLooseEndCount + players.size() - assignedKillers.size());
        otherAssignedIdxGroup.sort(Integer::compareTo);

        // 当索引等于亡命徒所在索引：分配亡命徒，否则分配杀手
        for (int i = 0, curOtherIdx = 0, curKillerIdx = 0; i < players.size(); ++i) {
            if (curOtherIdx < superLooseEndCount && i == otherAssignedIdxGroup.get(curOtherIdx)) {
                gameWorldComponent.addRole(players.get(i), ModRoles.SUPER_LOOSE_END);
                ++curOtherIdx;
            }
            else {
                if (curOtherIdx < otherAssignedIdxGroup.size() && i == otherAssignedIdxGroup.get(curOtherIdx)) {
                    gameWorldComponent.addRole(players.get(i), TMMRoles.KILLER);
                    ++curOtherIdx;
                }
                else if (curKillerIdx < assignedKillers.size()) {
                    SRERole role = assignedKillers.get(curKillerIdx++);
                    gameWorldComponent.addRole(players.get(i), role);
                    SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(players.get(i));
                    // 阴谋家首次获取金币减少
                    if (role == ModRoles.CONSPIRATOR) {
                        playerShopComponent.setBalance(-300);
                    // 潜行直接进入二阶段
                    } else if (role == ModRoles.STALKER) {
                        StalkerPlayerComponent stalkerPlayerComponent = StalkerPlayerComponent.KEY.get(players.get(i));
                        stalkerPlayerComponent.advanceToPhase2();
                    }
                }
                else
                    gameWorldComponent.addRole(players.get(i), TMMRoles.KILLER);
            }
        }
    }
    /** 初始化物品 */
    @Override
    protected void initPlayerItems(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : players) {
            player.getInventory().clearContent();
            if (gameWorldComponent.isRole(player, ModRoles.SUPER_LOOSE_END))
            {
                // 添加亡命徒模式专属物品
                for (Supplier<ItemStack> itemSupplier : looseEndsItems) {
                    ItemStack itemStack = itemSupplier.get();
                    if (itemStack != null && !itemStack.isEmpty()) {
                        player.addItem(itemStack);
                    }
                }
            }
        }
    }
    @Override
    protected void sendPackets(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : players) {
            var role = gameWorldComponent.getRole(player);
            ServerPlayNetworking.send(player,
                    new AnnounceWelcomePayload(role.getIdentifier().toString(), 0,0));
        }
    }
    protected void initModifier(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent, ServerLevel serverWorld) {
        WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(serverWorld);
        // 所有亡命徒都有矮小修饰符
        for (ServerPlayer player : players) {
            if (gameWorldComponent.isRole(player, ModRoles.SUPER_LOOSE_END)) {
                worldModifierComponent.addModifier(player.getUUID(), SEModifiers.TINY, false);
                // 使玩家缩小
                Objects.requireNonNull(player.getAttribute(Attributes.SCALE)).removeModifier(tinyModifier);
                Objects.requireNonNull(player.getAttribute(Attributes.SCALE)).addPermanentModifier(tinyModifier);
            }
        }
        // 一次性同步
        worldModifierComponent.sync();
    }
    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent, List<ServerPlayer> players) {
        super.initializeGame(serverWorld, gameWorldComponent, players);
        initModifier(players, gameWorldComponent, serverWorld);
        curTick = 0;
    }
    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        GameUtils.WinStatus winStatus = GameUtils.WinStatus.NONE;

        boolean civilianAlive = false;
        for (ServerPlayer player : serverWorld.players()) {
            // passive money
            if (gameWorldComponent.canAutoAddMoney(player)) {
//                Integer balanceToAdd = GameConstants.PASSIVE_MONEY_TICKER.apply(serverWorld.getGameTime());
//                if (balanceToAdd > 0)
//                    SREPlayerShopComponent.KEY.get(player).addToBalance(balanceToAdd);
                if (curTick++ >= ADD_BALANCE_TIME) {
                    SREPlayerShopComponent.KEY.get(player).addToBalance(500);
                    curTick = 0;
                }
            }

            // check if some civilians are still alive
            if (gameWorldComponent.isInnocent(player) && !GameUtils.isPlayerEliminated(player)) {
                civilianAlive = true;
            }
        }

        // check killer win condition (killed all civilians)
        if (!civilianAlive) {
            winStatus = GameUtils.WinStatus.KILLERS;
        }

        // 检查场上是否存在亡命徒
        if (winStatus != GameUtils.WinStatus.NONE) {
            boolean hasLooseEndAlive = false;
            List<ServerPlayer> lastLooseEnds = new ArrayList<>();

            for (ServerPlayer player : serverWorld.players()) {
                if ((gameWorldComponent.isRole(player, TMMRoles.LOOSE_END) || gameWorldComponent.isRole(player, ModRoles.SUPER_LOOSE_END))
                        && !GameUtils.isPlayerEliminated(player)) {
                    hasLooseEndAlive = true;
                    lastLooseEnds.add(player);
                }
            }

            // 如果只有亡命徒存活，且没有其他存活玩家，触发亡命徒获胜
            if (hasLooseEndAlive) {
                // 检查是否有其他非亡命徒的存活玩家
                boolean hasOtherAlive = false;
                for (ServerPlayer player : serverWorld.players()) {
                    if ((!gameWorldComponent.isRole(player, TMMRoles.LOOSE_END)
                            && !gameWorldComponent.isRole(player, ModRoles.SUPER_LOOSE_END))
                            && !GameUtils.isPlayerEliminated(player)) {
                        hasOtherAlive = true;
                        break;
                    }
                }
                if (!hasOtherAlive) {
                    winStatus = GameUtils.WinStatus.LOOSE_END;
                    // 补充 CustomWinnerID: loose_end
                    var roundEnd = SREGameRoundEndComponent.KEY.get(serverWorld);
                    roundEnd.CustomWinnerID = "loose_end";
                    for (ServerPlayer looseEnd : lastLooseEnds)
                        roundEnd.CustomWinnerPlayers.add(looseEnd.getUUID());
                } else {
                    // 有其他玩家存活，游戏继续
                    winStatus = GameUtils.WinStatus.NONE;
                }
            }
        }

        // check if out of time
        if (!SREGameTimeComponent.KEY.get(serverWorld).hasTime())
            winStatus = GameUtils.WinStatus.TIME;

//        GameUtils.WinStatus modifiedWinStatus = AllowGameEnd.EVENT.invoker().allowGameEnd(serverWorld, winStatus, false);
//        if (!modifiedWinStatus.equals(GameUtils.WinStatus.NOT_MODIFY)) {
//            winStatus = modifiedWinStatus;
//        }
        // game end on win and display
        if (winStatus != GameUtils.WinStatus.NONE
                && gameWorldComponent.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE) {
            SREGameRoundEndComponent.KEY.get(serverWorld).setRoundEndData(serverWorld.players(), winStatus);
            GameUtils.stopGame(serverWorld);
        }
    }

}
