package io.wifi.starrailexpress.content.block_entity;

import com.mojang.datafixers.util.Pair;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.*;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import io.wifi.starrailexpress.network.EntityInteractionBlockServerNetwork;
import io.wifi.starrailexpress.network.packet.CustomNarratorPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.ModifierRemoved;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体交互方块的BlockEntity
 * 存储条件和触发内容配置，处理逻辑执行
 */
public class EntityInteractionBlockEntity extends BlockEntity {
    // 运行范围（球形半径）
    public static final double MAX_RANGE = 100.0;

    // 按地图名追踪服务端实体交互方块数量，用于游戏结束时快速跳过无方块的地图
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>
            mapBlockCount = new java.util.concurrent.ConcurrentHashMap<>();

    // 按地图名追踪所有 EntityInteractionBlockEntity 实例（用于外部事件触发 DEATH 等条件）
    // 与 mapBlockCount 使用相同的 mapKey，仅扫描当前地图中 scan 到的方块
    private static final java.util.concurrent.ConcurrentHashMap<String,
            java.util.Set<BlockPos>> mapInstances = new java.util.concurrent.ConcurrentHashMap<>();

    private boolean tracked = false;

    private static String getMapKey(net.minecraft.world.level.Level level) {
        var areas = io.wifi.starrailexpress.cca.AreasWorldComponent.KEY.get(level);
        return areas != null && areas.mapName != null ? areas.mapName : "";
    }

    public static int getCountForMap(net.minecraft.world.level.Level level) {
        String key = getMapKey(level);
        if (key.isEmpty()) return 0;
        var counter = mapBlockCount.get(key);
        return counter != null ? counter.get() : 0;
    }

