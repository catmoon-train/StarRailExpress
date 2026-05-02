package io.wifi.events.day_night_fight;

import io.wifi.events.day_night_fight.block.DNFBlocks;
import io.wifi.events.day_night_fight.cca.DNFClothingComponent;
import io.wifi.events.day_night_fight.cca.DNFPlayerComponent;
import io.wifi.events.day_night_fight.cca.DNFUnderworldComponent;
import io.wifi.events.day_night_fight.cca.DNFWorldComponent;
import io.wifi.events.day_night_fight.entity.DNFEntities;
import io.wifi.events.day_night_fight.commands.ClueSystemCommand;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.content.vote.VoteManager;
import io.wifi.starrailexpress.content.vote.VoteOption;
import io.wifi.starrailexpress.cca.SRETrainWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.content.block.ToiletBlock;
import io.wifi.starrailexpress.content.vote.VoteSession;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.SREItemUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands.CommandSelection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.roles.neutral.vulture.VulturePlayerComponent;
import org.agmas.noellesroles.utils.RoleUtils;

import com.mojang.brigadier.CommandDispatcher;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

public class DNF {
    public static final int BLOOD_PER_CORPSE = 10;
    public static final int BLOOD_PRICE = 10;
    public static final int KNIFE_COOLDOWN_TICKS = 50 * 20;
    public static final int CROWBAR_COOLDOWN_TICKS = 300 * 20;
    public static final int FIRST_DAYLIGHT_TICKS = 2 * 60 * 20;
    public static final int DAYLIGHT_TICKS = 5 * 60 * 20;
    public static final int NIGHT_TICKS = 5 * 60 * 20;
    public static final int TWO_DAYS_TICKS = 2 * (DAYLIGHT_TICKS + NIGHT_TICKS);
    public static final int CLEANING_TICKS = 8 * 20;
    public static final int MAX_DAILY_CLEANING_TASKS = 3;
    public static final float INITIAL_SAN = 0.5f;
    public static final float MORNING_SAN = 0.3f;
    public static final float SAN_CLEANING_GAIN = 0.1f;
    public static final float SAN_CHAT_GAIN = 0.2f;
    public static final float SAN_FOOD_GAIN = 0.3f;
    public static final float SAN_WATER_GAIN = 0.1f;
    public static final float SAN_REPORT_COST = 0.2f;
    public static final float SAN_ROOM_INSPECT_THRESHOLD = 0.8f;
    public static final float SAN_TASK_MOOD_GAIN = SAN_CLEANING_GAIN;
    public static final int CHEF_DAILY_FOOD_CAPACITY = 40;
    public static final int CHEF_WATER_CHECK_COST = 10;
    public static final int CHEF_RECIPE_OUTPUT = 10;
    public static final int INITIAL_CAFETERIA_FOOD = 50;
    public static final int CLOTHES_DAILY_DIRT_DAMAGE = 12;
    public static final float CLOTHES_DIRTY_THRESHOLD = 0.6f;
    public static final float SAN_NO_CLOTHES_PENALTY = 0.8f;
    public static final float SAN_DIRTY_CLOTHES_PENALTY = 0.6f;
    public static final int REDEMPTION_HEART_COST = 5;

    private static boolean eventsRegistered;
    private static boolean winnerPredicatesRegistered;

    public static void init() {
        DNFBlocks.initialize();
        DNFItems.init();
        DNFEntities.initialize();
        TMMRoles.addRoleComponents(DNFPlayerComponent.KEY);
        TMMRoles.addRoleComponents(DNFClothingComponent.KEY);
        TMMRoles.addRoleComponents(DNFUnderworldComponent.KEY);
        registerEvents();
        registerShops();
        registerWinnerPredicates();
        DNFDebugCommand.register();
        DNFRuleBookCommand.register();
    }

    private static void registerWinnerPredicates() {
        if (winnerPredicatesRegistered) {
            return;
        }
        winnerPredicatesRegistered = true;
        GameUtils.CustomWinnersPredicates.add(entry -> {
            if (!(entry.getKey() instanceof ServerPlayer player) || entry.getValue() == null) {
                return false;
            }
            SRERole role = SREGameWorldComponent.KEY.get(player.level()).getRole(player);
            return switch (entry.getValue()) {
                case "dnf_true" -> isDnfAlive(player) && !isDNFManiac(role);
                case "dnf_survival" -> isDnfAlive(player) && !isDNFAntagonist(role);
                case "dnf_wipeout" -> isDnfAlive(player) && isDNFAntagonist(role);
                case "dnf_redemption" -> isDnfAlive(player)
                        && DNFPlayerComponent.KEY.get(player).isRedemptionPotionCrafted();
                default -> false;
            };
        });
    }

