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
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.role_data.innocence;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.entity.StarProjectileEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 摘星者（{@code star_gatherer}，警长阵营）的职业数据。
 * <p>
 * <b>被动 · 星之光环</b>：6 格半径内所有存活玩家获得潮涌能量 I + 领域庇护
 * （{@link ModEffects#DOMAIN_SANCTUARY}，持有者无法被拉入除愚者会议以外的任何领域）；
 * 摘星者死亡时立即清除全场所有庇护。
 * <p>
 * <b>星之权杖</b>（{@link ModItems#STAR_SCEPTER}）：右击锁定视线 35 格内最近的玩家，
 * 2 秒后召唤一颗能穿墙的追踪星星砸向目标（死因「星星」）。摘星者死亡后权杖变成左轮手枪。
 * <p>
 * <b>技能</b>（切换键 Y 切换、G 释放）：
 * <ul>
 * <li>庇护强化 —— 光环半径 6 → 10，持续 30 秒，冷却 90 秒。</li>
 * <li>星之引力 —— 一局 2 次，冷却 150 秒；自身定身 5 秒，随后吸取 16 格内所有非平民阵营
 * 玩家快捷栏里的金属物品（同类只留一件）。</li>
 * <li>施舍 —— 把手中的金属物品（三叉戟 / dream 铁斧除外）交给目标玩家。</li>
 * </ul>
 */
public class StarGathererRoleData extends SimpleRoleData {

    // ── 星之光环 ────────────────────────────────────────────────
    /** 光环基础半径。 */
    public static final double AURA_RADIUS = 6.0D;
    /** 「庇护强化」期间的光环半径。 */
    public static final double AURA_RADIUS_ENHANCED = 10.0D;
    /** 光环刷新间隔（tick）。 */
    public static final int AURA_REFRESH_TICKS = 20;
    /** 光环授予的庇护持续时间（比刷新间隔略长，避免闪断）。 */
    public static final int AURA_SANCTUARY_TICKS = AURA_REFRESH_TICKS + 10;

    // ── 星之权杖 ────────────────────────────────────────────────
    /** 权杖攻击距离（格）。 */
    public static final double SCEPTER_RANGE = 35.0D;
    /** 锁定后到星星出现的延迟（2 秒）。 */
    public static final int SCEPTER_DROP_DELAY_TICKS = 2 * 20;
    /** 权杖冷却（25 秒）。 */
    public static final int SCEPTER_COOLDOWN_TICKS = 25 * 20;

    // ── 技能冷却 ────────────────────────────────────────────────
    public static final int ENHANCE_COOLDOWN_TICKS = 90 * 20;
    public static final int GRAVITY_COOLDOWN_TICKS = 150 * 20;
    public static final int ALMS_COOLDOWN_TICKS = 30 * 20;
    /** 庇护强化持续时间。 */
    public static final int ENHANCE_DURATION_TICKS = 30 * 20;
    /** 星之引力一局限用次数。 */
    public static final int GRAVITY_MAX_USES = 2;
    /** 星之引力施法定身时间。 */
    public static final int GRAVITY_ROOT_TICKS = 5 * 20;
    /** 星之引力吸取半径。 */
    public static final double GRAVITY_RADIUS = 16.0D;

    /** 摘星者的三个可切换技能。 */
    public enum StarGathererSkill {
        ENHANCE("skill.noellesroles.star_gatherer.enhance"),
        GRAVITY("skill.noellesroles.star_gatherer.gravity"),
        ALMS("skill.noellesroles.star_gatherer.alms");

        /** 技能名翻译键。 */
        public final String nameKey;

        StarGathererSkill(String nameKey) {
            this.nameKey = nameKey;
        }
    }

    /** 当前选中的技能（切换键循环）。 */
    private int selectedSkill;
    /** 各技能冷却结束时刻（{@link GameUtils#getTicksFromGameStart}）。 */
    private final long[] skillReadyAt = new long[StarGathererSkill.values().length];
    /** 星之引力已使用次数。 */
    private int gravityUses;
    /** 庇护强化结束时刻（0 = 未强化）。 */
    private long enhanceUntil;
    /** 星之引力定身结束时刻。 */
    private long rootedUntil;
    /** 权杖冷却结束时刻。 */
    private long scepterReadyAt;
    /** 待降落的星星：目标 UUID → 剩余 tick。 */
    private final List<PendingStar> pendingStars = new ArrayList<>();

    /** 一颗待降落的星星。 */
    private record PendingStar(java.util.UUID target, int ticksLeft) {
    }

    public StarGathererRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public void init() {
        selectedSkill = 0;
        for (int i = 0; i < skillReadyAt.length; i++) {
            skillReadyAt[i] = 0L;
        }
        gravityUses = 0;
        enhanceUntil = 0L;
        rootedUntil = 0L;
        scepterReadyAt = 0L;
        pendingStars.clear();
        sync();
    }

    @Override
    public void clear() {
        // 职业数据被清除（换职业 / 对局结束）时，把庇护一起撤掉，避免残留给下一局
        if (player instanceof ServerPlayer sp) {
            clearAllSanctuary(sp.serverLevel());
        }
        init();
    }

    public Player getPlayer() {
        return player;
    }

    // ── 当前选中技能 ────────────────────────────────────────────

    public StarGathererSkill getSelectedSkill() {
        StarGathererSkill[] values = StarGathererSkill.values();
        return values[Math.floorMod(selectedSkill, values.length)];
    }

    /** 技能切换键：切到下一个技能。 */
    public StarGathererSkill cycleSkill(ServerPlayer sp) {
        StarGathererSkill[] values = StarGathererSkill.values();
        selectedSkill = Math.floorMod(selectedSkill + 1, values.length);
        sync();
        StarGathererSkill now = getSelectedSkill();
        sp.displayClientMessage(Component
                .translatable("message.noellesroles.star_gatherer.skill_switched",
                        Component.translatable(now.nameKey))
                .withStyle(ChatFormatting.AQUA), true);
        return now;
    }

    /** 某技能剩余冷却秒数；0 表示可用。 */
    public int cooldownSecondsLeft(StarGathererSkill skill) {
        long now = GameUtils.getTicksFromGameStart(player.level());
        long readyAt = skillReadyAt[skill.ordinal()];
        return (int) Math.max(0L, (readyAt - now + 19L) / 20L);
    }

    public boolean isSkillReady(StarGathererSkill skill) {
        return cooldownSecondsLeft(skill) <= 0;
    }

    /** 释放当前选中的技能（不带目标）。 */
    public boolean useSelectedSkill(ServerPlayer sp) {
        return tryUseSkill(sp, getSelectedSkill(), null);
    }

    /**
     * 释放指定技能。
     *
     * @param target 目标玩家（只有「施舍」需要）
     */
    public boolean tryUseSkill(ServerPlayer sp, StarGathererSkill skill, @Nullable ServerPlayer target) {
        if (!isActiveStarGatherer(sp))
            return false;
        if (!GameUtils.isPlayerAliveAndSurvival(sp))
            return false;
        if (GameUtils.isTimeFrozen(sp.level()))
            return false;

        long now = GameUtils.getTicksFromGameStart(sp.level());
        int left = cooldownSecondsLeft(skill);
        if (left > 0) {
            sp.displayClientMessage(Component
                    .translatable("message.noellesroles.star_gatherer.skill_on_cooldown", left)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (skill == StarGathererSkill.GRAVITY && gravityUses >= GRAVITY_MAX_USES) {
            sp.displayClientMessage(Component
                    .translatable("message.noellesroles.star_gatherer.gravity_exhausted")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        boolean success = switch (skill) {
            case ENHANCE -> doEnhance(sp);
            case GRAVITY -> doGravity(sp);
            case ALMS -> doAlms(sp, target);
        };
        if (!success)
            return false;

        long cooldown = switch (skill) {
            case ENHANCE -> ENHANCE_COOLDOWN_TICKS;
            case GRAVITY -> GRAVITY_COOLDOWN_TICKS;
            case ALMS -> ALMS_COOLDOWN_TICKS;
        };
        skillReadyAt[skill.ordinal()] = now + cooldown;
        if (skill == StarGathererSkill.GRAVITY) {
            gravityUses++;
        }
        sync();
        return true;
    }

    // ── 技能一：庇护强化 ────────────────────────────────────────

    /** 光环半径 6 → 10，持续 30 秒。 */
    private boolean doEnhance(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        enhanceUntil = GameUtils.getTicksFromGameStart(level) + ENHANCE_DURATION_TICKS;
        sync();
        refreshAura(sp);
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.6F);
        level.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1.2D, sp.getZ(),
                60, AURA_RADIUS_ENHANCED, 1.5D, AURA_RADIUS_ENHANCED, 0.02D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, sp.getX(), sp.getY() + 1.0D, sp.getZ(),
                40, 2.0D, 1.0D, 2.0D, 0.08D);
        sp.displayClientMessage(Component
                .translatable("message.noellesroles.star_gatherer.enhance_used", (int) AURA_RADIUS_ENHANCED)
                .withStyle(ChatFormatting.AQUA), false);
        return true;
    }

    // ── 技能二：星之引力 ────────────────────────────────────────

    /**
     * 自身定身 {@value #GRAVITY_ROOT_TICKS} tick，随后吸取 16 格内所有非平民阵营玩家快捷栏中的金属物品。
     * <p>
     * 吸取在定身结束时执行 —— 先把「吸取清单」记在 {@link #gravityPullAt}，
     * 由 {@link #serverTick()} 到点触发。为了简单可靠，这里直接在同一次调用里
     * 排入定身并在定身结束的那一 tick 执行吸取。
     */
    private boolean doGravity(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        rootedUntil = GameUtils.getTicksFromGameStart(level) + GRAVITY_ROOT_TICKS;
        pullPending = true;
        sync();
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8F, 1.4F);
        level.sendParticles(ParticleTypes.PORTAL, sp.getX(), sp.getY() + 1.0D, sp.getZ(),
                60, 2.0D, 2.0D, 2.0D, 0.3D);
        sp.displayClientMessage(Component
                .translatable("message.noellesroles.star_gatherer.gravity_used")
                .withStyle(ChatFormatting.AQUA), false);
        return true;
    }

    /** 定身结束后是否还有一次待执行的吸取。 */
    private boolean pullPending;

    /**
     * 执行吸取：把 16 格内所有<b>非平民阵营</b>玩家快捷栏里的金属物品拿到自己快捷栏，
     * 同类物品只保留一件，多余的直接丢弃。
     */
    private void executePull(ServerPlayer sp) {
        pullPending = false;
        ServerLevel level = sp.serverLevel();
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
        int stolen = 0;
        for (ServerPlayer other : level.getEntitiesOfClass(ServerPlayer.class,
                sp.getBoundingBox().inflate(GRAVITY_RADIUS),
                p -> p != sp && GameUtils.isPlayerAliveAndSurvival(p) && !p.isSpectator())) {
            // 只吸「非平民阵营」：警长 / 杀手 / 中立都算
            if (gameWorld.isInnocentTeam(other))
                continue;
            for (int slot = 0; slot < 9; slot++) {
                ItemStack stack = other.getInventory().getItem(slot);
                if (stack.isEmpty() || !isMetalItem(stack))
                    continue;
                other.getInventory().setItem(slot, ItemStack.EMPTY);
                // 同类只留一件；已经有一件的话就直接丢弃
                if (SREItemUtils.countItem(sp, stack.getItem()) > 0 || !SREItemUtils.insertStackInFreeSlot(sp, stack.copy())) {
                    // 丢弃：掉在原地，不进入任何人的背包
                    other.drop(stack.copy(), false);
                } else {
                    stolen++;
                }
                other.containerMenu.broadcastChanges();
            }
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, other.getX(), other.getY() + 1.0D, other.getZ(),
                    20, 0.6D, 0.8D, 0.6D, 0.1D);
        }
        sp.containerMenu.broadcastChanges();
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 0.6F);
        sp.displayClientMessage(Component
                .translatable("message.noellesroles.star_gatherer.gravity_pulled", stolen)
                .withStyle(ChatFormatting.AQUA), false);
    }

    // ── 技能三：施舍 ────────────────────────────────────────────

    /**
     * 把手中除三叉戟与 dream 铁斧以外的金属物品交给目标玩家。
     */
    private boolean doAlms(ServerPlayer sp, @Nullable ServerPlayer target) {
        if (target == null) {
            sp.displayClientMessage(Component
                    .translatable("message.noellesroles.star_gatherer.alms_no_target")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (target == sp) {
            sp.displayClientMessage(Component
                    .translatable("message.noellesroles.star_gatherer.alms_self")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ItemStack hand = sp.getMainHandItem();
        if (hand.isEmpty() || !isMetalItem(hand)) {
            sp.displayClientMessage(Component
                    .translatable("message.noellesroles.star_gatherer.alms_not_metal")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (hand.is(Items.TRIDENT) || hand.is(ModItems.DREAM_AXE)) {
            sp.displayClientMessage(Component
                    .translatable("message.noellesroles.star_gatherer.alms_excluded")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!SREItemUtils.insertStackInFreeSlot(target, hand.copy())) {
            sp.displayClientMessage(Component
                    .translatable("message.noellesroles.star_gatherer.alms_target_full")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ServerLevel level = sp.serverLevel();
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 1.2D, target.getZ(),
                16, 0.4D, 0.5D, 0.4D, 0.02D);
        sp.containerMenu.broadcastChanges();
        target.containerMenu.broadcastChanges();
        target.displayClientMessage(Component
                .translatable("message.noellesroles.star_gatherer.alms_received", sp.getName(),
                        hand.getHoverName())
                .withStyle(ChatFormatting.GOLD), false);
        sp.displayClientMessage(Component
                .translatable("message.noellesroles.star_gatherer.alms_used", target.getName(),
                        hand.getHoverName())
                .withStyle(ChatFormatting.AQUA), false);
        return true;
    }

    /**
     * 「金属物品」清单：刀、左轮手枪、制式左轮、三叉戟、dream 铁斧、
     * 飞斧、飞刀、开锁器、劣质开锁器、撬棍、短管霰弹枪。
     */
    public static boolean isMetalItem(ItemStack stack) {
        if (stack.isEmpty())
            return false;
        return stack.is(TMMItems.KNIFE)
                || stack.is(TMMItems.REVOLVER)
                || stack.is(TMMItems.STANDARD_REVOLVER)
                || stack.is(Items.TRIDENT)
                || stack.is(ModItems.DREAM_AXE)
                || stack.is(ModItems.THROWING_AXE)
                || stack.is(ModItems.THROWING_KNIFE)
                || stack.is(TMMItems.LOCKPICK)
                || stack.is(ModItems.INFERIOR_LOCKPICK)
                || stack.is(TMMItems.CROWBAR)
                || stack.is(ModItems.SHORT_SHOTGUN);
    }

    // ── 星之光环（被动）──────────────────────────────────────────

    /** 当前光环半径（庇护强化中为 10，否则 6）。 */
    public double currentAuraRadius() {
        long now = GameUtils.getTicksFromGameStart(player.level());
        return now < enhanceUntil ? AURA_RADIUS_ENHANCED : AURA_RADIUS;
    }

    /** 给光环内的所有存活玩家补潮涌能量 + 领域庇护；光环外的庇护不主动撤销（等自然过期）。 */
    private void refreshAura(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        double radius = currentAuraRadius();
        List<ServerPlayer> inRange = level.getEntitiesOfClass(ServerPlayer.class,
                sp.getBoundingBox().inflate(radius),
                p -> GameUtils.isPlayerAliveAndSurvival(p) && !p.isSpectator()
                        && p.distanceTo(sp) <= radius);
        for (ServerPlayer target : inRange) {
            target.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, AURA_SANCTUARY_TICKS, 0,
                    false, false, true));
            target.addEffect(new MobEffectInstance(ModEffects.DOMAIN_SANCTUARY, AURA_SANCTUARY_TICKS, 0,
                    false, false, true));
        }
        // 光环边缘的呼吸感粒子（每 2 秒一次）
        if (level.getGameTime() % 40 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1.0D, sp.getZ(),
                    24, radius * 0.85D, 0.6D, radius * 0.85D, 0.0D);
        }
    }

    /** 清除全场所有「领域庇护」—— 摘星者死亡时调用。 */
    public static void clearAllSanctuary(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (p.hasEffect(ModEffects.DOMAIN_SANCTUARY)) {
                p.removeEffect(ModEffects.DOMAIN_SANCTUARY);
            }
        }
    }

    // ── 星之权杖 ────────────────────────────────────────────────

    public boolean isScepterReady() {
        return GameUtils.getTicksFromGameStart(player.level()) >= scepterReadyAt;
    }

    /**
     * 使用星之权杖：锁定视线方向 {@value #SCEPTER_RANGE} 格内最近的玩家，
     * {@value #SCEPTER_DROP_DELAY_TICKS} tick 后星星从高空砸下。
     */
    public static boolean useScepter(ServerPlayer sp) {
        StarGathererRoleData data = RoleData.getNullable(StarGathererRoleData.class, sp);
        if (!RoleData.isAttached(data))
            return false;
        if (!GameUtils.isPlayerAliveAndSurvival(sp))
            return false;
        if (GameUtils.isTimeFrozen(sp.level()))
            return false;
        if (!data.isScepterReady()) {
            long now = GameUtils.getTicksFromGameStart(sp.level());
            int left = (int) Math.max(0L, (data.scepterReadyAt - now + 19L) / 20L);
            sp.displayClientMessage(Component
                    .translatable("message.noellesroles.star_gatherer.scepter_cooldown", left)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        ServerPlayer target = findScepterTarget(sp);
        if (target == null) {
            sp.displayClientMessage(Component
                    .translatable("message.noellesroles.star_gatherer.scepter_no_target")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        ServerLevel level = sp.serverLevel();
        data.pendingStars.add(new PendingStar(target.getUUID(), SCEPTER_DROP_DELAY_TICKS));
        data.scepterReadyAt = GameUtils.getTicksFromGameStart(level) + SCEPTER_COOLDOWN_TICKS;
        data.sync();

        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.4F, 1.2F);
        level.sendParticles(ParticleTypes.ENCHANT, target.getX(), target.getY() + 1.0D, target.getZ(),
                30, 0.6D, 1.0D, 0.6D, 0.4D);
        sp.displayClientMessage(Component
                .translatable("message.noellesroles.star_gatherer.scepter_locked", target.getName())
                .withStyle(ChatFormatting.AQUA), false);
        return true;
    }

    /** 视线方向 35 格内、夹角最小的存活玩家。 */
    @Nullable
    private static ServerPlayer findScepterTarget(ServerPlayer sp) {
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getLookAngle().normalize();
        ServerPlayer best = null;
        double bestDot = 0.94D; // 约 20 度锥形
        for (ServerPlayer candidate : sp.serverLevel().getEntitiesOfClass(ServerPlayer.class,
                sp.getBoundingBox().inflate(SCEPTER_RANGE),
                p -> p != sp && GameUtils.isPlayerAliveAndSurvival(p) && !p.isSpectator())) {
            Vec3 to = candidate.getEyePosition().subtract(eye);
            double dist = to.length();
            if (dist > SCEPTER_RANGE || dist < 0.001D)
                continue;
            double dot = to.normalize().dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = candidate;
            }
        }
        return best;
    }

    // ── 判断 / tick ────────────────────────────────────────────

    private boolean isActiveStarGatherer(ServerPlayer sp) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        return gameWorld != null && gameWorld.isRole(sp, ModRoles.STAR_GATHERER);
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp))
            return;
        if (!(player.level() instanceof ServerLevel level))
            return;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
        if (gameWorld == null || !gameWorld.isRunning() || !gameWorld.isRole(sp, ModRoles.STAR_GATHERER))
            return;

        boolean alive = GameUtils.isPlayerAliveAndSurvival(sp);

        // 摘星者死亡 → 立刻撤掉全场庇护
        if (!alive) {
            clearAllSanctuary(level);
            pendingStars.clear();
            return;
        }

        long now = GameUtils.getTicksFromGameStart(level);

        // 光环刷新
        if (level.getGameTime() % AURA_REFRESH_TICKS == 0) {
            refreshAura(sp);
        }

        // 星之引力定身
        if (now < rootedUntil) {
            sp.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 10, 0, false, false, false));
            if (level.getGameTime() % 5 == 0) {
                level.sendParticles(ParticleTypes.PORTAL, sp.getX(), sp.getY() + 1.0D, sp.getZ(),
                        8, 0.6D, 0.8D, 0.6D, 0.1D);
            }
        } else if (pullPending) {
            executePull(sp);
        }

        // 待降落的星星
        if (!pendingStars.isEmpty()) {
            List<PendingStar> remaining = new ArrayList<>(pendingStars.size());
            for (PendingStar star : pendingStars) {
                int left = star.ticksLeft() - 1;
                if (left > 0) {
                    remaining.add(new PendingStar(star.target(), left));
                    continue;
                }
                ServerPlayer target = level.getServer().getPlayerList().getPlayer(star.target());
                if (target == null || !GameUtils.isPlayerAliveAndSurvival(target)) {
                    continue;
                }
                // 星星从目标上方 24 格的高空落下
                Vec3 from = new Vec3(target.getX(), target.getY() + 24.0D, target.getZ());
                StarProjectileEntity.spawn(level, from, target, sp);
                level.playSound(null, target.blockPosition(), SoundEvents.BEACON_DEACTIVATE,
                        SoundSource.PLAYERS, 1.2F, 0.7F);
            }
            pendingStars.clear();
            pendingStars.addAll(remaining);
        }
    }

    // ── 同步 ────────────────────────────────────────────────────

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putInt("selectedSkill", selectedSkill);
        tag.putInt("gravityUses", gravityUses);
        tag.putLong("enhanceUntil", enhanceUntil);
        tag.putLong("rootedUntil", rootedUntil);
        tag.putLong("scepterReadyAt", scepterReadyAt);
        CompoundTag cds = new CompoundTag();
        for (StarGathererSkill skill : StarGathererSkill.values()) {
            cds.putLong(skill.name(), skillReadyAt[skill.ordinal()]);
        }
        tag.put("skillReadyAt", cds);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        selectedSkill = tag.getInt("selectedSkill");
        gravityUses = tag.getInt("gravityUses");
        enhanceUntil = tag.getLong("enhanceUntil");
        rootedUntil = tag.getLong("rootedUntil");
        scepterReadyAt = tag.getLong("scepterReadyAt");
        CompoundTag cds = tag.getCompound("skillReadyAt");
        for (StarGathererSkill skill : StarGathererSkill.values()) {
            skillReadyAt[skill.ordinal()] = cds.getLong(skill.name());
        }
    }

    // ── 事件：权杖变左轮 / 对局结束清理星星 ─────────────────────

    /** 事件只注册一次。 */
    private static boolean eventsRegistered;

    /**
     * 注册两条和摘星者相关的事件（幂等）：
     * <ul>
     * <li>摘星者死亡 → 背包里的星之权杖立刻替换成左轮手枪；
     * 同时清掉全场「领域庇护」。</li>
     * <li>对局结束 → 清掉场上所有未命中的星星实体。</li>
     * </ul>
     */
    public static void registerEvents() {
        if (eventsRegistered)
            return;
        eventsRegistered = true;

        // 摘星者死亡：权杖（在背包里 / 已掉落成物品）换成左轮手枪 + 撤销全场庇护
        io.wifi.starrailexpress.event.OnPlayerDeath.EVENT.register((deadPlayer, deathReason) -> {
            if (!(deadPlayer instanceof ServerPlayer sp))
                return;
            if (!(sp.level() instanceof ServerLevel level))
                return;
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
            if (gameWorld == null || !gameWorld.isRole(sp, ModRoles.STAR_GATHERER))
                return;

            // 背包里的权杖 → 左轮手枪
            var inventory = sp.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty() && stack.is(ModItems.STAR_SCEPTER)) {
                    inventory.setItem(slot, TMMItems.REVOLVER.getDefaultInstance());
                }
            }
            // 掉在地上的权杖 → 左轮手枪
            for (var dropped : level.getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class,
                    sp.getBoundingBox().inflate(8.0D),
                    e -> e.getItem().is(ModItems.STAR_SCEPTER))) {
                dropped.setItem(TMMItems.REVOLVER.getDefaultInstance());
            }

            // 摘星者死亡 → 立即移除所有庇护
            clearAllSanctuary(level);
        });

        // 对局结束 / 回到大厅：清掉所有星星
        io.wifi.starrailexpress.event.OnGameEnd.EVENT.register((serverLevel, gameWorldComponent) -> {
            for (StarProjectileEntity star : serverLevel.getEntitiesOfClass(
                    StarProjectileEntity.class,
                    new net.minecraft.world.phys.AABB(-3.0E7D, -512.0D, -3.0E7D,
                            3.0E7D, 512.0D, 3.0E7D),
                    e -> true)) {
                star.discard();
            }
        });
    }
}
