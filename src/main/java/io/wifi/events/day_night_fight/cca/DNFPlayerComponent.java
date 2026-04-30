package io.wifi.events.day_night_fight.cca;

import io.wifi.events.day_night_fight.DNF;
import io.wifi.events.day_night_fight.DNFDailyTaskComponent;
import io.wifi.events.day_night_fight.DNFItems;
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

/**
 * DNF facade component:
 * <p>
 * 1) killer stats synced by {@link DNFKillerStatsComponent}
 * 2) daily/chef task state synced by {@link DNFDailyTaskComponent}
 * <p>
 * This facade keeps old API usage unchanged while reducing sync payload fanout.
 */
public class DNFPlayerComponent implements RoleComponent {
    public static final ComponentKey<DNFPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("dnf_killer"), DNFPlayerComponent.class);

    private final Player player;

    public DNFPlayerComponent(Player player) {
        this.player = player;
    }

    private DNFKillerStatsComponent stats() {
        return DNFKillerStatsComponent.KEY.get(player);
    }

    private DNFDailyTaskComponent daily() {
        return DNFDailyTaskComponent.KEY.get(player);
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        clear();
        stats().setLastCorpseEatTick(player.level().getGameTime());
        stats().sync();
        daily().sync();
    }

    @Override
    public void clear() {
        stats().clear();
        daily().clear();
    }

    public int getBlood() {
        return stats().getBlood();
    }

    public int getBodiesEaten() {
        return stats().getBodiesEaten();
    }

    public boolean hasPersonalEnding() {
        return stats().hasPersonalEnding();
    }

    public float getTransformationProgress() {
        return stats().getTransformationProgress();
    }

    public int getDnfDay() {
        return daily().getDnfDay();
    }

    public int getChefFoodWorkToday() {
        return daily().getChefFoodWorkToday();
    }

    public boolean hasChefWaterCheckedToday() {
        return daily().isChefWaterCheckedToday();
    }

    public void startDnfDay(ServerPlayer serverPlayer, int day, boolean isChef) {
        DNFDailyTaskComponent daily = daily();
        if (daily.getDnfDay() == day) {
            return;
        }

        daily.setDnfDay(day);
        daily.setAteToday(false);
        daily.setDrankToday(false);
        daily.setMealTaskCompleted(false);
        daily.setWebCleanedToday(false);
        daily.setDustCleanedToday(false);
        daily.setToiletToday(false);
        daily.setLectureToday(false);
        daily.setChefFoodWorkToday(0);
        daily.setChefWaterCheckedToday(false);

        setupHudTasks(serverPlayer, isChef);
        if (isChef) {
            giveChefDailySupplies(serverPlayer);
        }
        daily.sync();
    }

    public void markAteFood(ServerPlayer serverPlayer) {
        DNFDailyTaskComponent daily = daily();
        daily.setAteToday(true);
        tryCompleteMealTask(serverPlayer, daily);
        daily.sync();
    }

    public void markDrankWater(ServerPlayer serverPlayer) {
        DNFDailyTaskComponent daily = daily();
        daily.setDrankToday(true);
        tryCompleteMealTask(serverPlayer, daily);
        daily.sync();
    }

    public boolean cleanLibraryWeb(ServerPlayer serverPlayer) {
        DNFDailyTaskComponent daily = daily();
        if (daily.isWebCleanedToday()) {
            return false;
        }
        if (!completeSanTask(serverPlayer, daily, "message.dnf.task.library_web")) {
            return false;
        }
        daily.setWebCleanedToday(true);
        removeHudTask(SREPlayerTaskComponent.Task.DNF_LIBRARY_WEB);
        daily.sync();
        return true;
    }

    public boolean cleanPrisonDust(ServerPlayer serverPlayer) {
        DNFDailyTaskComponent daily = daily();
        if (daily.isDustCleanedToday()) {
            return false;
        }
        if (!completeSanTask(serverPlayer, daily, "message.dnf.task.prison_dust")) {
            return false;
        }
        daily.setDustCleanedToday(true);
        removeHudTask(SREPlayerTaskComponent.Task.DNF_PRISON_DUST);
        daily.sync();
        return true;
    }

    public boolean completeToilet(ServerPlayer serverPlayer) {
        DNFDailyTaskComponent daily = daily();
        if (daily.isToiletToday()) {
            return false;
        }
        if (!completeSanTask(serverPlayer, daily, "message.dnf.task.toilet")) {
            return false;
        }
        daily.setToiletToday(true);
        removeHudTask(SREPlayerTaskComponent.Task.DNF_TOILET);
        daily.sync();
        return true;
    }

    public boolean completeLecture(ServerPlayer serverPlayer) {
        DNFDailyTaskComponent daily = daily();
        if (daily.isLectureToday()) {
            return false;
        }
        if (!completeSanTask(serverPlayer, daily, "message.dnf.task.lecture")) {
            return false;
        }
        daily.setLectureToday(true);
        removeHudTask(SREPlayerTaskComponent.Task.DNF_LECTURE);
        daily.sync();
        return true;
    }

    public boolean useChefCapacity(ServerPlayer serverPlayer, int amount) {
        DNFDailyTaskComponent daily = daily();
        if (daily.getChefFoodWorkToday() + amount > DNF.CHEF_DAILY_FOOD_CAPACITY) {
            serverPlayer.displayClientMessage(Component.translatable("message.dnf.chef.capacity",
                    daily.getChefFoodWorkToday(), DNF.CHEF_DAILY_FOOD_CAPACITY).withStyle(ChatFormatting.YELLOW), true);
            return false;
        }
        daily.setChefFoodWorkToday(daily.getChefFoodWorkToday() + amount);
        if (completeSanTask(serverPlayer, daily, "message.dnf.task.chef_work")) {
            removeHudTask(SREPlayerTaskComponent.Task.DNF_CHEF_WORK);
        }
        daily.sync();
        return true;
    }

    public boolean checkChefWater(ServerPlayer serverPlayer) {
        DNFDailyTaskComponent daily = daily();
        if (daily.isChefWaterCheckedToday()) {
            serverPlayer.displayClientMessage(Component.translatable("message.dnf.chef.water_already_checked")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (!useChefCapacity(serverPlayer, DNF.CHEF_WATER_CHECK_COST)) {
            return false;
        }
        daily.setChefWaterCheckedToday(true);
        serverPlayer.displayClientMessage(Component.translatable("message.dnf.chef.water_checked")
                .withStyle(ChatFormatting.AQUA), false);
        daily.sync();
        return true;
    }

    public void giveInitialCafeteriaFood(ServerPlayer serverPlayer) {
        DNFDailyTaskComponent daily = daily();
        if (daily.isChefInitialFoodSeeded()) {
            return;
        }
        daily.setChefInitialFoodSeeded(true);
        giveOrDrop(serverPlayer, new ItemStack(DNFItems.CORN_GRUEL, DNF.INITIAL_CAFETERIA_FOOD));
        serverPlayer.displayClientMessage(Component.translatable("message.dnf.chef.initial_food",
                DNF.INITIAL_CAFETERIA_FOOD).withStyle(ChatFormatting.GREEN), false);
        daily.sync();
    }

    private void giveChefDailySupplies(ServerPlayer serverPlayer) {
        giveOrDrop(serverPlayer, new ItemStack(DNFItems.CORNMEAL_BAG));
        giveOrDrop(serverPlayer, new ItemStack(DNFItems.FLOUR_BAG));
        giveOrDrop(serverPlayer, new ItemStack(DNFItems.SUSPICIOUS_MEAT));
        giveOrDrop(serverPlayer, new ItemStack(DNFItems.WATER_BOTTLE, 2));
        serverPlayer.displayClientMessage(Component.translatable("message.dnf.chef.daily_supplies")
                .withStyle(ChatFormatting.DARK_GREEN), false);
    }

    private void tryCompleteMealTask(ServerPlayer serverPlayer, DNFDailyTaskComponent daily) {
        if (!daily.isMealTaskCompleted() && daily.isAteToday() && daily.isDrankToday()) {
            daily.setMealTaskCompleted(true);
            completeSanTask(serverPlayer, daily, "message.dnf.task.meal");
            removeHudTask(SREPlayerTaskComponent.Task.DNF_MEAL);
        }
    }

    private boolean completeSanTask(ServerPlayer serverPlayer, DNFDailyTaskComponent daily, String messageKey) {
        if (!messageKey.equals("message.dnf.task.meal") && !daily.isAteToday()) {
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
        stats().eatCorpse(roleId, bloodGain);
    }

    public boolean spendBlood(int amount) {
        return stats().spendBlood(amount);
    }

    public void setBlood(int blood) {
        stats().setBlood(blood);
    }

    public void addBlood(int amount) {
        stats().addBlood(amount);
    }

    public void checkHunger(ServerPlayer serverPlayer) {
        DNFKillerStatsComponent stats = stats();
        if (stats.getLastCorpseEatTick() <= 0) {
            stats.setLastCorpseEatTick(serverPlayer.level().getGameTime());
            stats.sync();
            return;
        }

        long since = serverPlayer.level().getGameTime() - stats.getLastCorpseEatTick();
        if (since >= DNF.TWO_DAYS_TICKS) {
            serverPlayer.displayClientMessage(Component.translatable("message.dnf.killer.starved")
                    .withStyle(ChatFormatting.DARK_RED), false);
            GameUtils.forceKillPlayer(serverPlayer, false, null, GameConstants.DeathReasons.GENERIC);
        } else if (!stats.isHungerWarned() && since >= DNF.TWO_DAYS_TICKS - 12000L) {
            stats.setHungerWarned(true);
            serverPlayer.displayClientMessage(Component.translatable("message.dnf.killer.hunger_warning")
                    .withStyle(ChatFormatting.RED), false);
            stats.sync();
        }
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // Deprecated facade state. Keep empty to avoid duplicate persistence and sync traffic.
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // Deprecated facade state. State migrated to dedicated components.
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // Deprecated facade state. State migrated to dedicated components.
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // Deprecated facade state. State migrated to dedicated components.
    }
}