    private void tryTrack() {
        if (!tracked && level != null && !level.isClientSide) {
            tracked = true;
            String key = getMapKey(level);
            if (!key.isEmpty()) {
                mapBlockCount
                        .computeIfAbsent(key,
                                k -> new java.util.concurrent.atomic.AtomicInteger(0))
                        .incrementAndGet();
            }
            // 注册到地图实例集，用于外部事件触发 DEATH 等条件（仅扫描当前地图中 scan 到的方块）
            mapInstances
                    .computeIfAbsent(key,
                            k -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                    .add(worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (tracked) {
            tracked = false;
            if (level != null && !level.isClientSide) {
                String key = getMapKey(level);
                if (!key.isEmpty()) {
                    var counter = mapBlockCount.get(key);
                    if (counter != null) {
                        counter.decrementAndGet();
                    }
                }
                // 从地图实例集中注销
                var set = mapInstances.get(key);
                if (set != null) {
                    set.remove(worldPosition);
                }
            }
        }
    }

    // 条件列表
    private List<TriggerCondition> conditions = new ArrayList<>();
    // 触发内容列表
    private List<TriggerAction> actions = new ArrayList<>();
    // 内置触发间隔（tick）
    private int cooldownTicks = 40; // 默认2秒
    // 玩家上次触发时间映射
    private final Map<UUID, Long> lastTriggerTime = new HashMap<>();
    // 定时器计数（用于自动定时条件）
    private int timerTick = 0;
    // 红石信号相关
    private boolean receivedRedstoneSignal = false; // 是否接收到红石信号
    private boolean isOutputtingRedstone = false; // 是否正在输出红石信号
    private int redstoneOutputStrength = 15; // 输出的红石信号强度（0-15）
    // 是否启用碰撞箱
    private boolean collisionEnabled = false;
    private int collisionRemainingTicks = 0;
    // 是否是传送点
    private boolean isTeleportPoint = false;
    private int teleportPointId = -1; // 传送点数字ID
    // 方块冷却（期间不触发）
    private int blockCooldownTicks = 0;
    private int blockCooldownEndGameTime = 0; // 基于游戏时间的冷却结束时刻

    // 玩家点击追踪（用于CLICK_BLOCK条件）
    private final Map<UUID, Pair<Boolean, Long>> playerClicks = new HashMap<>();
    // <PlayerUUID, <isLeftClick, timestamp>>
    // 已触发过的点击记录（用于一次性触发）
    private final Set<String> triggeredClicks = new HashSet<>(); // "uuid:timestamp"

    public EntityInteractionBlockEntity(BlockPos pos, BlockState state) {
        super(TMMBlockEntities.ENTITY_INTERACTION_BLOCK, pos, state);
    }

    // 添加条件
    public void addCondition(TriggerCondition condition) {
        conditions.add(condition);
        setChanged();
    }

    /**
     * 替换指令中的相对坐标
     * ~ 表示方块坐标，~number 表示相对偏移
     * 例如: "tp <player> ~ ~2 ~" -> "tp <player> 100 66 200" (假设方块在100,64,200)
     *
     * @param command  原始指令
     * @param blockPos 方块位置
     * @return 替换后的指令
     */
    private String replaceRelativeCoordinates(String command, BlockPos blockPos) {
        int blockX = blockPos.getX();
        int blockY = blockPos.getY();
        int blockZ = blockPos.getZ();

        StringBuilder result = new StringBuilder();
        int i = 0;

        while (i < command.length()) {
            char c = command.charAt(i);

            if (c == '~') {
                // 找到~的结束位置，收集后续的数字（可能是负数或小数）
                int start = i;
                i++;
                boolean hasNumber = false;
                StringBuilder number = new StringBuilder();

                while (i < command.length()) {
                    char next = command.charAt(i);
                    if ((next >= '0' && next <= '9') || next == '.' || next == '-' || next == '+') {
                        hasNumber = true;
                        number.append(next);
                        i++;
                    } else {
                        break;
                    }
                }

                if (hasNumber) {
                    // 有数字，计算相对坐标
                    try {
                        double offset = Double.parseDouble(number.toString());
                        int coord = (int) (blockX + offset);
                        result.append(coord);
                    } catch (NumberFormatException e) {
                        // 解析失败，保留原始内容
                        result.append(command, start, i);
                    }
                } else {
                    // 没有数字，用方块x坐标替换
                    result.append(blockX);
                }
            } else {
                result.append(c);
                i++;
            }
        }

        // 处理 ~~ 转义（用特殊标记来区分）
        // 用户可以通过输入 ~~ 来表示一个字面 ~
        // 这里需要用户知道这个约定，默认不处理

        return result.toString();
    }

    // 移除条件
    public void removeCondition(int index) {
        if (index >= 0 && index < conditions.size()) {
            conditions.remove(index);
            setChanged();
        }
    }

    // 添加触发内容
    public void addAction(TriggerAction action) {
        actions.add(action);
        setChanged();
    }

    // 移除触发内容
    public void removeAction(int index) {
        if (index >= 0 && index < actions.size()) {
            actions.remove(index);
            setChanged();
        }
    }

    // 设置冷却时间
    public void setCooldownTicks(int ticks) {
        this.cooldownTicks = ticks;
        setChanged();
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public List<TriggerCondition> getConditions() {
        return conditions;
    }

    public List<TriggerAction> getActions() {
        return actions;
    }

    // 打开UI
    public void openUI(ServerPlayer player) {
        EntityInteractionBlockServerNetwork.sendOpenUI(player, this.worldPosition, this);
    }

    // 记录玩家点击（用于CLICK_BLOCK条件）
    public void recordPlayerClick(ServerPlayer player, boolean isLeftClick) {
        playerClicks.put(player.getUUID(), Pair.of(isLeftClick, player.level().getGameTime()));
        setChanged();
    }

    // 检查玩家点击条件
    private boolean checkPlayerClickCondition(ServerPlayer player, boolean requireLeftClick) {
        UUID playerId = player.getUUID();
        if (!playerClicks.containsKey(playerId)) {
            return false;
        }
        Pair<Boolean, Long> clickData = playerClicks.get(playerId);
        boolean clickedLeft = clickData.getFirst();
        long clickTime = clickData.getSecond();
        // 点击记录在10秒（200 tick）内有效
        long currentTime = player.level().getGameTime();
        if (currentTime - clickTime > 200) {
            playerClicks.remove(playerId);
            return false;
        }
        return clickedLeft == requireLeftClick;
    }

    // 检查玩家是否匹配目标阵营（用于传送逻辑）
    private boolean checkTeamMatch(ServerPlayer player, ServerLevel world, TeamType targetTeamType) {
        if (targetTeamType == TeamType.ALL) {
            return true;
        }
        SRERoleWorldComponent roles = SRERoleWorldComponent.KEY.get(world);
        SRERole role = roles.getRole(player);
        if (role == null) {
            return false;
        }

        boolean result = switch (targetTeamType) {
            case CIVILIAN -> role.isInnocent() && !role.isVigilanteTeam();
            case SHERIFF -> role.isVigilanteTeam();
            case NEUTRAL -> role.isNeutrals() || (!role.isInnocent() && !role.canUseKiller());
            case NEUTRAL_KILLER -> role.isNeutrals() && role.isNeutralForKiller();
            case NEUTRAL_SPECIAL -> role.isNeutrals() && !role.isNeutralForKiller();
            case KILLER -> role.canUseKiller() && !role.isInnocent();
            default -> true;
        };

        return result;
    }

    // 传送点相关getter/setter
    public boolean isTeleportPoint() {
        return isTeleportPoint;
    }

    public int getTeleportPointId() {
        return teleportPointId;
    }

    // ===== 任务路标相关 =====
    // 是否为任务路标
    private boolean isTaskMarker = false;
    // 任务框颜色（RGB，默认为白色）
    private int taskMarkerColor = 0xFFFFFF;
    // 任务透视条件类型
    private TaskHighlightCondition taskHighlightCondition = TaskHighlightCondition.NONE;
    // 一般任务类型（用于"一般任务"条件，*表示所有任务）
    private String taskHighlightTaskType = "*";
    // 自定义任务ID（用于"自定义任务"条件）
    private String taskHighlightCustomTaskId = "";
    // 任务本能ID（用于渲染时区分不同类型的任务标记，默认为100）
    private int taskInstinctId = 100;

    public boolean isTaskMarker() {
        return isTaskMarker;
    }

    public int getTaskMarkerColor() {
        return taskMarkerColor;
    }

    public TaskHighlightCondition getTaskHighlightCondition() {
        return taskHighlightCondition;
    }

    public String getTaskHighlightTaskType() {
        return taskHighlightTaskType;
    }

    public String getTaskHighlightCustomTaskId() {
        return taskHighlightCustomTaskId;
    }

    public int getTaskInstinctId() {
        return taskInstinctId;
    }

    public void setTaskMarker(boolean taskMarker) {
        this.isTaskMarker = taskMarker;
        setChanged();
    }

    public void setTaskMarkerColor(int color) {
        this.taskMarkerColor = color;
        setChanged();
    }

    public void setTaskHighlightCondition(TaskHighlightCondition condition) {
        this.taskHighlightCondition = condition;
        setChanged();
    }

    public void setTaskHighlightTaskType(String taskType) {
        this.taskHighlightTaskType = taskType != null ? taskType : "*";
        setChanged();
    }

    public void setTaskHighlightCustomTaskId(String customTaskId) {
        this.taskHighlightCustomTaskId = customTaskId != null ? customTaskId : "";
        setChanged();
    }

    public void setTaskInstinctId(int id) {
        this.taskInstinctId = id;
        setChanged();
    }

    // 任务透视条件类型枚举
    public enum TaskHighlightCondition {
        NONE, // 无（不使用任务透视）
        ALWAYS, // 常驻透视
        NORMAL_TASK, // 一般任务时透视
        CUSTOM_TASK // 自定义任务时透视
    }

    // 方块冷却相关
    public boolean isInCooldown(long currentGameTime) {
        // 如果冷却已过期，自动重置为0
        if (blockCooldownEndGameTime > 0 && currentGameTime > blockCooldownEndGameTime) {
            blockCooldownEndGameTime = 0;
            blockCooldownTicks = 0;
            setChanged();
        }
        return blockCooldownEndGameTime > 0 && currentGameTime <= blockCooldownEndGameTime;
    }

    public void setBlockCooldown(int seconds, long currentGameTime) {
        this.blockCooldownTicks = Math.max(0, seconds) * 20;
        // 使用安全的计算，避免整数溢出
        if (this.blockCooldownTicks > 0) {
            this.blockCooldownEndGameTime = (int) (currentGameTime + this.blockCooldownTicks);
        } else {
            this.blockCooldownEndGameTime = 0;
        }
        setChanged();
    }

    // 不带游戏时间的旧方法，保持兼容
    public void setBlockCooldown(int seconds) {
        // 使用默认值，将在tick时正确设置
        this.blockCooldownTicks = Math.max(0, seconds) * 20;
    }

    // 重置方块冷却
    public void resetBlockCooldown() {
        this.blockCooldownTicks = 0;
        this.blockCooldownEndGameTime = 0;
        setChanged();
    }

    /**
     * 重置所有内置冷却状态（玩家触发冷却、方块冷却、点击记录等）
     * 用于游戏结束时清理所有实体交互方块的状态
     */
    public void resetAllCooldowns() {
        this.lastTriggerTime.clear();
        this.blockCooldownTicks = 0;
        this.blockCooldownEndGameTime = 0;
        this.playerClicks.clear();
        this.triggeredClicks.clear();
        setChanged();
    }

    // 红石信号相关 getter/setter
    public void setReceivedRedstoneSignal(boolean received) {
        this.receivedRedstoneSignal = received;
    }

    public boolean isReceivedRedstoneSignal() {
        return receivedRedstoneSignal;
    }

    public boolean isOutputtingRedstone() {
        return isOutputtingRedstone;
    }

    public int getRedstoneOutputStrength() {
        return redstoneOutputStrength;
    }

    public void setRedstoneOutput(boolean outputting, int strength) {
        this.isOutputtingRedstone = outputting;
        this.redstoneOutputStrength = Math.max(0, Math.min(15, strength));
    }

    // 从服务端接收更新
    public void updateFromServer(List<TriggerCondition> newConditions, List<TriggerAction> newActions,
            int newCooldown) {
        this.conditions = new ArrayList<>(newConditions);
        this.actions = new ArrayList<>(newActions);
        this.cooldownTicks = newCooldown;
        setChanged();
    }

    // 根据任务类型创建任务实例
    private static SREPlayerTaskComponent.TrainTask createTaskByType(SREPlayerTaskComponent.Task taskType) {
        return switch (taskType) {
            case SLEEP ->
                new SREPlayerTaskComponent.SleepTask(io.wifi.starrailexpress.game.GameConstants.SLEEP_TASK_DURATION);
            case OUTSIDE -> new SREPlayerTaskComponent.OutsideTask(
                    io.wifi.starrailexpress.game.GameConstants.OUTSIDE_TASK_DURATION);
            case RAED_BOOK -> new SREPlayerTaskComponent.ReadBookTask(
                    io.wifi.starrailexpress.game.GameConstants.READ_BOOK_TASK_DURATION);
            case EAT -> new SREPlayerTaskComponent.EatTask();
            case DRINK -> new SREPlayerTaskComponent.DrinkTask();
            case EXERCISE -> new SREPlayerTaskComponent.ExerciseTask(
                    io.wifi.starrailexpress.game.GameConstants.EXERCISE_TASK_DURATION);
            case MEDITATE -> new SREPlayerTaskComponent.MeditateTask(
                    io.wifi.starrailexpress.game.GameConstants.MEDITATE_TASK_DURATION);
            case BATHE ->
                new SREPlayerTaskComponent.BatheTask(io.wifi.starrailexpress.game.GameConstants.BATHE_TASK_DURATION);
            case NOTE_BLOCK -> new SREPlayerTaskComponent.NoteBlockTask(
                    io.wifi.starrailexpress.game.GameConstants.NOTE_BLOCK_TASK_CLICK_COUNTS);
            case TOILET ->
                new SREPlayerTaskComponent.ToiletTask(io.wifi.starrailexpress.game.GameConstants.TOILET_TASK_DURATION);
            case CHAIR ->
                new SREPlayerTaskComponent.ChairTask(io.wifi.starrailexpress.game.GameConstants.CHAIR_TASK_DURATION);
            case BREATHE -> new SREPlayerTaskComponent.BreatheTask(
                    io.wifi.starrailexpress.game.GameConstants.BREATHE_TASK_DURATION);
            default -> null;
        };
    }

    // 每tick调用
    public static void tick(Level world, BlockPos pos, BlockState state, EntityInteractionBlockEntity entity) {
        if (world.isClientSide)
            return;
        if (!(world instanceof ServerLevel serverWorld))
            return;

        entity.tryTrack();

        // 获取游戏世界组件
        SREGameTimeComponent timeComponent = SREGameTimeComponent.KEY.get(world);
        // 使用 resetTime - time 计算游戏开始后经过的时间（tick）
        long elapsedGameTime = timeComponent.getResetTime() - timeComponent.getTime();
        // 获取剩余时间（tick）
        long remainingTime = timeComponent.getTime();

        // 处理碰撞箱计时
        if (entity.collisionEnabled) {
            if (entity.collisionRemainingTicks > 0) {
                entity.collisionRemainingTicks--;
            } else if (entity.collisionRemainingTicks == 0) {
                entity.collisionEnabled = false;
            }
        }

        // 每tick重置红石输出状态
        entity.isOutputtingRedstone = false;

        // 检查方块冷却（使用经过的时间计算）
        if (entity.isInCooldown(elapsedGameTime)) {
            return; // 冷却期间不处理任何触发
        }

        entity.timerTick++;

        // 获取范围内的玩家
        AABB rangeBox = new AABB(pos).inflate(MAX_RANGE);
        List<ServerPlayer> playersInRange = serverWorld.getEntitiesOfClass(ServerPlayer.class, rangeBox);

        boolean anyPlayerTriggered = false;
        for (ServerPlayer player : playersInRange) {
            // 检查所有条件是否满足（传入剩余时间用于TIME_ANCHOR条件检查）
            if (entity.checkConditions(player, serverWorld, pos, elapsedGameTime, remainingTime, entity.timerTick)) {
                // 检查玩家冷却
                long lastTrigger = entity.lastTriggerTime.getOrDefault(player.getUUID(), 0L);
                if (elapsedGameTime - lastTrigger >= entity.cooldownTicks) {
                    anyPlayerTriggered = true;
                    // 检查是否有 CLICK_BLOCK 条件需要特殊处理
                    boolean hasClickBlockCondition = entity.conditions.stream()
                            .anyMatch(c -> c.type == ConditionType.CLICK_BLOCK && c.triggerOnce);
                    if (hasClickBlockCondition) {
                        // 标记点击为已触发
                        entity.markClickAsTriggered(player);
                    }
                    // 执行触发内容
                    entity.executeActions(player, serverWorld, pos, elapsedGameTime);
                    entity.lastTriggerTime.put(player.getUUID(), elapsedGameTime);
                }
            }
        }

        // 如果有玩家触发了条件，且存在 OUTPUT_REDSTONE 动作，则更新红石输出并刷新邻居
        if (anyPlayerTriggered && entity.isOutputtingRedstone) {
            // 刷新方块状态，使红石信号更新
            serverWorld.updateNeighborsAt(pos, state.getBlock());
            serverWorld.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    /**
     * 由外部死亡事件调用，检查当前地图中 scan 到的所有 EntityInteractionBlockEntity 的 DEATH 条件。
     * 当玩家死亡时，扫描当前地图已注册的实体交互方块，
     * 若有 DEATH 条件匹配死亡原因则触发对应的动作。
     */
    public static void onPlayerDeath(ServerLevel world, ServerPlayer victim,
            net.minecraft.resources.ResourceLocation deathReason) {
        if (world == null || victim == null) return;
        String key = getMapKey(world);
        if (key.isEmpty()) return;
        var set = mapInstances.get(key);
        if (set == null || set.isEmpty()) return;

        for (BlockPos pos : set) {
            if (world.getBlockEntity(pos) instanceof EntityInteractionBlockEntity entity) {
                if (entity.isRemoved()) continue;
                entity.handleDeathTrigger(victim, world, pos, deathReason);
            }
        }
    }

    /**
     * 处理单个方块的 DEATH 条件触发
     */
    private void handleDeathTrigger(ServerPlayer player, ServerLevel world, BlockPos pos,
            net.minecraft.resources.ResourceLocation deathReason) {
        if (conditions.isEmpty()) return;

        // 检查方块冷却
        long currentGameTime;
        long remainingGameTime;
        try {
            var timeComponent = SREGameTimeComponent.KEY.get(world);
            currentGameTime = timeComponent.getResetTime() - timeComponent.getTime();
            remainingGameTime = timeComponent.getTime();
        } catch (Exception e) {
            return;
        }
        if (isInCooldown(currentGameTime)) return;

        // 检查玩家冷却
        long lastTrigger = lastTriggerTime.getOrDefault(player.getUUID(), 0L);
        if (currentGameTime - lastTrigger < cooldownTicks) return;

        // 综合检查所有条件：DEATH 条件视为满足（并校验死亡原因），
        // 其余条件（如 ROLE_IS）照常对死亡玩家求值，逻辑运算符（AND/OR/...）生效。
        boolean triggered = checkConditionsForDeath(player, world, pos, deathReason,
                currentGameTime, remainingGameTime, 0);

        if (triggered) {
            executeActions(player, world, pos, currentGameTime);
            lastTriggerTime.put(player.getUUID(), currentGameTime);
        }
    }

    /**
     * 死亡触发时综合检查所有条件（与 tick 路径的区别：DEATH 条件在这里视为已满足，
     * 并额外校验死亡原因是否匹配；其余条件与 tick 路径一致对玩家求值）。
     * 使用逻辑运算符把多个条件组合起来。
     */
    private boolean checkConditionsForDeath(ServerPlayer player, ServerLevel world, BlockPos pos,
            net.minecraft.resources.ResourceLocation deathReason, long elapsedGameTime, long remainingGameTime,
            int timerTick) {
        if (conditions.isEmpty())
            return false;
        if (conditions.size() == 1) {
            return checkSingleConditionForDeath(conditions.get(0), player, world, pos, deathReason, elapsedGameTime,
                    remainingGameTime, timerTick);
        }

        boolean result = checkSingleConditionForDeath(conditions.get(0), player, world, pos, deathReason,
                elapsedGameTime, remainingGameTime, timerTick);

        for (int i = 1; i < conditions.size(); i++) {
            TriggerCondition currentCondition = conditions.get(i);
            TriggerCondition previousCondition = conditions.get(i - 1);
            boolean currentResult = checkSingleConditionForDeath(currentCondition, player, world, pos, deathReason,
                    elapsedGameTime, remainingGameTime, timerTick);

            LogicOperator operator = previousCondition.logicOperator != null ? previousCondition.logicOperator
                    : LogicOperator.AND;

            result = switch (operator) {
                case AND -> result && currentResult;
                case OR -> result || currentResult;
                case NAND -> !(result && currentResult);
                case NOR -> !(result || currentResult);
            };
        }

        return result;
    }

    /**
     * 死亡触发时的单条件检查。DEATH 条件在此校验死亡原因，其余条件复用 checkSingleCondition。
     */
    private boolean checkSingleConditionForDeath(TriggerCondition condition, ServerPlayer player, ServerLevel world,
            BlockPos pos, net.minecraft.resources.ResourceLocation deathReason, long elapsedGameTime,
            long remainingGameTime, int timerTick) {
        if (condition.type == ConditionType.DEATH) {
            String requiredReason = condition.stringValue;
            return requiredReason == null || requiredReason.isEmpty()
                    || requiredReason.equals("*")
                    || requiredReason.equals(deathReason.toString())
                    || requiredReason.equals(deathReason.getPath());
        }
        return checkSingleCondition(condition, player, world, pos, elapsedGameTime, remainingGameTime, timerTick);
    }

    // 检查条件（支持复杂逻辑运算）
    private boolean checkConditions(ServerPlayer player, ServerLevel world, BlockPos pos, long elapsedGameTime,
            long remainingGameTime, int timerTick) {
        if (conditions.isEmpty())
            return false;
        if (conditions.size() == 1) {
            return checkSingleCondition(conditions.get(0), player, world, pos, elapsedGameTime, remainingGameTime,
                    timerTick);
        }

        // 使用逻辑运算符组合条件
        boolean result = checkSingleCondition(conditions.get(0), player, world, pos, elapsedGameTime, remainingGameTime,
                timerTick);

        for (int i = 1; i < conditions.size(); i++) {
            TriggerCondition currentCondition = conditions.get(i);
            TriggerCondition previousCondition = conditions.get(i - 1);
            boolean currentResult = checkSingleCondition(currentCondition, player, world, pos, elapsedGameTime,
                    remainingGameTime, timerTick);

            // 获取与前一个条件的逻辑运算符（默认AND）
            LogicOperator operator = previousCondition.logicOperator != null ? previousCondition.logicOperator
                    : LogicOperator.AND;

            result = switch (operator) {
                case AND -> result && currentResult;
                case OR -> result || currentResult;
                case NAND -> !(result && currentResult);
                case NOR -> !(result || currentResult);
            };
        }

        return result;
    }

    // 标记点击为已触发（用于一次性触发）
    private void markClickAsTriggered(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (playerClicks.containsKey(playerId)) {
            long clickTime = playerClicks.get(playerId).getSecond();
            String clickKey = playerId.toString() + ":" + clickTime;
            triggeredClicks.add(clickKey);
        }
    }

    private boolean checkSingleCondition(TriggerCondition condition, ServerPlayer player, ServerLevel world,
            BlockPos pos, long elapsedGameTime, long remainingGameTime, int timerTick) {
        return switch (condition.type) {
            case PASS_THROUGH -> {
                // 玩家穿过方块时触发
                AABB blockBox = new AABB(pos);
                yield blockBox.intersects(player.getBoundingBox());
            }
            case TIMER -> {
                // 自动定时
                int intervalTicks = (int) (condition.value * 20); // 秒转tick
                yield intervalTicks > 0 && timerTick % intervalTicks == 0;
            }
            case TIME_ANCHOR -> {
                // 时间锚点触发（使用剩余时间作为判断依据）
                long remainingSeconds = remainingGameTime / 20; // tick转秒
                long targetSeconds = (long) (condition.value * 60); // 分钟转秒
                yield switch (condition.comparison) {
                    case EQUALS -> remainingSeconds == targetSeconds;
                    case GREATER -> remainingSeconds > targetSeconds;
                    case LESS -> remainingSeconds < targetSeconds;
                    case GREATER_EQUAL -> remainingSeconds >= targetSeconds;
                    case LESS_EQUAL -> remainingSeconds <= targetSeconds;
                };
            }
            case ELAPSED_TIME -> {
                // 游戏经过的时间（使用已过去的时间作为判断依据）
                long elapsedSeconds = elapsedGameTime / 20; // tick转秒
                long targetSeconds = (long) (condition.value * 60); // 分钟转秒
                yield switch (condition.comparison) {
                    case EQUALS -> elapsedSeconds == targetSeconds;
                    case GREATER -> elapsedSeconds > targetSeconds;
                    case LESS -> elapsedSeconds < targetSeconds;
                    case GREATER_EQUAL -> elapsedSeconds >= targetSeconds;
                    case LESS_EQUAL -> elapsedSeconds <= targetSeconds;
                };
            }
            case PROXIMITY_SPHERE -> {
                // 球形范围
                double distance = player.distanceToSqr(Vec3.atCenterOf(pos));
                yield Math.sqrt(distance) <= condition.value;
            }
            case PROXIMITY_LINE -> {
                // 直线范围（支持方向检测）
                double dx = player.getX() - (pos.getX() + 0.5);
                double dy = player.getY() - (pos.getY() + 0.5);
                double dz = player.getZ() - (pos.getZ() + 0.5);
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                // 如果距离超出范围，直接返回false
                if (distance > condition.value) {
                    yield false;
                }

                // 检查方向
                LineDirection direction = condition.lineDirection != null ? condition.lineDirection : LineDirection.ALL;
                if (direction == LineDirection.ALL) {
                    // 所有方向都包含
                    yield true;
                }

                // 计算玩家相对于方块的方向偏移
                double absDx = Math.abs(dx);
                double absDy = Math.abs(dy);
                double absDz = Math.abs(dz);

                // 判断是水平方向还是垂直方向，以及东西还是南北
                boolean isVertical = absDy >= absDx && absDy >= absDz;
                boolean isEastWest = absDx > absDz;

                yield switch (direction) {
                    case EAST -> dx > 0 && isEastWest;
                    case WEST -> dx < 0 && isEastWest;
                    case SOUTH -> dz > 0 && !isEastWest;
                    case NORTH -> dz < 0 && !isEastWest;
                    case UP -> dy > 0 && isVertical;
                    case DOWN -> dy < 0 && isVertical;
                    case ALL -> true; // 默认全部方向
                };
            }
            case HAS_ITEM -> {
                // 物品栏中有特定物品
                String itemId = condition.stringValue;
                yield player.getInventory().items.stream()
                        .anyMatch(stack -> !stack.isEmpty() &&
                                stack.getItem().builtInRegistryHolder().key().location().toString().equals(itemId));
            }
            case CLICK_BLOCK -> {
                // 右键方块（需要玩家在方块范围内并右键点击）
                AABB blockBox = new AABB(pos).inflate(2); // 2格范围内
                if (!blockBox.contains(player.getBoundingBox().getCenter())) {
                    yield false; // 玩家不在方块附近
                }
                // 检查是否右键点击（false = 右键）
                boolean clickValid = checkPlayerClickCondition(player, false);
                if (!clickValid) {
                    yield false;
                }
                // 检查是否是一次性触发且已经触发过
                if (condition.triggerOnce) {
                    String clickKey = player.getUUID().toString() + ":"
                            + playerClicks.get(player.getUUID()).getSecond();
                    if (triggeredClicks.contains(clickKey)) {
                        yield false; // 已经触发过
                    }
                }
                yield true;
            }
            case LOOKING_AT -> {
                // 玩家看向方块
                Vec3 eyePos = player.getEyePosition(1.0f);
                Vec3 lookVec = player.getViewVector(1.0f);
                Vec3 blockCenter = Vec3.atCenterOf(pos);
                double distanceToBlock = eyePos.distanceTo(blockCenter);
                if (distanceToBlock > condition.value)
                    yield false;

                Vec3 toBlock = blockCenter.subtract(eyePos).normalize();
                double dot = lookVec.dot(toBlock);
                yield dot > 0.95; // 视角差小于约18度
            }
            case STANDING_ON_BLOCK -> {
                // 站在特定方块上
                double radius = condition.value;
                String blockId = condition.stringValue;
                AABB checkBox = new AABB(pos).inflate(radius);
                if (!checkBox.intersects(player.getBoundingBox()))
                    yield false;

                BlockPos standingPos = player.blockPosition().below();
                BlockState standingState = world.getBlockState(standingPos);
                yield standingState.getBlock().builtInRegistryHolder().key().location().toString().equals(blockId);
            }
            case DEATH -> {
                // 死亡原因（需要外部触发）
                yield false; // 这个条件由死亡事件处理
            }
            case USE_ITEM -> {
                // 使用物品（需要外部触发）
                yield false; // 这个条件由使用物品事件处理
            }
            case SPEAK -> {
                // 说话（需要外部触发，使用SimpleVoiceChat）
                yield false; // 这个条件由语音事件处理
            }
            case COIN_AMOUNT -> {
                // 金币数量
                SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
                yield compareValue(shop.balance, condition.value, condition.comparison);
            }
            case ROLE_IS -> {
                // 特定职业
                SRERoleWorldComponent roles = SRERoleWorldComponent.KEY.get(world);
                var role = roles.getRole(player);
                yield role != null && role.identifier().toString().equals(condition.stringValue);
            }
            case ROLE_TEAM -> {
                // 职业阵营
                SRERoleWorldComponent roles = SRERoleWorldComponent.KEY.get(world);
                var role = roles.getRole(player);
                if (role == null)
                    yield false;
                yield switch (condition.teamType) {
                    case ALL -> true; // 所有职业都匹配
                    case CIVILIAN -> role.isInnocent() && !role.isVigilanteTeam();
                    case SHERIFF -> role.isVigilanteTeam();
                    case NEUTRAL -> role.isNeutrals();
                    case NEUTRAL_KILLER -> role.isNeutrals() && role.isNeutralForKiller();
                    case NEUTRAL_SPECIAL -> role.isNeutrals() && !role.isNeutralForKiller();
                    case KILLER -> role.canUseKiller() && !role.isNeutrals() && !role.isNeutralForKiller();
                };
            }
            case HAS_KILLED -> {
                // 击杀过其他玩家 - 检查本局击杀数
                SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(world);
                yield gameWorldComponent.getPlayerKills(player.getUUID()) > 0;
            }
            case PLAYER_COUNT -> {
                // 玩家数量条件
                int playerCount = world.players().size();
                yield compareValue(playerCount, condition.value, condition.comparison);
            }
            case ALIVE_PLAYERS -> {
                // 存活玩家数量（非旁观模式）
                long aliveCount = world.players().stream()
                        .filter(p -> !p.isSpectator())
                        .count();
                yield compareValue((int) aliveCount, condition.value, condition.comparison);
            }
            case IS_SNEAKING -> player.isCrouching();
            case IS_SPRINTING -> player.isSprinting();
            case HAS_EFFECT -> {
                // 检查是否有特定效果
                String effectId = condition.stringValue;
                yield player.getActiveEffects().stream()
                        .anyMatch(effect -> effect.getEffect().getRegisteredName().equals(effectId));
            }
            case PROBABILITY -> {
                // 触发概率（0-100%）
                double probability = condition.value / 100.0;
                yield world.getRandom().nextDouble() < probability;
            }
            case WORLD_TIME -> {
                // 检查世界时间
                long time = world.getDayTime() % 24000;
                yield switch (condition.worldTimeType) {
                    case DAY -> time >= 0 && time < 12000;
                    case NOON -> time >= 5000 && time < 7000;
                    case SUNSET -> time >= 12000 && time < 13000;
                    case NIGHT -> time >= 13000 || time < 0;
                    case MIDNIGHT -> time >= 17000 && time < 19000;
                };
            }
            case ENTITY_COUNT -> {
                // 检查范围内指定实体的数量
                double radius = condition.value;
                String entityId = condition.stringValue;
                int requiredCount = condition.entityCount;
                boolean checkAny = condition.checkAnyCount; // * 表示不检查数量，只要有就行

                AABB checkBox = new AABB(pos).inflate(radius);
                int actualCount = 0;

                for (var entity : world.getEntities(null, checkBox)) {
                    boolean matches = false;
                    if ("*".equals(entityId)) {
                        // * 表示任意实体（不包括玩家，避免与player条件混淆）
                        matches = !(entity instanceof ServerPlayer);
                    } else if ("player".equalsIgnoreCase(entityId)) {
                        // 特殊值 "player" 代表玩家实体
                        matches = entity instanceof ServerPlayer;
                    } else if ("*all".equalsIgnoreCase(entityId)) {
                        // *all 表示任意实体（包括玩家）
                        matches = true;
                    } else {
                        String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                                .getKey(entity.getType()).toString();
                        matches = id.equals(entityId);
                    }
                    if (matches) {
                        actualCount++;
                        if (checkAny && actualCount > 0) {
                            yield true; // 只要有一个就满足
                        }
                    }
                }

                if (checkAny) {
                    yield actualCount > 0;
                } else {
                    yield compareValue(actualCount, requiredCount, condition.comparison);
                }
            }
            case MOOD_VALUE -> {
                // 心情值
                SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(player);
                float moodValue = mood.getMood();
                yield compareFloatValue(moodValue, condition.value, condition.comparison);
            }
            case IS_PSYCHO -> {
                // 是否处于疯狂模式
                SREPlayerPsychoComponent psycho = SREPlayerPsychoComponent.KEY.get(player);
                yield psycho.getPsychoTicks() > 0;
            }
            case IS_POISONED -> {
                // 是否中毒
                SREPlayerPoisonComponent poison = SREPlayerPoisonComponent.KEY.get(player);
                yield poison.getPoisonTicks() > 0;
            }
            case ARMOR_AMOUNT -> {
                // 护盾值（普通护盾 + 限时护盾 + 弱效护盾叠加层数）
                SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(player);
                SREWeakArmorPlayerComponent weakArmor = SREWeakArmorPlayerComponent.KEY.get(player);
                int totalShield = armor.getArmor() + weakArmor.getWeakArmor();
                yield compareValue(totalShield, condition.value, condition.comparison);
            }
            case HAS_TASK -> {
                // 是否有任务
                SREPlayerTaskComponent taskComponent = SREPlayerTaskComponent.KEY.get(player);
                yield !taskComponent.tasks.isEmpty();
            }
            case TASK_STREAK -> {
                // 连续完成任务数
                SREPlayerTaskComponent taskComponent = SREPlayerTaskComponent.KEY.get(player);
                yield compareValue(taskComponent.taskStreak, condition.value, condition.comparison);
            }
            case GAME_RUNNING -> {
                // 游戏是否进行中
                SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(world);
                yield gameWorld.isRunning();
            }
            case PSYCHOS_ACTIVE -> {
                // 当前活跃疯狂玩家数量
                SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(world);
                yield compareValue(gameWorld.getPsychosActive(), condition.value, condition.comparison);
            }
            case IS_BLACKOUT -> {
                // 是否处于关灯状态
                SREWorldBlackoutComponent blackout = SREWorldBlackoutComponent.KEY.get(world);
                yield blackout.isBlackoutActive();
            }
            case IS_MONITOR_BROKEN -> {
                // 监控是否失灵
                SREMonitorWorldComponent monitor = SREMonitorWorldComponent.KEY.get(world);
                yield monitor.isBroken();
            }
            case NEED_TASK_TYPE -> {
                // 需要完成特定类型任务
                SREPlayerTaskComponent taskComponent = SREPlayerTaskComponent.KEY.get(player);
                String requiredTaskType = condition.stringValue;
                if (requiredTaskType == null || requiredTaskType.isEmpty()) {
                    yield false;
                }
                // 检查玩家当前任务是否匹配指定类型
                yield taskComponent.tasks.values().stream().anyMatch(task -> {
                    String taskType = task.getType().name().toLowerCase();
                    return requiredTaskType.equals(taskType) || ("random".equals(requiredTaskType) && taskType != null);
                });
            }
            case NEED_CUSTOM_TASK -> {
                // 需要完成自定义任务
                SREPlayerTaskComponent taskComponent = SREPlayerTaskComponent.KEY.get(player);
                String requiredTaskId = condition.stringValue;
                if (requiredTaskId == null || requiredTaskId.isEmpty()) {
                    yield false;
                }
                // 检查玩家当前是否有指定ID的自定义任务
                yield taskComponent.tasks.values().stream()
                        .anyMatch(task -> requiredTaskId.equals(task.getCustomTaskId()));
            }
            case PLAYER_DAMAGED_BY_PLAYER -> {
                // 玩家受到来自玩家的原版伤害
                // value表示半径范围，stringValue可选表示需要特定攻击者UUID
                double radius = condition.value;
                AABB checkBox = new AABB(pos).inflate(radius);
                if (!checkBox.contains(player.getBoundingBox().getCenter())) {
                    yield false; // 玩家不在范围内
                }
                // 检查是否在时间窗口内受到玩家伤害
                yield SREPlayerDamageTrackerComponent.hasPlayerDamage(player, elapsedGameTime);
            }
            case PLAYER_DAMAGED_BY_NON_PLAYER -> {
                // 玩家受到非玩家来源的原版伤害
                // value表示半径范围
                double radius = condition.value;
                AABB checkBox = new AABB(pos).inflate(radius);
                if (!checkBox.contains(player.getBoundingBox().getCenter())) {
                    yield false; // 玩家不在范围内
                }
                // 检查是否在时间窗口内受到非玩家伤害
                yield SREPlayerDamageTrackerComponent.hasNonPlayerDamage(player, elapsedGameTime);
            }
            case REDSTONE_SIGNAL -> {
                // 实体交互方块接收到红石信号
                yield receivedRedstoneSignal;
            }
            case HAS_MODIFIER -> {
                // 玩家拥有某个修饰符
                String modifierId = condition.stringValue;
                if (modifierId == null || modifierId.isEmpty()) {
                    yield false;
                }
                WorldModifierComponent modifierComponent = WorldModifierComponent.KEY.get(world);
                ResourceLocation modifierRl = ResourceLocation.parse(modifierId);
                // 在全局修饰符列表中找到对应的修饰符
                SREModifier targetModifier = org.agmas.harpymodloader.modifiers.HMLModifiers.MODIFIERS.stream()
                        .filter(m -> m.identifier().equals(modifierRl))
                        .findFirst().orElse(null);
                if (targetModifier == null) {
                    yield false;
                }
                yield modifierComponent.isModifier(player.getUUID(), targetModifier);
            }
            case FAKE_POISONED -> {
                // 玩家是否触发了假毒
                SREPlayerPoisonComponent poison = SREPlayerPoisonComponent.KEY.get(player);
                yield poison.fakePoison;
            }
            case HAS_WEAK_ARMOR -> {
                // 玩家是否拥有弱效护盾
                SREWeakArmorPlayerComponent weakArmor = SREWeakArmorPlayerComponent.KEY.get(player);
                yield weakArmor.getWeakArmor() > 0;
            }
        };
    }

    // 辅助方法：比较浮点数值
    private static boolean compareFloatValue(float actual, double target, ComparisonType comparison) {
        float targetFloat = (float) target;
        return switch (comparison) {
            case EQUALS -> Math.abs(actual - targetFloat) < 0.001f;
            case GREATER -> actual > targetFloat;
            case LESS -> actual < targetFloat;
            case GREATER_EQUAL -> actual >= targetFloat;
            case LESS_EQUAL -> actual <= targetFloat;
        };
    }

    // 辅助方法：比较数值
    private static boolean compareValue(int actual, double target, ComparisonType comparison) {
        int targetInt = (int) target;
        return switch (comparison) {
            case EQUALS -> actual == targetInt;
            case GREATER -> actual > targetInt;
            case LESS -> actual < targetInt;
            case GREATER_EQUAL -> actual >= targetInt;
            case LESS_EQUAL -> actual <= targetInt;
        };
    }

    // 执行触发内容
    private void executeActions(ServerPlayer player, ServerLevel world, BlockPos pos, long currentGameTime) {
        for (TriggerAction action : actions) {
            executeSingleAction(action, player, world, pos, currentGameTime);
        }
    }

    private void executeSingleAction(TriggerAction action, ServerPlayer player, ServerLevel world, BlockPos pos,
            long currentGameTime) {
        // 需要阵营过滤的触发类型（作用于玩家的动作）
        Set<ActionType> playerActions = Set.of(
                ActionType.POISON, ActionType.CURE_POISON, ActionType.SET_SHIELD, ActionType.DAMAGE_DEATH,
                ActionType.FORCE_KILL,
                ActionType.MOOD_CHANGE, ActionType.CHANGE_ROLE, ActionType.CHANGE_TASK, ActionType.PSYCHO_MODE,
                ActionType.COIN_CHANGE,
                ActionType.GIVE_EFFECT, ActionType.SHOW_TITLE, ActionType.ITEM_COOLDOWN, ActionType.SET_MOOD,
                ActionType.CURE_PSYCHO,
                ActionType.CLEAR_TASKS, ActionType.COMPLETE_TASK, ActionType.ADD_CUSTOM_TASK, ActionType.ADD_EXTRA_TASK,
                ActionType.COMPLETE_CUSTOM_TASK, ActionType.NARRATOR,
                ActionType.ADD_MODIFIER, ActionType.REMOVE_MODIFIER,
                ActionType.SEND_WELCOME, ActionType.SET_TIMED_SHIELD, ActionType.SET_WEAK_SHIELD,
                ActionType.TRIGGER_FAKE_POISON);

        // 特殊处理的动作类型（已有自己的玩家过滤逻辑）
        Set<ActionType> specialActions = Set.of(
                ActionType.TELEPORT, ActionType.RESURRECT, ActionType.BROADCAST_MESSAGE, ActionType.CLEAR_ENTITIES,
                ActionType.ROLE_CUSTOM_WIN, ActionType.TRIGGER_SABOTAGE);

        // 需要玩家过滤的动作类型
        if (playerActions.contains(action.type)) {
            // 阵营过滤：从所有玩家中筛选符合阵营的玩家
            List<ServerPlayer> eligiblePlayers;
            if (action.targetTeamType == TeamType.ALL) {
                // ALL 阵营：只作用于触发玩家
                eligiblePlayers = List.of(player);
            } else {
                // 非 ALL 阵营：从所有玩家中筛选
                eligiblePlayers = world.players().stream()
                        .filter(p -> !p.isSpectator())
                        .filter(p -> checkTeamMatch(p, world, action.targetTeamType))
                        .toList();
            }

            // 对每个符合条件的玩家执行动作
            for (ServerPlayer targetPlayer : eligiblePlayers) {
                executePlayerAction(action, targetPlayer, world, pos, currentGameTime);
            }
            return;
        }

        // 特殊动作或有自己过滤逻辑的动作，走原有逻辑
        executeSpecialAction(action, player, world, pos, currentGameTime);
    }

    // 执行作用于玩家的动作
    private void executePlayerAction(TriggerAction action, ServerPlayer player, ServerLevel world, BlockPos pos,
            long currentGameTime) {
        switch (action.type) {
            case POISON -> {
                SREPlayerPoisonComponent poison = SREPlayerPoisonComponent.KEY.get(player);
                int ticks = (int) (action.value * 20);
                poison.setPoisonTicks(ticks, null);
            }
            case CURE_POISON -> {
                SREPlayerPoisonComponent poison = SREPlayerPoisonComponent.KEY.get(player);
                poison.setPoisonTicks(0, null);
            }
            case SET_SHIELD -> {
                SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(player);
                armor.armor = Math.max(0, (int) action.value);
                armor.sync();
            }
            case DAMAGE_DEATH -> {
                ResourceLocation deathReason = GameConstants.DeathReasons.parseOrDefault(action.stringValue);
                GameUtils.killPlayer(player, false, null, deathReason);
            }
            case FORCE_KILL -> {
                ResourceLocation deathReason = GameConstants.DeathReasons.parseOrDefault(action.stringValue);
                GameUtils.forceKillPlayer(player, true, null, deathReason);
            }
            case MOOD_CHANGE -> {
                SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(player);
                float currentMood = mood.getMood();
                mood.setMood((float) Math.max(-1, Math.min(1, currentMood + action.value)));
            }
            case CHANGE_ROLE -> {
                // TODO: 实现职业切换逻辑
                player.displayClientMessage(Component.literal("未实现。 Not vailid."), true);
            }
            case CHANGE_TASK -> {
                SREPlayerTaskComponent taskComponent = SREPlayerTaskComponent.KEY.get(player);
                taskComponent.tasks.clear();
                taskComponent.parallelTaskTypes.clear();
                taskComponent.parallelTaskGenerated = false;
                taskComponent.currentTaskAge = 0;

                SREPlayerTaskComponent.TrainTask newTask = null;
                if (action.taskType != null && !action.taskType.isEmpty() && !action.taskType.equals("random")) {
                    try {
                        SREPlayerTaskComponent.Task specifiedTask = SREPlayerTaskComponent.Task
                                .valueOf(action.taskType.toUpperCase());
                        newTask = createTaskByType(specifiedTask);
                    } catch (IllegalArgumentException e) {
                        newTask = taskComponent.generateTask();
                    }
                } else {
                    newTask = taskComponent.generateTask();
                }

                if (newTask != null) {
                    taskComponent.tasks.put(newTask.getType(), newTask);
                    taskComponent.timesGotten.putIfAbsent(newTask.getType(), 1);
                    taskComponent.timesGotten.put(newTask.getType(),
                            taskComponent.timesGotten.get(newTask.getType()) + 1);
                    SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(player);
                    taskComponent.moodWhenTaskAssigned = mood != null ? mood.getMood() : 1f;
                }
                taskComponent.sync();
            }
            case PSYCHO_MODE -> {
                SREPlayerPsychoComponent psycho = SREPlayerPsychoComponent.KEY.get(player);
                psycho.setPsychoTicks(GameConstants.getPsychoTimer());
            }
            case COIN_CHANGE -> {
                SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
                shop.addToBalance((int) action.value);
            }
            case GIVE_EFFECT -> {
                String effectId = action.stringValue;
                int duration = action.effectDuration > 0 ? action.effectDuration * 20 : 200;
                int amplifier = Math.max(0, action.effectAmplifier);

                net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                        .getHolder(net.minecraft.resources.ResourceLocation.parse(effectId)).orElse(null);
                if (effect != null) {
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect, duration, amplifier));
                }
            }
            case SHOW_TITLE -> {
                String title = action.stringValue;
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                        Component.literal(title)));
            }
            case ITEM_COOLDOWN -> {
                String itemId = action.stringValue;
                int cooldownTicks = (int) (action.value * 20);

                if ("*".equals(itemId)) {
                    player.getCooldowns().addCooldown(player.getMainHandItem().getItem(), cooldownTicks);
                    player.getCooldowns().addCooldown(player.getOffhandItem().getItem(), cooldownTicks);
                } else {
                    net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .get(net.minecraft.resources.ResourceLocation.parse(itemId));
                    if (item != null) {
                        player.getCooldowns().addCooldown(item, cooldownTicks);
                    }
                }
            }
            case SET_MOOD -> {
                SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(player);
                if (action.stringValue != null && action.stringValue.equals("add")) {
                    mood.addMood((float) action.value);
                } else {
                    mood.setMood((float) action.value);
                }
            }
            case CURE_PSYCHO -> {
                SREPlayerPsychoComponent psycho = SREPlayerPsychoComponent.KEY.get(player);
                if (psycho.getPsychoTicks() > 0) {
                    psycho.stopPsychoAndSync();
                }
            }
            case CLEAR_TASKS -> {
                SREPlayerTaskComponent taskComponent = SREPlayerTaskComponent.KEY.get(player);
                taskComponent.tasks.clear();
                taskComponent.parallelTaskTypes.clear();
                taskComponent.parallelTaskGenerated = false;
                taskComponent.sync();
            }
            case COMPLETE_TASK -> {
                SREPlayerTaskComponent taskComponent = SREPlayerTaskComponent.KEY.get(player);
                if (!taskComponent.tasks.isEmpty()) {
                    var firstTask = taskComponent.tasks.values().iterator().next();
                    taskComponent.tasks.remove(firstTask.getType());
                    taskComponent.parallelTaskTypes.remove(firstTask.getType());
                    taskComponent.taskStreak++;
                    if (taskComponent.tasks.isEmpty()) {
                        taskComponent.currentTaskAge = 0;
                        taskComponent.parallelTaskGenerated = false;
                    }
                    taskComponent.sync();
                }
            }
            case ADD_CUSTOM_TASK -> {
                SREPlayerTaskComponent taskComponent = SREPlayerTaskComponent.KEY.get(player);

                if (action.clearTasks) {
                    taskComponent.tasks.clear();
                    taskComponent.parallelTaskTypes.clear();
                    taskComponent.parallelTaskGenerated = false;
                    taskComponent.currentTaskAge = 0;
                }

                String taskName = action.customTaskName != null ? action.customTaskName : "自定义任务";
                String taskId = action.customTaskId != null ? action.customTaskId
                        : "custom_" + System.currentTimeMillis();

                SREPlayerTaskComponent.CustomTask customTask = new SREPlayerTaskComponent.CustomTask(taskName, taskId);
                taskComponent.tasks.put(customTask.getType(), customTask);
                taskComponent.timesGotten.putIfAbsent(customTask.getType(), 1);
                taskComponent.timesGotten.put(customTask.getType(),
                        taskComponent.timesGotten.get(customTask.getType()) + 1);

                SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(player);
                taskComponent.moodWhenTaskAssigned = mood != null ? mood.getMood() : 1f;
                taskComponent.sync();

                player.sendSystemMessage(Component.translatable("message.custom_task.added", taskName));
            }
            case ADD_EXTRA_TASK -> {
                SREPlayerTaskComponent taskComponent = SREPlayerTaskComponent.KEY.get(player);
                String taskType = action.taskType;

                SREPlayerTaskComponent.TrainTask newTask = null;
                if (taskType != null && !taskType.isEmpty() && !"random".equalsIgnoreCase(taskType)) {
                    try {
                        SREPlayerTaskComponent.Task enumTask = SREPlayerTaskComponent.Task
                                .valueOf(taskType.toUpperCase());
                        newTask = enumTask.setFunction.apply(new CompoundTag());
                    } catch (IllegalArgumentException e) {
                        // 任务类型不存在，忽略
                    }
                }

                if (newTask == null) {
                    newTask = generateRandomTask(player);
                }

                if (newTask != null) {
                    taskComponent.tasks.put(newTask.getType(), newTask);
                    taskComponent.timesGotten.putIfAbsent(newTask.getType(), 1);
                    taskComponent.timesGotten.put(newTask.getType(),
                            taskComponent.timesGotten.get(newTask.getType()) + 1);
                    player.sendSystemMessage(Component.translatable("message.extra_task.added", newTask.getName()));
                }
                taskComponent.sync();
            }
            case COMPLETE_CUSTOM_TASK -> {
                String taskId = action.customTaskId;
                if (taskId == null || taskId.isEmpty()) {
                    return;
                }

                SREPlayerTaskComponent taskComponent = SREPlayerTaskComponent.KEY.get(player);
                var iterator = taskComponent.tasks.entrySet().iterator();
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    var task = entry.getValue();
                    if (taskId.equals(task.getCustomTaskId())) {
                        iterator.remove();
                        taskComponent.parallelTaskTypes.remove(task.getType());
                        taskComponent.taskStreak++;
                        player.sendSystemMessage(
                                Component.translatable("message.custom_task.completed", task.getName()));
                        break;
                    }
                }

                if (taskComponent.tasks.isEmpty()) {
                    taskComponent.currentTaskAge = 0;
                    taskComponent.parallelTaskGenerated = false;
                }
                taskComponent.sync();
            }
            case NARRATOR -> {
                String narratorText = action.narratorText;
                if (narratorText != null && !narratorText.isEmpty()) {
                    boolean shouldInterrupt = action.narratorInterrupt;
                    Component textComponent = Component.literal(narratorText);
                    ServerPlayNetworking.send(player, new CustomNarratorPacket(textComponent, shouldInterrupt));
                }
            }
            case ADD_MODIFIER -> {
                String modifierId = action.stringValue;
                if (modifierId == null || modifierId.isEmpty()) {
                    return;
                }
                ResourceLocation modifierRl = ResourceLocation.parse(modifierId);
                SREModifier targetModifier = org.agmas.harpymodloader.modifiers.HMLModifiers.MODIFIERS.stream()
                        .filter(m -> m.identifier().equals(modifierRl))
                        .findFirst().orElse(null);
                if (targetModifier != null) {
                    WorldModifierComponent modifierComponent = WorldModifierComponent.KEY.get(world);
                    modifierComponent.addModifier(player.getUUID(), targetModifier);
                    ModifierAssigned.EVENT.invoker().assignModifier(player, targetModifier);
                }
            }
            case REMOVE_MODIFIER -> {
                String modifierId = action.stringValue;
                if (modifierId == null || modifierId.isEmpty()) {
                    return;
                }
                ResourceLocation modifierRl = ResourceLocation.parse(modifierId);
                SREModifier targetModifier = org.agmas.harpymodloader.modifiers.HMLModifiers.MODIFIERS.stream()
                        .filter(m -> m.identifier().equals(modifierRl))
                        .findFirst().orElse(null);
                if (targetModifier != null) {
                    WorldModifierComponent modifierComponent = WorldModifierComponent.KEY.get(world);
                    ModifierRemoved.EVENT.invoker().removeModifier(player, targetModifier);
                    modifierComponent.removeModifier(player.getUUID(), targetModifier);
                }
            }
            case SEND_WELCOME -> {
                RoleUtils.sendWelcomeAnnouncement(player);
            }
            case TRIGGER_SABOTAGE -> {
                int duration = action.sabotageDuration > 0 ? action.sabotageDuration : 60;
                org.agmas.noellesroles.scene.SceneEventManager.startSabotage(world, duration * 20);
            }
            case SET_TIMED_SHIELD -> {
                // 给予限时护盾：叠加 1 层，重置计时器
                SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(player);
                int timedTicks = (int) (action.value * 20);
                armor.addTimedArmor(1, Math.max(0, timedTicks), true);
            }
            case SET_WEAK_SHIELD -> {
                // 给予弱效护盾：叠加 1 层，按所选死亡原因抵挡
                SREWeakArmorPlayerComponent weakArmor = SREWeakArmorPlayerComponent.KEY.get(player);
                int weakTicks = (int) (action.value * 20);
                if (action.stringValue == null || action.stringValue.isEmpty() || action.stringValue.equals("*")) {
                    weakArmor.giveWeakArmor(weakTicks, java.util.Set.of(), true);
                } else {
                    ResourceLocation reason = ResourceLocation.tryParse(action.stringValue);
                    java.util.Set<ResourceLocation> reasons = reason != null ? java.util.Set.of(reason)
                            : java.util.Set.of();
                    weakArmor.giveWeakArmor(weakTicks, reasons, false);
                }
            }
            case TRIGGER_FAKE_POISON -> {
                // 触发假毒：设置假毒 tick
                SREPlayerPoisonComponent poison = SREPlayerPoisonComponent.KEY.get(player);
                int fakeTicks = (int) action.value;
                poison.setFakePoisonTicks(Math.max(1, fakeTicks), null);
            }
            case TRIGGER_MEETING -> {
                // 触发会议：普通会议或紧急会议（紧急会议绕过冷却）
                String victim = (action.stringValue != null && !action.stringValue.isEmpty()) ? action.stringValue : null;
                net.exmo.sre.meeting.MeetingApi.startMeeting(world, player, victim, action.meetingEmergency);
            }
        }
    }

    // 执行特殊动作（已有自己过滤逻辑的动作）
    private void executeSpecialAction(TriggerAction action, ServerPlayer player, ServerLevel world, BlockPos pos,
            long currentGameTime) {
        switch (action.type) {
            case EXECUTE_COMMAND -> {
                String command = action.stringValue.replace("<player>", player.getGameProfile().getName());
                command = replaceRelativeCoordinates(command, pos);
                world.getServer().getCommands().performPrefixedCommand(
                        world.getServer().createCommandSourceStack()
                                .withPermission(4)
                                .withLevel(world),
                        command);
            }
            case ENABLE_COLLISION -> {
                this.collisionEnabled = true;
                this.collisionRemainingTicks = action.value < 0 ? -1 : (int) (action.value * 20);
            }
            case BLACKOUT -> {
                SREWorldBlackoutComponent blackout = SREWorldBlackoutComponent.KEY.get(world);
                blackout.triggerBlackout(true);
            }
            case MONITOR_BROKEN -> {
                SREMonitorWorldComponent monitor = SREMonitorWorldComponent.KEY.get(world);
                monitor.triggerBroken(true, 600);
            }
            case ADD_TIME -> {
                SREGameTimeComponent time = SREGameTimeComponent.KEY.get(world);
                int seconds = (int) (action.value * 60);
                time.addTime(seconds * 20);
            }
            case SET_TIME -> {
                SREGameTimeComponent time = SREGameTimeComponent.KEY.get(world);
                int seconds = (int) (action.value * 60);
                time.setTime(seconds * 20);
            }
            case GAME_WIN -> {
                String winnerMessage = action.stringValue.isEmpty() ? "custom" : action.stringValue;
                SREGameRoundEndComponent roundEnd = SREGameRoundEndComponent.KEY.get(world);
                roundEnd.CustomWinnerID = winnerMessage;
                roundEnd.setRoundEndData(world.players(), GameUtils.WinStatus.CUSTOM);
                GameUtils.stopGame(world);
            }
            case ROLE_CUSTOM_WIN -> {
                String roleId = action.roleWinId != null ? action.roleWinId : "";
                String description = action.roleWinDescription != null ? action.roleWinDescription : "";
                String subtitle = action.roleWinSubtitle != null ? action.roleWinSubtitle : "";
                SREGameRoundEndComponent roundEnd = SREGameRoundEndComponent.KEY.get(world);

                // 解析职业ID，查找角色以获得颜色
                // 支持三种格式：noellesroles:candlebearer（完整ID）、candlebearer（简写路径）、customrole:xxx（自定义职业）
                ResourceLocation roleRl = ResourceLocation.tryParse(roleId);
                String winnerIdPath; // 用于 CustomWinnerID，必须是纯路径（与 playerRole.identifier().getPath() 匹配）
                int winnerColor = 0xFFFFFF;

                if (roleRl != null) {
                    SRERole role = TMMRoles.ROLES.get(roleRl);
                    if (role != null) {
                        // 找到角色 → 用角色自身的路径和颜色
                        winnerIdPath = role.identifier().getPath();
                        winnerColor = role.color();
                    } else if (!roleRl.getNamespace().equals("minecraft")) {
                        // 完整ID但查不到角色（如 customrole:xxx）→ 用解析出的路径 + 默认颜色
                        winnerIdPath = roleRl.getPath();
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "§6提示: 未找到职业 '" + roleId + "', 使用默认颜色，路径为 '" + winnerIdPath + "'"));
                    } else {
                        // tryParse 自动补了 minecraft: 前缀，说明用户输入的是纯路径
                        // → 直接用原始输入作为路径
                        winnerIdPath = roleId;
                    }
                } else {
                    // 无法解析 → 直接用原始输入
                    winnerIdPath = roleId.isEmpty() ? "custom" : roleId;
                }

                roundEnd.CustomWinnerID = winnerIdPath;
                roundEnd.CustomWinnerColor = winnerColor;

                // 设置自定义描述（标题）
                if (!description.isEmpty()) {
                    roundEnd.CustomWinnerTitle = net.minecraft.network.chat.Component.literal(description);
                } else {
                    roundEnd.CustomWinnerTitle = null;
                }
                // 设置自定义子标题
                if (!subtitle.isEmpty()) {
                    roundEnd.CustomWinnerSubtitle = net.minecraft.network.chat.Component.literal(subtitle);
                } else {
                    roundEnd.CustomWinnerSubtitle = null;
                }

                // 如果有标题/子标题，使用 CUSTOM_COMPONENT 状态显示自定义文字
                GameUtils.WinStatus winStatus = (!description.isEmpty() || !subtitle.isEmpty())
                        ? GameUtils.WinStatus.CUSTOM_COMPONENT
                        : GameUtils.WinStatus.CUSTOM;

                roundEnd.setRoundEndData(world.players(), winStatus);
                roundEnd.sync();
                GameUtils.stopGame(world);
            }
            case BLOCK_COOLDOWN -> {
                int cooldownSeconds = (int) action.value;
                this.setBlockCooldown(cooldownSeconds, currentGameTime);
            }
            case END_BLACKOUT -> {
                SREWorldBlackoutComponent blackout = SREWorldBlackoutComponent.KEY.get(world);
                blackout.reset();
            }
            case FIX_MONITOR -> {
                SREMonitorWorldComponent monitor = SREMonitorWorldComponent.KEY.get(world);
                monitor.reset();
            }
            case OUTPUT_REDSTONE -> {
                // 输出红石信号
                int strength = (int) action.value;
                strength = Math.max(0, Math.min(15, strength));
                this.setRedstoneOutput(true, strength);
            }
            case TELEPORT -> {
                int targetId = (int) action.value;
                BlockPos targetPos = findTeleportPoint(world, pos, targetId);
                if (targetPos == null) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component
                            .translatable("message.teleport_point_not_found", targetId, MAX_TELEPORT_RANGE));
                    return;
                }

