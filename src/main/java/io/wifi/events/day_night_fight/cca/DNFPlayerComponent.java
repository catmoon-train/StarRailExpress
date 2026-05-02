package io.wifi.events.day_night_fight.cca;

import io.wifi.events.day_night_fight.DNF;
import io.wifi.events.day_night_fight.DNFDailyTaskComponent;
import io.wifi.events.day_night_fight.DNFItems;
import io.wifi.events.day_night_fight.DNFRoles;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.UUID;

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
    private int killedPlayers;
    private int poisonKills;
    private int poisonCraftDay = -1;
    private int poisonCraftedToday;
    private int knifeNightDay = -1;
    private int knifeUsesTonight;
    private int crowbarNightDay = -1;
    private boolean crowbarUsedTonight;
    private boolean aidReady;
    private int pendingAidType;
    private int pendingAidDay = -1;
    private int soldierShotDay = -1;
    private int soldierShotsToday;
    private boolean maniacStunned;
    private boolean chefDiaryFound;
    private boolean redemptionRecipeUnlocked;
    private UUID redemptionPartner;
    private boolean redemptionPotionCrafted;
    private boolean joinedMeeting;

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
        killedPlayers = 0;
        poisonKills = 0;
        poisonCraftDay = -1;
        poisonCraftedToday = 0;
        knifeNightDay = -1;
        knifeUsesTonight = 0;
        crowbarNightDay = -1;
        crowbarUsedTonight = false;
        aidReady = false;
        pendingAidType = 0;
        pendingAidDay = -1;
        soldierShotDay = -1;
        soldierShotsToday = 0;
        maniacStunned = false;
        chefDiaryFound = false;
        redemptionRecipeUnlocked = false;
        redemptionPartner = null;
        redemptionPotionCrafted = false;
        joinedMeeting = false;
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
        daily.setChatToday(false);
        daily.setCleaningTasksToday(0);
        daily.setCleaningInProgress(false);
        daily.setChefFoodWorkToday(0);
        daily.setChefWaterCheckedToday(false);
        soldierShotDay = day;
        soldierShotsToday = 0;
        poisonCraftDay = day;
        poisonCraftedToday = 0;
        maniacStunned = false;

        SREPlayerMoodComponent.KEY.get(serverPlayer).setMood(day == 0 ? DNF.INITIAL_SAN : DNF.MORNING_SAN);

        setupHudTasks(serverPlayer, isChef);
        if (isChef) {
            giveChefDailySupplies(serverPlayer);
        }
        daily.sync();
    }

    public void markAteFood(ServerPlayer serverPlayer) {
        DNFDailyTaskComponent daily = daily();
        if (daily.isAteToday()) {
            return;
        }
        daily.setAteToday(true);
        recoverSan(serverPlayer, DNF.SAN_FOOD_GAIN, "message.dnf.task.food");
        tryCompleteMealTask(serverPlayer, daily);
        daily.sync();
        KEY.sync(serverPlayer);
    }

    public void startDnfNight(ServerPlayer serverPlayer, int day) {
        knifeNightDay = day;
        knifeUsesTonight = 0;
        crowbarNightDay = day;
        crowbarUsedTonight = false;
        if (pendingAidType != 0 && pendingAidDay <= day) {
            if (pendingAidType == 1) {
                giveOrDrop(serverPlayer, DNFItems.LOCKPICK.getDefaultInstance());
                serverPlayer.displayClientMessage(Component.translatable("message.dnf.killer.aid_delivered_lockpick")
                        .withStyle(ChatFormatting.RED), false);
            } else if (pendingAidType == 2) {
                giveOrDrop(serverPlayer, ModItems.ONCE_REVOLVER.getDefaultInstance());
                serverPlayer.displayClientMessage(Component.translatable("message.dnf.killer.aid_delivered_gun")
                        .withStyle(ChatFormatting.RED), false);
            }
            pendingAidType = 0;
            pendingAidDay = -1;
        }
        KEY.sync(serverPlayer);
    }

    public void markDrankWater(ServerPlayer serverPlayer) {
        DNFDailyTaskComponent daily = daily();
        if (daily.isDrankToday()) {
            return;
        }
        daily.setDrankToday(true);
        recoverSan(serverPlayer, DNF.SAN_WATER_GAIN, "message.dnf.task.water");
        tryCompleteMealTask(serverPlayer, daily);
        daily.sync();
    }

    public boolean cleanLibraryWeb(ServerPlayer serverPlayer) {
        return beginCleaningTask(serverPlayer);
    }

    public boolean cleanPrisonDust(ServerPlayer serverPlayer) {
        return beginCleaningTask(serverPlayer);
    }

    public boolean beginCleaningTask(ServerPlayer serverPlayer) {
        DNFDailyTaskComponent daily = daily();
        if (daily.getCleaningTasksToday() >= DNF.MAX_DAILY_CLEANING_TASKS) {
            serverPlayer.displayClientMessage(Component.translatable("message.dnf.task.cleaning_exhausted")
                    .withStyle(ChatFormatting.YELLOW), true);
            removeHudTask(SREPlayerTaskComponent.Task.DNF_LIBRARY_WEB);
            removeHudTask(SREPlayerTaskComponent.Task.DNF_PRISON_DUST);
            return false;
        }
        if (daily.isCleaningInProgress()) {
            serverPlayer.displayClientMessage(Component.translatable("message.dnf.task.cleaning_busy")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        daily.setCleaningInProgress(true);
        daily.setCleaningTasksToday(daily.getCleaningTasksToday() + 1);
        serverPlayer.displayClientMessage(Component.translatable("message.dnf.task.cleaning_started",
                DNF.CLEANING_TICKS / 20, daily.getCleaningTasksToday(), DNF.MAX_DAILY_CLEANING_TASKS)
                .withStyle(ChatFormatting.GREEN), true);
        daily.sync();
        return true;
    }

    public void finishCleaningTask(ServerPlayer serverPlayer, SREPlayerTaskComponent.Task taskType, String messageKey) {
        DNFDailyTaskComponent daily = daily();
        if (!daily.isCleaningInProgress()) {
            return;
        }
        daily.setCleaningInProgress(false);
        if (taskType == SREPlayerTaskComponent.Task.DNF_LIBRARY_WEB) {
            daily.setWebCleanedToday(true);
        } else if (taskType == SREPlayerTaskComponent.Task.DNF_PRISON_DUST) {
            daily.setDustCleanedToday(true);
        }
        recoverSan(serverPlayer, DNF.SAN_CLEANING_GAIN, messageKey);
        giveOrDrop(serverPlayer, DNFItems.CLEANING_BYPRODUCT.getDefaultInstance());
        serverPlayer.displayClientMessage(Component.translatable("message.dnf.task.cleaning_reward")
                .withStyle(ChatFormatting.GREEN), true);
        if (daily.getCleaningTasksToday() >= DNF.MAX_DAILY_CLEANING_TASKS) {
            removeHudTask(SREPlayerTaskComponent.Task.DNF_LIBRARY_WEB);
            removeHudTask(SREPlayerTaskComponent.Task.DNF_PRISON_DUST);
            serverPlayer.displayClientMessage(Component.translatable("message.dnf.task.cleaning_exhausted")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        daily.sync();
    }

    public boolean completeToilet(ServerPlayer serverPlayer) {
        DNFDailyTaskComponent daily = daily();
        if (daily.isToiletToday()) {
            return false;
        }
        if (!completeSanTask(serverPlayer, daily, "message.dnf.task.toilet", DNF.SAN_CLEANING_GAIN)) {
            return false;
        }
        daily.setToiletToday(true);
        removeHudTask(SREPlayerTaskComponent.Task.DNF_TOILET);
        daily.sync();
        return true;
    }

    public boolean completeLecture(ServerPlayer serverPlayer) {
        return completeChat(serverPlayer, null);
    }

    public boolean completeChat(ServerPlayer serverPlayer, ServerPlayer target) {
        DNFDailyTaskComponent daily = daily();
        if (daily.isChatToday()) {
            return false;
        }
        recoverSan(serverPlayer, DNF.SAN_CHAT_GAIN,
                target == null ? "message.dnf.task.lecture" : "message.dnf.task.chat");
        daily.setChatToday(true);
        daily.setLectureToday(true);
        removeHudTask(SREPlayerTaskComponent.Task.DNF_LECTURE);
        daily.sync();
        if (target != null) {
            target.displayClientMessage(Component.translatable("message.dnf.task.chat_target",
                    serverPlayer.getDisplayName()).withStyle(ChatFormatting.GREEN), true);
        }
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
        if (completeSanTask(serverPlayer, daily, "message.dnf.task.chef_work", DNF.SAN_CLEANING_GAIN)) {
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
        DNFWorldComponent.KEY.get(serverPlayer.serverLevel()).clearWaterPoison();
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
        if (DNFItems.seedInitialFood(serverPlayer)) {
            daily.setChefInitialFoodSeeded(true);
            daily.sync();
        }
    }

    private void giveChefDailySupplies(ServerPlayer serverPlayer) {
        giveOrDrop(serverPlayer, new ItemStack(DNFItems.CORNMEAL_BAG));
        giveOrDrop(serverPlayer, new ItemStack(DNFItems.FLOUR_BAG));
        giveOrDrop(serverPlayer, new ItemStack(DNFItems.SUSPICIOUS_MEAT));
        giveOrDrop(serverPlayer, DNFItems.createWaterBottle(serverPlayer, 2));
        serverPlayer.displayClientMessage(Component.translatable("message.dnf.chef.daily_supplies")
                .withStyle(ChatFormatting.DARK_GREEN), false);
    }

    private void tryCompleteMealTask(ServerPlayer serverPlayer, DNFDailyTaskComponent daily) {
        if (!daily.isMealTaskCompleted() && daily.isAteToday() && daily.isDrankToday()) {
            daily.setMealTaskCompleted(true);
            ServerPlayNetworking.send(serverPlayer, new TaskCompletePayload());
            serverPlayer.displayClientMessage(Component.translatable("message.dnf.task.meal")
                    .withStyle(ChatFormatting.GREEN), true);
            removeHudTask(SREPlayerTaskComponent.Task.DNF_MEAL);
        }
    }

    private boolean completeSanTask(ServerPlayer serverPlayer, DNFDailyTaskComponent daily, String messageKey,
            float sanGain) {
        if (!daily.isAteToday()) {
            serverPlayer.displayClientMessage(Component.translatable("message.dnf.task.need_food")
                    .withStyle(ChatFormatting.YELLOW), true);
            return false;
        }
        recoverSan(serverPlayer, sanGain, messageKey);
        return true;
    }

    private void recoverSan(ServerPlayer serverPlayer, float sanGain, String messageKey) {
        SREPlayerMoodComponent.KEY.get(serverPlayer).addMood(sanGain);
        ServerPlayNetworking.send(serverPlayer, new TaskCompletePayload());
        serverPlayer.displayClientMessage(Component.translatable(messageKey,
                (int) (sanGain * 100)).withStyle(ChatFormatting.GREEN), true);
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

    public int getKilledPlayers() {
        return killedPlayers;
    }

    public void recordKill(ServerPlayer killer) {
        killedPlayers++;
        if (DNF.isDNFKiller(killer)) {
            aidReady = true;
            killer.displayClientMessage(Component.translatable("message.dnf.killer.aid_ready")
                    .withStyle(ChatFormatting.DARK_RED), true);
        }
        KEY.sync(killer);
    }

    public void recordPoisonKill() {
        poisonKills++;
        KEY.sync(player);
    }

    public void giveToxicHeart(ServerPlayer serverPlayer) {
        giveOrDrop(serverPlayer, DNFItems.TOXIC_HEART.getDefaultInstance());
        serverPlayer.displayClientMessage(Component.translatable("message.dnf.poisoner.toxic_heart",
                poisonKills).withStyle(ChatFormatting.DARK_GREEN), true);
    }

    public int getPoisonKills() {
        return poisonKills;
    }

    public boolean hasChefDiaryFound() {
        return chefDiaryFound;
    }

    public void markChefDiaryFound(ServerPlayer serverPlayer) {
        if (chefDiaryFound) {
            serverPlayer.displayClientMessage(Component.translatable("message.dnf.chef.diary_already_found")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        chefDiaryFound = true;
        giveOrDrop(serverPlayer, DNFItems.OLD_CHEF_DIARY.getDefaultInstance());
        serverPlayer.displayClientMessage(Component.translatable("message.dnf.chef.diary_found")
                .withStyle(ChatFormatting.DARK_GREEN), false);
        KEY.sync(serverPlayer);
    }

    public boolean isRedemptionRecipeUnlocked() {
        return redemptionRecipeUnlocked;
    }

    public boolean isRedemptionPotionCrafted() {
        return redemptionPotionCrafted;
    }

    public void unlockRedemptionRecipe(ServerPlayer self, ServerPlayer partner) {
        redemptionRecipeUnlocked = true;
        redemptionPartner = partner.getUUID();
        giveOrDrop(self, DNFItems.REDEMPTION_FORMULA.getDefaultInstance());
        self.displayClientMessage(Component.translatable("message.dnf.redemption.unlocked", partner.getDisplayName())
                .withStyle(ChatFormatting.DARK_GREEN), false);
        KEY.sync(self);
    }

    public ServerPlayer getRedemptionPartner(ServerLevel level) {
        if (redemptionPartner == null || !(level.getPlayerByUUID(redemptionPartner) instanceof ServerPlayer partner)) {
            return null;
        }
        return partner;
    }

    public void markRedemptionPotionCrafted(ServerPlayer serverPlayer) {
        redemptionPotionCrafted = true;
        KEY.sync(serverPlayer);
    }

    public boolean hasJoinedMeeting() {
        return joinedMeeting;
    }

    public void setJoinedMeeting(boolean joined, ServerPlayer player) {
        joinedMeeting = joined;
        if (player instanceof ServerPlayer serverPlayer) {
            KEY.sync(serverPlayer);
        }
    }

    public boolean craftPoisonedFood(ServerPlayer serverPlayer, int day, ItemStack stack) {
        if (poisonCraftDay != day) {
            poisonCraftDay = day;
            poisonCraftedToday = 0;
        }
        int limit = day == 0 ? 5 : 1;
        if (poisonCraftedToday >= limit) {
            serverPlayer.displayClientMessage(Component.translatable("message.dnf.poisoner.food_limit", limit)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        poisonCraftedToday++;
        stack.set(io.wifi.starrailexpress.index.SREDataComponentTypes.POISONER, serverPlayer.getUUID().toString());
        serverPlayer.displayClientMessage(Component.translatable("message.dnf.poisoner.food_made",
                poisonCraftedToday, limit).withStyle(ChatFormatting.DARK_GREEN), true);
        KEY.sync(serverPlayer);
        return true;
    }

    public boolean canUseKnife(ServerPlayer serverPlayer) {
        if (serverPlayer.getCooldowns().isOnCooldown(io.wifi.starrailexpress.index.TMMItems.KNIFE)) {
            serverPlayer.displayClientMessage(Component.translatable("message.dnf.killer.knife_cooldown")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }

    public void consumeKnifeUse(ServerPlayer serverPlayer) {
        knifeUsesTonight++;
        KEY.sync(serverPlayer);
    }

    public boolean tryUseCrowbar(ServerPlayer serverPlayer) {
        if (serverPlayer.getCooldowns().isOnCooldown(DNFItems.CROWBAR)) {
            serverPlayer.displayClientMessage(Component.translatable("message.dnf.killer.crowbar_cooldown")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        crowbarUsedTonight = true;
        KEY.sync(serverPlayer);
        return true;
    }

    public void requestAid(ServerPlayer serverPlayer, boolean lockpick) {
        if (!aidReady) {
            serverPlayer.displayClientMessage(Component.translatable("message.dnf.killer.aid_not_ready")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        DNFWorldComponent world = DNFWorldComponent.KEY.get(serverPlayer.serverLevel());
        pendingAidType = lockpick ? 1 : 2;
        pendingAidDay = world.getCurrentDay() + 1;
        aidReady = false;
        serverPlayer.displayClientMessage(Component.translatable(lockpick
                ? "message.dnf.killer.aid_requested_lockpick"
                : "message.dnf.killer.aid_requested_gun").withStyle(ChatFormatting.DARK_RED), false);
        KEY.sync(serverPlayer);
    }

    public boolean consumeSoldierShot(ServerPlayer serverPlayer) {
        int day = DNFWorldComponent.KEY.get(serverPlayer.serverLevel()).getCurrentDay();
        if (soldierShotDay != day) {
            soldierShotDay = day;
            soldierShotsToday = 0;
        }
        if (soldierShotsToday >= 1) {
            serverPlayer.displayClientMessage(Component.translatable("message.dnf.soldier.no_bullet")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        soldierShotsToday++;
        KEY.sync(serverPlayer);
        return true;
    }

    public boolean spendSan(ServerPlayer serverPlayer, float amount, String failKey) {
        SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(serverPlayer);
        if (mood.getMood() + 0.0001f < amount) {
            serverPlayer.displayClientMessage(Component.translatable(failKey, (int) (amount * 100))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        mood.setMood(mood.getMood() - amount);
        return true;
    }

    public boolean hasSafeRoomSan(ServerPlayer serverPlayer) {
        return SREPlayerMoodComponent.KEY.get(serverPlayer).getMood() >= DNF.SAN_ROOM_INSPECT_THRESHOLD;
    }

    public void stunManiac(ServerPlayer serverPlayer) {
        maniacStunned = true;
        serverPlayer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DNF.NIGHT_TICKS, 20, false, false));
        serverPlayer.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 8 * 20, 0, false, false));
        serverPlayer.setGameMode(GameType.SPECTATOR);
        serverPlayer.displayClientMessage(Component.translatable("message.dnf.maniac.stunned")
                .withStyle(ChatFormatting.DARK_PURPLE), false);
        KEY.sync(serverPlayer);
    }

    public boolean isManiacStunned() {
        return maniacStunned;
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
        writeRuntimeState(tag);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        readRuntimeState(tag);
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeRuntimeState(tag);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        readRuntimeState(tag);
    }

    private void writeRuntimeState(CompoundTag tag) {
        tag.putInt("KilledPlayers", killedPlayers);
        tag.putInt("PoisonKills", poisonKills);
        tag.putInt("PoisonCraftDay", poisonCraftDay);
        tag.putInt("PoisonCraftedToday", poisonCraftedToday);
        tag.putInt("KnifeNightDay", knifeNightDay);
        tag.putInt("KnifeUsesTonight", knifeUsesTonight);
        tag.putInt("CrowbarNightDay", crowbarNightDay);
        tag.putBoolean("CrowbarUsedTonight", crowbarUsedTonight);
        tag.putBoolean("AidReady", aidReady);
        tag.putInt("PendingAidType", pendingAidType);
        tag.putInt("PendingAidDay", pendingAidDay);
        tag.putInt("SoldierShotDay", soldierShotDay);
        tag.putInt("SoldierShotsToday", soldierShotsToday);
        tag.putBoolean("ManiacStunned", maniacStunned);
        tag.putBoolean("ChefDiaryFound", chefDiaryFound);
        tag.putBoolean("RedemptionRecipeUnlocked", redemptionRecipeUnlocked);
        if (redemptionPartner != null) {
            tag.putUUID("RedemptionPartner", redemptionPartner);
        }
        tag.putBoolean("RedemptionPotionCrafted", redemptionPotionCrafted);
    }

    private void readRuntimeState(CompoundTag tag) {
        killedPlayers = tag.getInt("KilledPlayers");
        poisonKills = tag.getInt("PoisonKills");
        poisonCraftDay = tag.getInt("PoisonCraftDay");
        poisonCraftedToday = tag.getInt("PoisonCraftedToday");
        knifeNightDay = tag.getInt("KnifeNightDay");
        knifeUsesTonight = tag.getInt("KnifeUsesTonight");
        crowbarNightDay = tag.getInt("CrowbarNightDay");
        crowbarUsedTonight = tag.getBoolean("CrowbarUsedTonight");
        aidReady = tag.getBoolean("AidReady");
        pendingAidType = tag.getInt("PendingAidType");
        pendingAidDay = tag.getInt("PendingAidDay");
        soldierShotDay = tag.getInt("SoldierShotDay");
        soldierShotsToday = tag.getInt("SoldierShotsToday");
        maniacStunned = tag.getBoolean("ManiacStunned");
        chefDiaryFound = tag.getBoolean("ChefDiaryFound");
        redemptionRecipeUnlocked = tag.getBoolean("RedemptionRecipeUnlocked");
        redemptionPartner = tag.contains("RedemptionPartner") ? tag.getUUID("RedemptionPartner") : null;
        redemptionPotionCrafted = tag.getBoolean("RedemptionPotionCrafted");
    }
}
