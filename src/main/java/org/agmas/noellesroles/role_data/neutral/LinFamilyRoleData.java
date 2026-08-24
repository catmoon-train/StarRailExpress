/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    10| * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.role_data.neutral;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.TrainWeapon;
import io.wifi.starrailexpress.content.item.KnifeItem;
import io.wifi.starrailexpress.content.item.component.SREWrittenBookContent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.AllowPlayerPunching;
import io.wifi.starrailexpress.event.OnRevolverUsed;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.KillerKnifeDurability;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.agmas.noellesroles.events.OnVendingMachinesBuyItems;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 林家子弟 —— 中立独立胜利职业数据。
 *
 * <p>
 * 开局获得「人数 × 300」金币，花光全部金币即独立获胜。无法杀人；攻击会金钱禁锢目标，
 * 被攻击则获得隐身。被动透视未持械的平民/杀手。技能走统一技能系统。
 */
public class LinFamilyRoleData extends SimpleRoleData {

    private static final String OFFER_ID_KEY = "lin_family_offer_id";
    private static final String OFFER_OWNER_KEY = "lin_family_offer_owner";
    private static final String MISFIRE_GUN_KEY = "lin_family_misfire_gun";
    private static final String WILL_MISFIRE_KEY = "lin_family_will_misfire";
    private static final int XRAY_REFRESH_INTERVAL = 20;
    private static final int ATTACK_COOLDOWN_TICK = 10 * 20;

    /** 售货机 / 抽奖机购买冷却（技能 HUD 显示，不通过技能键触发）。 */
    public static final ResourceLocation MACHINE_SKILL_ID = SRE.id("lin_family_machine");
    public static final int MACHINE_COOLDOWN_TICKS = 60 * 20;

    /** 金钱禁锢冷却：10 秒。 */
    private static final int MONEY_BIND_COOLDOWN_TICKS = 10 * 20;
    /** 用于金钱禁锢冷却的物品占位符。 */
    private static final Item MONEY_BIND_COOLDOWN_ITEM = Items.GOLD_NUGGET;

    /** 射击瞬间记录哑火结果（枪可能已被一次性消耗）。 */
    private static final ConcurrentHashMap<UUID, Boolean> PENDING_MISFIRE = new ConcurrentHashMap<>();

    private static boolean eventsRegistered = false;

