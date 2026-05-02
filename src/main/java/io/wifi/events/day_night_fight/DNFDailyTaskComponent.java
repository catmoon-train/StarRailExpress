package io.wifi.events.day_night_fight;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

public class DNFDailyTaskComponent implements RoleComponent {
    public static final ComponentKey<DNFDailyTaskComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("dnf_daily_task"), DNFDailyTaskComponent.class);

    private final Player player;
    private int dnfDay = -1;
    private boolean ateToday;
    private boolean drankToday;
    private boolean mealTaskCompleted;
    private boolean webCleanedToday;
    private boolean dustCleanedToday;
    private boolean toiletToday;
    private boolean lectureToday;
    private boolean chatToday;
    private int cleaningTasksToday;
    private boolean cleaningInProgress;
    private int chefFoodWorkToday;
    private boolean chefWaterCheckedToday;
    private boolean chefInitialFoodSeeded;

    public DNFDailyTaskComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        clear();
        sync();
    }

    @Override
    public void clear() {
        this.dnfDay = -1;
        this.ateToday = false;
        this.drankToday = false;
        this.mealTaskCompleted = false;
        this.webCleanedToday = false;
        this.dustCleanedToday = false;
        this.toiletToday = false;
        this.lectureToday = false;
        this.chatToday = false;
        this.cleaningTasksToday = 0;
        this.cleaningInProgress = false;
        this.chefFoodWorkToday = 0;
        this.chefWaterCheckedToday = false;
        this.chefInitialFoodSeeded = false;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public int getDnfDay() {
        return dnfDay;
    }

    public void setDnfDay(int dnfDay) {
        this.dnfDay = dnfDay;
    }

    public boolean isAteToday() {
        return ateToday;
    }

    public void setAteToday(boolean ateToday) {
        this.ateToday = ateToday;
    }

    public boolean isDrankToday() {
        return drankToday;
    }

    public void setDrankToday(boolean drankToday) {
        this.drankToday = drankToday;
    }

    public boolean isMealTaskCompleted() {
        return mealTaskCompleted;
    }

    public void setMealTaskCompleted(boolean mealTaskCompleted) {
        this.mealTaskCompleted = mealTaskCompleted;
    }

    public boolean isWebCleanedToday() {
        return webCleanedToday;
    }

    public void setWebCleanedToday(boolean webCleanedToday) {
        this.webCleanedToday = webCleanedToday;
    }

    public boolean isDustCleanedToday() {
        return dustCleanedToday;
    }

    public void setDustCleanedToday(boolean dustCleanedToday) {
        this.dustCleanedToday = dustCleanedToday;
    }

    public boolean isToiletToday() {
        return toiletToday;
    }

    public void setToiletToday(boolean toiletToday) {
        this.toiletToday = toiletToday;
    }

    public boolean isLectureToday() {
        return lectureToday;
    }

    public void setLectureToday(boolean lectureToday) {
        this.lectureToday = lectureToday;
    }

    public boolean isChatToday() {
        return chatToday;
    }

    public void setChatToday(boolean chatToday) {
        this.chatToday = chatToday;
    }

    public int getCleaningTasksToday() {
        return cleaningTasksToday;
    }

    public void setCleaningTasksToday(int cleaningTasksToday) {
        this.cleaningTasksToday = cleaningTasksToday;
    }

    public boolean isCleaningInProgress() {
        return cleaningInProgress;
    }

    public void setCleaningInProgress(boolean cleaningInProgress) {
        this.cleaningInProgress = cleaningInProgress;
    }

    public int getChefFoodWorkToday() {
        return chefFoodWorkToday;
    }

    public void setChefFoodWorkToday(int chefFoodWorkToday) {
        this.chefFoodWorkToday = chefFoodWorkToday;
    }

    public boolean isChefWaterCheckedToday() {
        return chefWaterCheckedToday;
    }

    public void setChefWaterCheckedToday(boolean chefWaterCheckedToday) {
        this.chefWaterCheckedToday = chefWaterCheckedToday;
    }

    public boolean isChefInitialFoodSeeded() {
        return chefInitialFoodSeeded;
    }

    public void setChefInitialFoodSeeded(boolean chefInitialFoodSeeded) {
        this.chefInitialFoodSeeded = chefInitialFoodSeeded;
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
        tag.putInt("DnfDay", dnfDay);
        tag.putBoolean("AteToday", ateToday);
        tag.putBoolean("DrankToday", drankToday);
        tag.putBoolean("MealTaskCompleted", mealTaskCompleted);
        tag.putBoolean("WebCleanedToday", webCleanedToday);
        tag.putBoolean("DustCleanedToday", dustCleanedToday);
        tag.putBoolean("ToiletToday", toiletToday);
        tag.putBoolean("LectureToday", lectureToday);
        tag.putBoolean("ChatToday", chatToday);
        tag.putInt("CleaningTasksToday", cleaningTasksToday);
        tag.putBoolean("CleaningInProgress", cleaningInProgress);
        tag.putInt("ChefFoodWorkToday", chefFoodWorkToday);
        tag.putBoolean("ChefWaterCheckedToday", chefWaterCheckedToday);
        tag.putBoolean("ChefInitialFoodSeeded", chefInitialFoodSeeded);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.dnfDay = tag.getInt("DnfDay");
        this.ateToday = tag.getBoolean("AteToday");
        this.drankToday = tag.getBoolean("DrankToday");
        this.mealTaskCompleted = tag.getBoolean("MealTaskCompleted");
        this.webCleanedToday = tag.getBoolean("WebCleanedToday");
        this.dustCleanedToday = tag.getBoolean("DustCleanedToday");
        this.toiletToday = tag.getBoolean("ToiletToday");
        this.lectureToday = tag.getBoolean("LectureToday");
        this.chatToday = tag.getBoolean("ChatToday");
        this.cleaningTasksToday = tag.getInt("CleaningTasksToday");
        this.cleaningInProgress = tag.getBoolean("CleaningInProgress");
        this.chefFoodWorkToday = tag.getInt("ChefFoodWorkToday");
        this.chefWaterCheckedToday = tag.getBoolean("ChefWaterCheckedToday");
        this.chefInitialFoodSeeded = tag.getBoolean("ChefInitialFoodSeeded");
    }
}
