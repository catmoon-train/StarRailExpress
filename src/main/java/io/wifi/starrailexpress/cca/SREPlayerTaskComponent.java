package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.block.ToiletBlock;
import io.wifi.starrailexpress.block.entity.SeatEntity;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.tag.TMMBlockTags;
import io.wifi.starrailexpress.network.original.TaskCompletePayload;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import static io.wifi.starrailexpress.SRE.isSkyVisibleAdjacent;
import static io.wifi.starrailexpress.SRE.isSkyVisible;

import java.util.*;
import java.util.function.Function;

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
        this.sync();
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
            TrainTask task = this.generateTask();
            if (task != null) {
                this.tasks.put(task.getType(), task);
                this.timesGotten.putIfAbsent(task.getType(), 1);
                this.timesGotten.put(task.getType(), this.timesGotten.get(task.getType()) + 1);
                // 记录任务分配时的情绪值
                this.moodWhenTaskAssigned = (playerMoodComponent != null) ? playerMoodComponent.getMood() : 1f;
                this.currentTaskAge = 0;
                this.parallelTaskGenerated = false;
            }
            // 使用动态任务冷却：根据游戏已过时间调整
            SREGameTimeComponent gameTimeComponent = SREGameTimeComponent.KEY.get(this.player.level());
            long gameElapsedTicks = Math.max(0, gameTimeComponent.getResetTime() - gameTimeComponent.getTime());
            int minCooldown = GameConstants.getDynamicMinTaskCooldown(gameElapsedTicks);
            int maxCooldown = GameConstants.getDynamicMaxTaskCooldown(gameElapsedTicks);
            this.nextTaskTimer = (int) (this.player.getRandom().nextFloat()
                    * (maxCooldown - minCooldown)
                    + minCooldown);
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
                boolean isParallel = this.parallelTaskTypes.contains(task.getType());
                // 并列任务奖励减少
                float moodGain = isParallel
                        ? GameConstants.MOOD_GAIN * GameConstants.PARALLEL_TASK_REWARD_MULTIPLIER
                        : GameConstants.MOOD_GAIN;
                this.playerMoodComponent.addMood(moodGain);
                if (this.player instanceof ServerPlayer tempPlayer)
                    ServerPlayNetworking.send(tempPlayer, new TaskCompletePayload());
                shouldSync = true;
            }
        }
        for (TrainTask task : removals) {
            boolean isParallel = this.parallelTaskTypes.contains(task.getType());
            this.tasks.remove(task.getType());
            this.parallelTaskTypes.remove(task.getType());
            // 更新计分板上的任务计数
            if (this.player instanceof ServerPlayer serverPlayer) {
                SREGameScoreboardComponent scoreboardComponent = SREGameScoreboardComponent.KEY
                        .get(serverPlayer.getServer().getScoreboard());
                scoreboardComponent.incrementPlayerTaskCount(this.player);

                // 调用角色的任务完成方法（包含连击奖励，并列任务奖励减少）
                int streak = isParallel ? 0 : this.taskStreak;
                io.wifi.starrailexpress.api.RoleMethodDispatcher.callOnFinishQuest(this.player, task.getName(),
                        streak, isParallel);
            }
            if (!isParallel) {
                this.taskStreak++; // 完成奖励发放后再增加连击计数（并列任务不增加连击）
            }
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

    /**
     * 获取当前地图禁用的任务列表
     */
    private Set<String> getDisabledTasks() {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(this.player.level());
        if (areas != null && areas.mapName != null) {
            io.wifi.starrailexpress.data.MapConfig.MapEntry mapEntry = io.wifi.starrailexpress.data.MapConfig
                    .getInstance().getMapById(areas.mapName);
            if (mapEntry != null && mapEntry.disabledTasks != null) {
                return new HashSet<>(mapEntry.disabledTasks);
            }
        }
        return Set.of();
    }

    public @Nullable TrainTask generateTask() {
        if (!this.tasks.isEmpty())
            return null;
        return generateTaskInternal();
    }

    /**
     * 生成并列任务：当原始任务超时且情绪下降时生成
     * 不会生成与已有任务相同类型的任务
     */
    public @Nullable TrainTask generateParallelTask() {
        return generateTaskInternal();
    }

    private @Nullable TrainTask generateTaskInternal() {
        HashMap<Task, Float> map = new HashMap<>();
        float total = 0f;
        // 获取当前情绪状态用于动态权重调整
        float currentMood = (playerMoodComponent != null) ? playerMoodComponent.getMood() : 1f;
        // 获取当前地图禁用的任务
        Set<String> disabledTasks = getDisabledTasks();
        for (Task task : Task.getAvailableTasksList()) {
            if (this.tasks.containsKey(task))
                continue;
            // 检查任务是否被当前地图禁用
            if (disabledTasks.contains(task.name()))
                continue;
            float weight = 1f / this.timesGotten.getOrDefault(task, 1);
            // 情绪驱动的任务权重调整
            if (currentMood < GameConstants.MID_MOOD_THRESHOLD) {
                // 情绪低落时：安抚性任务权重翻倍
                if (task == Task.MEDITATE || task == Task.SLEEP || task == Task.CHAIR) {
                    weight *= 2f;
                }
                // 活跃性任务权重降低（呼吸任务需要到室外，归类为活跃性）
                if (task == Task.EXERCISE || task == Task.OUTSIDE || task == Task.BREATHE) {
                    weight *= 0.5f;
                }
            } else if (currentMood > GameConstants.ANGRY_MOOD_THRESHOLD) {
                // 情绪亢奋时：活跃性任务权重提升
                if (task == Task.EXERCISE || task == Task.OUTSIDE || task == Task.NOTE_BLOCK) {
                    weight *= 1.5f;
                }
                // 静态任务权重降低
                if (task == Task.SLEEP || task == Task.MEDITATE) {
                    weight *= 0.5f;
                }
            }
            map.put(task, weight);
            total += weight;
        }

        if (total <= 0) return null;

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
            default -> null;
        };
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
        SLEEP(nbt -> new SleepTask(nbt.getInt("timer"))),
        OUTSIDE(nbt -> new OutsideTask(nbt.getInt("timer"))), // 不要OUTSIDE
        RAED_BOOK(nbt -> new ReadBookTask(nbt.getInt("timer"))),
        EAT(nbt -> new EatTask()),
        DRINK(nbt -> new DrinkTask()),
        EXERCISE(nbt -> new ExerciseTask(nbt.getInt("timer"))),
        MEDITATE(nbt -> new MeditateTask(nbt.getInt("timer"))), // 添加冥想任务
        BATHE(nbt -> new BatheTask(nbt.getInt("timer"))), // 添加洗澡任务
        TOILET(nbt -> new ToiletTask(nbt.getInt("timer"))), // 添加厕所任务
        CHAIR(nbt -> new ChairTask(nbt.getInt("timer"))), // 添加座椅休息任务
        NOTE_BLOCK(nbt -> new NoteBlockTask(nbt.getInt("timer"))), // 添加音符盒任务
        BREATHE(nbt -> new BreatheTask(nbt.getInt("timer"))); // 呼吸新鲜空气任务

        private static List<Task> availableTasksList = List.of(SLEEP, RAED_BOOK, EAT, DRINK, EXERCISE, MEDITATE, BATHE,
                CHAIR,
                NOTE_BLOCK, TOILET, BREATHE);
        public final @NotNull Function<CompoundTag, TrainTask> setFunction;

        Task(@NotNull Function<CompoundTag, TrainTask> function) {
            this.setFunction = function;
        }

        public static List<Task> getAvailableTasksList() {
            return availableTasksList;
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
                    if (vehicleE instanceof SeatEntity) {
                        this.timer--;
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

    public interface TrainTask {
        default void tick(@NotNull Player player) {
        }

        boolean isFulfilled(Player player);

        String getName();

        Task getType();

        CompoundTag toNbt();
    }

}