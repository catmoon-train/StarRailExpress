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

import java.util.*;
import java.util.function.Function;

public class SREPlayerTaskComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SREPlayerTaskComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("task"),
            SREPlayerTaskComponent.class);
    private final Player player;
    public final Map<Task, TrainTask> tasks = new HashMap<>();
    public final Map<Task, Integer> timesGotten = new HashMap<>();
    public int nextTaskTimer = 0;
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
            }
            this.nextTaskTimer = (int) (this.player.getRandom().nextFloat()
                    * (GameConstants.MAX_TASK_COOLDOWN - GameConstants.MIN_TASK_COOLDOWN)
                    + GameConstants.MIN_TASK_COOLDOWN);
            this.nextTaskTimer = Math.max(this.nextTaskTimer, 2);
            shouldSync = true;
        }
        ArrayList<TrainTask> removals = new ArrayList<>();
        for (TrainTask task : this.tasks.values()) {
            task.tick(this.player);
            if (task.isFulfilled(this.player)) {
                removals.add(task);
                this.playerMoodComponent.addMood(GameConstants.MOOD_GAIN);
                if (this.player instanceof ServerPlayer tempPlayer)
                    ServerPlayNetworking.send(tempPlayer, new TaskCompletePayload());
                shouldSync = true;
            }
        }
        for (TrainTask task : removals) {
            this.tasks.remove(task.getType());
            // 更新计分板上的任务计数
            if (this.player instanceof ServerPlayer serverPlayer) {
                SREGameScoreboardComponent scoreboardComponent = SREGameScoreboardComponent.KEY
                        .get(serverPlayer.getServer().getScoreboard());
                scoreboardComponent.incrementPlayerTaskCount(this.player);

                // 调用角色的任务完成方法
                io.wifi.starrailexpress.api.RoleMethodDispatcher.callOnFinishQuest(this.player, task.getName());
            }
        }
        if (shouldSync)
            this.sync();
    }

    public @Nullable TrainTask generateTask() {
        if (!this.tasks.isEmpty())
            return null;
        HashMap<Task, Float> map = new HashMap<>();
        float total = 0f;
        for (Task task : Task.getAvailableTasksList()) {
            if (this.tasks.containsKey(task))
                continue;
            float weight = 1f / this.timesGotten.getOrDefault(task, 1);
            map.put(task, weight);
            total += weight;
        }

        float random = this.player.getRandom().nextFloat() * total;
        var entries = new ArrayList<>(map.entrySet());
        Collections.shuffle(entries);
        for (Map.Entry<Task, Float> entry : entries) {
            random -= entry.getValue();
            if (random <= 0) {
                return switch (entry.getKey()) {
                    case SLEEP -> new SleepTask(GameConstants.SLEEP_TASK_DURATION);
                    case OUTSIDE -> new OutsideTask(GameConstants.OUTSIDE_TASK_DURATION);
                    case RAED_BOOK -> new ReadBookTask(GameConstants.READ_BOOK_TASK_DURATION);
                    case EAT -> new EatTask();
                    case DRINK -> new DrinkTask();
                    case EXERCISE -> new ExerciseTask(GameConstants.EXERCISE_TASK_DURATION);
                    case MEDITATE -> new MeditateTask(GameConstants.MEDITATE_TASK_DURATION); // 添加冥想任务生成
                    case BATHE -> new BatheTask(GameConstants.BATHE_TASK_DURATION); // 添加洗澡任务生成
                    case NOTE_BLOCK -> new NoteBlockTask(GameConstants.NOTE_BLOCK_TASK_CLICK_COUNTS);
                    case TOILET -> new ToiletTask(GameConstants.TOILET_TASK_DURATION);
                    case CHAIR -> new ChairTask(GameConstants.CHAIR_TASK_DURATION);
                    default -> null;
                };
            }
        }
        return null;
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
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        ListTag tasks = new ListTag();
        for (TrainTask task : this.tasks.values())
            tasks.add(task.toNbt());
        tag.put("tasks", tasks);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
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
        NOTE_BLOCK(nbt -> new NoteBlockTask(nbt.getInt("timer"))); // 添加音符盒任务

        private static List<Task> availableTasksList = List.of(SLEEP, RAED_BOOK, EAT, DRINK, EXERCISE, MEDITATE, BATHE,
                CHAIR,
                NOTE_BLOCK, TOILET);
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

    public interface TrainTask {
        default void tick(@NotNull Player player) {
        }

        boolean isFulfilled(Player player);

        String getName();

        Task getType();

        CompoundTag toNbt();
    }

}