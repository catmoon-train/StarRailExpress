package org.agmas.noellesroles.game.roles.innocence.tailor;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.content.item.KnifeItem;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnKillPlayerTriggered;
import io.wifi.starrailexpress.util.TrueFalseResult;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import org.agmas.noellesroles.init.ModItems;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 「裁缝的衣服」相关规则的中心处理类。
 *
 * <ul>
 * <li><b>物品标记</b>：衣服就是改过名的原版皮革胸甲，通过在物品 {@code CUSTOM_DATA} 上打
 * {@value #CLOTHES_TAG_KEY} 标签来识别（参考 {@code AnglerItemTags}），颜色随机并由
 * {@code DYED_COLOR} 组件承载；</li>
 * <li><b>抵挡伤害</b>：身着衣服的玩家受到<b>非 forcekill</b> 的击杀时，有 5% 概率抵挡这次伤害，
 * 抵挡成功时衣服直接破损消失，并记录回放事件；</li>
 * <li><b>冷却惩罚</b>：上述被攻击时，攻击者主手上的物品在进入原版物品冷却时会<b>额外 +5 秒</b>
 * （惩罚以玩家的 {@link ItemCooldowns} 实例为键暂存，由
 * {@code TailorCooldownPenaltyMixin} 在 {@code onCooldownStarted} 时结算）；</li>
 * <li><b>保暖</b>：身着衣服时保暖值不再自然下降（由 {@code MapStatusBarRuntime#tickWarmth}
 * 调用 {@link #isWearingTailorClothes} 判定）。</li>
 * </ul>
 *
 * 规则本体集中在本类，物品类 / 事件注册只做薄转发。
 */
public final class TailorClothesHandler {

    /** 「裁缝的衣服」在物品 CUSTOM_DATA 上的标记键。 */
    public static final String CLOTHES_TAG_KEY = "tailor_clothes";

    /** 抵挡概率（百分比）。 */
    public static final int BLOCK_CHANCE_PERCENT = 5;
    /** 攻击者主手物品被额外附加的冷却（tick，5 秒）。 */
    public static final int COOLDOWN_PENALTY_TICKS = 100;
    /** 冷却惩罚的暂存窗口（tick）：攻击自身的冷却通常在同一个 tick 内落下，窗口仅用于兜底。 */
    public static final int PENALTY_WINDOW_TICKS = 200;

    /**
     * 攻击者冷却表 -> 待结算的冷却惩罚。
     *
     * <p>以 {@link ItemCooldowns} 实例为键（每玩家一个、身份唯一），用 {@link WeakHashMap}
     * 让离线玩家的条目随实例一起被回收；惩罚在被匹配的冷却落下时消费掉，过期则由
     * {@link #consumeCooldownPenalty} 惰性清理。</p>
     */
    private static final Map<ItemCooldowns, CooldownPenalty> COOLDOWN_PENALTIES = new WeakHashMap<>();

    private TailorClothesHandler() {
    }

    // ==================== 事件注册 ====================

    /** 注册「裁缝的衣服」的抵挡事件（由 {@code NRCombatEvents.registerWeaponHandlers()} 调用）。 */
    public static void register() {
        OnKillPlayerTriggered.EVENT.register(TailorClothesHandler::onKillPlayerTriggered);
        AllowPlayerDeathWithKiller.EVENT.register(TailorClothesHandler::allowDeathWithKiller);
        OnGameEnd.EVENT.register((serverLevel, gameWorldComponent) -> COOLDOWN_PENALTIES.clear());
    }

    /**
     * 身着「裁缝的衣服」的玩家受到非 forcekill 的击杀时：
     * <ol>
     * <li>攻击者主手物品进入冷却时额外 +5 秒（无论是否抵挡都生效）；</li>
     * <li>5% 概率抵挡这次伤害：衣服直接破损消失并记录回放，返回 {@code false} 否决死亡。</li>
     * </ol>
     */
    private static boolean allowDeathWithKiller(Player victim, Player killer, ResourceLocation deathReason) {
        if (killer == null || !(victim instanceof ServerPlayer wearer) || !(killer instanceof ServerPlayer attacker)) {
            return true;
        }
        // 强制死亡（forcekill）无论如何都会处死玩家，挡不住；此时不消耗衣服、不作提示
        if (GameUtils.isForcedDeathInProgress()) {
            return true;
        }
        if (!isWearingTailorClothes(wearer)) {
            return true;
        }
        // 冷却惩罚：攻击者主手物品下次进入原版冷却时额外 +5 秒
        applyCooldownPenalty(attacker);

        if (wearer.getRandom().nextInt(100) >= BLOCK_CHANCE_PERCENT) {
            return true;
        }
        // 抵挡成功：衣服直接破损消失
        breakTailorClothes(wearer);
        wearer.displayClientMessage(
                Component.translatable("message.noellesroles.tailor.blocked_self").withStyle(ChatFormatting.GREEN),
                true);
        attacker.displayClientMessage(
                Component.translatable("message.noellesroles.tailor.blocked_attacker", wearer.getDisplayName())
                        .withStyle(ChatFormatting.RED),
                true);
        // 回放记录：裁缝的衣服为 xxx 抵挡了这次伤害
        SRE.REPLAY_MANAGER.recordCustomEvent(Component.translatable("replay.event.tailor.blocked",
                GameReplayUtils.getReplayPlayerDisplayText(wearer, true)));
        return false;
    }

    /**
     * 命中即登记冷却惩罚。
     *
     * <p>{@link AllowPlayerDeathWithKiller} 位于死亡链较后段（{@code role.allowDeath} 之后），
     * 目标被角色能力拦下时根本走不到，因此这里在死亡链最早期再登记一次；
     * 两次登记内容相同、键相同，属于幂等覆盖。</p>
     */
    private static TrueFalseResult onKillPlayerTriggered(Player victim, boolean spawnBody, Player killer,
            ResourceLocation deathReason, boolean forceDeath) {
        if (killer instanceof ServerPlayer attacker && victim instanceof ServerPlayer wearer
                && attacker != wearer && !GameUtils.isForcedDeathInProgress()
                && isWearingTailorClothes(wearer)) {
            applyCooldownPenalty(attacker);
        }
        return TrueFalseResult.PASS;
    }

    // ==================== 冷却惩罚 ====================

    /** 给攻击者暂存一条「主手物品进入冷却时额外 +5 秒」的惩罚。 */
    public static void applyCooldownPenalty(ServerPlayer attacker) {
        Set<Item> items = new HashSet<>();
        ItemStack mainHand = attacker.getMainHandItem();
        if (mainHand.isEmpty()) {
            // 投掷三叉戟等投掷武器命中时主手为空，冷却落在三叉戟本身（见 TridentMixin）。
            items.add(Items.TRIDENT);
        } else {
            // 只锁定主手这一件物品：枪械命中会一次性给所有枪加冷却，若把同类物品都纳入匹配，
            // 惩罚会被最先落到的那把枪消费掉，主手枪反而拿不到 +5 秒。
            items.add(mainHand.getItem());
            if (mainHand.getItem() instanceof KnifeItem) {
                // 刀系攻击的原版冷却统一落在 TMMItems.KNIFE 上（见 KnifeStabPayload），一并计入
                items.add(TMMItems.KNIFE);
            }
        }
        ItemCooldowns cooldowns = attacker.getCooldowns();
        COOLDOWN_PENALTIES.put(cooldowns,
                new CooldownPenalty(items, COOLDOWN_PENALTY_TICKS, cooldowns.tickCount + PENALTY_WINDOW_TICKS));
        // 主手物品此刻可能已经在冷却中（枪械的冷却先于命中结算落下），直接延长那条冷却；
        // 没能延长时保留待结算记录，交给下一次 onCooldownStarted 结算。
        extendActiveCooldown(attacker);
    }

    /**
     * 由 {@code TailorCooldownPenaltyMixin} 在物品进入冷却时调用：若有匹配的惩罚则消费掉并返回
     * 需要附加的 tick 数，否则返回 0。
     *
     * @param cooldowns   攻击者的冷却表
     * @param item        正在进入冷却的物品
     * @param currentTick 冷却表当前的 tickCount
     */
    public static int consumeCooldownPenalty(ItemCooldowns cooldowns, Item item, int currentTick) {
        CooldownPenalty penalty = COOLDOWN_PENALTIES.get(cooldowns);
        if (penalty == null) {
            return 0;
        }
        if (currentTick > penalty.expireTick()) {
            COOLDOWN_PENALTIES.remove(cooldowns);
            return 0;
        }
        if (!penalty.items().contains(item)) {
            return 0;
        }
        COOLDOWN_PENALTIES.remove(cooldowns);
        return penalty.extraTicks();
    }

    /**
     * 直接给攻击者主手物品的<b>当前</b>冷却追加惩罚时长。
     *
     * <p>{@link #consumeCooldownPenalty} 只在该物品「下一次进入冷却」时生效；若物品此刻已经在
     * 冷却中（例如枪械命中的冷却先落下、投掷三叉戟命中时主手已空），那条待结算的惩罚就永远
     * 等不到下一次进入冷却。这里直接把现有冷却的结束时间往后推，并补发冷却包同步客户端。</p>
     *
     * @return 是否确实延长了某个正在冷却的物品
     */
    public static boolean extendActiveCooldown(ServerPlayer attacker) {
        ItemCooldowns cooldowns = attacker.getCooldowns();
        CooldownPenalty penalty = COOLDOWN_PENALTIES.get(cooldowns);
        if (penalty == null) {
            return false;
        }
        if (cooldowns.tickCount > penalty.expireTick()) {
            COOLDOWN_PENALTIES.remove(cooldowns);
            return false;
        }
        ItemCooldowns.CooldownInstance instance = null;
        Item target = null;
        for (Item item : penalty.items()) {
            ItemCooldowns.CooldownInstance candidate = cooldowns.cooldowns.get(item);
            if (candidate == null) {
                continue;
            }
            // 只延长真正还没结束的那条冷却
            if (candidate.endTime <= cooldowns.tickCount) {
                continue;
            }
            instance = candidate;
            target = item;
            break;
        }
        if (instance == null || target == null) {
            return false;
        }
        COOLDOWN_PENALTIES.remove(cooldowns);
        int newEnd = instance.endTime + penalty.extraTicks();
        cooldowns.cooldowns.put(target,
                new ItemCooldowns.CooldownInstance(instance.startTime, newEnd));
        // 服务端冷却表已改，客户端需要重新收一次冷却包才能看到延长后的读条
        attacker.connection.send(new ClientboundCooldownPacket(target, newEnd - cooldowns.tickCount));
        return true;
    }

    /** 暂存的冷却惩罚：作用于哪些物品、附加多少 tick、何时过期。 */
    private record CooldownPenalty(Set<Item> items, int extraTicks, int expireTick) {
    }

    // ==================== 衣服的创建 / 标记 / 穿着 ====================

    /** 创建一件「裁缝的衣服」：改名的原版皮革胸甲 + 随机染色 + 标记标签。 */
    public static ItemStack createTailorClothes() {
        ItemStack stack = new ItemStack(Items.LEATHER_CHESTPLATE);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.translatable("item.noellesroles.tailor_clothes").withStyle(ChatFormatting.LIGHT_PURPLE));
        stack.set(DataComponents.DYED_COLOR,
                new DyedItemColor(ThreadLocalRandom.current().nextInt(0x1000000), false));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("item.noellesroles.tailor_clothes.lore").withStyle(ChatFormatting.GRAY))));
        markTailorClothes(stack);
        return stack;
    }

    /** 给物品栈打上「裁缝的衣服」标签。 */
    public static void markTailorClothes(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean(CLOTHES_TAG_KEY, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** 该物品栈是否是「裁缝的衣服」。 */
    public static boolean isTailorClothes(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.LEATHER_CHESTPLATE)
                && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                        .getBoolean(CLOTHES_TAG_KEY);
    }

    /** 玩家胸甲栏是否身着「裁缝的衣服」。 */
    public static boolean isWearingTailorClothes(Player player) {
        return isTailorClothes(player.getItemBySlot(EquipmentSlot.CHEST));
    }

    /** 目标当前是否能安置一件衣服（胸甲栏为空，或快捷栏有空位）。 */
    public static boolean canPlaceOn(ServerPlayer target) {
        if (target.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
            return true;
        }
        return firstEmptyHotbarSlot(target) >= 0;
    }

    /**
     * 把衣服给目标穿上：胸甲栏为空则自动穿上，否则进入目标空余的快捷栏。
     *
     * @return 是否安置成功（胸甲栏被占用且快捷栏已满时失败）
     */
    public static boolean placeOn(ServerPlayer target, ItemStack clothes) {
        if (target.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
            target.setItemSlot(EquipmentSlot.CHEST, clothes);
            return true;
        }
        int slot = firstEmptyHotbarSlot(target);
        if (slot < 0) {
            return false;
        }
        target.getInventory().setItem(slot, clothes);
        return true;
    }

    /** 目标第一个空余快捷栏槽位（0~8），没有则返回 -1。 */
    public static int firstEmptyHotbarSlot(ServerPlayer target) {
        for (int slot = 0; slot < 9; slot++) {
            if (target.getInventory().getItem(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    /** 从裁缝快捷栏里取出一根毛线球（不消耗），没有则返回空栈。 */
    public static ItemStack findYarnBallInHotbar(ServerPlayer tailor) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = tailor.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(ModItems.YARN_BALL)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** 「裁缝的衣服」破损消失：移除胸甲 + 布料撕裂音效与粒子。 */
    public static void breakTailorClothes(ServerPlayer wearer) {
        wearer.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        if (wearer.level() instanceof ServerLevel level) {
            level.playSound(null, wearer.blockPosition(), SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F);
            level.sendParticles(ParticleTypes.POOF,
                    wearer.getX(), wearer.getY() + 1.0D, wearer.getZ(),
                    12, 0.35D, 0.45D, 0.35D, 0.02D);
        }
    }
}
