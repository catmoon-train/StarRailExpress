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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.agmas.noellesroles.game.roles.killer.pooler.PoolerDomainManager;
import org.agmas.noellesroles.init.ModEffects;
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

    /** 当前选中的技能（由「技能切换键」Y 循环）。 */
    private int selectedSkill;

    /** 四个技能各自的冷却结束时刻（{@link GameUtils#getTicksFromGameStart}）。 */
    private final long[] skillReadyAt = new long[PoolerSkill.values().length];

    /** 月之祝福的冷却结束时刻。 */
    private long moonBlessingReadyAt;

    /** 疯狂模式是否由本职业开启（用于结束时还原手持的三叉戟）。 */
    private boolean tideFrenzy;

    public PoolerRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public void init() {
        selectedSkill = 0;
        for (int i = 0; i < skillReadyAt.length; i++) {
            skillReadyAt[i] = 0L;
        }
        moonBlessingReadyAt = 0L;
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

    // ── 当前选中技能 ────────────────────────────────────────────

    public PoolerSkill getSelectedSkill() {
        PoolerSkill[] values = PoolerSkill.values();
        return values[Math.floorMod(selectedSkill, values.length)];
    }

    /** 技能切换键：切到下一个技能。返回切换后的技能。 */
    public PoolerSkill cycleSkill(ServerPlayer serverPlayer) {
        PoolerSkill[] values = PoolerSkill.values();
        selectedSkill = Math.floorMod(selectedSkill + 1, values.length);
        sync();
        PoolerSkill now = getSelectedSkill();
        serverPlayer.displayClientMessage(Component
                .translatable("message.noellesroles.pooler.skill_switched",
                        Component.translatable(now.nameKey))
                .withStyle(ChatFormatting.AQUA), true);
        return now;
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
        moonBlessingReadyAt = Math.max(now, moonBlessingReadyAt - cut);
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

    /** 把身上所有三叉戟换成「激流 III」（不保留其它附魔），并播放海潮特效。 */
    private boolean doTide(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        int changed = 0;
        List<ItemStack> trunks = new ArrayList<>();
        trunks.addAll(sp.getInventory().items);
        trunks.addAll(sp.getInventory().offhand);
        trunks.addAll(sp.getInventory().armor);
        for (ItemStack stack : trunks) {
            if (stack.isEmpty() || !stack.is(Items.TRIDENT))
                continue;
            applyRiptide(stack, level);
            changed++;
        }
        if (changed <= 0) {
            sp.displayClientMessage(Component
                    .translatable("message.noellesroles.pooler.tide_no_trident")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

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

    /** 清空三叉戟的所有附魔，只保留激流 III。 */
    private static void applyRiptide(ItemStack stack, ServerLevel level) {
        stack.remove(DataComponents.ENCHANTMENTS);
        net.minecraft.core.Holder<Enchantment> riptide = level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT).holders()
                .filter(holder -> holder.is(Enchantments.RIPTIDE))
                .findFirst()
                .orElse(null);
        if (riptide != null) {
            stack.enchant(riptide, 3);
        }
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

    public boolean isMoonBlessingReady() {
        return GameUtils.getTicksFromGameStart(player.level()) >= moonBlessingReadyAt;
    }

    public int moonBlessingCooldownSeconds() {
        long now = GameUtils.getTicksFromGameStart(player.level());
        return (int) Math.max(0L, (moonBlessingReadyAt - now + 19L) / 20L);
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
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 1.6F);
        level.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1.0D, sp.getZ(), 24, 0.6D, 0.8D, 0.6D,
                0.02D);
        data.moonBlessingReadyAt = GameUtils.getTicksFromGameStart(level) + MOON_BLESSING_COOLDOWN_TICKS;
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

    // ── 商店用的小工具 ──────────────────────────────────────────

    /** 商店「减少技能冷却」条目：最多买 2 次。 */
    public boolean buyCooldownReduction() {
        if (!(player instanceof ServerPlayer sp))
            return false;
        if (cooldownReductionBought >= 2) {
            sp.displayClientMessage(Component
                    .translatable("message.noellesroles.pooler.cd_reduce_max")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        cooldownReductionBought++;
        reduceAllCooldowns(SHOP_CD_REDUCE_SECONDS);
        sp.displayClientMessage(Component
                .translatable("message.noellesroles.pooler.cd_reduced", SHOP_CD_REDUCE_SECONDS)
                .withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    /** 已购买「减少技能冷却」的次数。 */
    public int cooldownReductionBought;

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
        tag.putInt("selectedSkill", selectedSkill);
        tag.putLong("moonBlessingReadyAt", moonBlessingReadyAt);
        tag.putBoolean("tideFrenzy", tideFrenzy);
        tag.putInt("cooldownReductionBought", cooldownReductionBought);
        CompoundTag cds = new CompoundTag();
        for (PoolerSkill skill : PoolerSkill.values()) {
            cds.putLong(skill.name(), skillReadyAt[skill.ordinal()]);
        }
        tag.put("skillReadyAt", cds);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        selectedSkill = tag.getInt("selectedSkill");
        moonBlessingReadyAt = tag.getLong("moonBlessingReadyAt");
        tideFrenzy = tag.getBoolean("tideFrenzy");
        cooldownReductionBought = tag.getInt("cooldownReductionBought");
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
    /** 疯狂模式（祈月者版）价格。 */
    public static final int FRENZY_PRICE = 325;

    /**
     * 祈月者的商店：三叉戟（130 金币，每买一次降 25，最多降 2 次）、
     * 减少技能冷却 40 秒（150 金币，每买一次涨 25%，最多涨 2 次）、
     * 关灯 / 开锁器 / 撬棍（配置价）、疯狂模式（配置价，祈月者特殊版）。
     */
    public static java.util.List<io.wifi.starrailexpress.util.ShopEntry> buildShopEntries() {
        java.util.List<io.wifi.starrailexpress.util.ShopEntry> shop = new java.util.ArrayList<>();

        // 三叉戟：每次购买后降价 25 金币，最多降价 2 次
        shop.add(new io.wifi.starrailexpress.util.ShopEntry(
                new ItemStack(Items.TRIDENT), TRIDENT_PRICE,
                io.wifi.starrailexpress.util.ShopEntry.Type.WEAPON) {
            @Override
            public boolean onBuy(@NotNull Player buyer) {
                if (!(buyer instanceof ServerPlayer sp))
                    return false;
                ItemStack trident = createBlessedTrident(sp.serverLevel());
                if (!io.wifi.starrailexpress.util.SREItemUtils.insertStackInFreeSlot(sp, trident))
                    return false;
                applyFlatPriceStep(sp, trident.getDescriptionId(), -TRIDENT_DISCOUNT_PERCENT, 2);
                return true;
            }
        });

        // 减少技能冷却 40 秒：每次购买后涨价 25%，最多涨价 2 次
        shop.add(new io.wifi.starrailexpress.util.ShopEntry(
                new ItemStack(Items.CLOCK), CD_REDUCE_PRICE,
                io.wifi.starrailexpress.util.ShopEntry.Type.TOOL) {
            @Override
            public boolean canBuy(@NotNull Player buyer) {
                PoolerRoleData data = of(buyer);
                if (!RoleData.isAttached(data) || data.cooldownReductionBought >= 2)
                    return false;
                return super.canBuy(buyer);
            }

            @Override
            public boolean onBuy(@NotNull Player buyer) {
                PoolerRoleData data = of(buyer);
                if (!RoleData.isAttached(data))
                    return false;
                if (!data.buyCooldownReduction())
                    return false;
                applyFlatPriceStep(buyer, "noellesroles:pooler_cd_reduce",
                        CD_REDUCE_MARKUP_PERCENT, 2);
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

    /**
     * 按「购买次数」给某个价格键挂固定增减价（{@code percent} 为负表示降价）。
     * 用 {@link io.wifi.starrailexpress.cca.DynamicShopComponent} 记录购买次数，
     * 商店 UI 与服务端扣费读的都是同一个实际价格。
     */
    private static void applyFlatPriceStep(Player buyer, String priceKey, int percent, int maxTimes) {
        if (!(buyer instanceof ServerPlayer sp))
            return;
        io.wifi.starrailexpress.cca.DynamicShopComponent dynamicShop =
                io.wifi.starrailexpress.cca.DynamicShopComponent.KEY.get(sp);
        int bought = dynamicShop.getPurchaseCount(priceKey);
        if (bought < maxTimes) {
            dynamicShop.setPercentDiscount(priceKey, -percent * (bought + 1));
        }
        dynamicShop.recordPurchase(priceKey);
    }

    /** 便于商店条目等外部代码消费（避免重复写 null 判断）。 */
    @Nullable
    public static PoolerRoleData of(Player player) {
        return RoleData.getNullable(PoolerRoleData.class, player);
    }
}
