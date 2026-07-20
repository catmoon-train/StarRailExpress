package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.block.ToiletBlock;
import io.wifi.starrailexpress.content.block.entity.SeatEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.tag.TMMBlockTags;
import io.wifi.starrailexpress.network.original.TaskCompletePayload;
import io.wifi.starrailexpress.util.BlockTypeChecker;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.neutral.raven.RavenPlayerComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.ModifierEffects;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.agmas.noellesroles.scene.SceneTaskManager;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.*;
import java.util.function.Function;

import static io.wifi.starrailexpress.SRE.isSkyVisible;
import static io.wifi.starrailexpress.SRE.isSkyVisibleAdjacent;

public class SREPlayerTaskComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SREPlayerTaskComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("task"),
            SREPlayerTaskComponent.class);
    private final Player player;
    public final Map<Task, TrainTask> tasks = new HashMap<>();
    public final Map<Task, Integer> timesGotten = new HashMap<>();
    public int nextTaskTimer = 0;
    public int taskStreak = 0; // 连续完成任务计数
    public int currentTaskAge = 0; // 当前任务已存在的时间（ticks）
    public float moodWhenTaskAssigned = 1f; // 任务分配时的情绪值
    public boolean parallelTaskGenerated = false; // 是否已生成并列任务
    public final Set<Task> parallelTaskTypes = new HashSet<>(); // 记录哪些任务是并列任务
    public SREPlayerMoodComponent playerMoodComponent;

    public SREPlayerTaskComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return player == this.player;
    }

    /** 防止重复注册场景任务回调。 */
    private static volatile boolean sceneCallbackRegistered = false;

    @Override
    public void init() {
        if (playerMoodComponent == null) {
            playerMoodComponent = SREPlayerMoodComponent.KEY.get(player);
        }
        this.tasks.clear();
        this.timesGotten.clear();
        this.taskStreak = 0;
        this.currentTaskAge = 0;
        this.moodWhenTaskAssigned = 1f;
        this.parallelTaskGenerated = false;
        this.parallelTaskTypes.clear();
        this.nextTaskTimer = GameConstants.TIME_TO_FIRST_TASK;
        SceneTaskManager.clear(this.player);
        this.sync();

        // 注册场景任务完成回调（全局仅注册一次）
        if (!sceneCallbackRegistered) {
            sceneCallbackRegistered = true;
            SceneTaskManager.registerCompletionCallback((sp, sceneType) -> {
                // 将 SceneTaskManager.Type 映射到 Task 枚举，触发对应 SceneTriggeredTask 完成
                Task taskType = switch (sceneType) {
                    case LIGHT_STOVE -> Task.LIGHT_STOVE;
                    case CLEAN_DUST -> Task.CLEAN_DUST;
                    case TRANSPORT -> Task.TRANSPORT;
                    case PRAY -> Task.PRAY;
                    case PRUNE_BUSH -> Task.PRUNE_BUSH;
                    case HARVEST_CROP -> Task.HARVEST_CROP;
                    default -> null;
                };
                if (taskType != null) {
                    SREPlayerTaskComponent comp = KEY.get(sp);
                    if (comp != null && comp.tasks.get(taskType) instanceof SceneTriggeredTask stt) {
                        stt.setFulfilled(true);
                        comp.sync();
                    }
                }
            });
        }
    }

    @Override
    public void clear() {
        this.init();
    }

    @Override
    public void clientTick() {
        if (playerMoodComponent == null) {
            playerMoodComponent = SREPlayerMoodComponent.KEY.get(player);
        }
        if (!SREGameWorldComponent.KEY.get(this.player.level()).isRunning() || !SREClient.isPlayerAliveAndInSurvival())
            return;
    }

    @Override
    public void serverTick() {
        if (playerMoodComponent == null) {
            playerMoodComponent = SREPlayerMoodComponent.KEY.get(player);
        }
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
        if (!gameWorldComponent.isRunning() || !GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(this.player))
            return;
        boolean shouldSync = false;
        this.nextTaskTimer--;
        if (this.nextTaskTimer <= 0) {
            // 小游戏任务轮换模式：刷满 2~3 个普通任务后，本次刷新槽位替换为派发一个小游戏任务

            boolean minigameDispatched = false;
            SREPlayerMinigameTaskComponent minigameComponent = null;
            boolean rotationActive = false;
            boolean parallelMinigame = SREConfig.instance().minigameTaskParallelMode;
            if (this.player instanceof ServerPlayer sp
                    && sp.level() instanceof net.minecraft.server.level.ServerLevel serverLevel
                    && SREPlayerMinigameTaskComponent.isRotationModeActive(serverLevel)) {
                rotationActive = true;
                minigameComponent = SREPlayerMinigameTaskComponent.KEY.get(sp);
                if (parallelMinigame || this.tasks.isEmpty()) {
                    if (minigameComponent.shouldReplaceNormalTask(sp)) {
                        // 并列模式：无论当前有无普通任务，均可派发小游戏任务
                        // 原模式：仅在无普通任务时才替换
                        minigameDispatched = minigameComponent.dispatchRotationTask(sp, serverLevel);
                    }
                }
            }

            if (!minigameDispatched || parallelMinigame) {
                TrainTask task = this.generateTask();
                if (task != null) {
                    this.tasks.put(task.getType(), task);
                    this.timesGotten.putIfAbsent(task.getType(), 1);
                    this.timesGotten.put(task.getType(), this.timesGotten.get(task.getType()) + 1);
                    // 记录任务分配时的情绪值
                    this.moodWhenTaskAssigned = (playerMoodComponent != null) ? playerMoodComponent.getMood() : 1f;
                    this.currentTaskAge = 0;
                    this.parallelTaskGenerated = false;
                    // 任务出现时通过字幕报幕通知玩家（TOP 模式，兼容 broadcast）
                    if (this.player instanceof ServerPlayer sp) {
                        SRERole role = gameWorldComponent.getRole(sp);
                        if (role != null && role.getMoodType() != SRERole.MoodType.NONE) {
                            Component taskTitle = Component.translatable("task." + task.getName());
                            Component taskSub = Component.translatable("subtitle.task.new");
                            net.exmo.sre.subtitle.SubtitleCommand.sendToPlayerTop(sp, taskTitle, taskSub, 75, false);
                            // 轮换模式：普通任务刷新计数（供轮换到小游戏任务判断）
                        }
                        if (minigameComponent != null) {
                            minigameComponent.onNormalTaskGenerated(sp);
                        }
                    }
                }
            }
            // 使用动态任务冷却：根据游戏已过时间调整
            SREGameTimeComponent gameTimeComponent = SREGameTimeComponent.KEY.get(this.player.level());
            long gameElapsedTicks = Math.max(0, gameTimeComponent.getResetTime() - gameTimeComponent.getTime());
            int minCooldown = GameConstants.getDynamicMinTaskCooldown(gameElapsedTicks);
            int maxCooldown = GameConstants.getDynamicMaxTaskCooldown(gameElapsedTicks);
            this.nextTaskTimer = (int) (this.player.getRandom().nextFloat()
                    * (maxCooldown - minCooldown)
                    + minCooldown);
            // 小游戏任务未启用或者并列小游戏任务模式时，正常任务刷新加快 30%
            if (parallelMinigame
                    || !AreasWorldComponent.KEY.get(this.player.level()).areasSettings.minigameQuestEnabled) {
                this.nextTaskTimer = (int) (this.nextTaskTimer * 0.7f);
            }
            // 轮换模式：非并列小游戏任务模式则全局任务刷新速率减缓 15%
            if (rotationActive && !parallelMinigame) {
                this.nextTaskTimer = (int) (this.nextTaskTimer * GameConstants.MINIGAME_ROTATION_REFRESH_SLOWDOWN);
            }
            this.nextTaskTimer = Math.max(this.nextTaskTimer, 2);
            shouldSync = true;
        }

        // 并列任务机制：任务超时且情绪下降30%以上时，生成一个并列任务
        if (!this.tasks.isEmpty() && !this.parallelTaskGenerated) {
            this.currentTaskAge++;
            if (this.currentTaskAge >= GameConstants.PARALLEL_TASK_THRESHOLD) {
                float currentMood = (playerMoodComponent != null) ? playerMoodComponent.getMood() : 1f;
                float moodDrop = this.moodWhenTaskAssigned - currentMood;
                if (moodDrop >= GameConstants.PARALLEL_TASK_MOOD_DROP) {
                    TrainTask parallelTask = this.generateParallelTask();
                    if (parallelTask != null) {
                        this.tasks.put(parallelTask.getType(), parallelTask);
                        this.parallelTaskTypes.add(parallelTask.getType());
                        this.timesGotten.putIfAbsent(parallelTask.getType(), 1);
                        this.timesGotten.put(parallelTask.getType(),
                                this.timesGotten.get(parallelTask.getType()) + 1);
                        this.parallelTaskGenerated = true;
                        // 并列任务也通过字幕报幕通知（TOP 模式）
                        if (this.player instanceof ServerPlayer sp) {
                            SRERole role = gameWorldComponent.getRole(sp);
                            if (role != null && role.getMoodType() != SRERole.MoodType.NONE) {
                                Component taskTitle = Component.translatable("task." + parallelTask.getName());
                                Component taskSub = Component.translatable("subtitle.task.parallel");
                                net.exmo.sre.subtitle.SubtitleCommand.sendToPlayerTop(sp, taskTitle, taskSub, 75,
                                        false);
                            }
                        }
                        shouldSync = true;
                    }
                }
            }
        }

        ArrayList<TrainTask> removals = new ArrayList<>();
        for (TrainTask task : this.tasks.values()) {
            task.tick(this.player);
            if (task.isFulfilled(this.player)) {
                removals.add(task);
                // 并列任务完成时给予完整奖励（不再减少）
                float moodGain = GameConstants.MOOD_GAIN;
                // 并列任务完成时额外奖励情绪加成（玩家做出了选择）
                if (this.parallelTaskGenerated) {
                    moodGain += GameConstants.PARALLEL_TASK_COMPLETION_BONUS;
                }
                this.playerMoodComponent.addMood(moodGain);
                if (this.player instanceof ServerPlayer tempPlayer)
                    ServerPlayNetworking.send(tempPlayer, new TaskCompletePayload());
                shouldSync = true;
            }
        }
        // 并列任务机制：完成其中一个任务时，另一个任务自动消失
        ArrayList<TrainTask> dismissed = new ArrayList<>();
        if (!removals.isEmpty() && this.parallelTaskGenerated) {
            for (TrainTask task : this.tasks.values()) {
                if (!removals.contains(task)) {
                    dismissed.add(task);
                }
            }
        }
        for (TrainTask task : removals) {
            this.tasks.remove(task.getType());
            this.parallelTaskTypes.remove(task.getType());
            // 更新计分板上的任务计数
            if (this.player instanceof ServerPlayer) {
                // 调用角色的任务完成方法（完整奖励，并列任务不再减少奖励）
                io.wifi.starrailexpress.api.RoleMethodDispatcher.callOnFinishQuest(this.player, task.getName(),
                        this.taskStreak, false);
            }
            this.taskStreak++; // 完成奖励发放后增加连击计数（并列任务也增加连击）
            // 小游戏任务已与 Mood 任务解耦，改由 SREPlayerMinigameTaskComponent 独立计时刷新
            // 大胃王：完成进食任务时额外恢复理智并给金币
            if (task.getType() == Task.EAT && this.player instanceof ServerPlayer sp) {
                ModifierEffects.onBigEaterTaskComplete(sp);
            }
            // 触发附近任务完成联动（狂躁症、渡鸦等）
            if (this.player instanceof ServerPlayer sp) {
                notifyNearbyTaskComplete(sp);
            }
        }
        // 移除被消失的并列任务（不给予奖励）
        for (TrainTask task : dismissed) {
            this.tasks.remove(task.getType());
            this.parallelTaskTypes.remove(task.getType());
            clearSceneTask(task.getType());
            shouldSync = true;
        }
        // 所有任务完成后重置并列任务追踪
        if (this.tasks.isEmpty()) {
            this.currentTaskAge = 0;
            this.parallelTaskGenerated = false;
            this.parallelTaskTypes.clear();
        }
        // 当情绪过低时重置连击计数
        if (playerMoodComponent != null && playerMoodComponent.isLowerThanDepressed()) {
            this.taskStreak = 0;
        }
        if (shouldSync)
            this.sync();
    }

    public boolean completeManicTask() {
        TrainTask task = this.tasks.get(Task.MANIC);
        if (task == null || !(this.player instanceof ServerPlayer sp))
            return false;
        if (this.playerMoodComponent == null) {
            this.playerMoodComponent = SREPlayerMoodComponent.KEY.get(this.player);
        }
        float moodGain = GameConstants.MOOD_GAIN;
        if (this.parallelTaskGenerated) {
            moodGain += GameConstants.PARALLEL_TASK_COMPLETION_BONUS;
        }
        if (this.playerMoodComponent != null) {
            this.playerMoodComponent.addMood(moodGain);
        }
        ServerPlayNetworking.send(sp, new TaskCompletePayload());
        this.tasks.remove(Task.MANIC);
        this.parallelTaskTypes.remove(Task.MANIC);
        io.wifi.starrailexpress.api.RoleMethodDispatcher.callOnFinishQuest(this.player, task.getName(),
                this.taskStreak, false);
        this.taskStreak++;
        if (this.tasks.isEmpty()) {
            this.currentTaskAge = 0;
            this.parallelTaskGenerated = false;
            this.parallelTaskTypes.clear();
        }
        notifyNearbyTaskComplete(sp);
        this.sync();
        return true;
    }

    private static void notifyNearbyTaskComplete(ServerPlayer completingPlayer) {
        var worldModifiers = WorldModifierComponent.KEY.get(completingPlayer.level());
        if (worldModifiers != null) {
            for (Player nearby : completingPlayer.level().players()) {
                if (nearby != completingPlayer && nearby.distanceTo(completingPlayer) <= 11.0
                        && nearby instanceof ServerPlayer nearbySp
                        && GameUtils.isPlayerAliveAndSurvival(nearbySp)
                        && worldModifiers.isModifier(nearbySp.getUUID(), TraitorAndModifiers.MANIC)) {
                    ModifierEffects.onNearbyTaskComplete(nearbySp, completingPlayer);
                }
            }
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(completingPlayer.level());
        if (gameWorld != null) {
            for (Player nearby : completingPlayer.level().players()) {
                if (nearby != completingPlayer
                        && nearby.distanceToSqr(completingPlayer) <= RavenPlayerComponent.CHARGE_RADIUS
                                * RavenPlayerComponent.CHARGE_RADIUS
                        && nearby instanceof ServerPlayer nearbySp
                        && GameUtils.isPlayerAliveAndSurvival(nearbySp)
                        && gameWorld.isRole(nearbySp, ModRoles.RAVEN)) {
                    ModComponents.RAVEN.get(nearbySp).onNearbyTaskComplete();
                }
            }
        }
    }

    /**
     * 获取当前地图禁用的任务列表
     */
    private Set<String> getDisabledTasks() {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(this.player.level());
        if (areas != null) {
            return areas.getDisabledTasks();
        }
        return Set.of();
    }

    /**
     * 获取当前地图启用的场景任务列表
     */
    private Set<String> getEnabledSceneTasks() {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(this.player.level());
        if (areas != null) {
            return areas.getEnabledSceneTasks();
        }
        return Set.of();
    }

    public @Nullable TrainTask generateTask() {
        if (!this.tasks.isEmpty())
            return null;

        // 检查玩家是否拥有狂躁症修饰符
        if (isManicPlayer()) {
            // 狂躁症玩家直接获得乱码任务
            return new ManicTask();
        }

        return generateTaskInternal();
    }

    /**
     * 生成并列任务：当原始任务超时且情绪下降时生成
     * 不会生成与已有任务相同类型的任务
     */
    public @Nullable TrainTask generateParallelTask() {
        // 检查玩家是否拥有狂躁症修饰符
        if (isManicPlayer()) {
            // 狂躁症玩家直接获得乱码任务
            return new ManicTask();
        }
        return generateTaskInternal();
    }

    /**
     * 检查玩家是否拥有狂躁症修饰符
     */
    private boolean isManicPlayer() {
        if (this.player instanceof ServerPlayer sp && this.player.level().isClientSide == false) {
            WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(this.player.level());
            if (modifiers != null && modifiers.isModifier(sp.getUUID(), TraitorAndModifiers.MANIC)) {
                return true;
            }
        }
        return false;
    }

    public @Nullable TrainTask generateTaskInternal() {
        HashMap<Task, Float> map = new HashMap<>();
        float total = 0f;
        // 获取当前情绪状态用于动态权重调整
        float currentMood = (playerMoodComponent != null) ? playerMoodComponent.getMood() : 1f;
        // 获取当前地图禁用的任务
        Set<String> disabledTasks = getDisabledTasks();
        // 获取职业用于任务刷新控制（SRERole.canRefreshTask）
        io.wifi.starrailexpress.api.SRERole role = SREGameWorldComponent.KEY.get(this.player.level())
                .getRole(this.player);
        // 遍历非场景任务
        for (Task task : Task.getAvailableTasksList()) {
            if (this.tasks.containsKey(task))
                continue;
            // 检查任务是否被当前地图禁用
            if (disabledTasks.contains(task.name()))
                continue;
            // 检查任务是否被职业禁止刷新
            if (role != null && !role.canRefreshTask(this.player, task))
                continue;
            float weight = 1f / this.timesGotten.getOrDefault(task, 1);
            // 情绪驱动的任务权重调整（基于任务种类）
            weight = applyMoodWeight(weight, task.category, currentMood);
            map.put(task, weight);
            total += weight;
        }
        // 遍历启用的场景任务
        Set<String> enabledSceneTasks = getEnabledSceneTasks();
        if (!enabledSceneTasks.isEmpty()) {
            for (Task task : Task.getSceneTasksList()) {
                if (this.tasks.containsKey(task))
                    continue;
                // 只有在地图启用列表中的场景任务才可选
                if (!enabledSceneTasks.contains(task.name()))
                    continue;
                // 检查任务是否被职业禁止刷新
                if (role != null && !role.canRefreshTask(this.player, task))
                    continue;
                float weight = 1f / this.timesGotten.getOrDefault(task, 1);
                weight = applyMoodWeight(weight, task.category, currentMood);
                map.put(task, weight);
                total += weight;
            }
        }

        if (total <= 0)
            return null;

        float random = this.player.getRandom().nextFloat() * total;
        var entries = new ArrayList<>(map.entrySet());
        Collections.shuffle(entries);
        for (Map.Entry<Task, Float> entry : entries) {
            random -= entry.getValue();
            if (random <= 0) {
                return createTaskInstance(entry.getKey());
            }
        }
        return null;
    }

    /** 根据情绪状态调整任务权重。 */
    private static float applyMoodWeight(float weight, Task.TaskCategory category, float currentMood) {
        if (currentMood < GameConstants.MID_MOOD_THRESHOLD) {
            // 情绪低落时：安抚性任务权重翻倍，活跃性任务权重降低
            if (category == Task.TaskCategory.SOOTHING) {
                weight *= 2f;
            }
            if (category == Task.TaskCategory.ACTIVE) {
                weight *= 0.5f;
            }
        } else if (currentMood > GameConstants.ANGRY_MOOD_THRESHOLD) {
            // 情绪亢奋时：活跃性任务权重提升，静态任务权重降低
            if (category == Task.TaskCategory.ACTIVE) {
                weight *= 1.5f;
            }
            if (category == Task.TaskCategory.STATIC || category == Task.TaskCategory.SOOTHING) {
                weight *= 0.5f;
            }
        }
        return weight;
    }

    private @Nullable TrainTask createTaskInstance(Task taskType) {
        return switch (taskType) {
            case SLEEP -> new SleepTask(GameConstants.SLEEP_TASK_DURATION);
            case OUTSIDE -> new OutsideTask(GameConstants.OUTSIDE_TASK_DURATION);
            case RAED_BOOK -> new ReadBookTask(GameConstants.READ_BOOK_TASK_DURATION);
            case EAT -> new EatTask();
            case DRINK -> new DrinkTask();
            case EXERCISE -> new ExerciseTask(GameConstants.EXERCISE_TASK_DURATION);
            case MEDITATE -> new MeditateTask(GameConstants.MEDITATE_TASK_DURATION);
            case BATHE -> new BatheTask(GameConstants.BATHE_TASK_DURATION);
            case NOTE_BLOCK -> new NoteBlockTask(GameConstants.NOTE_BLOCK_TASK_CLICK_COUNTS);
            case TOILET -> new ToiletTask(GameConstants.TOILET_TASK_DURATION);
            case CHAIR -> new ChairTask(GameConstants.CHAIR_TASK_DURATION);
            case BREATHE -> new BreatheTask(GameConstants.BREATHE_TASK_DURATION);
            case BE_ALONE -> new BeAloneTask(GameConstants.BE_ALONE_TASK_DURATION);
            case LIGHT_STOVE -> createSceneTriggeredTask(SceneTaskManager.Type.LIGHT_STOVE, "light_stove");
            case CLEAN_DUST -> createSceneTriggeredTask(SceneTaskManager.Type.CLEAN_DUST, "clean_dust");
            case TRANSPORT -> createSceneTriggeredTask(SceneTaskManager.Type.TRANSPORT, "transport");
            case PRAY -> createSceneTriggeredTask(SceneTaskManager.Type.PRAY, "pray");
            case PRUNE_BUSH -> createSceneTriggeredTask(SceneTaskManager.Type.PRUNE_BUSH, "prune_bush");
            case HARVEST_CROP -> createSceneTriggeredTask(SceneTaskManager.Type.HARVEST_CROP, "harvest_crop");
            case MANIC -> new ManicTask();
            default -> null;
        };
    }

    private TrainTask createSceneTriggeredTask(SceneTaskManager.Type sceneType, String name) {
        if (this.player instanceof ServerPlayer sp) {
            SceneTaskManager.assign(sp, sceneType);
        }
        return new SceneTriggeredTask(name);
    }

    private void clearSceneTask(Task taskType) {
        SceneTaskManager.Type sceneType = switch (taskType) {
            case LIGHT_STOVE -> SceneTaskManager.Type.LIGHT_STOVE;
            case CLEAN_DUST -> SceneTaskManager.Type.CLEAN_DUST;
            case TRANSPORT -> SceneTaskManager.Type.TRANSPORT;
            case PRAY -> SceneTaskManager.Type.PRAY;
            case PRUNE_BUSH -> SceneTaskManager.Type.PRUNE_BUSH;
            case HARVEST_CROP -> SceneTaskManager.Type.HARVEST_CROP;
            default -> null;
        };
        if (sceneType != null) {
            SceneTaskManager.clear(this.player, sceneType);
        }
    }

    public void eatFood() {
        if (this.tasks.get(Task.EAT) instanceof EatTask eatTask)
            eatTask.fulfilled = true;
    }

    public void playNoteBlock() {
        if (this.tasks.get(Task.NOTE_BLOCK) instanceof NoteBlockTask noteBlockTask)
            noteBlockTask.trigger();
    }

    public void drinkCocktail() {
        if (RoleUtils.isPlayerTheJob(player, ModRoles.DREAM)) {
            player.addEffect(ModEffects.of(ModEffects.USED_BANED, 10 * 20, 1, false, false, true));
        }
        if (this.tasks.get(Task.DRINK) instanceof DrinkTask drinkTask)
            drinkTask.fulfilled = true;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        ListTag tasks = new ListTag();
        for (TrainTask task : this.tasks.values())
            tasks.add(task.toNbt());
        tag.put("tasks", tasks);
        tag.putInt("taskStreak", this.taskStreak);
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        this.tasks.clear();
        if (tag.contains("tasks", Tag.TAG_LIST)) {
            for (Tag element : tag.getList("tasks", Tag.TAG_COMPOUND)) {
                if (element instanceof CompoundTag compound && compound.contains("type")) {
                    int type = compound.getInt("type");
                    if (type < 0 || type >= Task.values().length)
                        continue;
                    Task typeEnum = Task.values()[type];
                    this.tasks.put(typeEnum, typeEnum.setFunction.apply(compound));
                }
            }
        }
        this.taskStreak = tag.contains("taskStreak", Tag.TAG_INT) ? tag.getInt("taskStreak") : 0;
    }

    public enum Task {
        // ───────── 非场景任务 ─────────
        SLEEP(nbt -> new SleepTask(nbt.getInt("timer")), TaskCategory.SOOTHING),
        OUTSIDE(nbt -> new OutsideTask(nbt.getInt("timer")), TaskCategory.ACTIVE),
        RAED_BOOK(nbt -> new ReadBookTask(nbt.getInt("timer")), TaskCategory.STATIC),
        EAT(nbt -> new EatTask(), TaskCategory.ACTIVE),
        DRINK(nbt -> new DrinkTask(), TaskCategory.ACTIVE),
        EXERCISE(nbt -> new ExerciseTask(nbt.getInt("timer")), TaskCategory.ACTIVE),
        MEDITATE(nbt -> new MeditateTask(nbt.getInt("timer")), TaskCategory.SOOTHING),
        BATHE(nbt -> new BatheTask(nbt.getInt("timer")), TaskCategory.STATIC),
        TOILET(nbt -> new ToiletTask(nbt.getInt("timer")), TaskCategory.STATIC),
        CHAIR(nbt -> new ChairTask(nbt.getInt("timer")), TaskCategory.SOOTHING),
        NOTE_BLOCK(nbt -> new NoteBlockTask(nbt.getInt("timer")), TaskCategory.ACTIVE),
        BE_ALONE(nbt -> new BeAloneTask(nbt.getInt("timer")), TaskCategory.STATIC), // 一个人静静

        // ───────── 场景任务 ─────────
        BREATHE(nbt -> new BreatheTask(nbt.getInt("timer")), TaskCategory.ACTIVE), // 呼吸新鲜空气
        LIGHT_STOVE(nbt -> new SceneTriggeredTask("light_stove"), TaskCategory.SOOTHING), // 取暖
        CLEAN_DUST(nbt -> new SceneTriggeredTask("clean_dust"), TaskCategory.ACTIVE), // 清扫灰尘
        TRANSPORT(nbt -> new SceneTriggeredTask("transport"), TaskCategory.ACTIVE), // 运送物资
        PRAY(nbt -> new SceneTriggeredTask("pray"), TaskCategory.SOOTHING), // 祷告
        PRUNE_BUSH(nbt -> new SceneTriggeredTask("prune_bush"), TaskCategory.ACTIVE), // 修剪灌木
        HARVEST_CROP(nbt -> new SceneTriggeredTask("harvest_crop"), TaskCategory.ACTIVE), // 活动筋骨

        // ───────── 不可刷新任务 ─────────
        CUSTOM(nbt -> new CustomTask(nbt.getString("customName"), nbt.getString("customId")),
                TaskCategory.NON_REFRESHABLE),
        MANIC(nbt -> new ManicTask(), TaskCategory.NON_REFRESHABLE);

        /**
         * 任务种类：用于任务生成时的权重调整。
         * 安抚性任务 —— 情绪低落时权重翻倍
         * 活跃性任务 —— 情绪亢奋时权重提升，情绪低落时降低
         * 静态任务 —— 情绪亢奋时权重降低
         * 不可刷新任务 —— 不参与随机任务池（如狂躁症任务、自定义任务）
         */
        public enum TaskCategory {
            SOOTHING, // 安抚性
            ACTIVE, // 活跃性
            STATIC, // 静态
            NON_REFRESHABLE // 不可刷新
        }

        /**
         * 场景任务标记：
         * 非场景任务 —— 在所有地图中都会出现。
         * 场景任务 —— 只有在地图配置中设置了才会出现。
         * 任务种类为不可刷新的任务始终视为场景任务。
         */
        public boolean isSceneTask() {
            if (this.category == TaskCategory.NON_REFRESHABLE)
                return true;
            return switch (this) {
                case BREATHE, LIGHT_STOVE, CLEAN_DUST, TRANSPORT, PRAY, PRUNE_BUSH, HARVEST_CROP -> true;
                default -> false;
            };
        }

        /** 获取任务种类。 */
        public final @NotNull Function<CompoundTag, TrainTask> setFunction;
        public final TaskCategory category;

        Task(@NotNull Function<CompoundTag, TrainTask> function, TaskCategory category) {
            this.setFunction = function;
            this.category = category;
        }

        /** 非场景任务列表（在所有地图中都可刷出的任务）。 */
        private static final List<Task> availableTasksList = List.of(
                SLEEP, RAED_BOOK, EAT, DRINK, EXERCISE, MEDITATE, BATHE,
                CHAIR, NOTE_BLOCK, TOILET, BE_ALONE);

        public static List<Task> getAvailableTasksList() {
            return availableTasksList;
        }

        /** 场景任务列表（仅在地图启用时才能刷出）。 */
        private static final List<Task> sceneTasksList = List.of(
                BREATHE, LIGHT_STOVE, CLEAN_DUST, TRANSPORT, PRAY, PRUNE_BUSH, HARVEST_CROP);

        public static List<Task> getSceneTasksList() {
            return sceneTasksList;
        }
    }

    public static class SleepTask implements TrainTask {
        private int timer;

        public SleepTask(int time) {
            this.timer = time;
        }

        @Override
        public void tick(@NotNull Player player) {
            if (player.isSleeping() && this.timer > 0)
                this.timer--;
        }

        @Override
        public boolean isFulfilled(@NotNull Player player) {
            return this.timer <= 0;
        }

        @Override
        public String getName() {
            return "sleep";
        }

        @Override
        public Task getType() {
            return Task.SLEEP;
        }

        @Override
        public CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt("type", Task.SLEEP.ordinal());
            nbt.putInt("timer", this.timer);
            return nbt;
        }
    }

    /**
     * 一个人静静任务类（非场景任务）
     * 玩家周围4格内不能有其他玩家持续6秒， Y轴只检查3格
     */
    public static class BeAloneTask implements TrainTask {
        private int timer;

        public BeAloneTask(int time) {
            this.timer = time;
        }

        @Override
        public void tick(@NotNull Player player) {
            if (this.timer > 0) {
                double radius = 4.0;
                net.minecraft.world.phys.AABB box = player.getBoundingBox().inflate(radius, 3.0, radius);
                boolean someoneNear = player.level().getEntitiesOfClass(Player.class, box,
                        other -> other != player && other.isAlive() && !other.isSpectator()).size() > 0;
                if (!someoneNear) {
                    this.timer--;
                }
            }
        }

        @Override
        public boolean isFulfilled(@NotNull Player player) {
            return this.timer <= 0;
        }

        @Override
        public String getName() {
            return "be_alone";
        }

        @Override
        public Task getType() {
            return Task.BE_ALONE;
        }

        @Override
        public CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt("type", Task.BE_ALONE.ordinal());
            nbt.putInt("timer", this.timer);
            return nbt;
        }
    }

    public static class OutsideTask implements TrainTask {
        private int timer;

        public OutsideTask(int time) {
            this.timer = time + 6;
        }

        @Override
        public void tick(@NotNull Player player) {
            if (isSkyVisibleAdjacent(player) && this.timer > 0)
                this.timer--;
        }

        @Override
        public boolean isFulfilled(@NotNull Player player) {
            return this.timer <= 0;
        }

        @Override
        public String getName() {
            return "outside";
        }

        @Override
        public Task getType() {
            return Task.OUTSIDE;
        }

        @Override
        public CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt("type", Task.OUTSIDE.ordinal());
            nbt.putInt("timer", this.timer);
            return nbt;
        }
    }

    public static class ReadBookTask implements TrainTask {
        private int timer;

        public ReadBookTask(int time) {
            this.timer = time;
        }

        public void setTimer(int time) {
            this.timer = time;
        }

        @Override
        public void tick(@NotNull Player player) {
            if (player.containerMenu instanceof LecternMenu && this.timer > 0) {
                this.timer--;
            }
        }

        @Override
        public boolean isFulfilled(@NotNull Player player) {
            return this.timer <= 0;
        }

        @Override
        public String getName() {
            return "read_book";
        }

        @Override
        public Task getType() {
            return Task.RAED_BOOK;
        }

        @Override
        public CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt("type", Task.RAED_BOOK.ordinal());
            nbt.putInt("timer", this.timer);
            return nbt;
        }
    }

    public static class EatTask implements TrainTask {
        public boolean fulfilled = false;

        @Override
        public boolean isFulfilled(@NotNull Player player) {
            return this.fulfilled;
        }

        @Override
        public String getName() {
            return "eat";
        }

        @Override
        public Task getType() {
            return Task.EAT;
        }

        @Override
        public CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt("type", Task.EAT.ordinal());
            return nbt;
        }
    }

    public static class DrinkTask implements TrainTask {
        public boolean fulfilled = false;

        @Override
        public boolean isFulfilled(@NotNull Player player) {
            return this.fulfilled;
        }

        @Override
        public String getName() {
            return "drink";
        }

        @Override
        public Task getType() {
            return Task.DRINK;
        }

        @Override
        public CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt("type", Task.DRINK.ordinal());
            return nbt;
        }
    }

    public static class ExerciseTask implements TrainTask {
        public int timer;

        public ExerciseTask(int time) {
            this.timer = time;
        }

        @Override
        public void tick(@NotNull Player player) {
            // 玩家必须在跑步状态下才能完成锻炼任务
            if (player.level().getBlockState(player.blockPosition().offset(0, -1, 0))
                    .getBlock() == Blocks.BLACK_CONCRETE && this.timer > 0) {
                this.timer--;
            }
        }

        @Override
        public boolean isFulfilled(@NotNull Player player) {
            return this.timer <= 0;
        }

        @Override
        public String getName() {
            return "exercise";
        }

        @Override
        public Task getType() {
            return Task.EXERCISE;
        }

        @Override
        public CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt("type", Task.EXERCISE.ordinal());
            nbt.putInt("timer", this.timer);
            return nbt;
        }
    }

    /**
     * 冥想任务类
     * 玩家需要保持静止并蹲下来完成冥想
     */
    public static class MeditateTask implements TrainTask {
        private int timer;

        public MeditateTask(int time) {
            this.timer = time;
        }

        @Override
        public void tick(@NotNull Player player) {
            // 玩家必须蹲下且保持静止才能完成冥想任务
            if (player.isCrouching() && player.getDeltaMovement().lengthSqr() < 0.01 && this.timer > 0) {
                this.timer--;
            }
        }

        @Override
        public boolean isFulfilled(@NotNull Player player) {
            return this.timer <= 0;
        }

        @Override
        public String getName() {
            return "meditate";
        }

        @Override
        public Task getType() {
            return Task.MEDITATE;
        }

        @Override
        public CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt("type", Task.MEDITATE.ordinal());
            nbt.putInt("timer", this.timer);
            return nbt;
        }
    }

    /**
     * 音符盒任务类
     * 玩家需要站在水中或雨中完成洗澡
     */
    public static class NoteBlockTask implements TrainTask {
        private int timer;

        public NoteBlockTask(int time) {
            this.timer = time;
        }

        public void trigger() {
            if (this.timer > 0)
                this.timer--;
        }

        @Override
        public void tick(@NotNull Player player) {
        }

        @Override
        public boolean isFulfilled(@NotNull Player player) {
            return this.timer <= 0;
        }

        @Override
        public String getName() {
            return "note_block";
        }

        @Override
        public Task getType() {
            return Task.NOTE_BLOCK;
        }

        @Override
        public CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt("type", Task.NOTE_BLOCK.ordinal());
            nbt.putInt("timer", this.timer);
            return nbt;
        }
    }

    /**
     * 座椅休息任务类
     * 玩家需要在座椅（包括马桶）上坐着完成
     */
    public static class ChairTask implements TrainTask {
        private int timer;

        public ChairTask(int time) {
            this.timer = time;
        }

        @Override
        public void tick(@NotNull Player player) {
            if (this.timer > 0) {
                var vehicleE = player.getVehicle();
                if (vehicleE != null) {
                    if (isSeat(vehicleE)) {
                        this.timer--;
                    }
                }
            }
        }

        private static boolean isSeat(Entity vehicleE) {
            return BlockTypeChecker.isSeatEntity(vehicleE);
        }

        @Override
        public boolean isFulfilled(@NotNull Player player) {
            return this.timer <= 0;
        }

        @Override
        public String getName() {
            return "chair";
        }

        @Override
        public Task getType() {
            return Task.CHAIR;
        }

        @Override
        public CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt("type", Task.CHAIR.ordinal());
            nbt.putInt("timer", this.timer);
            return nbt;
        }
    }

    /**
     * 厕所任务类
     * 玩家需要在马桶上坐着完成
     */
    public static class ToiletTask implements TrainTask {
        private int timer;

        public ToiletTask(int time) {
            this.timer = time;
        }

        @Override
        public void tick(@NotNull Player player) {
            if (this.timer > 0) {
                var vehicleE = player.getVehicle();
                if (vehicleE != null) {
                    if (vehicleE instanceof SeatEntity entity) {
                        var seatPos = entity.getSeatPos();
                        if (seatPos != null) {
                            BlockState seatBlockState = player.level().getBlockState(seatPos);
                            if (seatBlockState.getBlock() instanceof ToiletBlock) {
                                this.timer--;
                            }
                        }
                    }
                }
            }
        }

        @Override
        public boolean isFulfilled(@NotNull Player player) {
            return this.timer <= 0;
        }

        @Override
        public String getName() {
            return "toilet";
        }

        @Override
        public Task getType() {
            return Task.TOILET;
        }

        @Override
        public CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt("type", Task.TOILET.ordinal());
            nbt.putInt("timer", this.timer);
            return nbt;
        }
    }

    /**
     * 洗澡任务类
     * 玩家需要站在水中或雨中完成洗澡
     */
    public static class BatheTask implements TrainTask {
        private int timer;

        public BatheTask(int time) {
            this.timer = time;
        }

        @Override
        public void tick(@NotNull Player player) {
            // 检查玩家是否在水中或头顶4格内有洒水器(SPRINKLERS)
            if (player.isInWater() && this.timer > 0) {
                this.timer--;
            } else {
                // 检查头顶4格范围内是否有洒水器
                for (int y = 0; y < 4; y++) {
                    if (player.level().getBlockState(player.blockPosition().above(y)).is(TMMBlockTags.SPRINKLERS)
                            && this.timer > 0) {
                        this.timer--;
                        break;
                    }
                }
            }
        }

        @Override
        public boolean isFulfilled(@NotNull Player player) {
            return this.timer <= 0;
        }

        @Override
        public String getName() {
            return "bathe";
        }

        @Override
        public Task getType() {
            return Task.BATHE;
        }

        @Override
        public CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt("type", Task.BATHE.ordinal());
            nbt.putInt("timer", this.timer);
            return nbt;
        }
    }

    /**
     * 呼吸任务类
     * 玩家需要站在天空下呼吸新鲜空气
     */
    public static class BreatheTask implements TrainTask {
        private int timer;

        public BreatheTask(int time) {
            this.timer = time;
        }

        @Override
        public void tick(@NotNull Player player) {
            // 检查玩家头顶是否能看到天空
            if (isSkyVisible(player) && this.timer > 0) {
                this.timer--;
            }
        }

        @Override
        public boolean isFulfilled(@NotNull Player player) {
            return this.timer <= 0;
        }

        @Override
        public String getName() {
            return "breathe";
        }

        @Override
        public Task getType() {
            return Task.BREATHE;
        }

        @Override
        public CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt("type", Task.BREATHE.ordinal());
            nbt.putInt("timer", this.timer);
            return nbt;
        }
    }

    /**
     * 场景触发任务：通过 SceneTaskManager 回调触发完成。
     * 任务本身不自行检测完成条件，而是等待外部调用 setFulfilled()。
     * 为避免枚举自引用，Task 类型通过名称字符串在运行时解析。
     */
    public static class SceneTriggeredTask implements TrainTask {
        private final String name;
        private boolean fulfilled;

        /** @param name 任务名称（与 Task 枚举名小写对应，如 "light_stove"） */
        public SceneTriggeredTask(String name) {
            this.name = name;
            this.fulfilled = false;
        }

        public void setFulfilled(boolean fulfilled) {
            this.fulfilled = fulfilled;
        }

        @Override
        public boolean isFulfilled(@NotNull Player player) {
            return this.fulfilled;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Task getType() {
            return Task.valueOf(name.toUpperCase());
        }

        @Override
        public CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt("type", Task.valueOf(name.toUpperCase()).ordinal());
            nbt.putString("name", name);
            nbt.putBoolean("fulfilled", fulfilled);
            return nbt;
        }
    }

    /**
     * 自定义任务类
     * 用于实体交互方块创建的自定义任务
     */
    public static class CustomTask implements TrainTask {
        private final String customName;
        private final String customId;
        private boolean fulfilled = false;

        public CustomTask(String customName, String customId) {
            this.customName = customName != null ? customName : "自定义任务";
            this.customId = customId != null ? customId : "custom_" + System.currentTimeMillis();
        }

        @Override
        public boolean isFulfilled(@NotNull Player player) {
            return this.fulfilled;
        }

        public void setFulfilled(boolean fulfilled) {
            this.fulfilled = fulfilled;
        }

        @Override
        public String getName() {
            return this.customName;
        }

        public String getCustomTaskId() {
            return this.customId;
        }

        @Override
        public Task getType() {
            return Task.CUSTOM;
        }

        @Override
        public CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt("type", Task.CUSTOM.ordinal());
            nbt.putString("customName", this.customName);
            nbt.putString("customId", this.customId);
            nbt.putBoolean("fulfilled", this.fulfilled);
            return nbt;
        }
    }

    /**
     * 乱码任务类
     * 狂躁症玩家专属任务，永远无法完成
     */
    public static class ManicTask implements TrainTask {
        @Override
        public boolean isFulfilled(Player player) {
            // 乱码任务永远无法完成
            return false;
        }

        @Override
        public String getName() {
            // 使用语言文件中的翻译键，系统会查找并渲染翻译值
            return "task.manic.name";
        }

        @Override
        public Task getType() {
            return Task.MANIC;
        }

        @Override
        public CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt("type", Task.MANIC.ordinal());
            return nbt;
        }
    }

    public interface TrainTask {
        public boolean finishByOther = false;

        default void tick(@NotNull Player player) {
        }

        boolean isFulfilled(Player player);

        String getName();

        default String getCustomTaskId() {
            return null;
        }

        Task getType();

        CompoundTag toNbt();
    }

}