                switch (action.teleportTarget) {
                    case 0 -> {
                        // 触发玩家（需要阵营检查）
                        if (action.targetTeamType == TeamType.ALL
                                || checkTeamMatch(player, world, action.targetTeamType)) {
                            player.teleportTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
                        }
                    }
                    case 1 -> {
                        // 随机玩家（根据阵营过滤）
                        List<ServerPlayer> eligiblePlayers = world.players().stream()
                                .filter(p -> !p.isSpectator())
                                .filter(p -> action.targetTeamType == TeamType.ALL
                                        || checkTeamMatch(p, world, action.targetTeamType))
                                .toList();
                        if (!eligiblePlayers.isEmpty()) {
                            ServerPlayer randomPlayer = eligiblePlayers
                                    .get(world.getRandom().nextInt(eligiblePlayers.size()));
                            randomPlayer.teleportTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
                        }
                    }
                    case 2 -> {
                        // 所有玩家（根据阵营过滤）
                        for (ServerPlayer p : world.players()) {
                            if (!p.isSpectator() && (action.targetTeamType == TeamType.ALL
                                    || checkTeamMatch(p, world, action.targetTeamType))) {
                                p.teleportTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
                            }
                        }
                    }
                }
            }
            case RESURRECT -> {
                double radius = action.value;
                AABB box = new AABB(pos).inflate(radius);
                List<PlayerBodyEntity> bodies = world.getEntitiesOfClass(PlayerBodyEntity.class, box);

                for (PlayerBodyEntity body : bodies) {
                    ServerPlayer deadPlayer = (ServerPlayer) world.getPlayerByUUID(body.getPlayerUuid());
                    if (deadPlayer == null || !deadPlayer.isSpectator())
                        continue;

                    if (action.targetTeamType != TeamType.ALL
                            && !checkTeamMatch(deadPlayer, world, action.targetTeamType)) {
                        continue;
                    }

                    deadPlayer.getInventory().clearContent();
                    deadPlayer.teleportTo(body.getX(), body.getY(), body.getZ());
                    deadPlayer.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
                    body.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);

                    SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(deadPlayer);
                    shop.setBalance(200);

                    world.players().forEach(p -> {
                        p.playNotifySound(net.minecraft.sounds.SoundEvents.TOTEM_USE,
                                deadPlayer.getSoundSource(), 1.2f, 1.5f);
                    });
                    break;
                }
            }
            case BROADCAST_MESSAGE -> {
                String message = action.stringValue;
                for (ServerPlayer p : world.players()) {
                    if (action.targetTeamType == TeamType.ALL || checkTeamMatch(p, world, action.targetTeamType)) {
                        p.sendSystemMessage(Component.literal(message));
                    }
                }
            }
            case CLEAR_ENTITIES -> {
                double radius = action.value;
                String entityId = action.stringValue;
                AABB checkBox = new AABB(pos).inflate(radius);
                var entitiesToRemove = new ArrayList<net.minecraft.world.entity.Entity>();

                for (var entity : world.getEntities(null, checkBox)) {
                    boolean matches = false;
                    if ("*".equals(entityId)) {
                        matches = !(entity instanceof ServerPlayer);
                    } else if ("player".equalsIgnoreCase(entityId)) {
                        if (entity instanceof ServerPlayer serverPlayer) {
                            matches = action.targetTeamType == TeamType.ALL
                                    || checkTeamMatch(serverPlayer, world, action.targetTeamType);
                        } else {
                            matches = false;
                        }
                    } else if ("*all".equalsIgnoreCase(entityId)) {
                        matches = true;
                    } else {
                        String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                                .getKey(entity.getType()).toString();
                        matches = id.equals(entityId);
                    }
                    if (matches) {
                        entitiesToRemove.add(entity);
                    }
                }

                for (var entity : entitiesToRemove) {
                    entity.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
                }
            }
        }
    }

    // 传送点注册表 - 用于高效查找传送点（按维度存储，支持同一ID多个位置）
    private static final Map<Level, Map<Integer, List<BlockPos>>> TELEPORT_POINT_REGISTRY = new java.util.concurrent.ConcurrentHashMap<>();
    // 传送范围限制（球形半径）
    private static final int MAX_TELEPORT_RANGE = 100;

    // 注册传送点（当方块设置为传送点时调用）
    public void registerTeleportPoint() {
        if (this.isTeleportPoint && this.teleportPointId >= 0 && this.level != null) {
            TELEPORT_POINT_REGISTRY
                    .computeIfAbsent(this.level, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(this.teleportPointId, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                    .add(this.worldPosition.immutable());
        }
    }

    // 注销传送点（当方块不再作为传送点时调用）
    public void unregisterTeleportPoint() {
        if (this.level != null) {
            Map<Integer, List<BlockPos>> registry = TELEPORT_POINT_REGISTRY.get(this.level);
            if (registry != null) {
                List<BlockPos> positions = registry.get(this.teleportPointId);
                if (positions != null) {
                    positions.remove(this.worldPosition.immutable());
                    if (positions.isEmpty()) {
                        registry.remove(this.teleportPointId);
                    }
                }
            }
        }
    }

    // 设置传送点时调用此方法
    public void setTeleportPoint(boolean teleportPoint) {
        // 如果之前是传送点，先注销
        if (this.isTeleportPoint) {
            unregisterTeleportPoint();
        }
        this.isTeleportPoint = teleportPoint;
        setChanged();
        // 如果现在是传送点，注册它
        if (teleportPoint && this.teleportPointId >= 0) {
            registerTeleportPoint();
        }
    }

    // 设置传送点ID时调用此方法
    public void setTeleportPointId(int id) {
        // 如果之前是传送点，先注销
        if (this.isTeleportPoint) {
            unregisterTeleportPoint();
        }
        this.teleportPointId = id;
        setChanged();
        // 如果是传送点，用新ID重新注册
        if (this.isTeleportPoint && id >= 0) {
            registerTeleportPoint();
        }
    }

    // 查找指定ID的传送点（限制在100格范围内）
    private static BlockPos findTeleportPoint(ServerLevel world, BlockPos sourcePos, int targetId) {
        // 先尝试从注册表快速查找
        Map<Integer, List<BlockPos>> registry = TELEPORT_POINT_REGISTRY.get(world);
        if (registry != null) {
            List<BlockPos> positions = registry.get(targetId);
            if (positions != null && !positions.isEmpty()) {
                for (BlockPos registeredPos : positions) {
                    // 检查是否在范围内
                    double distSq = sourcePos.distSqr(registeredPos);
                    if (distSq <= MAX_TELEPORT_RANGE * MAX_TELEPORT_RANGE) {
                        // 验证该位置确实是传送点
                        if (world.getBlockEntity(registeredPos) instanceof EntityInteractionBlockEntity blockEntity) {
                            if (blockEntity.isTeleportPoint && blockEntity.getTeleportPointId() == targetId) {
                                return registeredPos;
                            }
                        }
                    }
                }
            }
        }

        // 如果注册表中没有找到（可能未注册或全部超出范围），限制范围搜索
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        int range = MAX_TELEPORT_RANGE;
        int sourceX = sourcePos.getX();
        int sourceY = sourcePos.getY();
        int sourceZ = sourcePos.getZ();
        int rangeSq = range * range;

        // 只搜索源点周围100格的球形范围
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    // 球形检查
                    if (dx * dx + dy * dy + dz * dz > rangeSq)
                        continue;

                    mutablePos.set(sourceX + dx, sourceY + dy, sourceZ + dz);

                    BlockEntity be = world.getBlockEntity(mutablePos);
                    if (be instanceof EntityInteractionBlockEntity blockEntity) {
                        if (blockEntity.isTeleportPoint && blockEntity.getTeleportPointId() == targetId) {
                            BlockPos result = mutablePos.immutable();
                            // 更新注册表（添加到列表）
                            TELEPORT_POINT_REGISTRY
                                    .computeIfAbsent(world, k -> new ConcurrentHashMap<>())
                                    .computeIfAbsent(targetId, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                                    .add(result);
                            return result;
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        // 保存条件
        ListTag conditionsTag = new ListTag();
        for (TriggerCondition condition : conditions) {
            conditionsTag.add(condition.toNbt());
        }
        tag.put("Conditions", conditionsTag);

        // 保存触发内容
        ListTag actionsTag = new ListTag();
        for (TriggerAction action : actions) {
            actionsTag.add(action.toNbt());
        }
        tag.put("Actions", actionsTag);

        tag.putInt("CooldownTicks", cooldownTicks);
        tag.putBoolean("CollisionEnabled", collisionEnabled);
        tag.putInt("CollisionRemainingTicks", collisionRemainingTicks);
        tag.putBoolean("IsTeleportPoint", isTeleportPoint);
        tag.putInt("TeleportPointId", teleportPointId);
        tag.putInt("BlockCooldownTicks", blockCooldownTicks);
        tag.putInt("BlockCooldownEndGameTime", blockCooldownEndGameTime);

        // 任务路标相关
        tag.putBoolean("IsTaskMarker", isTaskMarker);
        tag.putInt("TaskMarkerColor", taskMarkerColor);
        if (taskHighlightCondition != null) {
            tag.putString("TaskHighlightCondition", taskHighlightCondition.name());
        }
        tag.putString("TaskHighlightTaskType", taskHighlightTaskType != null ? taskHighlightTaskType : "*");
        tag.putString("TaskHighlightCustomTaskId", taskHighlightCustomTaskId != null ? taskHighlightCustomTaskId : "");
        tag.putInt("TaskInstinctId", taskInstinctId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        // 加载条件
        conditions.clear();
        if (tag.contains("Conditions", ListTag.TAG_LIST)) {
            ListTag conditionsTag = tag.getList("Conditions", ListTag.TAG_COMPOUND);
            for (int i = 0; i < conditionsTag.size(); i++) {
                conditions.add(TriggerCondition.fromNbt(conditionsTag.getCompound(i)));
            }
        }

        // 加载触发内容
        actions.clear();
        if (tag.contains("Actions", ListTag.TAG_LIST)) {
            ListTag actionsTag = tag.getList("Actions", ListTag.TAG_COMPOUND);
            for (int i = 0; i < actionsTag.size(); i++) {
                actions.add(TriggerAction.fromNbt(actionsTag.getCompound(i)));
            }
        }

        cooldownTicks = tag.getInt("CooldownTicks");
        if (cooldownTicks == 0)
            cooldownTicks = 40;
        collisionEnabled = tag.getBoolean("CollisionEnabled");
        collisionRemainingTicks = tag.getInt("CollisionRemainingTicks");
        isTeleportPoint = tag.getBoolean("IsTeleportPoint");
        // 使用 contains 检查，避免旧数据或缺失时覆盖默认值 -1
        teleportPointId = tag.contains("TeleportPointId") ? tag.getInt("TeleportPointId") : -1;
        blockCooldownTicks = tag.getInt("BlockCooldownTicks");
        blockCooldownEndGameTime = tag.getInt("BlockCooldownEndGameTime");

        // 任务路标相关
        isTaskMarker = tag.getBoolean("IsTaskMarker");
        taskMarkerColor = tag.contains("TaskMarkerColor") ? tag.getInt("TaskMarkerColor") : 0xFFFFFF;
        if (tag.contains("TaskHighlightCondition")) {
            taskHighlightCondition = TaskHighlightCondition.valueOf(tag.getString("TaskHighlightCondition"));
        } else {
            taskHighlightCondition = TaskHighlightCondition.NONE;
        }
        taskHighlightTaskType = tag.getString("TaskHighlightTaskType");
        if (taskHighlightTaskType.isEmpty())
            taskHighlightTaskType = "*";
        taskHighlightCustomTaskId = tag.getString("TaskHighlightCustomTaskId");
        if (taskHighlightCustomTaskId == null)
            taskHighlightCustomTaskId = "";
        taskInstinctId = tag.contains("TaskInstinctId") ? tag.getInt("TaskInstinctId") : 100;
    }

    // 条件类型枚举
    public enum ConditionType {
        PASS_THROUGH, // 穿过方块
        TIMER, // 自动定时
        TIME_ANCHOR, // 时间锚点
        PROXIMITY_SPHERE, // 球形范围
        PROXIMITY_LINE, // 直线范围
        HAS_ITEM, // 有特定物品
        CLICK_BLOCK, // 点击方块
        LOOKING_AT, // 看向方块
        STANDING_ON_BLOCK, // 站在特定方块上
        DEATH, // 死亡
        USE_ITEM, // 使用物品
        SPEAK, // 说话
        COIN_AMOUNT, // 金币数量
        ROLE_IS, // 特定职业
        ROLE_TEAM, // 职业阵营
        HAS_KILLED, // 击杀过玩家
        PLAYER_COUNT, // 玩家数量
        ALIVE_PLAYERS, // 存活玩家数量
        IS_SNEAKING, // 潜行状态
        IS_SPRINTING, // 疾跑状态
        HAS_EFFECT, // 有特定效果
        PROBABILITY, // 触发概率
        WORLD_TIME, // 世界时间（白天/中午/傍晚/午夜）
        ENTITY_COUNT, // 范围内实体数量
        MOOD_VALUE, // 心情值
        IS_PSYCHO, // 是否处于疯狂模式
        IS_POISONED, // 是否中毒
        ARMOR_AMOUNT, // 护盾值
        HAS_TASK, // 是否有任务
        TASK_STREAK, // 连续完成任务数
        GAME_RUNNING, // 游戏是否进行中
        PSYCHOS_ACTIVE, // 当前活跃疯狂玩家数量
        IS_BLACKOUT, // 是否处于关灯状态
        IS_MONITOR_BROKEN, // 监控是否失灵
        NEED_TASK_TYPE, // 需要完成特定类型任务
        NEED_CUSTOM_TASK, // 需要完成自定义任务
        PLAYER_DAMAGED_BY_PLAYER, // 玩家受到来自玩家的原版伤害
        PLAYER_DAMAGED_BY_NON_PLAYER, // 玩家受到非玩家来源的原版伤害
        ELAPSED_TIME, // 游戏经过的时间
        REDSTONE_SIGNAL, // 接收到红石信号
        HAS_MODIFIER, // 玩家拥有某个修饰符
        FAKE_POISONED, // 玩家是否触发了假毒
        HAS_WEAK_ARMOR // 玩家是否拥有弱效护盾
    }

    // 世界时间类型枚举
    public enum WorldTimeType {
        DAY, // 白天 (0-12000)
        NOON, // 中午 (6000)
        SUNSET, // 傍晚 (12000-13000)
        NIGHT, // 夜晚 (13000-23000)
        MIDNIGHT // 午夜 (18000)
    }

    // 比较类型枚举
    public enum ComparisonType {
        EQUALS, GREATER, LESS, GREATER_EQUAL, LESS_EQUAL
    }

    // 阵营类型枚举
    public enum TeamType {
        ALL, CIVILIAN, SHERIFF, NEUTRAL, NEUTRAL_KILLER, NEUTRAL_SPECIAL, KILLER
    }

    // 直线范围方向枚举
    public enum LineDirection {
        ALL, // * - 所有方向
        EAST, // 东 (+X)
        WEST, // 西 (-X)
        SOUTH, // 南 (+Z)
        NORTH, // 北 (-Z)
        UP, // 上 (+Y)
        DOWN // 下 (-Y)
    }

    // 逻辑运算符枚举（用于条件组合）
    public enum LogicOperator {
        AND, // 与 - 前一个和后一个条件都必须满足
        OR, // 或 - 前一个或后一个条件满足即可
        NAND, // 与非 - 前一个和后一个条件不同时满足
        NOR // 或非 - 前一个和后一个条件都不满足
    }

    // 触发内容类型枚举
    public enum ActionType {
        EXECUTE_COMMAND, // 执行指令
        POISON, // 中毒
        CURE_POISON, // 解毒
        SET_SHIELD, // 设置护盾
        DAMAGE_DEATH, // 一般受击死亡
        FORCE_KILL, // 强制死亡
        ENABLE_COLLISION, // 启用碰撞箱
        MOOD_CHANGE, // 改变心情值（增减）
        CHANGE_ROLE, // 改变职业
        CHANGE_TASK, // 更改任务
        RESURRECT, // 复活
        PSYCHO_MODE, // 疯狂模式
        BLACKOUT, // 关灯
        MONITOR_BROKEN, // 监控失灵
        ADD_TIME, // 增加/减少时间
        SET_TIME, // 设置时间
        GAME_WIN, // 游戏胜利
        COIN_CHANGE, // 改变金币
        GIVE_EFFECT, // 给予状态效果
        TELEPORT, // 传送到指定传送点
        SHOW_TITLE, // 显示标题
        BROADCAST_MESSAGE, // 广播消息
        ITEM_COOLDOWN, // 物品冷却
        BLOCK_COOLDOWN, // 方块进入冷却
        CLEAR_ENTITIES, // 清除范围内实体
        SET_MOOD, // 设置心情值（直接设置或增减）
        CURE_PSYCHO, // 解除疯狂模式
        CLEAR_TASKS, // 清除所有任务
        COMPLETE_TASK, // 强制完成当前任务
        END_BLACKOUT, // 结束关灯
        FIX_MONITOR, // 修复监控
        ADD_CUSTOM_TASK, // 增加自定义任务（根据clearTasks决定是否清空当前任务）
        ADD_EXTRA_TASK, // 额外添加任务（不清空当前任务，支持random随机任务）
        COMPLETE_CUSTOM_TASK, // 完成自定义任务
        NARRATOR, // 语音播报
        OUTPUT_REDSTONE, // 输出红石信号
        ADD_MODIFIER, // 为玩家添加修饰符
        REMOVE_MODIFIER, // 为玩家移除修饰符
        ROLE_CUSTOM_WIN, // 带绑定职业的自定义获胜
        SEND_WELCOME, // 发送欢迎报幕
        TRIGGER_SABOTAGE, // 触发破坏任务
        SET_TIMED_SHIELD, // 给予限时护盾
        SET_WEAK_SHIELD, // 给予弱效护盾
        TRIGGER_FAKE_POISON, // 触发假毒
        TRIGGER_MEETING // 触发会议（普通会议 / 紧急会议）
    }

    // 条件数据类
    public static class TriggerCondition {
        public ConditionType type;
        public double value; // 数值参数
        public String stringValue; // 字符串参数
        public ComparisonType comparison = ComparisonType.EQUALS;
        public TeamType teamType = TeamType.CIVILIAN; // 默认值，确保序列化安全
        public LogicOperator logicOperator = LogicOperator.AND; // 与下一个条件的逻辑关系
        public WorldTimeType worldTimeType; // 世界时间类型（用于WORLD_TIME条件）
        public int entityCount; // 实体数量（用于ENTITY_COUNT条件）
        public boolean checkAnyCount; // 是否检查任意数量（*表示不检查数量）
        public LineDirection lineDirection = LineDirection.ALL; // 默认值，确保序列化安全
        public boolean triggerOnce = false; // 是否一次性触发（触发后不再触发，用于CLICK_BLOCK等）

        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Type", type != null ? type.name() : "PASS_THROUGH");
            tag.putDouble("Value", value);
            tag.putString("StringValue", stringValue != null ? stringValue : "");
            tag.putString("Comparison", comparison != null ? comparison.name() : ComparisonType.EQUALS.name());
            tag.putString("TeamType", teamType != null ? teamType.name() : TeamType.CIVILIAN.name());
            tag.putString("LogicOperator", logicOperator != null ? logicOperator.name() : LogicOperator.AND.name());
            if (worldTimeType != null)
                tag.putString("WorldTimeType", worldTimeType.name());
            tag.putInt("EntityCount", entityCount);
            tag.putBoolean("CheckAnyCount", checkAnyCount);
            if (lineDirection != null)
                tag.putString("LineDirection", lineDirection.name());
            tag.putBoolean("TriggerOnce", triggerOnce);
            return tag;
        }

        public static TriggerCondition fromNbt(CompoundTag tag) {
            TriggerCondition condition = new TriggerCondition();
            condition.type = ConditionType.valueOf(tag.getString("Type"));
            condition.value = tag.getDouble("Value");
            condition.stringValue = tag.getString("StringValue");
            if (condition.stringValue.isEmpty())
                condition.stringValue = null;
            condition.comparison = ComparisonType.valueOf(tag.getString("Comparison"));
            if (tag.contains("TeamType")) {
                condition.teamType = TeamType.valueOf(tag.getString("TeamType"));
            }
            if (tag.contains("LogicOperator")) {
                condition.logicOperator = LogicOperator.valueOf(tag.getString("LogicOperator"));
            } else {
                condition.logicOperator = LogicOperator.AND;
            }
            if (tag.contains("WorldTimeType")) {
                condition.worldTimeType = WorldTimeType.valueOf(tag.getString("WorldTimeType"));
            }
            condition.entityCount = tag.getInt("EntityCount");
            condition.checkAnyCount = tag.getBoolean("CheckAnyCount");
            if (tag.contains("LineDirection")) {
                condition.lineDirection = LineDirection.valueOf(tag.getString("LineDirection"));
            } else {
                condition.lineDirection = LineDirection.ALL; // 默认值
            }
            condition.triggerOnce = tag.contains("TriggerOnce") && tag.getBoolean("TriggerOnce");
            return condition;
        }
    }

    // 触发内容数据类
    public static class TriggerAction {
        public ActionType type;
        public double value; // 数值参数
        public String stringValue; // 字符串参数
        public String taskType; // 任务类型（用于CHANGE_TASK）
        public int effectDuration; // 效果持续时间（秒，用于GIVE_EFFECT）
        public int effectAmplifier; // 效果等级（用于GIVE_EFFECT）
        public String customTaskName; // 自定义任务名称（用于ADD_CUSTOM_TASK和ADD_EXTRA_TASK）
        public String customTaskId; // 自定义任务ID（用于ADD_CUSTOM_TASK和COMPLETE_CUSTOM_TASK）
        public String narratorText; // 语音播报文本（用于NARRATOR）
        public boolean narratorInterrupt; // 是否打断当前播报（用于NARRATOR）
        public boolean clearTasks; // 是否清空当前任务（用于ADD_CUSTOM_TASK，true=清空后添加，false=不清空直接添加）
        // 阵营过滤类型（用于过滤触发内容对什么阵营生效，默认为ALL表示全部阵营）
        public TeamType targetTeamType = TeamType.ALL;
        // 传送目标类型（用于TELEPORT动作，0=触发玩家，1=随机玩家，2=所有玩家）
        public int teleportTarget = 0;
        // 带绑定职业的自定义获胜字段（用于ROLE_CUSTOM_WIN）
        public String roleWinId = ""; // 职业ID
        public String roleWinDescription = ""; // 获胜描述（标题）
        public String roleWinSubtitle = ""; // 获胜子标题
        public int sabotageDuration = 60; // 破坏任务持续时间（秒，用于TRIGGER_SABOTAGE）
        public boolean meetingEmergency = false; // 是否紧急会议（true=紧急会议，绕过冷却；false=普通会议，用于TRIGGER_MEETING）

        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Type", type != null ? type.name() : ActionType.EXECUTE_COMMAND.name());
            tag.putDouble("Value", value);
            tag.putString("StringValue", stringValue != null ? stringValue : "");
            tag.putString("TaskType", taskType != null ? taskType : "");
            tag.putInt("EffectDuration", effectDuration);
            tag.putInt("EffectAmplifier", effectAmplifier);
            tag.putString("CustomTaskName", customTaskName != null ? customTaskName : "");
            tag.putString("CustomTaskId", customTaskId != null ? customTaskId : "");
            tag.putString("NarratorText", narratorText != null ? narratorText : "");
            tag.putBoolean("NarratorInterrupt", narratorInterrupt);
            tag.putBoolean("ClearTasks", clearTasks);
            tag.putString("TargetTeamType", targetTeamType != null ? targetTeamType.name() : TeamType.ALL.name());
            tag.putInt("TeleportTarget", teleportTarget);
            tag.putString("RoleWinId", roleWinId != null ? roleWinId : "");
            tag.putString("RoleWinDescription", roleWinDescription != null ? roleWinDescription : "");
            tag.putString("RoleWinSubtitle", roleWinSubtitle != null ? roleWinSubtitle : "");
            tag.putInt("SabotageDuration", sabotageDuration);
            tag.putBoolean("MeetingEmergency", meetingEmergency);
            return tag;
        }

        public static TriggerAction fromNbt(CompoundTag tag) {
            TriggerAction action = new TriggerAction();
            action.type = ActionType.valueOf(tag.getString("Type"));
            action.value = tag.getDouble("Value");
            action.stringValue = tag.getString("StringValue");
            if (action.stringValue.isEmpty())
                action.stringValue = null;
            action.taskType = tag.getString("TaskType");
            if (action.taskType.isEmpty())
                action.taskType = null;
            action.effectDuration = tag.getInt("EffectDuration");
            action.effectAmplifier = tag.getInt("EffectAmplifier");
            action.customTaskName = tag.getString("CustomTaskName");
            if (action.customTaskName.isEmpty())
                action.customTaskName = null;
            action.customTaskId = tag.getString("CustomTaskId");
            if (action.customTaskId.isEmpty())
                action.customTaskId = null;
            action.narratorText = tag.getString("NarratorText");
            if (action.narratorText.isEmpty())
                action.narratorText = null;
            action.narratorInterrupt = tag.getBoolean("NarratorInterrupt");
            action.clearTasks = tag.getBoolean("ClearTasks");
            if (tag.contains("TargetTeamType")) {
                action.targetTeamType = TeamType.valueOf(tag.getString("TargetTeamType"));
            } else {
                action.targetTeamType = TeamType.ALL;
            }
            action.teleportTarget = tag.contains("TeleportTarget") ? tag.getInt("TeleportTarget") : 0;
            action.roleWinId = tag.getString("RoleWinId");
            if (action.roleWinId.isEmpty()) action.roleWinId = "";
            action.roleWinDescription = tag.getString("RoleWinDescription");
            if (action.roleWinDescription.isEmpty()) action.roleWinDescription = "";
            action.roleWinSubtitle = tag.getString("RoleWinSubtitle");
            if (action.roleWinSubtitle.isEmpty()) action.roleWinSubtitle = "";
            action.sabotageDuration = tag.contains("SabotageDuration") ? tag.getInt("SabotageDuration") : 60;
            action.meetingEmergency = tag.contains("MeetingEmergency") && tag.getBoolean("MeetingEmergency");
            return action;
        }
    }

    /**
     * 生成随机任务
     */
    private static SREPlayerTaskComponent.TrainTask generateRandomTask(ServerPlayer player) {
        java.util.List<SREPlayerTaskComponent.Task> availableTasks = SREPlayerTaskComponent.Task
                .getAvailableTasksList();
        if (availableTasks.isEmpty()) {
            return null;
        }
        // 过滤掉已存在的任务类型
        SREPlayerTaskComponent taskComponent = SREPlayerTaskComponent.KEY.get(player);
        java.util.List<SREPlayerTaskComponent.Task> filteredTasks = availableTasks.stream()
                .filter(t -> !taskComponent.tasks.containsKey(t))
                .collect(java.util.stream.Collectors.toList());
        if (filteredTasks.isEmpty()) {
            // 如果所有任务都已存在，返回任意一个可用任务
            filteredTasks = new java.util.ArrayList<>(availableTasks);
        }
        SREPlayerTaskComponent.Task randomTask = filteredTasks.get(player.getRandom().nextInt(filteredTasks.size()));
        return randomTask.setFunction.apply(new net.minecraft.nbt.CompoundTag());
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registryLookup);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