    /** 内定的可购买职业物品池（通用可用道具，不含钥匙与真刀真枪）。 */
    private static final List<Supplier<ItemStack>> COLLECTOR_POOL = List.of(
            () -> TMMItems.LOCKPICK.getDefaultInstance(),
            () -> ModItems.INFERIOR_LOCKPICK.getDefaultInstance(),
            () -> TMMItems.CROWBAR.getDefaultInstance(),
            () -> TMMItems.NOTE.getDefaultInstance(),
            () -> TMMItems.SCOPE.getDefaultInstance(),
            () -> TMMItems.WEAK_DEFENSE_VIAL.getDefaultInstance(),
            () -> TMMItems.DEFENSE_VIAL.getDefaultInstance(),
            () -> TMMItems.FIRECRACKER.getDefaultInstance(),
            () -> TMMItems.BODY_BAG.getDefaultInstance(),
            () -> TMMItems.BLACKOUT.getDefaultInstance(),
            () -> TMMItems.MONITOR_BROKEN.getDefaultInstance(),
            () -> TMMItems.POISON_VIAL.getDefaultInstance(),
            () -> TMMItems.SCORPION.getDefaultInstance(),
            () -> TMMItems.DISGUISE_1.getDefaultInstance(),
            () -> TMMItems.DISGUISE_2.getDefaultInstance(),
            () -> TMMItems.DISGUISE_3.getDefaultInstance(),
            () -> ModItems.RADIO.getDefaultInstance(),
            () -> ModItems.NEWSPAPER.getDefaultInstance(),
            () -> ModItems.DELIVERY_BOX.getDefaultInstance(),
            () -> ModItems.FAKE_REVOLVER.getDefaultInstance(),
            () -> ModItems.FAKE_KNIFE.getDefaultInstance(),
            () -> ModItems.FAKE_LOCKPICK.getDefaultInstance(),
            () -> ModItems.FAKE_CROWBAR.getDefaultInstance(),
            () -> ModItems.FAKE_BAT.getDefaultInstance(),
            () -> ModItems.FAKE_GRENADE.getDefaultInstance(),
            () -> ModItems.FAKE_BODY_BAG.getDefaultInstance(),
            () -> ModItems.FAKE_PSYCHO_MODE.getDefaultInstance(),
            () -> ModItems.ALARM_TRAP.getDefaultInstance(),
            () -> ModItems.NIGHT_VISION_GLASSES.getDefaultInstance(),
            () -> ModItems.FLASHLIGHT.getDefaultInstance(),
            () -> ModItems.HANDCUFFS.getDefaultInstance(),
            () -> ModItems.ANTIDOTE.getDefaultInstance(),
            () -> ModItems.BLOOD_BOTTLE.getDefaultInstance(),
            () -> ModItems.FLASH_GRENADE.getDefaultInstance(),
            () -> ModItems.DECOY_GRENADE.getDefaultInstance(),
            () -> ModItems.SMOKE_GRENADE.getDefaultInstance(),
            () -> ModItems.PURIFY_BOMB.getDefaultInstance(),
            () -> ModItems.BLANK_CARTRIDGE.getDefaultInstance(),
            () -> ModItems.REINFORCEMENT.getDefaultInstance(),
            () -> ModItems.SCREWDRIVER.getDefaultInstance(),
            () -> ModItems.LOCK_ITEM.getDefaultInstance(),
            () -> ModItems.NOELL_PAPERCLIP.getDefaultInstance(),
            () -> ModItems.SMOKE_PELLET.getDefaultInstance(),
            () -> ModItems.DECOY_BEACON.getDefaultInstance(),
            () -> ModItems.FLARE.getDefaultInstance(),
            () -> ModItems.HALLUCINATION_BOTTLE.getDefaultInstance(),
            () -> ModItems.GIANT_NOTE.getDefaultInstance(),
            () -> ModItems.POCKET_WATCH.getDefaultInstance(),
            () -> ModItems.CRYSTAL_BALL.getDefaultInstance(),
            () -> ModItems.MINT_CANDIES.getDefaultInstance(),
            () -> ModItems.CHOCOLATE.getDefaultInstance(),
            () -> ModItems.CALMING_TEA.getDefaultInstance(),
            () -> ModItems.ENERGIZING_COFFEE.getDefaultInstance(),
            () -> ModItems.TALISMAN.getDefaultInstance(),
            () -> ModItems.SHILIJIA.getDefaultInstance(),
            () -> ModItems.ADRENALINE.getDefaultInstance(),
            () -> ModItems.ANTIBIOTIC.getDefaultInstance(),
            () -> ModItems.DOGSKIN_PLASTER.getDefaultInstance(),
            () -> ModItems.ALCHEMIST_BUFF_POTION.getDefaultInstance(),
            () -> ModItems.TOILET_POISON.getDefaultInstance(),
            () -> ModItems.SILENCE_TOTEM.getDefaultInstance(),
            () -> ModItems.ROPE.getDefaultInstance(),
            () -> ModItems.PASSBOOK.getDefaultInstance(),
            () -> ModItems.WREATH.getDefaultInstance(),
            () -> ModItems.SANITY_MEDS.getDefaultInstance(),
            () -> ModItems.AREA_MAP.getDefaultInstance(),
            () -> ModItems.BOXING_GLOVE.getDefaultInstance(),
            () -> ModItems.RIOT_SHIELD.getDefaultInstance(),
            () -> ModItems.BATON.getDefaultInstance(),
            () -> ModItems.WHEELCHAIR.getDefaultInstance(),
            () -> ModItems.createPillStack(false));