    public static boolean isDNFKiller(SRERole role) {
        return role != null && role.identifier().equals(DNFRoles.KILLER_ID);
    }

    public static boolean isDNFKiller(Player player) {
        return player != null && isDNFKiller(SREGameWorldComponent.KEY.get(player.level()).getRole(player));
    }

    public static boolean isDNFLocksmith(Player player) {
        return player != null && SREGameWorldComponent.KEY.get(player.level()).isRole(player, DNFRoles.LOCKSMITH);
    }

    public static boolean isDNFChef(Player player) {
        return player != null && SREGameWorldComponent.KEY.get(player.level()).isRole(player, DNFRoles.CHEF);
    }

    public static boolean isDNFManiac(SRERole role) {
        return role != null && role.identifier().equals(DNFRoles.MANIAC_ID);
    }

    public static boolean isDNFManiac(Player player) {
        return player != null && isDNFManiac(SREGameWorldComponent.KEY.get(player.level()).getRole(player));
    }

    public static boolean isDNFPoisoner(SRERole role) {
        return role != null && role.identifier().equals(DNFRoles.POISONER_ID);
    }

    public static boolean isDNFPoisoner(Player player) {
        return player != null && isDNFPoisoner(SREGameWorldComponent.KEY.get(player.level()).getRole(player));
    }

    public static boolean isDNFAntagonist(SRERole role) {
        return isDNFKiller(role) || isDNFPoisoner(role) || isDNFManiac(role);
    }

