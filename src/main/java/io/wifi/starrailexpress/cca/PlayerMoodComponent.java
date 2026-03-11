package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.block.ToiletBlock;
import io.wifi.starrailexpress.block.entity.SeatEntity;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameFunctions;
import io.wifi.starrailexpress.index.tag.TMMBlockTags;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.network.original.TaskCompletePayload;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

public class PlayerMoodComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<PlayerMoodComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("mood"),
            PlayerMoodComponent.class);
    private final Player player;
    public final Map<Task, TrainTask> tasks = new HashMap<>();
    public final Map<Task, Integer> timesGotten = new HashMap<>();
    private int nextTaskTimer = 0;
    private float mood = 1f;
    private final HashMap<UUID, ItemStack> psychosisItems = new HashMap<>();
    private static List<Item> cachedPsychosisItems = null;

    public PlayerMoodComponent(Player player) {
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
    public void reset() {
        this.tasks.clear();
        this.timesGotten.clear();
        this.nextTaskTimer = GameConstants.TIME_TO_FIRST_TASK;
        this.psychosisItems.clear();
        this.setMood(1f);
        this.sync();
    }

    @Override
    public void clear() {
        this.reset();
    }

    private List<Item> getPsychosisItemPool() {
        if (cachedPsychosisItems == null) {
            cachedPsychosisItems = this.player.registryAccess()
                    .asGetterLookup()
                    .lookupOrThrow(Registries.ITEM)
                    .get(TMMItemTags.PSYCHOSIS_ITEMS)
                    .map(HolderSet.ListBacked::stream)
                    .map(stream -> stream.map(Holder::value).toList())
                    .orElseGet(() -> {
                        SRE.LOGGER.error("Server provided empty tag {}", TMMItemTags.PSYCHOSIS_ITEMS.location());
                        return List.of();
                    });
        }
        return cachedPsychosisItems;
    }

    @Override
    public void clientTick() {
        if (!GameWorldComponent.KEY.get(this.player.level()).isRunning() || !SREClient.isPlayerAliveAndInSurvival())
            return;
        if (!this.tasks.isEmpty()) {
            if (this.mood > 0)
                this.mood = this.mood - this.tasks.size() * GameConstants.MOOD_DRAIN;
            if (this.mood < 0)
                this.mood = 0;
        }

        if (this.isLowerThanMid()) {
            // imagine random items for players
            for (Player playerEntity : this.player.level().players()) {
                if (!playerEntity.equals(this.player)
                        && this.player.level().getRandom().nextInt(GameConstants.ITEM_PSYCHOSIS_REROLL_TIME) == 0) {
                    ItemStack psychosisStack;
                    List<Item> taggedItems = getPsychosisItemPool();

                    if (!taggedItems.isEmpty()
                            && this.player.getRandom().nextFloat() < GameConstants.ITEM_PSYCHOSIS_CHANCE) {
                        Item item = Util.getRandom(taggedItems, this.player.getRandom());
                        psychosisStack = new ItemStack(item);
                    } else {
                        psychosisStack = playerEntity.getMainHandItem();
                    }

                    // this.psychosisItems.put(playerEntity.getUuid(),
                    // playerEntity.getRandom().nextFloat() < GameConstants.ITEM_PSYCHOSIS_CHANCE ?
                    // PSYCHOSIS_ITEM_POOL[playerEntity.getRandom().nextInt(PSYCHOSIS_ITEM_POOL.length)].getDefaultStack()
                    // : playerEntity.getMainHandStack());
                    this.psychosisItems.put(playerEntity.getUUID(), psychosisStack);
                }
            }
        } else {
            if (!this.psychosisItems.isEmpty())
                this.psychosisItems.clear();
        }
    }

    @Override
    public void serverTick() {

        GameWorldComponent gameWorldComponent = GameWorldComponent.KEY.get(this.player.level());
        if (!gameWorldComponent.isRunning() || !GameFunctions.isPlayerAliveAndSurvival(this.player))
            return;
        boolean shouldSync = false;
        if (!this.tasks.isEmpty()) {
            if (this.mood > 0)
                this.mood = this.mood - this.tasks.size() * GameConstants.MOOD_DRAIN;// 替换setMood避免高频率同步
            if (this.mood < 0)
                this.mood = 0;
            if (this.nextTaskTimer % 100 == 0) { // 5s一次同步
                shouldSync = true;
            }
        }
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
            if (nextTaskTimer % 40 == 0) { // 2秒一次
                shouldSync = true;
            }
        }
        ArrayList<TrainTask> removals = new ArrayList<>();
        for (TrainTask task : this.tasks.values()) {
            task.tick(this.player);
            if (task.isFulfilled(this.player)) {
                removals.add(task);
                this.setMood(this.mood + GameConstants.MOOD_GAIN);
                if (this.player instanceof ServerPlayer tempPlayer)
                    ServerPlayNetworking.send(tempPlayer, new TaskCompletePayload());
                shouldSync = true;
            }
        }
        for (TrainTask task : removals) {
            this.tasks.remove(task.getType());
            // 更新计分板上的任务计数
            if (this.player instanceof ServerPlayer serverPlayer) {
                GameScoreboardComponent scoreboardComponent = GameScoreboardComponent.KEY
                        .get(serverPlayer.getServer().getScoreboard());
                scoreboardComponent.incrementPlayerTaskCount(this.player);

                // 调用角色的任务完成方法
                io.wifi.starrailexpress.api.RoleMethodDispatcher.callOnFinishQuest(this.player, task.getName());
            }
        }
        if (shouldSync)
            this.sync();

        // 根据情绪值调整玩家速度
        updatePlayerMovementSpeed();
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

    public float getMood() {
        GameWorldComponent gameWorldComponent = GameWorldComponent.KEY.get(this.player.level());

        Role role = gameWorldComponent.getRole(player);
        if (gameWorldComponent.isRunning() && role != null && role.getMoodType() == Role.MoodType.REAL) {
            return this.mood;
        } else
            return 1;
    }

    public void setMood(float mood) {
        Role role = GameWorldComponent.KEY.get(this.player.level()).getRole(player);

        if (role != null && role.getMoodType() == Role.MoodType.REAL) {
            float clampedMood = Math.clamp(mood, 0, 1);
            // 只有当情绪变化超过0.05时才同步（减少网络占用）
            if (Math.abs(this.mood - clampedMood) > 0.01f) {
                this.mood = clampedMood;
                this.sync();
            } else {
                this.mood = clampedMood;
            }
        } else {
            if (this.mood != 1f) {
                this.mood = 1f;
                this.sync();
            }
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
        if (this.tasks.get(Task.DRINK) instanceof DrinkTask drinkTask)
            drinkTask.fulfilled = true;
    }

    public boolean isLowerThanMid() {
        return this.getMood() < GameConstants.MID_MOOD_THRESHOLD;
    }

    public boolean isLowerThanDepressed() {
        return this.getMood() < GameConstants.DEPRESSIVE_MOOD_THRESHOLD;
    }

    public boolean isHigherThanAngry() {
        return this.getMood() > GameConstants.ANGRY_MOOD_THRESHOLD;
    }

    public HashMap<UUID, ItemStack> getPsychosisItems() {
        return this.psychosisItems;
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        tag.putFloat("mood", this.mood);
        ListTag tasks = new ListTag();
        for (TrainTask task : this.tasks.values())
            tasks.add(task.toNbt());
        tag.put("tasks", tasks);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        this.mood = tag.contains("mood", Tag.TAG_FLOAT) ? tag.getFloat("mood") : 1f;
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

    /**
     * 根据玩家的情绪值更新其移动速度
     */
    private void updatePlayerMovementSpeed() {
        if (this.player instanceof ServerPlayer) {

            // 获取当前玩家的移动速度属性
            var speedAttribute = this.player.getAttribute(Attributes.MOVEMENT_SPEED);

            if (speedAttribute != null) {
                // 移除之前可能添加的修饰符
                speedAttribute.removeModifier(SRE.id("mood_speed_modifier"));
                if (SRE.isLobby) {
                    // 删除速度
                    return;
                }
                // 根据情绪值添加新的修饰符
                if (this.isLowerThanDepressed()) {
                    // 抑郁状态 - 降低20%速度
                    AttributeModifier modifier = new AttributeModifier(
                            SRE.id("mood_speed_modifier"),
                            -0.2,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                    speedAttribute.addTransientModifier(modifier);
                } else if (this.isHigherThanAngry()) {
                    // 愤怒状态 - 提高15%速度
                    AttributeModifier modifier = new AttributeModifier(
                            SRE.id("mood_speed_modifier"),
                            0.15,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                    speedAttribute.addTransientModifier(modifier);
                }
                // 正常情绪范围内保持默认速度
            }
        }
    }

}