    public boolean startingGoldGranted = false;
    public long lastShieldBuyGameTime = Long.MIN_VALUE / 4;
    public final Set<UUID> xrayTargets = new HashSet<>();

    @Nullable
    public UUID pendingOfferId = null;
    @Nullable
    public UUID pendingOfferTarget = null;
    @Nullable
    public ItemStack pendingGift = ItemStack.EMPTY;
    public int pendingOfferCost = 0;
    public long pendingOfferExpireGameTime = 0;

    public LinFamilyRoleData(RoleDataContext context) {
        super(context);
    }

    public static void registerEvents() {
        if (eventsRegistered) {
            return;
        }
        eventsRegistered = true;

        AllowPlayerPunching.EVENT.register(player -> {
            return isLinFamily(player);
        });

        AttackEntityCallback.EVENT.register((attacker, level, hand, entity, hitResult) -> {
            if (level.isClientSide || attacker == null || !(entity instanceof Player victim)) {
                return InteractionResult.PASS;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(attacker) || !GameUtils.isPlayerAliveAndSurvival(victim)) {
                return InteractionResult.PASS;
            }
            if (attacker.getCooldowns().isOnCooldown(Items.BARRIER)) {
                return InteractionResult.PASS;
            }
            attacker.getCooldowns().addCooldown(Items.BARRIER, ATTACK_COOLDOWN_TICK);
            if (isLinFamily(attacker)) {
                ItemStack weapon = attacker.getMainHandItem();
                if (isConsideredWeapon(weapon)) {
                    applyMoneyBind(attacker, victim);
                }
            }
            if (isLinFamily(victim)) {
                applyInvisibility((ServerPlayer) victim);
            }
            return InteractionResult.PASS;
        });

        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (killer == null) {
                return true;
            }
            if (isLinFamily(killer)) {
                return false;
            }
            if (isMisfireShot(killer)) {
                killer.displayClientMessage(
                        Component.translatable("message.noellesroles.lin_family.gun_misfire")
                                .withStyle(ChatFormatting.RED),
                        true);
                return false;
            }
            return true;
        });

        OnRevolverUsed.EVENT.register((shooter, target) -> {
            PENDING_MISFIRE.remove(shooter.getUUID());
            if (target != null && isLinFamily(target) && GameUtils.isPlayerAliveAndSurvival(target)) {
                applyInvisibility(target);
            }
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (isMisfireGun(stack)) {
                PENDING_MISFIRE.put(player.getUUID(), willMisfire(stack));
                return InteractionResultHolder.pass(stack);
            }
            if (!stack.is(ModItems.NEWSPAPER) || !hasOfferTag(stack)) {
                return InteractionResultHolder.pass(stack);
            }
            if (world.isClientSide()) {
                return InteractionResultHolder.pass(stack);
            }
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResultHolder.pass(stack);
            }
            if (tryAcceptOffer(serverPlayer, stack, hand)) {
                return InteractionResultHolder.success(serverPlayer.getItemInHand(hand));
            }
            return InteractionResultHolder.pass(stack);
        });

        OnVendingMachinesBuyItems.EVENT.register((player, entry) -> allowMachinePurchase(player));
    }

    public static boolean isLinFamily(Player player) {
        if (player == null || player.level() == null) {
            return false;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        return game != null && game.isRole(player, ModRoles.LIN_FAMILY);
    }

    /** 是否是不可丢弃的钥匙类物品（信封/邮件已允许丢弃） */
    public static boolean isKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.is(TMMItems.KEY)
                || stack.is(TMMItems.IRON_DOOR_KEY)
                || stack.is(ModItems.MASTER_KEY)
                || stack.is(ModItems.MASTER_KEY_P)
                || stack.is(ModItems.NOELL_ARTISAN_KEY)
                || stack.is(ModItems.NOELL_KEY_BLANK)
                || stack.is(ModItems.SEALED_DOORLESS_KEY)
                || stack.is(ModItems.REPAIR_AREA_KEY)
                || stack.is(ModItems.REPAIR_OLD_KEY);
    }

    public static boolean hasGun(Player player) {
        return SREItemUtils.hasItem(player, TMMItemTags.GUNS);
    }

    public static boolean hasKnife(Player player) {
        return SREItemUtils.hasItem(player, stack -> stack.is(TMMItems.KNIFE) || stack.getItem() instanceof KnifeItem);
    }

    /**
     * 是否属于会触发“金钱禁锢”的武器：枪械、刀、球棒等 TrainWeapon，以及弓。
     * 空手不属于武器。
     */
    public static boolean isConsideredWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(TMMItemTags.GUNS) || stack.is(TMMItemTags.BOWS)) {
            return true;
        }
        if (stack.getItem() instanceof KnifeItem || stack.getItem() instanceof TrainWeapon) {
            return true;
        }
        return false;
    }

    public static boolean isXrayable(Player observer, Player target) {
        if (observer == null || target == null || observer == target) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(target)) {
            return false;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(target.level());
        if (game == null) {
            return false;
        }
        SRERole targetRole = game.getRole(target);
        if (targetRole == null || targetRole.isNeutrals()) {
            return false;
        }
        if (targetRole.canUseKiller()) {
            return !hasKnife(target) && !hasGun(target);
        }
        return !hasGun(target);
    }

    public boolean isXrayTarget(UUID uuid) {
        return uuid != null && xrayTargets.contains(uuid);
    }

    public boolean canBuyShield(long gameTime) {
        return gameTime >= lastShieldBuyGameTime
                + 60 * 20L;
    }

    public void markShieldBought(long gameTime) {
        lastShieldBuyGameTime = gameTime;
    }

    /**
     * 林家子弟购买售货机 / 抽奖机前的冷却检查。非本职业直接放行。
     * 冷却只在 {@link #markMachinePurchased} 于购买成功后写入，避免失败购买提前上 CD。
     */
    public static boolean allowMachinePurchase(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        LinFamilyRoleData data = RoleData.getNullable(LinFamilyRoleData.class, serverPlayer);
        if (data == null) {
            return true;
        }
        if (SREAbilityPlayerComponent.KEY.get(serverPlayer).getSkillState(MACHINE_SKILL_ID).cooldown > 0) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.lin_family.vending_cooldown")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        return true;
    }

    /** 售货机 / 抽奖机购买成功后进入 60 秒冷却，并检查是否花光金币。 */
    public static void markMachinePurchased(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        LinFamilyRoleData data = RoleData.getNullable(LinFamilyRoleData.class, serverPlayer);
        if (data == null) {
            return;
        }
        SREAbilityPlayerComponent.KEY.get(serverPlayer)
                .setSkillCooldown(MACHINE_SKILL_ID, MACHINE_COOLDOWN_TICKS);
        data.tryWin();
    }

    /** 将售货机 / 抽奖机物品放入快捷栏，满则掉在脚下。 */
    public static void givePurchasedItem(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ItemStack copy = stack.copy();
        if (!RoleUtils.insertStackInFreeSlot(player, copy)) {
            spawnAtFeet(player, copy);
        }
    }

    @Override
    public void init() {
        grantStartingGold();
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
            return;
        }
        tickPendingOffer(serverPlayer);
        if (serverPlayer.tickCount % XRAY_REFRESH_INTERVAL == 0) {
            refreshXrayTargets(serverPlayer);
        }
        tryWin();
    }

    private void grantStartingGold() {
        if (startingGoldGranted || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(serverPlayer.level());
        if (game == null || !game.isRunning()) {
            return;
        }
        int playerCount = Math.max(1, game.getPlayerCount());
        int gold = playerCount * 300;
        SREPlayerShopComponent.KEY.get(serverPlayer).setBalance(gold);
        startingGoldGranted = true;
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.lin_family.starting_gold", gold)
                        .withStyle(ChatFormatting.GOLD),
                false);
        sync();
    }

    private void refreshXrayTargets(ServerPlayer self) {
        Set<UUID> next = new HashSet<>();
        for (ServerPlayer other : self.serverLevel().players()) {
            if (isXrayable(self, other)) {
                next.add(other.getUUID());
            }
        }
        if (!next.equals(xrayTargets)) {
            xrayTargets.clear();
            xrayTargets.addAll(next);
            sync();
        }
    }

    private void tickPendingOffer(ServerPlayer self) {
        if (pendingOfferId == null) {
            return;
        }
        long now = self.level().getGameTime();
        ServerPlayer target = pendingOfferTarget == null ? null
                : self.server.getPlayerList().getPlayer(pendingOfferTarget);
        boolean expired = now >= pendingOfferExpireGameTime;
        boolean missingPaper = target == null || !hasOfferNewspaper(target, pendingOfferId);
        if (expired || missingPaper) {
            rejectPendingOffer(self, target, true);
        }
    }

    public static boolean useGenerosity(ServerPlayer self, @Nullable ServerPlayer target) {
        LinFamilyRoleData data = RoleData.getNullable(LinFamilyRoleData.class, self);
        if (data == null || !GameUtils.isPlayerAliveAndSurvival(self)) {
            return false;
        }
        if (target == null || !GameUtils.isPlayerAliveAndSurvival(target)) {
            self.displayClientMessage(
                    Component.translatable("message.noellesroles.lin_family.no_target")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (self.distanceToSqr(target) > 144) {
            self.displayClientMessage(
                    Component.translatable("message.noellesroles.lin_family.too_far")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (!isXrayable(self, target)) {
            self.displayClientMessage(
                    Component.translatable("message.noellesroles.lin_family.not_xray")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (data.pendingOfferId != null) {
            self.displayClientMessage(
                    Component.translatable("message.noellesroles.lin_family.offer_pending")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(self.level());
        SRERole targetRole = game.getRole(target);
        GiftChoice choice = data.chooseGift(self, targetRole);
        if (choice == null) {
            self.displayClientMessage(
                    Component.translatable("message.noellesroles.lin_family.not_enough_gold")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (!trySpend(self, choice.cost)) {
            self.displayClientMessage(
                    Component.translatable("message.noellesroles.lin_family.not_enough_gold")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        boolean deliveredByNewspaper = RoleUtils.hasHotbarFreeSlot(target);
        if (deliveredByNewspaper) {
            data.startNewspaperOffer(self, target, choice);
        } else {
            spawnAtFeet(target, choice.gift.copy());
            self.displayClientMessage(
                    Component.translatable("message.noellesroles.lin_family.gift_dropped", target.getName(),
                            choice.cost)
                            .withStyle(ChatFormatting.GOLD),
                    true);
            target.displayClientMessage(
                    Component.translatable("message.noellesroles.lin_family.gift_received_drop", self.getName())
                            .withStyle(ChatFormatting.GOLD),
                    false);
            data.tryWin();
        }
        return true;
    }

    public static boolean useCollector(ServerPlayer self) {
        LinFamilyRoleData data = RoleData.getNullable(LinFamilyRoleData.class, self);
        if (data == null || !GameUtils.isPlayerAliveAndSurvival(self)) {
            return false;
        }
        int price = 400;
        if (!trySpend(self, price)) {
            self.displayClientMessage(
                    Component.translatable("message.noellesroles.lin_family.not_enough_gold")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        ItemStack reward = COLLECTOR_POOL.get(self.getRandom().nextInt(COLLECTOR_POOL.size())).get().copy();
        if (!RoleUtils.insertStackInFreeSlot(self, reward)) {
            spawnAtFeet(self, reward);
        }
        self.displayClientMessage(
                Component.translatable("message.noellesroles.lin_family.collector_got",
                        reward.getHoverName(), price)
                        .withStyle(ChatFormatting.GOLD),
                true);
        data.tryWin();
        return true;
    }

    @Nullable
    private GiftChoice chooseGift(ServerPlayer self, @Nullable SRERole targetRole) {
        boolean killer = targetRole != null && targetRole.canUseKiller() && !targetRole.isNeutrals();
        if (killer) {
            boolean preferKnife = self.getRandom().nextBoolean();
            GiftChoice knife = new GiftChoice(createDurabilityKnife(), 150);
            GiftChoice gun = new GiftChoice(createMisfireGun(false), 300);
            GiftChoice first = preferKnife ? knife : gun;
            GiftChoice second = preferKnife ? gun : knife;
            if (getBalance(self) >= first.cost) {
                return first;
            }
            if (getBalance(self) >= second.cost) {
                return second;
            }
            return null;
        }
        int cost = 200;
        if (getBalance(self) < cost) {
            return null;
        }
        return new GiftChoice(createMisfireGun(true), cost);
    }

    private void startNewspaperOffer(ServerPlayer self, ServerPlayer target, GiftChoice choice) {
        UUID offerId = UUID.randomUUID();
        pendingOfferId = offerId;
        pendingOfferTarget = target.getUUID();
        pendingGift = choice.gift.copy();
        pendingOfferCost = choice.cost;
        pendingOfferExpireGameTime = self.level().getGameTime()
                + 15 * 20L;

        ItemStack newspaper = createOfferNewspaper(self, offerId);
        RoleUtils.insertStackInFreeSlot(target, newspaper);
        self.displayClientMessage(
                Component.translatable("message.noellesroles.lin_family.offer_sent", target.getName(),
                        choice.cost)
                        .withStyle(ChatFormatting.GOLD),
                true);
        target.displayClientMessage(
                Component.translatable("message.noellesroles.lin_family.offer_received", self.getName())
                        .withStyle(ChatFormatting.GOLD),
                false);
    }

    private static boolean tryAcceptOffer(ServerPlayer target, ItemStack newspaper, InteractionHand hand) {
        CompoundTag tag = newspaper.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.hasUUID(OFFER_ID_KEY) || !tag.hasUUID(OFFER_OWNER_KEY)) {
            return false;
        }
        UUID offerId = tag.getUUID(OFFER_ID_KEY);
        UUID ownerId = tag.getUUID(OFFER_OWNER_KEY);
        ServerPlayer owner = target.server.getPlayerList().getPlayer(ownerId);
        LinFamilyRoleData data = owner == null ? null : RoleData.getNullable(LinFamilyRoleData.class, owner);
        if (data == null || data.pendingOfferId == null || !data.pendingOfferId.equals(offerId)) {
            target.displayClientMessage(
                    Component.translatable("message.noellesroles.lin_family.offer_expired")
                            .withStyle(ChatFormatting.RED),
                    true);
            consumeNewspaper(target, newspaper, hand);
            return true;
        }
        ItemStack gift = data.pendingGift == null ? ItemStack.EMPTY : data.pendingGift.copy();
        data.clearPendingOffer();
        consumeNewspaper(target, newspaper, hand);
        if (!gift.isEmpty()) {
            if (!RoleUtils.insertStackInFreeSlot(target, gift)) {
                spawnAtFeet(target, gift);
            }
        }
        target.displayClientMessage(
                Component.translatable("message.noellesroles.lin_family.offer_accepted")
                        .withStyle(ChatFormatting.GREEN),
                true);
        if (owner != null) {
            owner.displayClientMessage(
                    Component.translatable("message.noellesroles.lin_family.offer_accepted_owner",
                            target.getName())
                            .withStyle(ChatFormatting.GOLD),
                    true);
            data.tryWin();
        }
        return true;
    }

    private void rejectPendingOffer(ServerPlayer self, @Nullable ServerPlayer target, boolean notify) {
        int refund = pendingOfferCost;
        UUID offerId = pendingOfferId;
        clearPendingOffer();
        if (target != null && offerId != null) {
            removeOfferNewspaper(target, offerId);
        }
        if (refund > 0) {
            SREPlayerShopComponent.KEY.get(self).addToBalance(refund);
        }
        if (notify) {
            self.displayClientMessage(
                    Component.translatable("message.noellesroles.lin_family.offer_rejected", refund)
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            if (target != null) {
                target.displayClientMessage(
                        Component.translatable("message.noellesroles.lin_family.offer_rejected_target")
                                .withStyle(ChatFormatting.YELLOW),
                        true);
            }
        }
    }

    private void clearPendingOffer() {
        pendingOfferId = null;
        pendingOfferTarget = null;
        pendingGift = ItemStack.EMPTY;
        pendingOfferCost = 0;
        pendingOfferExpireGameTime = 0;
    }

    public void tryWin() {
        if (!startingGoldGranted || pendingOfferId != null) {
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
            return;
        }
        if (getBalance(serverPlayer) > 0) {
            return;
        }
        if (!(serverPlayer.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        RoleUtils.customWinnerWin(serverLevel, GameUtils.WinStatus.CUSTOM,
                ModRoles.LIN_FAMILY_ID.getPath(), OptionalInt.of(ModRoles.LIN_FAMILY.color()));
    }

    public static boolean checkLinFamilyVictory(ServerLevel serverLevel) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
        for (ServerPlayer sp : serverLevel.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(sp) || !gameWorld.isRole(sp, ModRoles.LIN_FAMILY)) {
                continue;
            }
            LinFamilyRoleData data = RoleData.getNullable(LinFamilyRoleData.class, sp);
            if (data != null && data.startingGoldGranted && data.pendingOfferId == null
                    && getBalance(sp) <= 0) {
                RoleUtils.customWinnerWin(serverLevel, GameUtils.WinStatus.CUSTOM,
                        ModRoles.LIN_FAMILY_ID.getPath(), OptionalInt.of(ModRoles.LIN_FAMILY.color()));
                return true;
            }
        }
        return false;
    }

    private static void applyMoneyBind(Player attacker, Player victim) {
        if (attacker.getCooldowns().isOnCooldown(MONEY_BIND_COOLDOWN_ITEM)) {
            return;
        }
        attacker.getCooldowns().addCooldown(MONEY_BIND_COOLDOWN_ITEM, MONEY_BIND_COOLDOWN_TICKS);
        int ticks = 5 * 20;
        victim.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, ticks, 0, false, false, true));
        victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, true, true));
        victim.displayClientMessage(
                Component.translatable("message.noellesroles.lin_family.bound")
                        .withStyle(ChatFormatting.GOLD),
                true);
        attacker.displayClientMessage(
                Component.translatable("message.noellesroles.lin_family.bind_hit", victim.getName())
                        .withStyle(ChatFormatting.GOLD),
                true);
    }

    private static void applyInvisibility(ServerPlayer linFamily) {
        int ticks = 20 * 20;
        linFamily.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false, true));
        linFamily.displayClientMessage(
                Component.translatable("message.noellesroles.lin_family.invis")
                        .withStyle(ChatFormatting.AQUA),
                true);
        linFamily.level().playSound(null, linFamily.getX(), linFamily.getY(), linFamily.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    private static boolean trySpend(ServerPlayer player, int amount) {
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
        if (shop.balance < amount) {
            return false;
        }
        shop.addToBalance(-amount);
        return true;
    }

    private static int getBalance(Player player) {
        return SREPlayerShopComponent.KEY.get(player).balance;
    }

    private static ItemStack createDurabilityKnife() {
        ItemStack knife = TMMItems.KNIFE.getDefaultInstance();
        KillerKnifeDurability.applyFreshDurability(knife);
        return knife;
    }

    private static ItemStack createMisfireGun(boolean disguiseAsRevolver) {
        ItemStack gun = ModItems.ONCE_REVOLVER.getDefaultInstance();
        if (disguiseAsRevolver) {
            gun.set(DataComponents.ITEM_NAME, Component.translatable(TMMItems.REVOLVER.getDescriptionId()));
        }
        CompoundTag tag = gun.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean(MISFIRE_GUN_KEY, true);
        tag.putBoolean(WILL_MISFIRE_KEY, Math.random() < 0.5D);
        gun.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return gun;
    }

    private static ItemStack createOfferNewspaper(ServerPlayer owner, UUID offerId) {
        ItemStack newspaper = ModItems.NEWSPAPER.getDefaultInstance();
        String title = Component.translatable("message.noellesroles.lin_family.newspaper_title").getString();
        Component page = Component.translatable("message.noellesroles.lin_family.offer_page", owner.getName());
        newspaper.set(SREDataComponentTypes.WRITTEN_BOOK_CONTENT,
                new SREWrittenBookContent(Filterable.passThrough(title), owner.getGameProfile().getName(),
                        List.of(Filterable.passThrough(page)), true));
        CompoundTag tag = newspaper.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putUUID(OFFER_ID_KEY, offerId);
        tag.putUUID(OFFER_OWNER_KEY, owner.getUUID());
        newspaper.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        newspaper.set(DataComponents.ITEM_NAME,
                Component.translatable("item.noellesroles.lin_family_offer").withStyle(ChatFormatting.GOLD));
        return newspaper;
    }

    private static boolean hasOfferTag(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID(OFFER_ID_KEY);
    }

    private static boolean hasOfferNewspaper(Player player, UUID offerId) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(ModItems.NEWSPAPER) || !hasOfferTag(stack)) {
                continue;
            }
            UUID id = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getUUID(OFFER_ID_KEY);
            if (offerId.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static void removeOfferNewspaper(Player player, UUID offerId) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(ModItems.NEWSPAPER) || !hasOfferTag(stack)) {
                continue;
            }
            UUID id = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getUUID(OFFER_ID_KEY);
            if (offerId.equals(id)) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private static void consumeNewspaper(ServerPlayer player, ItemStack newspaper, InteractionHand hand) {
        if (newspaper.getCount() <= 1) {
            player.setItemInHand(hand, ItemStack.EMPTY);
        } else {
            newspaper.shrink(1);
        }
    }

    private static boolean isMisfireGun(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(MISFIRE_GUN_KEY) && tag.getBoolean(MISFIRE_GUN_KEY);
    }

    private static boolean willMisfire(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(WILL_MISFIRE_KEY) && tag.getBoolean(WILL_MISFIRE_KEY);
    }

    private static boolean isMisfireShot(Player killer) {
        Boolean pending = PENDING_MISFIRE.remove(killer.getUUID());
        if (pending != null) {
            return pending;
        }
        return isMisfireGun(killer.getMainHandItem()) && willMisfire(killer.getMainHandItem());
    }

    private static void spawnAtFeet(Player target, ItemStack stack) {
        if (stack == null || stack.isEmpty() || target.level().isClientSide) {
            return;
        }
        ItemEntity entity = new ItemEntity(target.level(), target.getX(), target.getY() + 0.2D, target.getZ(),
                stack.copy());
        entity.setPickUpDelay(5);
        target.level().addFreshEntity(entity);
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("startingGoldGranted", startingGoldGranted);
        ListTag list = new ListTag();
        for (UUID uuid : xrayTargets) {
            list.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("xray", list);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        startingGoldGranted = tag.getBoolean("startingGoldGranted");
        xrayTargets.clear();
        ListTag list = tag.getList("xray", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                xrayTargets.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private record GiftChoice(ItemStack gift, int cost) {
    }
}
