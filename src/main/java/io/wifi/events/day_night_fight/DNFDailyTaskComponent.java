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
    private int waterDrinksToday;
    private int waterDrinksRequiredToday = 1;
    private boolean webCleanedToday;
    private boolean dustCleanedToday;
    private boolean toiletToday;
    private boolean toiletInProgress;
    private boolean lectureToday;
    private boolean chatToday;
    private int cleaningTasksToday;
    private boolean cleaningInProgress;
    private int chefFoodWorkToday;
    private boolean chefWaterCheckedToday;
    private boolean chefInitialFoodSeeded;
    // 新增：跟踪玩家每天拿取食物的次数
    private int foodTakenToday;
    // 新增：跟踪玩家每天是否使用过饮水机
    private boolean waterDispenserUsedToday;

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
        this.waterDrinksToday = 0;
        this.waterDrinksRequiredToday = 1;
        this.webCleanedToday = false;
        this.dustCleanedToday = false;
        this.toiletToday = false;
        this.toiletInProgress = false;
        this.lectureToday = false;
        this.chatToday = false;
        this.cleaningTasksToday = 0;
        this.cleaningInProgress = false;
        this.chefFoodWorkToday = 0;
        this.chefWaterCheckedToday = false;
        this.chefInitialFoodSeeded = false;
        this.foodTakenToday = 0;
        this.waterDispenserUsedToday = false;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {

    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {

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

    public int getWaterDrinksToday() {
        return waterDrinksToday;
    }

    public void setWaterDrinksToday(int waterDrinksToday) {
        this.waterDrinksToday = waterDrinksToday;
    }

    public int getWaterDrinksRequiredToday() {
        return waterDrinksRequiredToday;
    }

    public void setWaterDrinksRequiredToday(int waterDrinksRequiredToday) {
        this.waterDrinksRequiredToday = waterDrinksRequiredToday;
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

    public boolean isToiletInProgress() {
        return toiletInProgress;
    }

    public void setToiletInProgress(boolean toiletInProgress) {
        this.toiletInProgress = toiletInProgress;
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

    // 新增方法：获取和设置食物拿取次数
    public int getFoodTakenToday() {
        return foodTakenToday;
    }

    public void setFoodTakenToday(int foodTakenToday) {
        this.foodTakenToday = foodTakenToday;
    }

    public void incrementFoodTakenToday() {
        this.foodTakenToday++;
    }

    // 新增方法：获取和设置饮水机使用状态
    public boolean isWaterDispenserUsedToday() {
        return waterDispenserUsedToday;
    }

    public void setWaterDispenserUsedToday(boolean waterDispenserUsedToday) {
        this.waterDispenserUsedToday = waterDispenserUsedToday;
    }

    // 在NBT读写方法中添加foodTakenToday字段的处理
    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (tag.contains("dnfDay")) {
            this.dnfDay = tag.getInt("dnfDay");
        }
        this.ateToday = tag.getBoolean("ateToday");
        this.drankToday = tag.getBoolean("drankToday");
        this.mealTaskCompleted = tag.getBoolean("mealTaskCompleted");
        this.waterDrinksToday = tag.getInt("waterDrinksToday");
        this.waterDrinksRequiredToday = tag.getInt("waterDrinksRequiredToday");
        this.webCleanedToday = tag.getBoolean("webCleanedToday");
        this.dustCleanedToday = tag.getBoolean("dustCleanedToday");
        this.toiletToday = tag.getBoolean("toiletToday");
        this.toiletInProgress = tag.getBoolean("toiletInProgress");
        this.lectureToday = tag.getBoolean("lectureToday");
        this.chatToday = tag.getBoolean("chatToday");
        this.cleaningTasksToday = tag.getInt("cleaningTasksToday");
        this.cleaningInProgress = tag.getBoolean("cleaningInProgress");
        this.chefFoodWorkToday = tag.getInt("chefFoodWorkToday");
        this.chefWaterCheckedToday = tag.getBoolean("chefWaterCheckedToday");
        this.chefInitialFoodSeeded = tag.getBoolean("chefInitialFoodSeeded");
        // 读取foodTakenToday字段
        this.foodTakenToday = tag.getInt("foodTakenToday");
        // 读取waterDispenserUsedToday字段
        this.waterDispenserUsedToday = tag.getBoolean("waterDispenserUsedToday");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("dnfDay", this.dnfDay);
        tag.putBoolean("ateToday", this.ateToday);
        tag.putBoolean("drankToday", this.drankToday);
        tag.putBoolean("mealTaskCompleted", this.mealTaskCompleted);
        tag.putInt("waterDrinksToday", this.waterDrinksToday);
        tag.putInt("waterDrinksRequiredToday", this.waterDrinksRequiredToday);
        tag.putBoolean("webCleanedToday", this.webCleanedToday);
        tag.putBoolean("dustCleanedToday", this.dustCleanedToday);
        tag.putBoolean("toiletToday", this.toiletToday);
        tag.putBoolean("toiletInProgress", this.toiletInProgress);
        tag.putBoolean("lectureToday", this.lectureToday);
        tag.putBoolean("chatToday", this.chatToday);
        tag.putInt("cleaningTasksToday", this.cleaningTasksToday);
        tag.putBoolean("cleaningInProgress", this.cleaningInProgress);
        tag.putInt("chefFoodWorkToday", this.chefFoodWorkToday);
        tag.putBoolean("chefWaterCheckedToday", this.chefWaterCheckedToday);
        tag.putBoolean("chefInitialFoodSeeded", this.chefInitialFoodSeeded);
        // 写入foodTakenToday字段
        tag.putInt("foodTakenToday", this.foodTakenToday);
        // 写入waterDispenserUsedToday字段
        tag.putBoolean("waterDispenserUsedToday", this.waterDispenserUsedToday);
    }
}