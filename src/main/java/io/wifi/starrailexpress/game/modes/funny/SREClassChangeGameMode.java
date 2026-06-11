package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.api.RepairRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SRERoleWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;
import org.agmas.harpymodloader.events.ModifierRemoved;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentPool;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.content.item.LetterItem;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.Nullable;
import pro.fazeclan.river.stupid_express.StupidExpress;

import java.util.*;

/**
 * 职业变换模式 (haiman:class_change)
 * <p>
 * 开局与普通谋杀分配职业一致。
 * 游戏时间每过2分钟，场上的玩家随机变成其它职业，
 * 随机更换身上的修饰符并发送欢迎报幕，
 * 同时清除玩家背包中除了key和信件以外的所有物品。
 */
public class SREClassChangeGameMode extends SREMurderGameMode {

    // 职业变换间隔（tick）：2分钟
    private static final long TRANSFORMATION_INTERVAL = 2 * 60 * 20;

    // 下次变换的gameTime
    private long nextTransformationTime = -1;

    public SREClassChangeGameMode(net.minecraft.resources.ResourceLocation identifier) {
        super(identifier, 10, 6);
    }

    @Override
    public boolean shouldRecordPlayerStats() {
        return true;
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        // 复用谋杀模式的初始化和角色分配
        super.initializeGame(serverWorld, gameWorldComponent, players);

        // 设置第一次变换的时间
        nextTransformationTime = serverWorld.getGameTime() + TRANSFORMATION_INTERVAL;
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        super.tickServerGameLoop(serverWorld, gameWorldComponent);

        // 只在游戏活跃时执行变换逻辑
        if (gameWorldComponent.getGameStatus() != SREGameWorldComponent.GameStatus.ACTIVE) {
            return;
        }

        // 检查是否到了变换时间
        if (nextTransformationTime > 0 && serverWorld.getGameTime() >= nextTransformationTime) {
            performRoleTransformation(serverWorld, gameWorldComponent);
            // 设置下一次变换时间
            nextTransformationTime = serverWorld.getGameTime() + TRANSFORMATION_INTERVAL;
        }
    }

