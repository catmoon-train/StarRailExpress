/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.role_data.killer;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.game.DiscountShopEntry;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.agmas.noellesroles.game.roles.killer.pooler.PoolerDomainManager;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 祈月者（{@code pooler}，杀手阵营）的职业数据。
 * <p>
 * 一个职业带四个技能，靠「技能切换键（Y）」在
 * 奔涌 / 吞月 / 水中镜 / 称颂 之间切换，技能键（G）释放当前选中的技能：
 * <ul>
 * <li><b>奔涌</b>：把身上所有三叉戟的附魔换成激流 III，之后 30 秒不能再用。</li>
 * <li><b>吞月</b>：领域技，8 格内除自己以外的所有玩家被拉上月球 30 秒，冷却 120 秒。</li>
 * <li><b>水中镜</b>：领域技，6 格内除杀手阵营与魔术师以外（含自己）的玩家被拉进大水缸，
 * 冷却 150 秒；水缸里用三叉戟击杀不走物品冷却。</li>
 * <li><b>称颂</b>：全体杀手阵营（含魔术师）获得海豚的恩惠与水下呼吸 20 秒，冷却 80 秒。</li>
 * </ul>
 * 商店里还能买到「减少技能冷却 40 秒」，因此本类用「冷却结束时刻」而不是由
 * {@code RoleSkill} 统一扣冷却 —— 技能定义里冷却为 0，实际冷却由
 * {@link #tryUseSkill(ServerPlayer, PoolerSkill)} 自己记账（见 {@link #skillReadyAt})。
 * <p>
 * 开局自带的「月之祝福」在 {@link #useMoonBlessing(ServerPlayer)}：给使用者
 * 海豚的恩惠 + 速度 II + 无碰撞 15 秒，用后 40 秒冷却。
 */
public class PoolerRoleData extends SimpleRoleData {

    /** 三叉戟击杀后的物品冷却（30 秒）。 */
    public static final int TRIDENT_KILL_COOLDOWN_TICKS = 30 * 20;
    /** 疯狂模式中三叉戟击杀的物品冷却 = 基础值 / 6（5 秒）。 */
    public static final int TRIDENT_KILL_COOLDOWN_PSYCHO = TRIDENT_KILL_COOLDOWN_TICKS / 6;

    /** 奔涌：冷却。 */
    public static final int TIDE_COOLDOWN_TICKS = 30 * 20;
    /** 吞月：冷却。 */
    public static final int MOON_COOLDOWN_TICKS = 120 * 20;
    /** 水中镜：冷却。 */
    public static final int AQUARIUM_COOLDOWN_TICKS = 150 * 20;
    /** 称颂：冷却。 */
    public static final int PRAISE_COOLDOWN_TICKS = 80 * 20;

    /** 称颂：海豚的恩惠 / 水下呼吸持续时间。 */
    public static final int PRAISE_DURATION_TICKS = 20 * 20;

    /** 月之祝福：持续时间 15 秒。 */
    public static final int MOON_BLESSING_DURATION_TICKS = 15 * 20;
    /** 月之祝福：冷却 40 秒。 */
    public static final int MOON_BLESSING_COOLDOWN_TICKS = 40 * 20;

    /** 商店「减少技能冷却」每买一次减少的秒数。 */
    public static final int SHOP_CD_REDUCE_SECONDS = 40;

    /** 祈月者的四个可切换技能。 */
    public enum PoolerSkill {
        TIDE("skill.noellesroles.pooler.tide"),
        MOON("skill.noellesroles.pooler.moon"),
        AQUARIUM("skill.noellesroles.pooler.aquarium"),
        PRAISE("skill.noellesroles.pooler.praise");

        /** 技能名翻译键。 */
        public final String nameKey;

        PoolerSkill(String nameKey) {
            this.nameKey = nameKey;
        }
    }

    /** 四个技能各自的冷却结束时刻（{@link GameUtils#getTicksFromGameStart}）。 */
    private final long[] skillReadyAt = new long[PoolerSkill.values().length];


    /** 疯狂模式是否由本职业开启（用于结束时还原手持的三叉戟）。 */
    private boolean tideFrenzy;

    public PoolerRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public void init() {
        for (int i = 0; i < skillReadyAt.length; i++) {
            skillReadyAt[i] = 0L;
        }
        tideFrenzy = false;
        sync();
    }

    @Override
    public void clear() {
        yesRevertFrenzy();
        init();
    }

    public Player getPlayer() {
        return player;
    }

    /**
     * 当前选中的技能。
     * <p>
     * 切换键由统一技能系统 {@code SREAbilityPlayerComponent} 记账，这里只读它的权威
     * slot，本类不再自己存一份；服务端与客户端读到的永远是同一个值。
     */
    public PoolerSkill getSelectedSkill() {
        PoolerSkill[] values = PoolerSkill.values();
        int slot = io.wifi.starrailexpress.cca.SREAbilityPlayerComponent.KEY.get(player).getSelectedSkill();
        return values[Math.floorMod(slot, values.length)];
    }

    /** 指教某技能还要冷却多少秒；0 表示可用。 */
    public int cooldownSecondsLeft(PoolerSkill skill) {
        long now = GameUtils.getTicksFromGameStart(player.level());
        long readyAt = skillReadyAt[skill.ordinal()];
        return (int) Math.max(0L, (readyAt - now + 19L) / 20L);
    }

    public boolean isSkillReady(PoolerSkill skill) {
        return cooldownSecondsLeft(skill) <= 0;
    }

    /** 商店「减少技能冷却」：把所有技能与月之祝福的剩余冷却各减 40 秒。 */
    public void reduceAllCooldowns(int seconds) {
        if (!(player instanceof ServerPlayer sp))
            return;
        long now = GameUtils.getTicksFromGameStart(player.level());
        long cut = seconds * 20L;
        for (int i = 0; i < skillReadyAt.length; i++) {
            skillReadyAt[i] = Math.max(now, skillReadyAt[i] - cut);
        }
        sync();
    }

    // ── 技能总入口 ──────────────────────────────────────────────

    /** 释放当前选中的技能；返回 true 表示真正释放成功（消耗冷却）。 */
    public boolean useSelectedSkill(ServerPlayer sp) {
        return tryUseSkill(sp, getSelectedSkill());
    }

    /**
     * 释放指定技能。冷却由本类自己记账（结束时刻），因此统一技能定义里不写冷却，
     * 这样商店的「减少技能冷却」才能真正缩短剩余时间。
     */
    public boolean tryUseSkill(ServerPlayer sp, PoolerSkill skill) {
        if (!isActivePooler(sp))
            return false;
        if (!GameUtils.isPlayerAliveAndSurvival(sp))
            return false;
        if (GameUtils.isTimeFrozen(sp.level()))
            return false;

        long now = GameUtils.getTicksFromGameStart(sp.level());
        int left = cooldownSecondsLeft(skill);
        if (left > 0) {
            sp.displayClientMessage(Component
                    .translatable("message.noellesroles.pooler.skill_on_cooldown", left)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        boolean success = switch (skill) {
            case TIDE -> doTide(sp);
            case MOON -> doMoon(sp);
            case AQUARIUM -> doAquarium(sp);
            case PRAISE -> doPraise(sp);
        };
        if (!success)
            return false;

        long cooldown = switch (skill) {
            case TIDE -> TIDE_COOLDOWN_TICKS;
            case MOON -> MOON_COOLDOWN_TICKS;
            case AQUARIUM -> AQUARIUM_COOLDOWN_TICKS;
            case PRAISE -> PRAISE_COOLDOWN_TICKS;
        };
        skillReadyAt[skill.ordinal()] = now + cooldown;
        sync();
        return true;
    }

    // ── 一技能：奔涌 ────────────────────────────────────────────

    /**
     * 奔涌：在「激流 III」与「忠诚 III」之间**来回切换**身上所有三叉戟的附魔。
     * <p>
     * 每次释放都会先清空三叉戟的全部附魔，再按当前状态挂上另一种，所以
     * 第一次按 = 全换激流 III，第二次按 = 全换回忠诚 III，如此往复。
     * 改完物品后必须 {@code broadcastChanges()}，否则客户端背包里的附魔不会刷新。
     */
    private boolean doTide(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        List<ItemStack> trunks = new ArrayList<>();
        trunks.addAll(sp.getInventory().items);
        trunks.addAll(sp.getInventory().offhand);
        trunks.addAll(sp.getInventory().armor);

        int changed = 0;
        for (ItemStack stack : trunks) {
            if (stack.isEmpty() || !stack.is(Items.TRIDENT))
                continue;
            // 以「当前是否已有激流」决定这次换成哪一种：
            // 已有激流 → 换回忠诚；没有激流 → 换激流。
            boolean toRiptide = !hasEnchantment(stack, Enchantments.RIPTIDE);
            applyTridentEnchant(stack, level, toRiptide);
            changed++;
        }
        if (changed <= 0) {
            sp.displayClientMessage(Component
                    .translatable("message.noellesroles.pooler.tide_no_trident")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        // 物品 NBT 变了必须让客户端背包同步，否则手里看不到附魔变化
        sp.inventoryMenu.broadcastChanges();

        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.PLAYERS, 1.2F, 1.0F);
        level.sendParticles(ParticleTypes.SPLASH, sp.getX(), sp.getY() + 0.6D, sp.getZ(), 40, 1.2D, 0.6D, 1.2D,
                0.15D);
        level.sendParticles(ParticleTypes.BUBBLE, sp.getX(), sp.getY() + 0.8D, sp.getZ(), 24, 1.0D, 0.8D, 1.0D,
                0.05D);
        sp.displayClientMessage(Component
                .translatable("message.noellesroles.pooler.tide_used", changed)
                .withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    /** 判断物品身上是否带某个附魔。 */
    private static boolean hasEnchantment(ItemStack stack,
            net.minecraft.resources.ResourceKey<Enchantment> enchantment) {
        for (var entry : stack.getEnchantments().entrySet()) {
            if (entry.getKey().is(enchantment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 清空三叉戟的全部附魔，只挂上指定的一种：
     * {@code toRiptide = true} → 激流 III；{@code false} → 忠诚 III。
     * <p>
     * 关键顺序：**先**把目标附魔加上，**再**把不需要的附魔删掉。
     * 之前是「先 remove 再 enchant」，一旦 enchant 因为取不到 holder 而失败，
     * 三叉戟就会变成「附魔被清空但新附魔没加上」——正是游戏里看到的现象。
     */
    private static void applyTridentEnchant(ItemStack stack, ServerLevel level, boolean toRiptide) {
        var key = toRiptide ? Enchantments.RIPTIDE : Enchantments.LOYALTY;
        var opposite = toRiptide ? Enchantments.LOYALTY : Enchantments.RIPTIDE;

        // 1. 先加目标附魔（stack.enchant 是项目里已验证可用的写法）
        net.minecraft.core.Holder<Enchantment> target = level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(key)
                .orElse(null);
        if (target == null) {
            return; // 取不到就整个不动，绝不留下无附魔的三叉戟
        }
        stack.enchant(target, 3);

        // 2. 再把相反的附魔删掉（enchantments 是独立组件，需整表重写）
        var enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (enchantments.isEmpty()) {
            return;
        }
        var oppositeHolder = level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(opposite)
                .orElse(null);
        if (oppositeHolder == null || !enchantments.keySet().contains(oppositeHolder)) {
            return;
        }
        // ItemEnchantments 不可变，用 Mutable 重建后写回
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(enchantments);
        mutable.removeIf(h -> h.equals(oppositeHolder));
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
    }

    // ── 二技能：吞月 ────────────────────────────────────────────

    private boolean doMoon(ServerPlayer sp) {
        return PoolerDomainManager.openMoon(sp);
    }

    // ── 三技能：水中镜 ──────────────────────────────────────────

    private boolean doAquarium(ServerPlayer sp) {
        return PoolerDomainManager.openAquarium(sp);
    }

    // ── 四技能：称颂 ────────────────────────────────────────────

    /** 全体杀手阵营（{@code canUseKiller()}，含魔术师）获得海豚的恩惠 + 水下呼吸 20 秒。 */
    private boolean doPraise(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
        int buffed = 0;
        for (ServerPlayer target : level.getPlayers(this::isKillerCampPlayer)) {
            target.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, PRAISE_DURATION_TICKS, 0,
                    false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, PRAISE_DURATION_TICKS, 0,
                    false, false, true));
            target.displayClientMessage(Component
                    .translatable("message.noellesroles.pooler.praise_received")
                    .withStyle(ChatFormatting.AQUA), true);
            level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 1.2D, target.getZ(),
                    10, 0.4D, 0.5D, 0.4D, 0.01D);
            buffed++;
        }
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.2F);
        sp.displayClientMessage(Component
                .translatable("message.noellesroles.pooler.praise_used", buffed)
                .withStyle(ChatFormatting.AQUA), false);
        return buffed > 0;
    }

    /** 该玩家是否属于杀手阵营（魔术师也算）。 */
    private boolean isKillerCampPlayer(ServerPlayer candidate) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(candidate.level());
        if (gameWorld.isRole(candidate, ModRoles.MAGICIAN))
            return true;
        var role = gameWorld.getRole(candidate);
        return role != null && role.canUseKiller();
    }

    // ── 月之祝福（开局自带的物品）─────────────────────────────────

    /** 月之祝福是否可用：直接问原版物品冷却表。 */
    public boolean isMoonBlessingReady() {
        return !player.getCooldowns().isOnCooldown(ModItems.MOON_BLESSING);
    }

    /** 月之祝福剩余冷却秒数（读原版冷却表；0 表示就绪）。 */
    public int moonBlessingCooldownSeconds() {
        float left = player.getCooldowns().getCooldownPercent(ModItems.MOON_BLESSING, 0.0F);
        if (left <= 0.0F) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(left * (MOON_BLESSING_COOLDOWN_TICKS / 20.0D)));
    }

    /**
     * 月之祝福：海豚的恩惠 + 速度 II + 无碰撞，持续 15 秒；使用后 40 秒冷却。
     *
     * @return 是否成功使用
     */
    public static boolean useMoonBlessing(ServerPlayer sp) {
        PoolerRoleData data = RoleData.getNullable(PoolerRoleData.class, sp);
        if (!RoleData.isAttached(data))
            return false;
        if (!GameUtils.isPlayerAliveAndSurvival(sp))
            return false;
        if (!data.isMoonBlessingReady()) {
            sp.displayClientMessage(Component
                    .translatable("message.noellesroles.pooler.moon_blessing_cooldown",
                            data.moonBlessingCooldownSeconds())
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        sp.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, MOON_BLESSING_DURATION_TICKS, 0,
                false, false, true));
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MOON_BLESSING_DURATION_TICKS, 1,
                false, false, true));
        sp.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, MOON_BLESSING_DURATION_TICKS, 0,
                false, false, true));

        ServerLevel level = sp.serverLevel();
        // 冷却统一由原版物品冷却承担（MoonBlessingItem#use 里 addCooldown）
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 1.6F);
        level.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1.0D, sp.getZ(), 24, 0.6D, 0.8D, 0.6D,
                0.02D);
        data.sync();
        sp.displayClientMessage(Component
                .translatable("message.noellesroles.pooler.moon_blessing_used")
                .withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    // ── 疯狂模式（祈月者版：三叉戟 + 1 层盾）────────────────────

    /**
     * 开启祈月者的疯狂模式：
     * 手上一旦没有三叉戟就发一把（带忠诚 III），疯狂盾 1 层，
     * 疯狂期间三叉戟击杀只进入基础冷却的 1/6。已在疯狂中时返回 false。
     */
    public boolean startTideFrenzy() {
        if (!(player instanceof ServerPlayer sp))
            return false;
        SREPlayerPsychoComponent psycho = SREPlayerPsychoComponent.KEY.get(player);
        if (psycho.getPsychoTicks() > 0)
            return false;

        if (!sp.getMainHandItem().is(Items.TRIDENT)) {
            ItemStack trident = createBlessedTrident(sp.serverLevel());
            if (!sp.getInventory().getItem(0).isEmpty()) {
                // 槽位 0 被占用：找第一个空格子放三叉戟，并切到那一格
                int slot = sp.getInventory().getFreeSlot();
                if (slot >= 0 && slot < 9) {
                    sp.getInventory().setItem(slot, trident);
                    sp.getInventory().selected = slot;
                } else {
                    sp.setItemInHand(InteractionHand.MAIN_HAND, trident);
                }
            } else {
                sp.getInventory().setItem(0, trident);
                sp.getInventory().selected = 0;
            }
        }

        psycho.setPsychoTicks(GameConstants.getPsychoTimer());
        psycho.setArmour(1);
        psycho.type = 1;
        psycho.sync();
        SREGameWorldComponent.KEY.get(player.level()).refreshPsychoCount(true);

        this.tideFrenzy = true;
        sync();

        ServerLevel level = sp.serverLevel();
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1.4F, 0.6F);
        level.sendParticles(ParticleTypes.SPLASH, sp.getX(), sp.getY() + 1.0D, sp.getZ(), 50, 1.0D, 1.0D, 1.0D,
                0.2D);
        level.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1.2D, sp.getZ(), 24, 0.6D, 1.0D, 0.6D,
                0.02D);
        sp.displayClientMessage(Component
                .translatable("message.noellesroles.pooler.frenzy_start")
                .withStyle(ChatFormatting.DARK_AQUA), true);
        return true;
    }

    /** 同步疯狂状态（由 {@link SREPlayerPsychoComponent} 结束回调调用）。 */
    public void onFrenzyOver() {
        tideFrenzy = false;
        sync();
    }

    public boolean isTideFrenzy() {
        if (tideFrenzy) {
            SREPlayerPsychoComponent psycho = SREPlayerPsychoComponent.KEY.get(player);
            return psycho.getPsychoTicks() > 0;
        }
        return false;
    }

    /** 职业数据被清除时，若疯狂是祈月者开的，把它收掉，避免残留疯狂状态。 */
    private void yesRevertFrenzy() {
        if (!tideFrenzy)
            return;
        tideFrenzy = false;
        SREPlayerPsychoComponent psycho = SREPlayerPsychoComponent.KEY.get(player);
        if (psycho.getPsychoTicks() > 0 && psycho.type == 1) {
            psycho.stopPsycho(true);
        }
    }

    /** 生成一把「开局同款」的忠诚 III 三叉戟。 */
    public static ItemStack createBlessedTrident(ServerLevel level) {
        ItemStack stack = new ItemStack(Items.TRIDENT);
        stack.set(DataComponents.UNBREAKABLE, new net.minecraft.world.item.component.Unbreakable(true));
        net.minecraft.core.Holder<Enchantment> loyalty = level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT).holders()
                .filter(holder -> holder.is(Enchantments.LOYALTY))
                .findFirst()
                .orElse(null);
        if (loyalty != null) {
            stack.enchant(loyalty, 3);
        }
        return stack;
    }

    // ── 三叉戟击杀 → 物品冷却 ───────────────────────────────────

    /**
     * 三叉戟击杀后的物品冷却：基础 30 秒；
     * 处于水中镜领域内时 0（不进入冷却）；疯狂模式中为 1/6。
     */
    public static void applyTridentKillCooldown(ServerPlayer killer) {
        int ticks = TRIDENT_KILL_COOLDOWN_TICKS;
        PoolerRoleData data = RoleData.getNullable(PoolerRoleData.class, killer);
        if (RoleData.isAttached(data) && data.isTideFrenzy()) {
            ticks = TRIDENT_KILL_COOLDOWN_PSYCHO;
        } else if (isInAquarium(killer)) {
            return;
        }
        killer.getCooldowns().addCooldown(Items.TRIDENT, ticks);
    }

    /** 该玩家当前是否处在「水中镜」领域里。 */
    public static boolean isInAquarium(ServerPlayer player) {
        return PoolerDomainManager.domainKindOf(player.getUUID()) == PoolerDomainManager.Kind.AQUARIUM;
    }

    // ── 商店 ────────────────────────────────────────────────────

    /**
     * 商店「减少技能冷却」：把所有技能剩余冷却各减 40 秒。
     * <p>
     * 可购买次数由 {@link io.wifi.starrailexpress.cca.DynamicShopComponent} 的购买计数
     * 记账（见 {@code buildShopEntries()} 里 {@link DiscountShopEntry} 的
     * {@code maxDiscountCount}），本类不再自己存计数。
     */
    public boolean buyCooldownReduction() {
        if (!(player instanceof ServerPlayer sp))
            return false;
        reduceAllCooldowns(SHOP_CD_REDUCE_SECONDS);
        sp.displayClientMessage(Component
                .translatable("message.noellesroles.pooler.cd_reduced", SHOP_CD_REDUCE_SECONDS)
                .withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    // ── 判断 / tick ────────────────────────────────────────────

    private boolean isActivePooler(ServerPlayer sp) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        return gameWorld != null && gameWorld.isRole(sp, ModRoles.POOLER);
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp))
            return;
        if (!(player.level() instanceof ServerLevel level))
            return;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
        if (gameWorld == null || !gameWorld.isRunning() || !gameWorld.isRole(sp, ModRoles.POOLER))
            return;
        if (!GameUtils.isPlayerAliveAndSurvival(sp))
            return;

        // 疯狂模式结束由 onPsychoOver 处理，这里只兜底：盾碎了 / 被提前停掉
        if (tideFrenzy && SREPlayerPsychoComponent.KEY.get(sp).getPsychoTicks() <= 0) {
            tideFrenzy = false;
            sync();
        }

        // 领域里每秒给一点水下 / 月尘氛围粒子（每个玩家一簇，属于「每玩家一包」的例外）
        if (level.getGameTime() % 20 == 0) {
            PoolerDomainManager.Kind kind = PoolerDomainManager.domainKindOf(sp.getUUID());
            if (kind == PoolerDomainManager.Kind.MOON) {
                level.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1.4D, sp.getZ(),
                        2, 0.4D, 0.3D, 0.4D, 0.0D);
            } else if (kind == PoolerDomainManager.Kind.AQUARIUM) {
                level.sendParticles(ParticleTypes.BUBBLE, sp.getX(), sp.getY() + 1.2D, sp.getZ(),
                        3, 0.4D, 0.4D, 0.4D, 0.02D);
            }
        }
    }

    // ── 同步 ────────────────────────────────────────────────────

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putBoolean("tideFrenzy", tideFrenzy);
        CompoundTag cds = new CompoundTag();
        for (PoolerSkill skill : PoolerSkill.values()) {
            cds.putLong(skill.name(), skillReadyAt[skill.ordinal()]);
        }
        tag.put("skillReadyAt", cds);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        tideFrenzy = tag.getBoolean("tideFrenzy");
        CompoundTag cds = tag.getCompound("skillReadyAt");
        for (PoolerSkill skill : PoolerSkill.values()) {
            skillReadyAt[skill.ordinal()] = cds.getLong(skill.name());
        }
    }

    // ── 商店 ────────────────────────────────────────────────────

    /** 三叉戟基础价格。 */
    public static final int TRIDENT_PRICE = 130;
    /** 三叉戟每次购买降价的百分比（最多 2 次）。 */
    public static final int TRIDENT_DISCOUNT_PERCENT = 25;
    /** 「减少技能冷却」基础价格。 */
    public static final int CD_REDUCE_PRICE = 150;
    /** 「减少技能冷却」每次购买涨价的百分比（最多 2 次）。 */
    public static final int CD_REDUCE_MARKUP_PERCENT = 25;
    /** 三叉戟 / 减少技能冷却的价格阶梯最多生效次数（超过后价格保持不变）。 */
    public static final int PRICE_STEP_MAX_TIMES = 2;
    /** 疯狂模式（祈月者版）价格。 */
    public static final int FRENZY_PRICE = 325;

    /**
     * 祈月者的商店：三叉戟（130 金币，每次购买降 25%，最多降 2 次）、
     * 减少技能冷却 40 秒（150 金币，每次购买涨 25%，最多涨 2 次）、
     * 关灯 / 开锁器 / 撬棍（配置价）、疯狂模式（配置价，祈月者特殊版）。
     * <p>
     * 价格阶梯一律用项目既有的 {@link DiscountShopEntry}：{@code discount} 为负即加价，
     * {@code maxDiscountCount} 是「价格最多变化几次」而不是购买上限 —— 阶梯走完 2 次后
     * 仍可继续购买，只是价格固定在第 2 次的价格上。购买次数与显示价都由
     * {@code DynamicShopComponent} 记账，本类不再自己维护任何价格状态。
     */
    public static java.util.List<io.wifi.starrailexpress.util.ShopEntry> buildShopEntries() {
        java.util.List<io.wifi.starrailexpress.util.ShopEntry> shop = new java.util.ArrayList<>();

        // 三叉戟：给附魔版三叉戟，每次购买降价 25%（最多 2 次）
        shop.add(new DiscountShopEntry(Items.TRIDENT.getDefaultInstance(), TRIDENT_PRICE,
                TRIDENT_DISCOUNT_PERCENT, PRICE_STEP_MAX_TIMES, false,
                io.wifi.starrailexpress.util.ShopEntry.Type.WEAPON) {
            @Override
            public boolean onBuy(@NotNull Player buyer) {
                if (!(buyer instanceof ServerPlayer sp))
                    return false;
                ItemStack trident = createBlessedTrident(sp.serverLevel());
                // 先塞进背包，失败就不算购买（不记账、不扣钱、不涨价）
                if (!io.wifi.starrailexpress.util.SREItemUtils.insertStackInFreeSlot(sp, trident))
                    return false;
                super.applyPurchaseDiscount(buyer);
                return true;
            }
        });

        // 减少技能冷却 40 秒：每次购买涨价 25%（最多 2 次）
        // 图标沿用原版时钟，用自定义名称说明用途（原版「时钟」没有语义）
        ItemStack cdReduceIcon = new ItemStack(Items.CLOCK);
        cdReduceIcon.set(DataComponents.CUSTOM_NAME,
                Component.translatable("item.noellesroles.pooler_cd_reduce"));
        shop.add(new DiscountShopEntry(cdReduceIcon, CD_REDUCE_PRICE,
                -CD_REDUCE_MARKUP_PERCENT, PRICE_STEP_MAX_TIMES, false,
                io.wifi.starrailexpress.util.ShopEntry.Type.TOOL) {
            @Override
            public boolean canBuy(@NotNull Player buyer) {
                // 只管数据挂载；能不能买、价格多少都由 DiscountShopEntry +
                // DynamicShopComponent 决定（阶梯走完后仍可买，价格不再变化）。
                return RoleData.isAttached(of(buyer)) && super.canBuy(buyer);
            }

            @Override
            public boolean onBuy(@NotNull Player buyer) {
                PoolerRoleData data = of(buyer);
                if (!RoleData.isAttached(data))
                    return false;
                if (!data.buyCooldownReduction())
                    return false;
                super.applyPurchaseDiscount(buyer);
                return true;
            }
        });

        // 关灯（配置价 + 每次加价 20%，最多 3 次）
        shop.add(io.wifi.starrailexpress.game.ShopContent.getBlackoutShopEntry());
        // 开锁器 / 撬棍（配置价）
        shop.add(new io.wifi.starrailexpress.util.ShopEntry(
                TMMItems.LOCKPICK.getDefaultInstance(),
                io.wifi.starrailexpress.SREConfig.instance().lockpickPrice,
                io.wifi.starrailexpress.util.ShopEntry.Type.TOOL));
        shop.add(new io.wifi.starrailexpress.util.ShopEntry(
                TMMItems.CROWBAR.getDefaultInstance(),
                io.wifi.starrailexpress.SREConfig.instance().crowbarPrice,
                io.wifi.starrailexpress.util.ShopEntry.Type.TOOL));

        // 疯狂模式：祈月者特殊版（三叉戟 + 1 层疯狂盾）
        shop.add(new io.wifi.starrailexpress.util.ShopEntry(
                TMMItems.PSYCHO_MODE.getDefaultInstance(),
                io.wifi.starrailexpress.SREConfig.instance().psychoModePrice,
                io.wifi.starrailexpress.util.ShopEntry.Type.WEAPON) {
            @Override
            public boolean canBuy(@NotNull Player buyer) {
                if (buyer.getCooldowns().isOnCooldown(TMMItems.PSYCHO_MODE))
                    return false;
                return super.canBuy(buyer);
            }

            @Override
            public boolean onBuy(@NotNull Player buyer) {
                PoolerRoleData data = of(buyer);
                if (!RoleData.isAttached(data))
                    return false;
                if (!data.startTideFrenzy())
                    return false;
                buyer.getCooldowns().addCooldown(TMMItems.PSYCHO_MODE,
                        GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.PSYCHO_MODE, 20 * 60));
                return true;
            }
        });

        return shop;
    }

    /** 便于商店条目等外部代码消费（避免重复写 null 判断）。 */
    @Nullable
    public static PoolerRoleData of(Player player) {
        return RoleData.getNullable(PoolerRoleData.class, player);
    }
}