    public static boolean isDnfAlive(ServerPlayer player) {
        if (player == null || player.isCreative()) {
            return false;
        }
        if (isDNFManiac(player)) {
            return player.isAlive();
        }
        return GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player);
    }

    public static boolean isDayNightFightMode(Level world) {
        return world != null && SREGameWorldComponent.KEY.get(world).getGameMode().identifier
                .equals(io.wifi.starrailexpress.api.SREGameModes.DAY_NIGHT_FIGHT_ID);
    }

    public static boolean isNight(Player player) {
        if (player == null) {
            return false;
        }
        SRETrainWorldComponent.TimeOfDay timeOfDay = SRETrainWorldComponent.KEY.get(player.level()).getTimeOfDay();
        return player.level().isNight()
                || timeOfDay == SRETrainWorldComponent.TimeOfDay.NIGHT
                || timeOfDay == SRETrainWorldComponent.TimeOfDay.MIDNIGHT
                || timeOfDay == SRETrainWorldComponent.TimeOfDay.SUNDOWN;
    }

    public static void applyKnifeCooldown(ServerPlayer player) {
        if (isDNFKiller(player)) {
            player.getCooldowns().addCooldown(TMMItems.KNIFE, KNIFE_COOLDOWN_TICKS);
        }
    }

    public static InteractionResult eatBody(ServerPlayer player, PlayerBodyEntity body) {
        DNFPlayerComponent component = DNFPlayerComponent.KEY.get(player);
        ResourceLocation bodyRole = PlayerBodyEntityComponent.KEY.get(body).playerRole;
        PlayerBodyEntityComponent playerBodyEntityComponent = PlayerBodyEntityComponent.KEY.get(body);
        if (playerBodyEntityComponent.vultured){
            player.displayClientMessage(Component.translatable("message.dnf.killer.eaten"),true);
            return InteractionResult.FAIL;

        }
        component.eatCorpse(player, bodyRole, BLOOD_PER_CORPSE);
        playerBodyEntityComponent.vultured = true;
        playerBodyEntityComponent.sync();
        player.level().playSound(null, body.blockPosition(), SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 0.8f, 0.55f);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1, false, false, false));
        if (component.hasPersonalEnding()) {
            player.displayClientMessage(Component.translatable("message.dnf.killer.ending")
                    .withStyle(ChatFormatting.DARK_PURPLE), false);
            if (player.serverLevel().getGameTime() % 10 == 0) {
                player.displayClientMessage(Component.translatable("game.win.star.dnf_killer")
                        .withStyle(ChatFormatting.DARK_PURPLE), true);
            }
        } else {
            player.displayClientMessage(Component.translatable("message.dnf.killer.blood",
                    component.getBlood(), component.getBodiesEaten()).withStyle(ChatFormatting.RED), true);
        }
        return InteractionResult.SUCCESS;
    }

    public static void exchangeBlood(ServerPlayer player, boolean lockpick) {
        DNFPlayerComponent component = DNFPlayerComponent.KEY.get(player);
        if (!component.spendBlood(BLOOD_PRICE)) {
            player.displayClientMessage(Component.translatable("message.dnf.killer.not_enough_blood", BLOOD_PRICE)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        ItemStack stack = lockpick ? DNFItems.LOCKPICK.getDefaultInstance()
                : DNFItems.FLYING_KNIFE.getDefaultInstance();
        if (!player.addItem(stack)) {
            player.drop(stack, false);
        }
        player.displayClientMessage(Component.translatable(lockpick
                ? "message.dnf.killer.exchange_lockpick"
                : "message.dnf.killer.exchange_flying_knife", component.getBlood()).withStyle(ChatFormatting.RED),
                true);
    }

    public static void updateNightTools(ServerPlayer player) {
        if (isDNFKiller(player) && isNight(player)) {
            equipNightTools(player);
        } else {
            removeNightTools(player);
        }
    }

    public static void equipNightTools(ServerPlayer player) {
        ensureHas(player, TMMItems.KNIFE.getDefaultInstance());
        ensureHas(player, DNFItems.CROWBAR.getDefaultInstance());
    }

    public static void removeNightTools(ServerPlayer player) {
        removeAll(player, TMMItems.KNIFE);
        removeAll(player, DNFItems.CROWBAR);
    }

    public static void ensureHas(ServerPlayer player, ItemStack stack) {
        if (SREItemUtils.hasItem(player, stack.getItem())) {
            return;
        }
        if (!player.addItem(stack.copy())) {
            player.drop(stack.copy(), false);
        }
    }

    public static void removeAll(ServerPlayer player, net.minecraft.world.item.Item item) {
        for (List<ItemStack> compartment : player.getInventory().compartments) {
            for (int i = 0; i < compartment.size(); i++) {
                if (compartment.get(i).is(item)) {
                    compartment.set(i, ItemStack.EMPTY);
                }
            }
        }
    }

    public static void applyPhaseState(ServerPlayer player, boolean night) {
        if (!isDayNightFightMode(player.level()) || !isDNFManiac(player)) {
            return;
        }
        DNFPlayerComponent component = DNFPlayerComponent.KEY.get(player);
        if (night && !component.isManiacStunned()) {
            if (player.isSpectator()) {
                player.setGameMode(GameType.ADVENTURE);
            }
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1, false, false));
            player.displayClientMessage(Component.translatable("message.dnf.maniac.night")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
        } else {
            player.setGameMode(GameType.SPECTATOR);
            player.displayClientMessage(Component.translatable("message.dnf.maniac.day")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
        }
    }

    public static void applyManiacTick(ServerPlayer player) {
        if (!isDNFManiac(player)) {
            return;
        }
        DNFPlayerComponent component = DNFPlayerComponent.KEY.get(player);
        if (isNight(player) && !component.isManiacStunned()) {
            if (player.isSpectator()) {
                player.setGameMode(GameType.ADVENTURE);
            }
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 45, 1, false, false));
        } else if (!isNight(player) && !player.isSpectator()) {
            player.setGameMode(GameType.SPECTATOR);
        }
    }

    public static boolean isTargetProtectedFromManiac(ServerPlayer target) {
        DNFWorldComponent world = DNFWorldComponent.KEY.get(target.serverLevel());
        AABB cafeteria = world.getCafeteriaArea();
        if (cafeteria != null && cafeteria.contains(target.position())) {
            return true;
        }
        if (!DNFPlayerComponent.KEY.get(target).hasSafeRoomSan(target)) {
            return false;
        }
        AABB dormRoom = world.getDormRoom(target.getUUID());
        if (dormRoom != null) {
            return dormRoom.contains(target.position());
        }
        Integer roomNumber = GameUtils.roomToPlayer.get(target.getUUID());
        if (roomNumber == null) {
            return false;
        }
        Vec3 roomCenter = AreasWorldComponent.KEY.get(target.level()).getRoomPosition(roomNumber);
        return roomCenter != null && target.position().distanceToSqr(roomCenter) <= 4.0 * 4.0;
    }

    /**
     * 发送玩家到里世界
     * 玩家死亡时调用,进入里世界而不是真正死亡
     */
    public static void sendPlayerToUnderworld(ServerPlayer player) {
        if (player == null || player.level() == null) return;

        DNFUnderworldComponent.KEY.get(player).enterUnderworld();
    }

    /**
     * 在里世界生成怪物
     */
    public static void spawnUnderworldMonster(net.minecraft.server.level.ServerLevel serverLevel, ServerPlayer player) {
        // 在玩家附近生成怪物
        double angle = Math.random() * Math.PI * 2;
        double distance = 15 + Math.random() * 10; // 15-25格距离
        
        int x = (int)(player.getX() + Math.cos(angle) * distance);
        int y = (int)player.getY();
        int z = (int)(player.getZ() + Math.sin(angle) * distance);
        
        BlockPos spawnPos = new BlockPos(x, y, z);
        BlockPos groundPos = serverLevel.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, spawnPos);
        
        io.wifi.events.day_night_fight.entity.UnderworldMonsterEntity monster = 
                io.wifi.events.day_night_fight.entity.DNFEntities.UNDERWORLD_MONSTER.create(serverLevel);
        
        if (monster != null) {
            monster.moveTo(groundPos.getX() + 0.5, groundPos.getY(), groundPos.getZ() + 0.5, 0, 0);
            serverLevel.addFreshEntity(monster);
        }
    }

    public static boolean tryPoisonerAbility(ServerPlayer player) {
        if (!isDNFPoisoner(player)) {
            return false;
        }
        if (!isNight(player)) {
            player.displayClientMessage(Component.translatable("message.dnf.poisoner.night_only")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        DNFWorldComponent world = DNFWorldComponent.KEY.get(player.serverLevel());
        if (player.isShiftKeyDown()) {
            if (DNFPlayerComponent.KEY.get(player).getPoisonKills() < 5) {
                player.displayClientMessage(Component.translatable("message.dnf.poisoner.water_locked")
                        .withStyle(ChatFormatting.RED), true);
                return false;
            }
            BlockHitResult hit = findLookedAtBlock(player, 6.0);
            if (hit == null || !world.isWaterSource(hit.getBlockPos())) {
                player.displayClientMessage(Component.translatable("message.dnf.poisoner.water_no_target")
                        .withStyle(ChatFormatting.GRAY), true);
                return false;
            }
            world.poisonWaterTomorrow(player.getUUID());
            player.displayClientMessage(Component.translatable("message.dnf.poisoner.water_poisoned")
                    .withStyle(ChatFormatting.DARK_GREEN), false);
            return true;
        }

        ItemStack held = player.getMainHandItem();
        if (!DNFItems.isDnfFood(held)) {
            player.displayClientMessage(Component.translatable("message.dnf.poisoner.need_food")
                    .withStyle(ChatFormatting.YELLOW), true);
            return false;
        }
        if (DNFItems.isContaminated(held)) {
            player.displayClientMessage(Component.translatable("message.dnf.poisoner.already_poisoned")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        ItemStack poisoned = held.copy();
        poisoned.setCount(1);
        if (!DNFPlayerComponent.KEY.get(player).craftPoisonedFood(player, world.getCurrentDay(), poisoned)) {
            return false;
        }
        if (!player.isCreative()) {
            held.shrink(1);
        }
        DNFItems.giveOrDrop(player, poisoned);
        return true;
    }

    public static boolean inspectFoodBox(ServerPlayer player) {
        BlockHitResult hit = findLookedAtBlock(player, 6.0);
        if (hit == null) {
            return false;
        }
        DNFWorldComponent world = DNFWorldComponent.KEY.get(player.serverLevel());
        if (!world.isFoodBox(hit.getBlockPos())) {
            return false;
        }
        Container container = world.getFoodBoxContainer();
        if (container == null) {
            return false;
        }
        int contaminated = DNFItems.countContaminated(container);
        player.displayClientMessage(Component.translatable("message.dnf.chef.food_box_checked", contaminated)
                .withStyle(contaminated > 0 ? ChatFormatting.RED : ChatFormatting.GREEN), false);
        return true;
    }

    public static boolean tryPoisonerMeetChef(ServerPlayer poisoner, ServerPlayer chef) {
        if (!isDNFPoisoner(poisoner) || !isDNFChef(chef)) {
            return false;
        }
        DNFPlayerComponent chefComponent = DNFPlayerComponent.KEY.get(chef);
        if (!chefComponent.hasChefDiaryFound()) {
            poisoner.displayClientMessage(Component.translatable("message.dnf.redemption.chef_no_diary")
                    .withStyle(ChatFormatting.YELLOW), true);
            return true;
        }
        if (!hasItem(poisoner, DNFItems.TOXIC_HEART, REDEMPTION_HEART_COST)) {
            poisoner.displayClientMessage(Component.translatable("message.dnf.redemption.need_hearts",
                    REDEMPTION_HEART_COST).withStyle(ChatFormatting.RED), true);
            return true;
        }
        DNFPlayerComponent poisonerComponent = DNFPlayerComponent.KEY.get(poisoner);
        if (poisonerComponent.isRedemptionRecipeUnlocked() && chefComponent.isRedemptionRecipeUnlocked()) {
            poisoner.displayClientMessage(Component.translatable("message.dnf.redemption.already_unlocked")
                    .withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        consumeItem(poisoner, DNFItems.TOXIC_HEART, REDEMPTION_HEART_COST);
        poisonerComponent.unlockRedemptionRecipe(poisoner, chef);
        chefComponent.unlockRedemptionRecipe(chef, poisoner);
        chef.displayClientMessage(Component.translatable("message.dnf.redemption.partner_met",
                poisoner.getDisplayName()).withStyle(ChatFormatting.DARK_GREEN), false);
        return true;
    }

    public static ServerPlayer findLookedAtPlayer(ServerPlayer player, double range) {
        HitResult result = ProjectileUtil.getHitResultOnViewVector(player,
                entity -> entity instanceof ServerPlayer target && target != player && isDnfAlive(target), range);
        if (result instanceof EntityHitResult entityHitResult
                && entityHitResult.getEntity() instanceof ServerPlayer target) {
            return target;
        }
        return null;
    }

    public static BlockHitResult findLookedAtBlock(ServerPlayer player, double range) {
        HitResult result = player.pick(range, 0.0f, false);
        return result instanceof BlockHitResult blockHitResult ? blockHitResult : null;
    }

    private static void registerEvents() {
        if (eventsRegistered) {
            return;
        }
        eventsRegistered = true;
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide || !(player instanceof ServerPlayer serverPlayer)
                    || !isDayNightFightMode(world) || !GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
                return InteractionResult.PASS;
            }

            BlockState state = world.getBlockState(hitResult.getBlockPos());
            DNFPlayerComponent component = DNFPlayerComponent.KEY.get(serverPlayer);
            DNFWorldComponent dnfWorld = DNFWorldComponent.KEY.get(world);
            BlockPos clickedPos = hitResult.getBlockPos();
            if (dnfWorld.isOldChefDiary(clickedPos)) {
                if (!DNF.isDNFChef(serverPlayer)) {
                    serverPlayer.displayClientMessage(Component.translatable("message.dnf.chef.diary_chef_only")
                            .withStyle(ChatFormatting.YELLOW), true);
                    return InteractionResult.FAIL;
                }
                component.markChefDiaryFound(serverPlayer);
                return InteractionResult.SUCCESS;
            }
            if (dnfWorld.isMeteor(clickedPos)) {
                triggerTrueEnding(serverPlayer.serverLevel());
                return InteractionResult.SUCCESS;
            }
            if (dnfWorld.isFoodBox(clickedPos)) {
                if (DNF.isDNFChef(serverPlayer) && serverPlayer.isShiftKeyDown()) {
                    return inspectFoodBox(serverPlayer) ? InteractionResult.SUCCESS : InteractionResult.PASS;
                }
                ItemStack held = serverPlayer.getItemInHand(hand);
                if (DNF.isDNFPoisoner(serverPlayer) && DNF.isNight(serverPlayer) && DNFItems.isContaminated(held)) {
                    Container container = dnfWorld.getFoodBoxContainer();
                    if (container == null) {
                        serverPlayer.displayClientMessage(Component.translatable("message.dnf.food_box.missing")
                                .withStyle(ChatFormatting.RED), true);
                        return InteractionResult.FAIL;
                    }
                    ItemStack single = held.copy();
                    single.setCount(1);
                    if (DNFWorldComponent.addToContainer(container, single)) {
                        if (!serverPlayer.isCreative()) {
                            held.shrink(1);
                        }
                        serverPlayer.displayClientMessage(Component.translatable("message.dnf.poisoner.food_deposited")
                                .withStyle(ChatFormatting.DARK_GREEN), true);
                        return InteractionResult.SUCCESS;
                    }
                    serverPlayer.displayClientMessage(Component.translatable("message.dnf.food_box.full")
                            .withStyle(ChatFormatting.RED), true);
                    return InteractionResult.FAIL;
                }
                return InteractionResult.PASS;
            }
            if (serverPlayer.isShiftKeyDown() && DNF.isNight(serverPlayer)
                    && (state.is(Blocks.BOOKSHELF) || state.is(Blocks.CHISELED_BOOKSHELF))) {
                ItemStack scraps = new ItemStack(DNFItems.PAPER_SCRAP, 10);
                DNFItems.giveOrDrop(serverPlayer, scraps);
                serverPlayer.getCooldowns().addCooldown(DNFItems.PAPER_SCRAP, 60 * 20);
                serverPlayer.displayClientMessage(Component.translatable("message.dnf.paper.scraps")
                        .withStyle(ChatFormatting.GRAY), true);
                return InteractionResult.SUCCESS;
            }
            if (state.is(Blocks.COBWEB)) {
                if (component.beginCleaningTask(serverPlayer)) {
                    BlockState originalState = state;
                    net.minecraft.core.BlockPos pos = hitResult.getBlockPos().immutable();
                    io.wifi.starrailexpress.util.Scheduler.schedule(() -> {
                        if (!serverPlayer.hasDisconnected()
                                && isDayNightFightMode(serverPlayer.level())
                                && serverPlayer.level().getBlockState(pos).is(originalState.getBlock())) {
                            serverPlayer.level().destroyBlock(pos, false, serverPlayer);
                        }
                        DNFPlayerComponent.KEY.get(serverPlayer).finishCleaningTask(serverPlayer,
                                SREPlayerTaskComponent.Task.DNF_LIBRARY_WEB, "message.dnf.task.library_web");
                    }, CLEANING_TICKS);
                    return InteractionResult.SUCCESS;
                }
                return InteractionResult.PASS;
            }
            if (state.is(Blocks.LECTERN)) {
                return component.completeLecture(serverPlayer) ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            if (state.getBlock() instanceof ToiletBlock) {
                return component.completeToilet(serverPlayer) ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            if (isDustBlock(state)) {
                if (component.beginCleaningTask(serverPlayer)) {
                    BlockState originalState = state;
                    net.minecraft.core.BlockPos pos = hitResult.getBlockPos().immutable();
                    io.wifi.starrailexpress.util.Scheduler.schedule(() -> {
                        if (!serverPlayer.hasDisconnected()
                                && isDayNightFightMode(serverPlayer.level())
                                && serverPlayer.level().getBlockState(pos).is(originalState.getBlock())) {
                            serverPlayer.level().destroyBlock(pos, false, serverPlayer);
                        }
                        DNFPlayerComponent.KEY.get(serverPlayer).finishCleaningTask(serverPlayer,
                                SREPlayerTaskComponent.Task.DNF_PRISON_DUST, "message.dnf.task.prison_dust");
                    }, CLEANING_TICKS);
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide || !(player instanceof ServerPlayer serverPlayer)
                    || !isDayNightFightMode(world) || !GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
                return InteractionResult.PASS;
            }
            if (entity instanceof ServerPlayer target && target != serverPlayer && isDnfAlive(target)) {
                if (tryPoisonerMeetChef(serverPlayer, target)) {
                    return InteractionResult.SUCCESS;
                }
                return DNFPlayerComponent.KEY.get(serverPlayer).completeChat(serverPlayer, target)
                        ? InteractionResult.SUCCESS
                        : InteractionResult.PASS;
            }
            if (!(entity instanceof PlayerBodyEntity)) {
                return InteractionResult.PASS;
            }
            SRERole role = SREGameWorldComponent.KEY.get(world).getRole(serverPlayer);
            if (role == DNFRoles.CHEF && !serverPlayer.isShiftKeyDown()) {
                return InteractionResult.PASS;
            }
            if (isDNFAntagonist(role)) {
                return InteractionResult.PASS;
            }
            return startBodyReportVote(serverPlayer);
        });
    }

    private static InteractionResult startBodyReportVote(ServerPlayer reporter) {
        DNFWorldComponent world = DNFWorldComponent.KEY.get(reporter.serverLevel());
        if (world.isMeetingActive() || VoteManager.getCurrentSession() != null) {
            reporter.displayClientMessage(Component.translatable("message.dnf.vote.already_active")
                    .withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.FAIL;
        }
        List<ServerPlayer> voters = reporter.serverLevel().players().stream()
                .filter(DNF::isDnfAlive)
                .filter(player -> !isDNFManiac(player))
                .toList();
        if (voters.isEmpty()) {
            return InteractionResult.FAIL;
        }
        if (!DNFPlayerComponent.KEY.get(reporter).spendSan(reporter, SAN_REPORT_COST,
                "message.dnf.vote.not_enough_san")) {
            return InteractionResult.FAIL;
        }
        
        // 第一步：询问玩家是否要参加会议
        return startMeetingPreVote(reporter, voters);
    }

    private static InteractionResult startMeetingPreVote(ServerPlayer reporter, List<ServerPlayer> voters) {
        DNFWorldComponent world = DNFWorldComponent.KEY.get(reporter.serverLevel());
        BlockPos meetingPos = world.getMeetingPos();
        
        if (meetingPos == null) {
            // 如果没有配置会议位置，直接开始投票
            return startMainVote(reporter, voters);
        }

        double meetingRadius = world.getMeetingRadius();
        VoteManager.VoteBuilder builder = VoteManager.builder(Component.translatable("message.dnf.vote.meeting_request"))
                .duration(30 * 20)
                .allowReVote(false)
                .showResults(false)
                .syncInterval(20)
                .targetPlayers(voters)
                .callback(session -> {
                    world.setMeetingActive(false);
                    handleMeetingPreVoteResult(reporter, session, voters);
                });
        
        // 添加选项：参加 / 不参加
        builder.addOption(VoteOption.text(Component.translatable("message.dnf.vote.join_meeting"), "join", 
                Component.translatable("message.dnf.vote.join_meeting_desc")));
        builder.addOption(VoteOption.text(Component.translatable("message.dnf.vote.skip_meeting"), "skip",
                Component.translatable("message.dnf.vote.skip_meeting_desc")));
        
        if (builder.start() == null) {
            return InteractionResult.FAIL;
        }
        
        world.setMeetingActive(true);
        reporter.serverLevel().getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.dnf.vote.meeting_initiated", reporter.getDisplayName())
                        .withStyle(ChatFormatting.YELLOW),
                false);
        return InteractionResult.SUCCESS;
    }

    private static void handleMeetingPreVoteResult(ServerPlayer reporter, VoteSession session, List<ServerPlayer> voters) {
        DNFWorldComponent world = DNFWorldComponent.KEY.get(reporter.serverLevel());
        BlockPos meetingPos = world.getMeetingPos();
        double meetingRadius = world.getMeetingRadius();
        
        var top = session.getTopResults();
        if (top == null || top.isEmpty()) {
            reporter.serverLevel().getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("message.dnf.vote.no_response").withStyle(ChatFormatting.GRAY),
                    false);
            return;
        }

        String resultId = top.get(0).getKey();
        List<ServerPlayer> joinedPlayers = new ArrayList<>();
        
        // 传送选择参加的玩家到会议位置
        if ("join".equals(resultId)) {
            for (ServerPlayer voter : voters) {
                DNFPlayerComponent playerComp = DNFPlayerComponent.KEY.get(voter);
                // 传送到会议位置
                voter.teleportTo(
                    voter.serverLevel(),
                    meetingPos.getX() + 0.5,
                    meetingPos.getY() + 1.0,
                    meetingPos.getZ() + 0.5,
                    voter.getYRot(),
                    voter.getXRot()
                );
                playerComp.setJoinedMeeting(true, voter);
                joinedPlayers.add(voter);
                voter.displayClientMessage(Component.translatable("message.dnf.vote.teleported_to_meeting")
                        .withStyle(ChatFormatting.AQUA), false);
            }
            
            reporter.serverLevel().getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("message.dnf.vote.players_joined_meeting", joinedPlayers.size())
                            .withStyle(ChatFormatting.YELLOW),
                    false);
            
            // 稍后启动主投票
            reporter.serverLevel().getServer().execute(() -> {
                if (VoteManager.getCurrentSession() == null) {
                    startMainVote(reporter, joinedPlayers);
                }
            });
        } else {
            reporter.serverLevel().getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("message.dnf.vote.meeting_cancelled")
                            .withStyle(ChatFormatting.GRAY),
                    false);
        }
    }

    private static InteractionResult startMainVote(ServerPlayer reporter, List<ServerPlayer> voters) {
        DNFWorldComponent world = DNFWorldComponent.KEY.get(reporter.serverLevel());
        
        VoteManager.VoteBuilder builder = VoteManager.builder(Component.translatable("message.dnf.vote.title"))
                .duration(60 * 20)
                .allowReVote(true)
                .showResults(true)
                .syncInterval(20)
                .targetPlayers(voters)
                .callback(session -> {
                    world.setMeetingActive(false);
                    var top = session.getTopResults();
                    if (top == null || top.isEmpty() || top.size() != 1 || top.get(0).getValue().count() <= 0) {
                        reporter.serverLevel().getServer().getPlayerList().broadcastSystemMessage(
                                Component.translatable("message.dnf.vote.no_target").withStyle(ChatFormatting.GRAY),
                                false);
                        world.setVotedTarget(null);
                        return;
                    }
                    String resultId = top.get(0).getKey();
                    try {
                        UUID targetId = UUID.fromString(resultId);
                        world.setVotedTarget(targetId);
                        Player playerTarget = reporter.serverLevel().getPlayerByUUID(targetId);
                        if (playerTarget instanceof ServerPlayer target) {
                            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 3 * 60 * 20, 0, false, false));
                            reporter.serverLevel().getServer().getPlayerList().broadcastSystemMessage(
                                    Component.translatable("message.dnf.vote.target", target.getDisplayName())
                                            .withStyle(ChatFormatting.YELLOW),
                                    false);
                        }
                    } catch (IllegalArgumentException ignored) {
                        world.setVotedTarget(null);
                    }
                    
                    // 重置玩家的会议参与状态
                    for (ServerPlayer voter : voters) {
                        DNFPlayerComponent.KEY.get(voter).setJoinedMeeting(false, voter);
                    }
                });
        
        for (ServerPlayer voter : voters) {
            builder.addOption(VoteOption.player(voter, voter.getUUID().toString()));
        }
        
        if (builder.start() == null) {
            return InteractionResult.FAIL;
        }
        
        world.setMeetingActive(true);
        reporter.serverLevel().getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.dnf.vote.reported", reporter.getDisplayName())
                        .withStyle(ChatFormatting.YELLOW),
                false);
        return InteractionResult.SUCCESS;
    }


    private static void triggerTrueEnding(ServerLevel serverLevel) {
        DNFWorldComponent world = DNFWorldComponent.KEY.get(serverLevel);
        if (world.isTrueEndingTriggered()) {
            return;
        }
        world.setTrueEndingTriggered(true);
        SREGameRoundEndComponent roundEnd = SREGameRoundEndComponent.KEY.get(serverLevel);
        roundEnd.CustomWinnerTitle = Component.translatable("game.win.star.dnf_true");
        roundEnd.CustomWinnerSubtitle = Component.translatable("message.dnf.ending.true");
        RoleUtils.customWinnerWin(serverLevel, "dnf_true", 0xA8F0FF);
    }

    public static void triggerRedemptionEnding(ServerLevel serverLevel) {
        SREGameRoundEndComponent roundEnd = SREGameRoundEndComponent.KEY.get(serverLevel);
        roundEnd.CustomWinnerTitle = Component.translatable("game.win.star.dnf_redemption");
        roundEnd.CustomWinnerSubtitle = Component.translatable("message.dnf.ending.redemption");
        RoleUtils.customWinnerWin(serverLevel, "dnf_redemption", 0x3A7F55);
    }

    private static boolean hasItem(ServerPlayer player, net.minecraft.world.item.Item item, int count) {
        int found = 0;
        for (List<ItemStack> compartment : player.getInventory().compartments) {
            for (ItemStack stack : compartment) {
                if (stack.is(item)) {
                    found += stack.getCount();
                    if (found >= count) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void consumeItem(ServerPlayer player, net.minecraft.world.item.Item item, int count) {
        int remaining = count;
        for (List<ItemStack> compartment : player.getInventory().compartments) {
            for (ItemStack stack : compartment) {
                if (!stack.is(item)) {
                    continue;
                }
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
                if (remaining <= 0) {
                    return;
                }
            }
        }
    }

    private static boolean isDustBlock(BlockState state) {
        return state.is(Blocks.GRAY_CARPET)
                || state.is(Blocks.LIGHT_GRAY_CARPET)
                || state.is(Blocks.BLACK_CARPET)
                || state.is(Blocks.GRAY_CONCRETE_POWDER)
                || state.is(Blocks.LIGHT_GRAY_CONCRETE_POWDER);
    }

    public static void registerShops() {
        {
            ArrayList<ShopEntry> shop = new ArrayList<>();
            shop.add(new BloodShopEntry(DNFItems.FLYING_KNIFE.getDefaultInstance(), BLOOD_PRICE,
                    "item.starrailexpress.dnf_flying_knife"));
            shop.add(new BloodShopEntry(DNFItems.LOCKPICK.getDefaultInstance(), BLOOD_PRICE,
                    "item.starrailexpress.dnf_lockpick"));
            ShopContent.customEntries.put(DNFRoles.KILLER_ID, shop);
        }
    }

    private static class BloodShopEntry extends ShopEntry {
        private final int bloodPrice;
        private final String itemNameKey;

        BloodShopEntry(ItemStack stack, int bloodPrice, String itemNameKey) {
            super(stack, 0, Type.TOOL);
            this.bloodPrice = bloodPrice;
            this.itemNameKey = itemNameKey;
        }

        @Override
        public boolean canBuy(Player player) {
            return player instanceof ServerPlayer && DNF.isDNFKiller(player)
                    && DNFPlayerComponent.KEY.get(player).getBlood() >= bloodPrice;
        }

        @Override
        public boolean onBuy(Player player) {
            return player instanceof ServerPlayer serverPlayer
                    && DNFBloodPurchaseItem.buy(serverPlayer, bloodPrice, stack().copy(), itemNameKey);
        }
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext registryAccess, CommandSelection environment) {
        ClueSystemCommand.register(dispatcher);
    }
}
