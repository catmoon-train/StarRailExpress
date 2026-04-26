package io.wifi.events.day_night_fight;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.network.original.TaskCompletePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

public class DNFPlayerComponent implements RoleComponent {
    public static final ComponentKey<DNFPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("dnf_killer"), DNFPlayerComponent.class);

    private final Player player;
    private int blood;
    private int bodiesEaten;
    private int soldierEaten;
    private int psychologistEaten;
    private int locksmithEaten;
    private int civilianEaten;
    private long lastCorpseEatTick;
    private boolean personalEnding;
    private boolean hungerWarned;
    private int dnfDay = -1;
    private boolean ateToday;
    private boolean drankToday;
    private boolean mealTaskCompleted;
    private boolean webCleanedToday;
    private boolean dustCleanedToday;
    private boolean toiletToday;
    private boolean lectureToday;
    private int chefFoodWorkToday;
    private boolean chefWaterCheckedToday;
    private boolean chefInitialFoodSeeded;

    public DNFPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        clear();
        this.lastCorpseEatTick = player.level().getGameTime();
        sync();
    }

    @Override
    public void clear() {
        this.blood = 0;
        this.bodiesEaten = 0;
        this.soldierEaten = 0;
        this.psychologistEaten = 0;
        this.locksmithEaten = 0;
        this.civilianEaten = 0;
        this.lastCorpseEatTick = 0;
        this.personalEnding = false;
        this.hungerWarned = false;
        this.dnfDay = -1;
        this.ateToday = false;
        this.drankToday = false;
        this.mealTaskCompleted = false;
        this.webCleanedToday = false;
        this.dustCleanedToday = false;
        this.toiletToday = false;
        this.lectureToday = false;
        this.chefFoodWorkToday = 0;
        this.chefWaterCheckedToday = false;
        this.chefInitialFoodSeeded = false;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public int getBlood() {
        return blood;
    }

    public int getBodiesEaten() {
        return bodiesEaten;
    }

    public boolean hasPersonalEnding() {
        return personalEnding;
    }

    public float getTransformationProgress() {
        float soldier = Math.min(1f, soldierEaten / 1f);
        float psycho = Math.min(1f, psychologistEaten / 1f);
        float locksmith = Math.min(1f, locksmithEaten / 1f);
        float civilians = Math.min(1f, civilianEaten / 2f);
        return (soldier + psycho + locksmith + civilians) / 4f;
    }

    public int getDnfDay() {
        return dnfDay;
    }

    public int getChefFoodWorkToday() {
        return chefFoodWorkToday;
    }

    public boolean hasChefWaterCheckedToday() {
        return chefWaterCheckedToday;
    }

    public void startDnfDay(ServerPlayer serverPlayer, int day, boolean isChef) {
        if (this.dnfDay == day) {
            return;
        }

        this.dnfDay = day;
        this.ateToday = false;
        this.drankToday = false;
        this.mealTaskCompleted = false;
        this.webCleanedToday = false;
        this.dustCleanedToday = false;
        this.toiletToday = false;
        this.lectureToday = false;
        this.chefFoodWorkToday = 0;
        this.chefWaterCheckedToday = false;

        setupHudTasks(serverPlayer, isChef);
        if (isChef) {
            giveChefDailySupplies(serverPlayer);
        }
        sync();
    }

    public void markAteFood(ServerPlayer serverPlayer) {
        this.ateToday = true;
        tryCompleteMealTask(serverPlayer);
        sync();
    }

    public void markDrankWater(ServerPlayer serverPlayer) {
        this.drankToday = true;
        tryCompleteMealTask(serverPlayer);
        sync();
    }

    public boolean cleanLibraryWeb(ServerPlayer serverPlayer) {
        if (webCleanedToday) {
            return false;
        }
        if (!completeSanTask(serverPlayer, "message.dnf.task.library_web")) {
            return false;
        }
        webCleanedToday = true;
        removeHudTask(SREPlayerTaskComponent.Task.DNF_LIBRARY_WEB);
        sync();
        return true;
    }

    public boolean cleanPrisonDust(ServerPlayer serverPlayer) {
        if (dustCleanedToday) {
            return false;
        }
        if (!completeSanTask(serverPlayer, "message.dnf.task.prison_dust")) {
            return false;
        }
        dustCleanedToday = true;
        removeHudTask(SREPlayerTaskComponent.Task.DNF_PRISON_DUST);
        sync();
        return true;
    }

    public boolean completeToilet(ServerPlayer serverPlayer) {
        if (toiletToday) {
            return false;
        }
        if (!completeSanTask(serverPlayer, "message.dnf.task.toilet")) {
            return false;
        }
        toiletToday = true;
        removeHudTask(SREPlayerTaskComponent.Task.DNF_TOILET);
        sync();
        return true;
    }

    public boolean completeLecture(ServerPlayer serverPlayer) {
        if (lectureToday) {
            return false;
        }
        if (!completeSanTask(serverPlayer, "message.dnf.task.lecture")) {
            return false;
        }
        lectureToday = true;
        removeHudTask(SREPlayerTaskComponent.Task.DNF_LECTURE);
        sync();
        return true;
    }

    public boolean useChefCapacity(ServerPlayer serverPlayer, int amount) {
        if (chefFoodWorkToday + amount > DNF.CHEF_DAILY_FOOD_CAPACITY) {
            serverPlayer.displayClientMessage(Component.translatable("message.dnf.chef.capacity",
                    chefFoodWorkToday, DNF.CHEF_DAILY_FOOD_CAPACITY).withStyle(ChatFormatting.YELLOW), true);
            return false;
        }
        chefFoodWorkToday += amount;
        if (completeSanTask(serverPlayer, "message.dnf.task.chef_work")) {
            removeHudTask(SREPlayerTaskComponent.Task.DNF_CHEF_WORK);
        }
        sync();
        return true;
    }

    public boolean checkChefWater(ServerPlayer serverPlayer) {
        if (chefWaterCheckedToday) {
            serverPlayer.displayClientMessage(Component.translatable("message.dnf.chef.water_already_checked")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (!useChefCapacity(serverPlayer, DNF.CHEF_WATER_CHECK_COST)) {
            return false;
        }
        chefWaterCheckedToday = true;
        serverPlayer.displayClientMessage(Component.translatable("message.dnf.chef.water_checked")
                .withStyle(ChatFormatting.AQUA), false);
        sync();
        return true;
    }

    public void giveInitialCafeteriaFood(ServerPlayer serverPlayer) {
        if (chefInitialFoodSeeded) {
            return;
        }
        chefInitialFoodSeeded = true;
        giveOrDrop(serverPlayer, new ItemStack(DNFItems.CORN_GRUEL, DNF.INITIAL_CAFETERIA_FOOD));
        serverPlayer.displayClientMessage(Component.translatable("message.dnf.chef.initial_food",
                DNF.INITIAL_CAFETERIA_FOOD).withStyle(ChatFormatting.GREEN), false);
        sync();
    }

    private void giveChefDailySupplies(ServerPlayer serverPlayer) {
        giveOrDrop(serverPlayer, new ItemStack(DNFItems.CORNMEAL_BAG));
        giveOrDrop(serverPlayer, new ItemStack(DNFItems.FLOUR_BAG));
        giveOrDrop(serverPlayer, new ItemStack(DNFItems.SUSPICIOUS_MEAT));
        giveOrDrop(serverPlayer, new ItemStack(DNFItems.WATER_BOTTLE, 2));
        serverPlayer.displayClientMessage(Component.translatable("message.dnf.chef.daily_supplies")
                .withStyle(ChatFormatting.DARK_GREEN), false);
    }

    private void tryCompleteMealTask(ServerPlayer serverPlayer) {
        if (!mealTaskCompleted && ateToday && drankToday) {
            mealTaskCompleted = true;
            completeSanTask(serverPlayer, "message.dnf.task.meal");
            removeHudTask(SREPlayerTaskComponent.Task.DNF_MEAL);
        }
    }

    private boolean completeSanTask(ServerPlayer serverPlayer, String messageKey) {
        if (!messageKey.equals("message.dnf.task.meal") && !ateToday) {
            serverPlayer.displayClientMessage(Component.translatable("message.dnf.task.need_food")
                    .withStyle(ChatFormatting.YELLOW), true);
            return false;
        }
        SREPlayerMoodComponent.KEY.get(serverPlayer).addMood(DNF.SAN_TASK_MOOD_GAIN);
        ServerPlayNetworking.send(serverPlayer, new TaskCompletePayload());
        serverPlayer.displayClientMessage(Component.translatable(messageKey,
                (int) (DNF.SAN_TASK_MOOD_GAIN * 100)).withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    private void setupHudTasks(ServerPlayer serverPlayer, boolean isChef) {
        SREPlayerTaskComponent tasks = SREPlayerTaskComponent.KEY.get(serverPlayer);
        tasks.tasks.clear();
        if (!DNF.isDNFKiller(serverPlayer)) {
            addHudTask(tasks, SREPlayerTaskComponent.Task.DNF_MEAL);
            addHudTask(tasks, SREPlayerTaskComponent.Task.DNF_TOILET);
            addHudTask(tasks, SREPlayerTaskComponent.Task.DNF_LECTURE);
        }
        addHudTask(tasks, SREPlayerTaskComponent.Task.DNF_LIBRARY_WEB);
        addHudTask(tasks, SREPlayerTaskComponent.Task.DNF_PRISON_DUST);
        if (isChef) {
            addHudTask(tasks, SREPlayerTaskComponent.Task.DNF_CHEF_WORK);
        }
        tasks.sync();
    }

    private static void addHudTask(SREPlayerTaskComponent tasks, SREPlayerTaskComponent.Task type) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("type", type.ordinal());
        tasks.tasks.put(type, type.setFunction.apply(tag));
    }

    private void removeHudTask(SREPlayerTaskComponent.Task type) {
        SREPlayerTaskComponent tasks = SREPlayerTaskComponent.KEY.get(player);
        tasks.tasks.remove(type);
        tasks.sync();
    }

    private static void giveOrDrop(ServerPlayer serverPlayer, ItemStack stack) {
        if (!serverPlayer.addItem(stack.copy())) {
            serverPlayer.drop(stack.copy(), false);
        }
    }

    public void eatCorpse(ServerPlayer eater, ResourceLocation roleId, int bloodGain) {
        this.blood += bloodGain;
        this.bodiesEaten++;
        this.lastCorpseEatTick = eater.level().getGameTime();
        this.hungerWarned = false;

        if (roleId.equals(DNFRoles.SOLDIER_ID)) {
            soldierEaten++;
        } else if (roleId.equals(DNFRoles.PSYCHOLOGIST_ID)) {
            psychologistEaten++;
        } else if (roleId.equals(DNFRoles.LOCKSMITH_ID)) {
            locksmithEaten++;
        } else if (roleId.equals(DNFRoles.CIVILIAN_ID)
                || roleId.equals(io.wifi.starrailexpress.api.TMMRoles.CIVILIAN.identifier())) {
            civilianEaten++;
        }

        if (!personalEnding && soldierEaten >= 1 && psychologistEaten >= 1 && locksmithEaten >= 1
                && civilianEaten >= 2) {
            personalEnding = true;
        }
        sync();
    }

    public boolean spendBlood(int amount) {
        if (blood < amount) {
            return false;
        }
        blood -= amount;
        sync();
        return true;
    }

    public void setBlood(int blood) {
        this.blood = Math.max(0, blood);
        sync();
    }

    public void addBlood(int amount) {
        setBlood(this.blood + amount);
    }

    public void checkHunger(ServerPlayer serverPlayer) {
        if (lastCorpseEatTick <= 0) {
            lastCorpseEatTick = serverPlayer.level().getGameTime();
            sync();
            return;
        }

        long since = serverPlayer.level().getGameTime() - lastCorpseEatTick;
        if (since >= DNF.TWO_DAYS_TICKS) {
            serverPlayer.displayClientMessage(Component.translatable("message.dnf.killer.starved")
                    .withStyle(ChatFormatting.DARK_RED), false);
            GameUtils.forceKillPlayer(serverPlayer, false, null, GameConstants.DeathReasons.GENERIC);
        } else if (!hungerWarned && since >= DNF.TWO_DAYS_TICKS - 12000L) {
            hungerWarned = true;
            serverPlayer.displayClientMessage(Component.translatable("message.dnf.killer.hunger_warning")
                    .withStyle(ChatFormatting.RED), false);
            sync();
        }
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeToSyncNbt(tag, registryLookup);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        readFromSyncNbt(tag, registryLookup);
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("Blood", blood);
        tag.putInt("BodiesEaten", bodiesEaten);
        tag.putInt("SoldierEaten", soldierEaten);
        tag.putInt("PsychologistEaten", psychologistEaten);
        tag.putInt("LocksmithEaten", locksmithEaten);
        tag.putInt("CivilianEaten", civilianEaten);
        tag.putLong("LastCorpseEatTick", lastCorpseEatTick);
        tag.putBoolean("PersonalEnding", personalEnding);
        tag.putBoolean("HungerWarned", hungerWarned);
        tag.putInt("DnfDay", dnfDay);
        tag.putBoolean("AteToday", ateToday);
        tag.putBoolean("DrankToday", drankToday);
        tag.putBoolean("MealTaskCompleted", mealTaskCompleted);
        tag.putBoolean("WebCleanedToday", webCleanedToday);
        tag.putBoolean("DustCleanedToday", dustCleanedToday);
        tag.putBoolean("ToiletToday", toiletToday);
        tag.putBoolean("LectureToday", lectureToday);
        tag.putInt("ChefFoodWorkToday", chefFoodWorkToday);
        tag.putBoolean("ChefWaterCheckedToday", chefWaterCheckedToday);
        tag.putBoolean("ChefInitialFoodSeeded", chefInitialFoodSeeded);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.blood = tag.getInt("Blood");
        this.bodiesEaten = tag.getInt("BodiesEaten");
        this.soldierEaten = tag.getInt("SoldierEaten");
        this.psychologistEaten = tag.getInt("PsychologistEaten");
        this.locksmithEaten = tag.getInt("LocksmithEaten");
        this.civilianEaten = tag.getInt("CivilianEaten");
        this.lastCorpseEatTick = tag.getLong("LastCorpseEatTick");
        this.personalEnding = tag.getBoolean("PersonalEnding");
        this.hungerWarned = tag.getBoolean("HungerWarned");
        this.dnfDay = tag.getInt("DnfDay");
        this.ateToday = tag.getBoolean("AteToday");
        this.drankToday = tag.getBoolean("DrankToday");
        this.mealTaskCompleted = tag.getBoolean("MealTaskCompleted");
        this.webCleanedToday = tag.getBoolean("WebCleanedToday");
        this.dustCleanedToday = tag.getBoolean("DustCleanedToday");
        this.toiletToday = tag.getBoolean("ToiletToday");
        this.lectureToday = tag.getBoolean("LectureToday");
        this.chefFoodWorkToday = tag.getInt("ChefFoodWorkToday");
        this.chefWaterCheckedToday = tag.getBoolean("ChefWaterCheckedToday");
        this.chefInitialFoodSeeded = tag.getBoolean("ChefInitialFoodSeeded");
    }
}