    @Override
    public void finalizeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        super.finalizeGame(serverWorld, gameWorldComponent);
        nextTransformationTime = -1;
    }

    // ==================== 核心变换逻辑 ====================

    /**
     * 执行职业变换
     */
    private void performRoleTransformation(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        // 获取所有存活且非旁观者/非创造模式的玩家
        List<ServerPlayer> transformablePlayers = new ArrayList<>();
        for (ServerPlayer player : serverWorld.players()) {
            if (GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)
                    && !GameUtils.isPlayerEliminated(player)
                    && !player.isSpectator()
                    && !player.isCreative()) {
                transformablePlayers.add(player);
            }
        }

        if (transformablePlayers.isEmpty()) {
            return;
        }

        SRERoleWorldComponent roleWorldComponent = SRERoleWorldComponent.KEY.get(serverWorld);

        // ===== 第一步：统计当前场上各阵营存活玩家数量 =====
        // 注意：普通杀手(TMMRoles.KILLER)、猫娘杀手的死亡不算杀手阵营死亡
        //       除了教父以外的家族成员不算中立阵营死亡（目前以isFamilyRole判断）
        int aliveKillerCount = 0;
        int aliveVigilanteCount = 0;
        int aliveNeutralCount = 0;

        for (ServerPlayer player : transformablePlayers) {
            SRERole role = roleWorldComponent.getRole(player);
            if (role == null) continue;

            int roleType = PlayerRoleWeightManager.getRoleType(role);
            if (isDeathIgnoredKillerRole(role)) {
                // 普通杀手/猫娘杀手不属于杀手阵营死亡统计
                continue;
            }
            if (isDeathIgnoredNeutralRole(role)) {
                // 家族成员（除教父外）不属于中立阵营死亡统计
                continue;
            }

            switch (roleType) {
                case 4: // 杀手
                    aliveKillerCount++;
                    break;
                case 5: // 警长
                    aliveVigilanteCount++;
                    break;
                case 2:
                case 3: // 中立
                    aliveNeutralCount++;
                    break;
            }
        }

        // ===== 第二步：构建各阵营角色池 =====
        // 过滤条件：排除非谋杀模式职业、修机模式职业、亡命徒/超级亡命徒、
        //          普通杀手、猫娘杀手、除教父以外的家族成员、setOtherModeRole(true)的职业
        List<SRERole> availableRoles = new ArrayList<>(StupidExpress.getEnableRoles(true));
        availableRoles.removeIf(role -> role == null
                || role.isOtherModeRole()
                || (role instanceof RepairRole)
                || role == TMMRoles.LOOSE_END
                || role == SpecialGameModeRoles.SUPER_LOOSE_END
                || isBannedKillerRole(role) // 普通杀手、猫娘杀手
                || isFamilyRoleExceptGodfather(role) // 除教父以外的家族成员
                || Harpymodloader.VANNILA_ROLES.contains(role));

        // 杀手池
        RoleAssignmentPool killerPool = RoleAssignmentPool.create("ClassChangeKiller",
                role -> availableRoles.contains(role)
                        && PlayerRoleWeightManager.getRoleType(role) == 4
                        && !isDeathIgnoredKillerRole(role));
        // 警长池
        RoleAssignmentPool vigilantePool = RoleAssignmentPool.create("ClassChangeVigilante",
                role -> availableRoles.contains(role)
                        && PlayerRoleWeightManager.getRoleType(role) == 5);
        // 中立池
        RoleAssignmentPool neutralPool = RoleAssignmentPool.create("ClassChangeNeutral",
                role -> availableRoles.contains(role)
                        && (PlayerRoleWeightManager.getRoleType(role) == 2
                                || PlayerRoleWeightManager.getRoleType(role) == 3)
                        && !isDeathIgnoredNeutralRole(role));
        // 平民池
        RoleAssignmentPool civilianPool = RoleAssignmentPool.create("ClassChangeCivilian",
                role -> availableRoles.contains(role)
                        && PlayerRoleWeightManager.getRoleType(role) == 1
                        );

        // ===== 第三步：为每个可变换玩家分配新职业 =====
        int remainingKillers = aliveKillerCount;
        int remainingVigilantes = aliveVigilanteCount;
        int remainingNeutrals = aliveNeutralCount;

        // 随机打乱玩家顺序
        Collections.shuffle(transformablePlayers, new Random(serverWorld.getGameTime()));

        // 存储新旧角色映射和新旧金币映射
        Map<UUID, SRERole> oldRoleMap = new HashMap<>();
        Map<UUID, SRERole> newRoleMap = new HashMap<>();
        Map<UUID, Integer> goldMap = new HashMap<>();

        // 先收集所有旧角色和金币
        for (ServerPlayer player : transformablePlayers) {
            SRERole oldRole = roleWorldComponent.getRole(player);
            oldRoleMap.put(player.getUUID(), oldRole);
            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
            goldMap.put(player.getUUID(), shop.balance);
        }

        // 分配杀手
        List<SRERole> assignedKillers = killerPool.selectRoles(remainingKillers);
        // 分配警长
        List<SRERole> assignedVigilantes = vigilantePool.selectRoles(remainingVigilantes);
        // 分配中立
        List<SRERole> assignedNeutrals = neutralPool.selectRoles(remainingNeutrals);

        // 计算平民数量
        int specialCount = assignedKillers.size() + assignedVigilantes.size() + assignedNeutrals.size();
        int civilianCount = transformablePlayers.size() - specialCount;

        List<SRERole> assignedCivilians;
        if (civilianCount > 0) {
            civilianPool.setIgnoreRoleOccupiedCount(true);
            assignedCivilians = civilianPool.selectRoles(civilianCount);
        } else {
            assignedCivilians = new ArrayList<>();
        }

        // 合并所有新角色
        List<SRERole> allNewRoles = new ArrayList<>();
        allNewRoles.addAll(assignedKillers);
        allNewRoles.addAll(assignedVigilantes);
        allNewRoles.addAll(assignedNeutrals);
        allNewRoles.addAll(assignedCivilians);

        // 如果角色不够，用平民填充
        while (allNewRoles.size() < transformablePlayers.size()) {
            allNewRoles.add(TMMRoles.CIVILIAN);
        }

        // 打乱新角色列表
        Collections.shuffle(allNewRoles, new Random(serverWorld.getGameTime()));

        // 给每个玩家分配新角色
        for (int i = 0; i < transformablePlayers.size(); i++) {
            ServerPlayer player = transformablePlayers.get(i);
            SRERole newRole = allNewRoles.get(i);
            newRoleMap.put(player.getUUID(), newRole);
        }

        // ===== 第四步：执行变换 =====
        WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(serverWorld);

        for (ServerPlayer player : transformablePlayers) {
            UUID uuid = player.getUUID();
            SRERole oldRole = oldRoleMap.get(uuid);
            SRERole newRole = newRoleMap.get(uuid);
            int oldGold = goldMap.getOrDefault(uuid, 0);

            if (newRole == null) {
                newRole = TMMRoles.CIVILIAN;
            }

            // --- 清除背包（保留key和信件） ---
            clearInventoryExceptKeysAndLetters(player);

            // --- 移除旧修饰符 ---
            HashSet<SREModifier> oldModifiers = new HashSet<>(worldModifierComponent.getModifiers(player));
            for (SREModifier modifier : oldModifiers) {
                worldModifierComponent.removeModifier(uuid, modifier, false);
                ModifierRemoved.EVENT.invoker().removeModifier(player, modifier);
            }

            // --- 移除旧角色，分配新角色 ---
            if (oldRole != null) {
                ModdedRoleRemoved.EVENT.invoker().removeModdedRole(player, oldRole);
            }
            gameWorldComponent.addRole(player, newRole, false);

            // --- 计算新金币 ---
            int newGold;
            int oldRoleType = oldRole != null ? getFactionType(oldRole) : 1; // 1=平民
            int newRoleType = getFactionType(newRole);

            // 扒手的默认基础金币数为0
            int killerBaseGold = isAvariciousRole(newRole) ? 0 : GameConstants.getMoneyStart();

            if (isCivilianOrVigilanteFaction(oldRoleType) && isKillerFaction(newRoleType)) {
                // 平民/中立 → 杀手：杀手基础金币 + 旧金币*20%
                newGold = killerBaseGold + (int) (oldGold * 0.2);
            } else if (isKillerFaction(oldRoleType) && isCivilianOrVigilanteFaction(newRoleType)) {
                // 杀手/中立 → 平民/警长：50金币 + 旧金币*50%
                newGold = 50 + (int) (oldGold * 0.5);
            } else if (isNeutralFaction(newRoleType) || oldRoleType == newRoleType) {
                // 变换后是中立 或 变换前后阵营一致：100%继承
                newGold = oldGold;
            } else {
                // 同一阵营内部变换（比如警长变警长，平民变平民）：100%继承
                newGold = oldGold;
            }

            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
            shop.setBalance(Math.max(0, newGold));

            // --- 触发新角色事件（内部会自动调用 RoleInitialItems.addInitialItemsForRole 发放默认物品） ---
            ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, newRole);
        }

        // --- 同步角色组件 ---
        roleWorldComponent.sync();

        // --- 分配新修饰符 ---
        int modifierRoleCount = (int) ((float) transformablePlayers.size()
                * HarpyModLoaderConfig.HANDLER.instance().modifierMultiplier);
        assignModifiers(modifierRoleCount, serverWorld, gameWorldComponent, transformablePlayers);

        // --- 同步修饰符 ---
        worldModifierComponent.sync();

        // --- 发送欢迎报幕 ---
        for (ServerPlayer player : transformablePlayers) {
            RoleUtils.sendWelcomeAnnouncement(player);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 清除背包中除了key和信件以外的所有物品
     */
    private void clearInventoryExceptKeysAndLetters(ServerPlayer player) {
        SREItemUtils.clearItem(player, (stack) ->
                !stack.is(TMMItems.KEY)
                        && !stack.is(TMMItems.LETTER)
                        && !(stack.getItem() instanceof LetterItem));
    }

    /**
     * 获取阵营类型：
     * 1 = 平民/警长阵营 (civilian/vigilante)
     * 2 = 中立阵营
     * 3 = 杀手阵营
     */
    private int getFactionType(@Nullable SRERole role) {
        if (role == null) return 1;
        int type = PlayerRoleWeightManager.getRoleType(role);
        if (type == 4) return 3; // 杀手
        if (type == 2 || type == 3) return 2; // 中立
        return 1; // 平民/警长
    }

    private boolean isKillerFaction(int factionType) {
        return factionType == 3;
    }

    private boolean isNeutralFaction(int factionType) {
        return factionType == 2;
    }

    private boolean isCivilianOrVigilanteFaction(int factionType) {
        return factionType == 1;
    }

    /**
     * 判断是否为扒手（Avaricious），扒手基础金币为0
     */
    private boolean isAvariciousRole(SRERole role) {
        if (role == null) return false;
        String path = role.identifier().getPath();
        return path.contains("avaricious") || path.contains("Avaricious");
    }

    /**
     * 判断是否是被禁止的杀手角色（普通杀手、猫娘杀手）
     * 这些角色不能在变换中出现
     */
    private boolean isBannedKillerRole(SRERole role) {
        if (role == null) return false;
        // 普通杀手
        if (role == TMMRoles.KILLER) return true;
        // 猫娘杀手
        if (role == ModRoles.CAT_KILLER) return true;
        return false;
    }

    /**
     * 判断是否为除教父以外的家族成员（这些职业也不能在变换中出现）
     */
    private boolean isFamilyRoleExceptGodfather(SRERole role) {
        if (role == null) return false;
        // 教父(Godfather)允许出现
        if (role == ModRoles.GODFATHER) return false;
        // 其他家族成员禁止（用 isMafiaTeam() 判断）
        return role.isMafiaTeam();
    }

    /**
     * 判断死亡时不计入杀手阵营死亡统计的角色
     * （普通杀手、猫娘杀手）
     */
    private boolean isDeathIgnoredKillerRole(SRERole role) {
        if (role == null) return false;
        // 普通杀手
        if (role == TMMRoles.KILLER) return true;
        // 猫娘杀手
        if (role == ModRoles.CAT_KILLER) return true;
        return false;
    }

    /**
     * 判断死亡时不计入中立阵营死亡统计的角色
     * （除了教父以外的家族成员）
     */
    private boolean isDeathIgnoredNeutralRole(SRERole role) {
        if (role == null) return false;
        // 教父(Godfather)计入中立死亡
        if (role == ModRoles.GODFATHER) return false;
        // 其他家族成员不计入
        return role.isMafiaTeam();
    }
}
