package io.wifi.starrailexpress.content.block_entity;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.*;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import io.wifi.starrailexpress.network.EntityInteractionBlockPayload;
import io.wifi.starrailexpress.network.packet.CustomNarratorPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体交互方块的BlockEntity
 * 存储条件和触发内容配置，处理逻辑执行
 */
public class EntityInteractionBlockEntity extends BlockEntity {
    // 运行范围（球形半径）
    public static final double MAX_RANGE = 100.0;

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
    // 是否启用碰撞箱
    private boolean collisionEnabled = false;
    private int collisionRemainingTicks = 0;
    // 是否是传送点
    private boolean isTeleportPoint = false;
    private int teleportPointId = -1; // 传送点数字ID
    // 方块冷却（期间不触发）
    private int blockCooldownTicks = 0;
    private int blockCooldownEndGameTime = 0; // 基于游戏时间的冷却结束时刻

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
     * @param command 原始指令
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
        EntityInteractionBlockPayload.sendOpenUI(player, this.worldPosition, this);
    }

    // 传送点相关getter/setter
    public boolean isTeleportPoint() {
        return isTeleportPoint;
    }

    public int getTeleportPointId() {
        return teleportPointId;
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

    // 从服务端接收更新
    public void updateFromServer(List<TriggerCondition> newConditions, List<TriggerAction> newActions, int newCooldown) {
        this.conditions = new ArrayList<>(newConditions);
        this.actions = new ArrayList<>(newActions);
        this.cooldownTicks = newCooldown;
        setChanged();
    }

    // 根据任务类型创建任务实例
    private static SREPlayerTaskComponent.TrainTask createTaskByType(SREPlayerTaskComponent.Task taskType) {
        return switch (taskType) {
            case SLEEP -> new SREPlayerTaskComponent.SleepTask(io.wifi.starrailexpress.game.GameConstants.SLEEP_TASK_DURATION);
            case OUTSIDE -> new SREPlayerTaskComponent.OutsideTask(io.wifi.starrailexpress.game.GameConstants.OUTSIDE_TASK_DURATION);
            case RAED_BOOK -> new SREPlayerTaskComponent.ReadBookTask(io.wifi.starrailexpress.game.GameConstants.READ_BOOK_TASK_DURATION);
            case EAT -> new SREPlayerTaskComponent.EatTask();
            case DRINK -> new SREPlayerTaskComponent.DrinkTask();
            case EXERCISE -> new SREPlayerTaskComponent.ExerciseTask(io.wifi.starrailexpress.game.GameConstants.EXERCISE_TASK_DURATION);
            case MEDITATE -> new SREPlayerTaskComponent.MeditateTask(io.wifi.starrailexpress.game.GameConstants.MEDITATE_TASK_DURATION);
            case BATHE -> new SREPlayerTaskComponent.BatheTask(io.wifi.starrailexpress.game.GameConstants.BATHE_TASK_DURATION);
            case NOTE_BLOCK -> new SREPlayerTaskComponent.NoteBlockTask(io.wifi.starrailexpress.game.GameConstants.NOTE_BLOCK_TASK_CLICK_COUNTS);
            case TOILET -> new SREPlayerTaskComponent.ToiletTask(io.wifi.starrailexpress.game.GameConstants.TOILET_TASK_DURATION);
            case CHAIR -> new SREPlayerTaskComponent.ChairTask(io.wifi.starrailexpress.game.GameConstants.CHAIR_TASK_DURATION);
            case BREATHE -> new SREPlayerTaskComponent.BreatheTask(io.wifi.starrailexpress.game.GameConstants.BREATHE_TASK_DURATION);
            default -> null;
        };
    }

    // 每tick调用
    public static void tick(Level world, BlockPos pos, BlockState state, EntityInteractionBlockEntity entity) {
        if (world.isClientSide) return;
        if (!(world instanceof ServerLevel serverWorld)) return;

        // 获取游戏世界组件
        SREGameTimeComponent timeComponent = SREGameTimeComponent.KEY.get(world);
        // 使用 resetTime - time 计算游戏开始后经过的时间（tick）
        long elapsedGameTime = timeComponent.getResetTime() - timeComponent.getTime();

        // 处理碰撞箱计时
        if (entity.collisionEnabled) {
            if (entity.collisionRemainingTicks > 0) {
                entity.collisionRemainingTicks--;
            } else if (entity.collisionRemainingTicks == 0) {
                entity.collisionEnabled = false;
            }
        }

        // 检查方块冷却（使用经过的时间计算）
        if (entity.isInCooldown(elapsedGameTime)) {
            return; // 冷却期间不处理任何触发
        }

        entity.timerTick++;

        // 获取范围内的玩家
        AABB rangeBox = new AABB(pos).inflate(MAX_RANGE);
        List<ServerPlayer> playersInRange = serverWorld.getEntitiesOfClass(ServerPlayer.class, rangeBox);

        for (ServerPlayer player : playersInRange) {
            // 检查所有条件是否满足
            if (entity.checkConditions(player, serverWorld, pos, elapsedGameTime, entity.timerTick)) {
                // 检查玩家冷却
                long lastTrigger = entity.lastTriggerTime.getOrDefault(player.getUUID(), 0L);
                if (elapsedGameTime - lastTrigger >= entity.cooldownTicks) {
                    // 执行触发内容
                    entity.executeActions(player, serverWorld, pos, elapsedGameTime);
                    entity.lastTriggerTime.put(player.getUUID(), elapsedGameTime);
                }
            }
        }
    }

    // 检查条件（支持复杂逻辑运算）
    private boolean checkConditions(ServerPlayer player, ServerLevel world, BlockPos pos, long gameTime, int timerTick) {
        if (conditions.isEmpty()) return false;
        if (conditions.size() == 1) {
            return checkSingleCondition(conditions.get(0), player, world, pos, gameTime, timerTick);
        }

        // 使用逻辑运算符组合条件
        boolean result = checkSingleCondition(conditions.get(0), player, world, pos, gameTime, timerTick);

        for (int i = 1; i < conditions.size(); i++) {
            TriggerCondition currentCondition = conditions.get(i);
            TriggerCondition previousCondition = conditions.get(i - 1);
            boolean currentResult = checkSingleCondition(currentCondition, player, world, pos, gameTime, timerTick);

            // 获取与前一个条件的逻辑运算符（默认AND）
            LogicOperator operator = previousCondition.logicOperator != null ? previousCondition.logicOperator : LogicOperator.AND;

            result = switch (operator) {
                case AND -> result && currentResult;
                case OR -> result || currentResult;
                case NAND -> !(result && currentResult);
                case NOR -> !(result || currentResult);
            };
        }

        return result;
    }

    private boolean checkSingleCondition(TriggerCondition condition, ServerPlayer player, ServerLevel world, BlockPos pos, long gameTime, int timerTick) {
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
                // 时间锚点触发
                long gameTimeSeconds = gameTime / 20;
                long targetSeconds = (long) (condition.value * 60); // 分钟转秒
                yield switch (condition.comparison) {
                    case EQUALS -> gameTimeSeconds == targetSeconds;
                    case GREATER -> gameTimeSeconds > targetSeconds;
                    case LESS -> gameTimeSeconds < targetSeconds;
                    case GREATER_EQUAL -> gameTimeSeconds >= targetSeconds;
                    case LESS_EQUAL -> gameTimeSeconds <= targetSeconds;
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
                // 点击方块（由方块处理）
                yield false; // 这个条件由方块右键事件处理
            }
            case LOOKING_AT -> {
                // 玩家看向方块
                Vec3 eyePos = player.getEyePosition(1.0f);
                Vec3 lookVec = player.getViewVector(1.0f);
                Vec3 blockCenter = Vec3.atCenterOf(pos);
                double distanceToBlock = eyePos.distanceTo(blockCenter);
                if (distanceToBlock > condition.value) yield false;

                Vec3 toBlock = blockCenter.subtract(eyePos).normalize();
                double dot = lookVec.dot(toBlock);
                yield dot > 0.95; // 视角差小于约18度
            }
            case STANDING_ON_BLOCK -> {
                // 站在特定方块上
                double radius = condition.value;
                String blockId = condition.stringValue;
                AABB checkBox = new AABB(pos).inflate(radius);
                if (!checkBox.intersects(player.getBoundingBox())) yield false;

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
                if (role == null) yield false;
                yield switch (condition.teamType) {
                    case CIVILIAN -> role.isInnocent() && !role.isVigilanteTeam();
                    case SHERIFF -> role.isVigilanteTeam();
                    case NEUTRAL -> role.isNeutrals();
                    case NEUTRAL_KILLER -> role.isNeutrals() && role.isNeutralForKiller();
                    case NEUTRAL_SPECIAL -> role.isNeutrals() && !role.isNeutralForKiller();
                    case KILLER -> role.canUseKiller() && !role.isNeutrals();
                };
            }
            case HAS_KILLED -> {
                // 击杀过其他玩家 - 检查玩家统计组件中的击杀记录
                SREPlayerStatsComponent stats = SREPlayerStatsComponent.KEY.get(player);
                yield stats.getTotalKills() > 0;
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
                // 护盾值
                SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(player);
                yield compareValue(armor.getArmor(), condition.value, condition.comparison);
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
                yield taskComponent.tasks.values().stream().anyMatch(task -> requiredTaskId.equals(task.getCustomTaskId()));
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
                yield SREPlayerDamageTrackerComponent.hasPlayerDamage(player, gameTime);
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
                yield SREPlayerDamageTrackerComponent.hasNonPlayerDamage(player, gameTime);
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

    private void executeSingleAction(TriggerAction action, ServerPlayer player, ServerLevel world, BlockPos pos, long currentGameTime) {
        switch (action.type) {
            case EXECUTE_COMMAND -> {
                // 执行指令
                String command = action.stringValue.replace("<player>", player.getGameProfile().getName());
                // 处理~相对坐标替换
                command = replaceRelativeCoordinates(command, pos);
                world.getServer().getCommands().performPrefixedCommand(
                        world.getServer().createCommandSourceStack()
                                .withPermission(4)
                                .withLevel(world),
                        command
                );
            }
            case POISON -> {
                // 中毒
                SREPlayerPoisonComponent poison = SREPlayerPoisonComponent.KEY.get(player);
                int ticks = (int) (action.value * 20); // 秒转tick
                poison.setPoisonTicks(ticks, null);
            }
            case CURE_POISON -> {
                // 解毒
                SREPlayerPoisonComponent poison = SREPlayerPoisonComponent.KEY.get(player);
                poison.setPoisonTicks(0, null);
            }
            case SET_SHIELD -> {
                // 设置护盾 - 直接设置armor值
                SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(player);
                armor.armor = Math.max(0, (int) action.value);
                armor.sync();
            }
            case DAMAGE_DEATH -> {
                // 一般受击死亡
                ResourceLocation deathReason = action.stringValue.isEmpty() ?
                        GameConstants.DeathReasons.GENERIC : ResourceLocation.parse(action.stringValue);
                GameUtils.killPlayer(player, false, null, deathReason);
            }
            case FORCE_KILL -> {
                // 强制死亡 - 使用forceKillPlayer，与巫毒师相同，无法被护盾/无敌状态阻挡
                ResourceLocation deathReason = action.stringValue.isEmpty() ?
                        GameConstants.DeathReasons.GENERIC : ResourceLocation.parse(action.stringValue);
                GameUtils.forceKillPlayer(player, true, null, deathReason);
            }
            case ENABLE_COLLISION -> {
                // 启用碰撞箱
                this.collisionEnabled = true;
                this.collisionRemainingTicks = action.value < 0 ? -1 : (int) (action.value * 20);
            }
            case MOOD_CHANGE -> {
                // 改变心情值
                SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(player);
                float currentMood = mood.getMood();
                mood.setMood((float) Math.max(-1, Math.min(1, currentMood + action.value)));
            }
            case CHANGE_ROLE -> {
                // 改变职业
                SRERoleWorldComponent roles = SRERoleWorldComponent.KEY.get(world);
                String roleId = action.stringValue;
                // TODO: 实现职业切换逻辑
                // 参考初学者改变职业的实现
            }
            case CHANGE_TASK -> {
                // 更改当前任务 - 参考厨师食物效果：清除当前任务并生成新任务
                SREPlayerTaskComponent taskComponent = SREPlayerTaskComponent.KEY.get(player);
                // 清除当前所有任务
                taskComponent.tasks.clear();
                taskComponent.parallelTaskTypes.clear();
                taskComponent.parallelTaskGenerated = false;
                taskComponent.currentTaskAge = 0;

                SREPlayerTaskComponent.TrainTask newTask = null;

                // 检查是否指定了特定任务类型
                if (action.taskType != null && !action.taskType.isEmpty() && !action.taskType.equals("random")) {
                    // 尝试根据名称创建指定任务
                    try {
                        SREPlayerTaskComponent.Task specifiedTask = SREPlayerTaskComponent.Task.valueOf(action.taskType.toUpperCase());
                        newTask = createTaskByType(specifiedTask);
                    } catch (IllegalArgumentException e) {
                        // 无效的任务类型，回退到随机生成
                        newTask = taskComponent.generateTask();
                    }
                } else {
                    // 随机生成任务
                    newTask = taskComponent.generateTask();
                }

                if (newTask != null) {
                    taskComponent.tasks.put(newTask.getType(), newTask);
                    taskComponent.timesGotten.putIfAbsent(newTask.getType(), 1);
                    taskComponent.timesGotten.put(newTask.getType(), taskComponent.timesGotten.get(newTask.getType()) + 1);
                    // 重置任务分配时的情绪值
                    SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(player);
                    taskComponent.moodWhenTaskAssigned = mood != null ? mood.getMood() : 1f;
                }
                taskComponent.sync();
            }
            case RESURRECT -> {
                // 复活范围内玩家 - 参考死灵法师的复活逻辑
                double radius = action.value;
                AABB box = new AABB(pos).inflate(radius);

                // 获取范围内的尸体实体
                List<PlayerBodyEntity> bodies = world.getEntitiesOfClass(PlayerBodyEntity.class, box);

                for (PlayerBodyEntity body : bodies) {
                    // 获取尸体对应的玩家
                    ServerPlayer deadPlayer = (ServerPlayer) world.getPlayerByUUID(body.getPlayerUuid());
                    if (deadPlayer == null) continue;
                    if (!deadPlayer.isSpectator()) continue; // 只复活旁观模式的玩家

                    // 复活玩家
                    deadPlayer.getInventory().clearContent();
                    deadPlayer.teleportTo(body.getX(), body.getY(), body.getZ());
                    deadPlayer.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);

                    // 移除尸体
                    body.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);

                    // 给予复活后的玩家基础金币
                    SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(deadPlayer);
                    shop.setBalance(200);

                    // 播放复活音效和消息
                    world.players().forEach(p -> {
                        p.playNotifySound(net.minecraft.sounds.SoundEvents.TOTEM_USE,
                                deadPlayer.getSoundSource(), 1.2f, 1.5f);
                    });

                    // 只复活一个玩家就结束（避免一次触发复活太多）
                    break;
                }
            }
            case PSYCHO_MODE -> {
                // 进入疯狂模式
                SREPlayerPsychoComponent psycho = SREPlayerPsychoComponent.KEY.get(player);
                psycho.setPsychoTicks(GameConstants.getPsychoTimer());
            }
            case BLACKOUT -> {
                // 关灯 - 使用triggerBlackout触发
                SREWorldBlackoutComponent blackout = SREWorldBlackoutComponent.KEY.get(world);
                blackout.triggerBlackout(true);
            }
            case MONITOR_BROKEN -> {
                // 监控失灵 - 使用triggerBroken触发
                SREMonitorWorldComponent monitor = SREMonitorWorldComponent.KEY.get(world);
                monitor.triggerBroken(true, 600); // 默认30秒(600 tick)
            }
            case ADD_TIME -> {
                // 增加/减少游戏时间
                SREGameTimeComponent time = SREGameTimeComponent.KEY.get(world);
                int seconds = (int) (action.value * 60); // 分钟转秒
                time.addTime(seconds * 20); // tick
            }
            case SET_TIME -> {
                // 设置游戏时间
                SREGameTimeComponent time = SREGameTimeComponent.KEY.get(world);
                int seconds = (int) (action.value * 60); // 分钟转秒
                time.setTime(seconds * 20); // tick
            }
            case GAME_WIN -> {
                // 游戏胜利 - 参考RoleUtils.customWinnerWin的实现
                String winnerMessage = action.stringValue.isEmpty() ? "custom" : action.stringValue;
                SREGameRoundEndComponent roundEnd = SREGameRoundEndComponent.KEY.get(world);
                roundEnd.CustomWinnerID = winnerMessage;
                roundEnd.setRoundEndData(world.players(), GameUtils.WinStatus.CUSTOM);
                GameUtils.stopGame(world);
            }
            case COIN_CHANGE -> {
                // 增加/减少金币
                SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
                shop.addToBalance((int) action.value);
            }
            case GIVE_EFFECT -> {
                // 给予状态效果
                String effectId = action.stringValue;
                int duration = action.effectDuration > 0 ? action.effectDuration * 20 : 200; // 默认10秒
                int amplifier = Math.max(0, action.effectAmplifier);

                net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                        .getHolder(net.minecraft.resources.ResourceLocation.parse(effectId)).orElse(null);
                if (effect != null) {
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect, duration, amplifier));
                }
            }
            case TELEPORT -> {
                // 传送到指定传送点（限制100格范围）
                int targetId = (int) action.value;
                BlockPos targetPos = findTeleportPoint(world, pos, targetId);
                if (targetPos != null) {
                    player.teleportTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
                } else {
                    // 传送目标不存在或在范围外
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.teleport_point_not_found", targetId, MAX_TELEPORT_RANGE));
                }
            }
            case SHOW_TITLE -> {
                // 显示标题
                String title = action.stringValue;
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal(title)));
            }
            case BROADCAST_MESSAGE -> {
                // 广播消息
                String message = action.stringValue;
                world.players().forEach(p -> p.sendSystemMessage(Component.literal(message)));
            }
            case ITEM_COOLDOWN -> {
                // 物品冷却
                String itemId = action.stringValue;
                int cooldownTicks = (int) (action.value * 20); // 秒转tick

                if ("*".equals(itemId)) {
                    // 所有物品进入冷却
                    player.getCooldowns().addCooldown(player.getMainHandItem().getItem(), cooldownTicks);
                    player.getCooldowns().addCooldown(player.getOffhandItem().getItem(), cooldownTicks);
                } else {
                    // 特定物品
                    net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .get(net.minecraft.resources.ResourceLocation.parse(itemId));
                    if (item != null) {
                        player.getCooldowns().addCooldown(item, cooldownTicks);
                    }
                }
            }
            case BLOCK_COOLDOWN -> {
                // 方块进入冷却
                int cooldownSeconds = (int) action.value;
                this.setBlockCooldown(cooldownSeconds, currentGameTime);
            }
            case CLEAR_ENTITIES -> {
                // 清除范围内的指定实体
                double radius = action.value;
                String entityId = action.stringValue;

                AABB checkBox = new AABB(pos).inflate(radius);
                var entitiesToRemove = new ArrayList<net.minecraft.world.entity.Entity>();

                for (var entity : world.getEntities(null, checkBox)) {
                    boolean matches = false;
                    if ("*".equals(entityId)) {
                        // * 表示任意实体（不包括玩家，避免误删）
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
                        entitiesToRemove.add(entity);
                    }
                }

                for (var entity : entitiesToRemove) {
                    entity.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
                }
            }
            case SET_MOOD -> {
                // 设置心情值（value为0表示直接设置，为1表示增减）
                SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(player);
                if (action.stringValue != null && action.stringValue.equals("add")) {
                    // 增减模式
                    mood.addMood((float) action.value);
                } else {
                    // 直接设置模式
                    mood.setMood((float) action.value);
                }
            }
            case CURE_PSYCHO -> {
                // 解除疯狂模式
                SREPlayerPsychoComponent psycho = SREPlayerPsychoComponent.KEY.get(player);
                if (psycho.getPsychoTicks() > 0) {
                    psycho.stopPsychoAndSync();
                }
            }
            case CLEAR_TASKS -> {
                // 清除所有任务
                SREPlayerTaskComponent taskComponent = SREPlayerTaskComponent.KEY.get(player);
                taskComponent.tasks.clear();
                taskComponent.parallelTaskTypes.clear();
                taskComponent.parallelTaskGenerated = false;
                taskComponent.sync();
            }
            case COMPLETE_TASK -> {
                // 强制完成当前任务 - 模拟任务完成逻辑
                SREPlayerTaskComponent taskComponent = SREPlayerTaskComponent.KEY.get(player);
                if (!taskComponent.tasks.isEmpty()) {
                    // 获取第一个任务并完成它
                    var firstTask = taskComponent.tasks.values().iterator().next();
                    // 移除任务并增加连击计数
                    taskComponent.tasks.remove(firstTask.getType());
                    taskComponent.parallelTaskTypes.remove(firstTask.getType());
                    taskComponent.taskStreak++;
                    // 如果所有任务都完成了，重置状态
                    if (taskComponent.tasks.isEmpty()) {
                        taskComponent.currentTaskAge = 0;
                        taskComponent.parallelTaskGenerated = false;
                    }
                    taskComponent.sync();
                }
            }
            case END_BLACKOUT -> {
                // 结束关灯
                SREWorldBlackoutComponent blackout = SREWorldBlackoutComponent.KEY.get(world);
                blackout.reset();
            }
            case FIX_MONITOR -> {
                // 修复监控
                SREMonitorWorldComponent monitor = SREMonitorWorldComponent.KEY.get(world);
                monitor.reset();
            }
            case ADD_CUSTOM_TASK -> {
                // 添加自定义任务：根据clearTasks决定是否清空当前任务
                SREPlayerTaskComponent taskComponent = SREPlayerTaskComponent.KEY.get(player);

                // 根据设置决定是否清空当前任务
                if (action.clearTasks) {
                    // 清空当前所有任务
                    taskComponent.tasks.clear();
                    taskComponent.parallelTaskTypes.clear();
                    taskComponent.parallelTaskGenerated = false;
                    taskComponent.currentTaskAge = 0;
                }

                // 创建自定义任务
                String taskName = action.customTaskName != null ? action.customTaskName : "自定义任务";
                String taskId = action.customTaskId != null ? action.customTaskId : "custom_" + System.currentTimeMillis();

                SREPlayerTaskComponent.CustomTask customTask = new SREPlayerTaskComponent.CustomTask(taskName, taskId);

                taskComponent.tasks.put(customTask.getType(), customTask);
                taskComponent.timesGotten.putIfAbsent(customTask.getType(), 1);
                taskComponent.timesGotten.put(customTask.getType(), taskComponent.timesGotten.get(customTask.getType()) + 1);

                // 重置任务分配时的情绪值
                SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(player);
                taskComponent.moodWhenTaskAssigned = mood != null ? mood.getMood() : 1f;

                taskComponent.sync();

                // 发送提示消息
                player.sendSystemMessage(Component.translatable("message.custom_task.added", taskName));
            }
            case ADD_EXTRA_TASK -> {
                // 额外添加任务：不清空当前任务，直接添加一个随机任务
                SREPlayerTaskComponent taskComponent = SREPlayerTaskComponent.KEY.get(player);
                String taskType = action.taskType;

                // 如果指定了任务类型，尝试使用该类型
                SREPlayerTaskComponent.TrainTask newTask = null;
                if (taskType != null && !taskType.isEmpty() && !"random".equalsIgnoreCase(taskType)) {
                    // 尝试匹配指定的任务类型
                    try {
                        SREPlayerTaskComponent.Task enumTask = SREPlayerTaskComponent.Task.valueOf(taskType.toUpperCase());
                        // 使用枚举的setFunction来创建任务
                        newTask = enumTask.setFunction.apply(new CompoundTag());
                    } catch (IllegalArgumentException e) {
                        // 任务类型不存在，忽略
                    }
                }

                // 如果没有指定类型或类型无效，随机生成任务
                if (newTask == null) {
                    // 使用任务组件的内部随机生成逻辑
                    newTask = generateRandomTask(player);
                }

                if (newTask != null) {
                    taskComponent.tasks.put(newTask.getType(), newTask);
                    taskComponent.timesGotten.putIfAbsent(newTask.getType(), 1);
                    taskComponent.timesGotten.put(newTask.getType(), taskComponent.timesGotten.get(newTask.getType()) + 1);
                    // 发送提示消息
                    player.sendSystemMessage(Component.translatable("message.extra_task.added", newTask.getName()));
                }

                taskComponent.sync();
            }
            case COMPLETE_CUSTOM_TASK -> {
                // 完成自定义任务
                String taskId = action.customTaskId;
                if (taskId == null || taskId.isEmpty()) {
                    break;
                }

                SREPlayerTaskComponent taskComponent = SREPlayerTaskComponent.KEY.get(player);
                // 查找并移除匹配的自定义任务
                var iterator = taskComponent.tasks.entrySet().iterator();
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    var task = entry.getValue();
                    if (taskId.equals(task.getCustomTaskId())) {
                        iterator.remove();
                        taskComponent.parallelTaskTypes.remove(task.getType());
                        taskComponent.taskStreak++;
                        // 发送提示消息
                        player.sendSystemMessage(Component.translatable("message.custom_task.completed", task.getName()));
                        break;
                    }
                }

                // 如果所有任务都完成了，重置状态
                if (taskComponent.tasks.isEmpty()) {
                    taskComponent.currentTaskAge = 0;
                    taskComponent.parallelTaskGenerated = false;
                }
                taskComponent.sync();
            }
            case NARRATOR -> {
                // 语音播报
                String narratorText = action.narratorText;
                if (narratorText != null && !narratorText.isEmpty()) {
                    boolean shouldInterrupt = action.narratorInterrupt;
                    Component textComponent = Component.literal(narratorText);
                    ServerPlayNetworking.send(player, new CustomNarratorPacket(textComponent, shouldInterrupt));
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
                    if (dx * dx + dy * dy + dz * dz > rangeSq) continue;

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
        if (cooldownTicks == 0) cooldownTicks = 40;
        collisionEnabled = tag.getBoolean("CollisionEnabled");
        collisionRemainingTicks = tag.getInt("CollisionRemainingTicks");
        isTeleportPoint = tag.getBoolean("IsTeleportPoint");
        // 使用 contains 检查，避免旧数据或缺失时覆盖默认值 -1
        teleportPointId = tag.contains("TeleportPointId") ? tag.getInt("TeleportPointId") : -1;
        blockCooldownTicks = tag.getInt("BlockCooldownTicks");
        blockCooldownEndGameTime = tag.getInt("BlockCooldownEndGameTime");
    }

    // 条件类型枚举
    public enum ConditionType {
        PASS_THROUGH,      // 穿过方块
        TIMER,             // 自动定时
        TIME_ANCHOR,       // 时间锚点
        PROXIMITY_SPHERE,  // 球形范围
        PROXIMITY_LINE,    // 直线范围
        HAS_ITEM,          // 有特定物品
        CLICK_BLOCK,       // 点击方块
        LOOKING_AT,        // 看向方块
        STANDING_ON_BLOCK, // 站在特定方块上
        DEATH,             // 死亡
        USE_ITEM,          // 使用物品
        SPEAK,             // 说话
        COIN_AMOUNT,       // 金币数量
        ROLE_IS,           // 特定职业
        ROLE_TEAM,         // 职业阵营
        HAS_KILLED,        // 击杀过玩家
        PLAYER_COUNT,      // 玩家数量
        ALIVE_PLAYERS,     // 存活玩家数量
        IS_SNEAKING,       // 潜行状态
        IS_SPRINTING,      // 疾跑状态
        HAS_EFFECT,        // 有特定效果
        PROBABILITY,       // 触发概率
        WORLD_TIME,        // 世界时间（白天/中午/傍晚/午夜）
        ENTITY_COUNT,      // 范围内实体数量
        MOOD_VALUE,        // 心情值
        IS_PSYCHO,         // 是否处于疯狂模式
        IS_POISONED,       // 是否中毒
        ARMOR_AMOUNT,      // 护盾值
        HAS_TASK,              // 是否有任务
        TASK_STREAK,           // 连续完成任务数
        GAME_RUNNING,          // 游戏是否进行中
        PSYCHOS_ACTIVE,        // 当前活跃疯狂玩家数量
        IS_BLACKOUT,           // 是否处于关灯状态
        IS_MONITOR_BROKEN,     // 监控是否失灵
        NEED_TASK_TYPE,        // 需要完成特定类型任务
        NEED_CUSTOM_TASK,      // 需要完成自定义任务
        PLAYER_DAMAGED_BY_PLAYER,      // 玩家受到来自玩家的原版伤害
        PLAYER_DAMAGED_BY_NON_PLAYER   // 玩家受到非玩家来源的原版伤害
    }

    // 世界时间类型枚举
    public enum WorldTimeType {
        DAY,      // 白天 (0-12000)
        NOON,     // 中午 (6000)
        SUNSET,   // 傍晚 (12000-13000)
        NIGHT,    // 夜晚 (13000-23000)
        MIDNIGHT  // 午夜 (18000)
    }

    // 比较类型枚举
    public enum ComparisonType {
        EQUALS, GREATER, LESS, GREATER_EQUAL, LESS_EQUAL
    }

    // 阵营类型枚举
    public enum TeamType {
        CIVILIAN, SHERIFF, NEUTRAL, NEUTRAL_KILLER, NEUTRAL_SPECIAL, KILLER
    }

    // 直线范围方向枚举
    public enum LineDirection {
        ALL,     // * - 所有方向
        EAST,    // 东 (+X)
        WEST,    // 西 (-X)
        SOUTH,   // 南 (+Z)
        NORTH,   // 北 (-Z)
        UP,      // 上 (+Y)
        DOWN     // 下 (-Y)
    }

    // 逻辑运算符枚举（用于条件组合）
    public enum LogicOperator {
        AND,      // 与 - 前一个和后一个条件都必须满足
        OR,       // 或 - 前一个或后一个条件满足即可
        NAND,     // 与非 - 前一个和后一个条件不同时满足
        NOR       // 或非 - 前一个和后一个条件都不满足
    }

    // 触发内容类型枚举
    public enum ActionType {
        EXECUTE_COMMAND,   // 执行指令
        POISON,            // 中毒
        CURE_POISON,       // 解毒
        SET_SHIELD,        // 设置护盾
        DAMAGE_DEATH,      // 一般受击死亡
        FORCE_KILL,        // 强制死亡
        ENABLE_COLLISION,  // 启用碰撞箱
        MOOD_CHANGE,       // 改变心情值（增减）
        CHANGE_ROLE,       // 改变职业
        CHANGE_TASK,       // 更改任务
        RESURRECT,         // 复活
        PSYCHO_MODE,       // 疯狂模式
        BLACKOUT,          // 关灯
        MONITOR_BROKEN,    // 监控失灵
        ADD_TIME,          // 增加/减少时间
        SET_TIME,          // 设置时间
        GAME_WIN,          // 游戏胜利
        COIN_CHANGE,       // 改变金币
        GIVE_EFFECT,       // 给予状态效果
        TELEPORT,          // 传送到指定传送点
        SHOW_TITLE,        // 显示标题
        BROADCAST_MESSAGE, // 广播消息
        ITEM_COOLDOWN,     // 物品冷却
        BLOCK_COOLDOWN,    // 方块进入冷却
        CLEAR_ENTITIES,    // 清除范围内实体
        SET_MOOD,          // 设置心情值（直接设置或增减）
        CURE_PSYCHO,           // 解除疯狂模式
        CLEAR_TASKS,           // 清除所有任务
        COMPLETE_TASK,         // 强制完成当前任务
        END_BLACKOUT,          // 结束关灯
        FIX_MONITOR,           // 修复监控
        ADD_CUSTOM_TASK,       // 增加自定义任务（根据clearTasks决定是否清空当前任务）
        ADD_EXTRA_TASK,        // 额外添加任务（不清空当前任务，支持random随机任务）
        COMPLETE_CUSTOM_TASK,   // 完成自定义任务
        NARRATOR              // 语音播报
    }

    // 条件数据类
    public static class TriggerCondition {
        public ConditionType type;
        public double value;           // 数值参数
        public String stringValue;     // 字符串参数
        public ComparisonType comparison = ComparisonType.EQUALS;
        public TeamType teamType;
        public boolean leftClick;      // 左键/右键（用于点击方块条件）
        public LogicOperator logicOperator = LogicOperator.AND; // 与下一个条件的逻辑关系
        public WorldTimeType worldTimeType; // 世界时间类型（用于WORLD_TIME条件）
        public int entityCount; // 实体数量（用于ENTITY_COUNT条件）
        public boolean checkAnyCount; // 是否检查任意数量（*表示不检查数量）
        public LineDirection lineDirection; // 直线范围方向（用于PROXIMITY_LINE条件）

        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Type", type.name());
            tag.putDouble("Value", value);
            tag.putString("StringValue", stringValue != null ? stringValue : "");
            tag.putString("Comparison", comparison.name());
            if (teamType != null) tag.putString("TeamType", teamType.name());
            tag.putBoolean("LeftClick", leftClick);
            tag.putString("LogicOperator", logicOperator.name());
            if (worldTimeType != null) tag.putString("WorldTimeType", worldTimeType.name());
            tag.putInt("EntityCount", entityCount);
            tag.putBoolean("CheckAnyCount", checkAnyCount);
            if (lineDirection != null) tag.putString("LineDirection", lineDirection.name());
            return tag;
        }

        public static TriggerCondition fromNbt(CompoundTag tag) {
            TriggerCondition condition = new TriggerCondition();
            condition.type = ConditionType.valueOf(tag.getString("Type"));
            condition.value = tag.getDouble("Value");
            condition.stringValue = tag.getString("StringValue");
            if (condition.stringValue.isEmpty()) condition.stringValue = null;
            condition.comparison = ComparisonType.valueOf(tag.getString("Comparison"));
            if (tag.contains("TeamType")) {
                condition.teamType = TeamType.valueOf(tag.getString("TeamType"));
            }
            condition.leftClick = tag.getBoolean("LeftClick");
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
            return condition;
        }
    }

    // 触发内容数据类
    public static class TriggerAction {
        public ActionType type;
        public double value;           // 数值参数
        public String stringValue;     // 字符串参数
        public String taskType;        // 任务类型（用于CHANGE_TASK）
        public int effectDuration;     // 效果持续时间（秒，用于GIVE_EFFECT）
        public int effectAmplifier;    // 效果等级（用于GIVE_EFFECT）
        public String customTaskName;  // 自定义任务名称（用于ADD_CUSTOM_TASK和ADD_EXTRA_TASK）
        public String customTaskId;    // 自定义任务ID（用于ADD_CUSTOM_TASK和COMPLETE_CUSTOM_TASK）
        public String narratorText;     // 语音播报文本（用于NARRATOR）
        public boolean narratorInterrupt; // 是否打断当前播报（用于NARRATOR）
        public boolean clearTasks;       // 是否清空当前任务（用于ADD_CUSTOM_TASK，true=清空后添加，false=不清空直接添加）

        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Type", type.name());
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
            return tag;
        }

        public static TriggerAction fromNbt(CompoundTag tag) {
            TriggerAction action = new TriggerAction();
            action.type = ActionType.valueOf(tag.getString("Type"));
            action.value = tag.getDouble("Value");
            action.stringValue = tag.getString("StringValue");
            if (action.stringValue.isEmpty()) action.stringValue = null;
            action.taskType = tag.getString("TaskType");
            if (action.taskType.isEmpty()) action.taskType = null;
            action.effectDuration = tag.getInt("EffectDuration");
            action.effectAmplifier = tag.getInt("EffectAmplifier");
            action.customTaskName = tag.getString("CustomTaskName");
            if (action.customTaskName.isEmpty()) action.customTaskName = null;
            action.customTaskId = tag.getString("CustomTaskId");
            if (action.customTaskId.isEmpty()) action.customTaskId = null;
            action.narratorText = tag.getString("NarratorText");
            if (action.narratorText.isEmpty()) action.narratorText = null;
            action.narratorInterrupt = tag.getBoolean("NarratorInterrupt");
            action.clearTasks = tag.getBoolean("ClearTasks");
            return action;
        }
    }

    /**
     * 生成随机任务
     */
    private static SREPlayerTaskComponent.TrainTask generateRandomTask(ServerPlayer player) {
        java.util.List<SREPlayerTaskComponent.Task> availableTasks = SREPlayerTaskComponent.Task.getAvailableTasksList();
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
}
