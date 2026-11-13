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
package org.agmas.noellesroles.role_data.killer;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ParticleFx;
import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.roles.killer.ma_chen_xu.GhostArt;
import org.agmas.noellesroles.game.roles.killer.ma_chen_xu.SackGhostItems;
import org.agmas.noellesroles.game.roles.killer.ma_chen_xu.SackGhostRules;
import org.agmas.noellesroles.game.roles.killer.ma_chen_xu.SackGhostPackets;
import org.agmas.noellesroles.game.roles.killer.ma_chen_xu.SackGhostVerdicts;
import org.agmas.noellesroles.api.time.TimeRewind;
import org.agmas.noellesroles.game.roles.killer.ma_chen_xu.SackGhostRewind;
import java.util.HashSet;
import java.util.Set;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.MoneyUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 四阶段诡物成长与里世界判死的服务端权威状态；不再存在自动小诡术或七套独立冷却。 */
public class MaChenXuRoleData extends SimpleRoleData {
    public static final int ULTIMATE_DURATION_STAGE_3 = 600;
    public static final int ULTIMATE_DURATION_STAGE_4 = 800;
    public static final double[] STAGE_RANGE = {0, 30, 40, 50, 50};
    private static final double[] EROSION = {0, 0.00032, 0.00052, 0.00072, 0.00092};
    private static final int OPENING_LOCK = 40;
    private static final int GLOW_INTERVAL = 15 * 20;

    public int stage = 1;
    public int totalSanLoss;
    public int initialPlayers = 1;
    public int STAGE_2_THRESHOLD = 90;
    public int STAGE_3_THRESHOLD = 220;
    public int STAGE_4_THRESHOLD = 400;
    public int generationCooldown;
    public int castCooldown;
    public int ultimateCooldown;
    public int realmHitCooldown;
    public boolean otherworldActive;
    public int otherworldTimer;
    public int otherworldDuration;
    public int otherworldTotalTicks;
    public final SackGhostVerdicts verdicts = new SackGhostVerdicts();
    public final List<String> ghostSkills = new ArrayList<>();

    private UUID sessionId = UUID.randomUUID();
    private double fear;
    private boolean started;
    private boolean previousInvulnerable;
    private long activeTicks;
    private ServerLevel realmLevel;
    private long generationReadyAt;
    private long castReadyAt;
    private long realmReadyAt;
    private long hitReadyAt;
    private Vec3 effigyPosition;
    private int effigyTicks;
    private UUID effigyEntity;
    private ServerLevel effigyLevel;
    private final Set<UUID> moodDirty = new HashSet<>();
    private final Map<UUID, Integer> parasites = new HashMap<>();
    private final Map<UUID, Map<Holder<MobEffect>, Lease>> realmEffects = new LinkedHashMap<>();
    private record Lease(MobEffectInstance previous, long capturedAt, int amplifier, long expectedEnd, long activeUntil) {}

    public MaChenXuRoleData(RoleDataContext context) {
        super(context);
        refreshSkills();
    }

    public UUID sessionId() { return sessionId; }
    private long now() { return activeTicks; }

    public boolean isActiveMaChenXu() {
        return stage > 0 && SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.MA_CHEN_XU);
    }

    private boolean matchActive() {
        return SREGameWorldComponent.KEY.get(player.level()).getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE;
    }

    private boolean clockRunning() {
        return matchActive() && SREGameWorldComponent.KEY.get(player.level()).isSkillAvailable
                && !SREGameTimeComponent.KEY.get(player.level()).isTimeFrozen();
    }

    private boolean canCast() {
        return isActiveMaChenXu() && clockRunning() && GameUtils.isPlayerAliveAndSurvival(player)
                && !player.hasEffect(ModEffects.SKILL_BANED) && !player.hasEffect(ModEffects.USED_BANED);
    }

    public boolean isEnemy(Player target) {
        return target != player && target.level() == player.level() && GameUtils.isPlayerAliveAndSurvival(target)
                && !SREGameWorldComponent.KEY.get(player.level()).isKillerTeam(target);
    }

    private void refreshSkills() {
        ghostSkills.clear();
        for (GhostArt art : GhostArt.unlocked(stage)) ghostSkills.add(art.id);
    }

    private void initializeRound(ServerPlayer owner) {
        started = true;
        initialPlayers = SackGhostItems.initialPlayers(owner.serverLevel());
        STAGE_2_THRESHOLD = SackGhostRules.stageThreshold(2, initialPlayers);
        STAGE_3_THRESHOLD = SackGhostRules.stageThreshold(3, initialPlayers);
        STAGE_4_THRESHOLD = SackGhostRules.stageThreshold(4, initialPlayers);
        MoneyUtils.setBalance(owner, 50);
        generationReadyAt = now() + SackGhostRules.generationCooldownSeconds(initialPlayers) * 20L;
        // 初始两件只给一次，不会因领域结束、冷却结束或重连重新补发。
        giveArt(owner, GhostArt.VEIL);
        giveArt(owner, GhostArt.PUSH);
        sync();
    }

    private boolean giveArt(ServerPlayer owner, GhostArt art) {
        if (SackGhostItems.artCount(owner) >= SackGhostRules.MAX_ART_ITEMS) return false;
        ItemStack stack = SackGhostItems.createArt(owner, sessionId, art);
        return owner.getInventory().add(stack);
    }

    public boolean drawArt() {
        if (!(player instanceof ServerPlayer owner) || !canCast()) return false;
        updateCooldowns();
        if (generationCooldown > 0) return fail("message.noellesroles.sack.cooldown", (generationCooldown + 19) / 20);
        if (SackGhostItems.artCount(owner) >= SackGhostRules.MAX_ART_ITEMS) return fail("message.noellesroles.sack.full");
        var pool = GhostArt.unlocked(stage);
        GhostArt art = pool.get(owner.getRandom().nextInt(pool.size()));
        if (!giveArt(owner, art)) return fail("message.noellesroles.sack.inventory_full");
        generationReadyAt = now() + SackGhostRules.generationCooldownSeconds(initialPlayers) * 20L;
        owner.displayClientMessage(Component.translatable("message.noellesroles.sack.drawn", Component.translatable(art.nameKey())), true);
        owner.playNotifySound(SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.7F, 0.75F);
        updateCooldowns();
        sync();
        return true;
    }

    public boolean useArt(ItemStack stack, GhostArt art) {
        if (!(player instanceof ServerPlayer owner) || !canCast() || art.stage > stage
                || !SackGhostItems.validArt(owner, stack, sessionId)) return false;
        if (SackGhostItems.artCount(owner) > SackGhostRules.MAX_ART_ITEMS) return fail("message.noellesroles.sack.full");
        updateCooldowns();
        if (castCooldown > 0) return fail("message.noellesroles.sack.cast_wait");
        boolean used = switch (art) {
            case VEIL -> castVeil(owner);
            case PUSH -> castPush(owner);
            case EFFIGY -> castEffigy(owner);
            case ECHO -> castEcho(owner);
            case WAIL -> castWail(owner);
            case PARASITE -> castParasite(owner);
            case SEIZE -> castSeize(owner);
        };
        if (!used) return false;
        SackGhostItems.consumeArt(owner, stack);
        castReadyAt = now() + SackGhostRules.CAST_INTERVAL;
        owner.getInventory().setChanged();
        owner.containerMenu.broadcastChanges();
        updateCooldowns();
        sync();
        return true;
    }

    /** 只累计真实SAN降幅；护心、满SAN锁和已归零者不会贡献虚假进阶。 */
    public double drainSan(Player target, double points) {
        if (!isEnemy(target) || points <= 0 || target.hasEffect(ModEffects.GHOST_WARD)) return 0;
        var mood = SREPlayerMoodComponent.KEY.get(target);
        float before = mood.getMood();
        if (!Float.isFinite(before) || before <= 0) return 0;
        mood.addMood(-(float) (points / 100));
        double removed = SackGhostRules.actualSanLoss(before, mood.getMood());
        if (removed > 0) {
            fear += removed;
            if (removed < 1) moodDirty.add(target.getUUID());
            totalSanLoss = (int) Math.floor(fear);
            int next = SackGhostRules.stageForFear(fear, initialPlayers);
            if (next > stage) advanceTo(next);
        }
        return removed;
    }

    private void advanceTo(int next) {
        int previous = stage;
        stage = next;
        refreshSkills();
        if (player instanceof ServerPlayer owner) {
            owner.sendSystemMessage(Component.translatable("message.noellesroles.sack.advanced", stage));
            for (GhostArt art : GhostArt.unlocked(stage)) {
                if (art.stage > previous) owner.sendSystemMessage(Component.translatable("message.noellesroles.ma_chen_xu.ghost_skill_acquired", Component.translatable(art.nameKey())));
            }
            if (previous < 3 && stage >= 3) owner.sendSystemMessage(Component.translatable("message.noellesroles.sack.realm_unlocked"));
            owner.playNotifySound(SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.8F, 0.65F);
        }
        sync();
    }

    private void tickErosion() {
        double range = STAGE_RANGE[stage];
        double base = EROSION[stage] * SackGhostRules.EROSION_MULTIPLIER;
        for (Player target : player.level().players()) {
            if (!isEnemy(target)) continue;
            double distance = player.distanceTo(target);
            if (!otherworldActive && distance >= range) continue;
            double proximity = Math.pow(Math.max(0, 1 - distance / range), 1.5);
            if (otherworldActive) proximity = Math.max(0.5, proximity);
            // 全图领域侵蚀仍保留，近身更强；只有近战攻击会登记归零判死。
            double gaze = target.hasLineOfSight(player) ? 1.8 : 1;
            drainSan(target, base * proximity * gaze * 100);
        }
    }

    public boolean usePrayerRain() {
        if (!(player instanceof ServerPlayer owner) || !canCast() || otherworldActive) return false;
        if (stage < 3) return fail("message.noellesroles.sack.realm_locked");
        updateCooldowns();
        if (ultimateCooldown > 0) return fail("message.noellesroles.sack.cooldown", (ultimateCooldown + 19) / 20);
        otherworldActive = true;
        otherworldTimer = 0;
        otherworldTotalTicks = SackGhostRules.realmDuration(stage);
        otherworldDuration = otherworldTotalTicks;
        verdicts.clear();
        previousInvulnerable = owner.isInvulnerable();
        owner.setInvulnerable(true);
        hitReadyAt = now();
        for (ServerPlayer target : owner.serverLevel().players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(target)) continue;
            lease(target, ModEffects.OTHERWORLD_AURA, otherworldDuration, stage >= 4 ? 1 : 0);
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false, false));
            if (target != owner && isEnemy(target)) {
                lease(target, ModEffects.MOVE_BANED, OPENING_LOCK, 0);
                lease(target, ModEffects.USED_BANED, OPENING_LOCK, 0);
                lease(target, ModEffects.SKILL_BANED, OPENING_LOCK, 0);
            }
            target.sendSystemMessage(Component.translatable("message.noellesroles.sack.realm_begins"));
            target.playNotifySound(SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 0.8F, 0.7F);
            SRENetworkMessageUtils.sendTitleTime(target, 10, 60, 20);
            SRENetworkMessageUtils.sendTitle(target,
                    Component.translatable("message.noellesroles.ma_chen_xu.otherworld_warning_title")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD, ChatFormatting.OBFUSCATED));
            SRENetworkMessageUtils.sendSubtitle(target,
                    Component.translatable("message.noellesroles.ma_chen_xu.otherworld_warning_subtitle")
                            .withStyle(ChatFormatting.RED));
        }
        refreshRealmEffects(owner);
        realmLevel = owner.serverLevel();
        SackGhostPackets.publish(owner, this);
        SRE.REPLAY_MANAGER.recordCustomEvent(Component.translatable("replay.event.sackghost.open_shadow", GameReplayUtils.getReplayPlayerDisplayText(owner, true)));
        sync();
        return true;
    }

    /** 命中削SAN，归零才标记；同一秒不能通过连点重复结算。 */
    public boolean markPlayer(Player target) {
        if (!(player instanceof ServerPlayer owner) || !otherworldActive || !canCast() || !isEnemy(target)
                || target.hasEffect(ModEffects.GHOST_WARD) || otherworldTimer < OPENING_LOCK
                || player.distanceToSqr(target) > SackGhostRules.REALM_ATTACK_RANGE * SackGhostRules.REALM_ATTACK_RANGE
                || !player.hasLineOfSight(target) || now() < hitReadyAt || verdicts.contains(target.getUUID())) return false;
        hitReadyAt = now() + SackGhostRules.REALM_ATTACK_INTERVAL;
        drainSan(target, SackGhostRules.realmAttackSan(stage));
        owner.playNotifySound(SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.7F, 0.65F);
        if (target instanceof ServerPlayer victim) {
            victim.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 0.7F, 0.8F);
            victim.displayClientMessage(Component.translatable("message.noellesroles.sack.realm_hit", SackGhostRules.realmAttackSan(stage)), true);
        }
        if (verdicts.markAfterHit(target.getUUID(), SREPlayerMoodComponent.KEY.get(target).getMood())) {
            lease((ServerPlayer) target, ModEffects.GHOST_CONDEMNED, otherworldDuration + 10, 0);
            target.sendSystemMessage(Component.translatable("message.noellesroles.sack.condemned"));
        }
        updateCooldowns();
        sync();
        return true;
    }

    public boolean cleanse(Player target) {
        boolean changed = parasites.remove(target.getUUID()) != null;
        if (verdicts.cleanse(target.getUUID())) {
            target.removeEffect(ModEffects.GHOST_CONDEMNED);
            var effects = realmEffects.get(target.getUUID());
            if (effects != null) effects.remove(ModEffects.GHOST_CONDEMNED);
            target.displayClientMessage(Component.translatable("message.noellesroles.sack.mark_cleansed"), true);
            changed = true;
        }
        if (changed) sync();
        return changed;
    }

    /** 自然结束才执行判死；法器、死亡、换职业或局末均取消这次待结算标记。 */
    public void endOtherworld(boolean natural) {
        if (!(player instanceof ServerPlayer owner) || !otherworldActive) return;
        List<UUID> condemned = verdicts.finish(natural);
        otherworldActive = false;
        otherworldDuration = 0;
        realmReadyAt = now() + SackGhostRules.REALM_COOLDOWN;
        owner.setInvulnerable(previousInvulnerable);
        restoreRealmEffects(owner);
        SackGhostPackets.publish(realmLevel == null ? owner.serverLevel() : realmLevel, owner, this);
        realmLevel = null;
        for (ServerPlayer target : owner.serverLevel().players()) {
            target.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 0.6F, 0.7F);
            target.displayClientMessage(Component.translatable(natural ? "message.noellesroles.sack.realm_ended" : "message.noellesroles.sack.realm_broken"), true);
            SRENetworkMessageUtils.sendTitleTime(target, 10, 40, 20);
            SRENetworkMessageUtils.sendTitle(target,
                    Component.translatable("message.noellesroles.ma_chen_xu.otherworld_end_notice")
                            .withStyle(ChatFormatting.GREEN));
            SRENetworkMessageUtils.sendSubtitle(target, Component.empty());
        }
        // 多布袋鬼环境中重新维持仍有效的领域，不能复活已结束领域的光环。
        for (ServerPlayer other : owner.server.getPlayerList().getPlayers()) {
            var data = SackGhostItems.data(other);
            if (data != null && data != this && data.otherworldActive) data.refreshRealmEffects(other);
        }
        updateCooldowns();
        sync();
        if (natural && matchActive()) {
            for (UUID id : condemned) {
                ServerPlayer target = owner.server.getPlayerList().getPlayer(id);
                if (target != null && isEnemy(target)) GameUtils.forceKillPlayer(target, true, owner, Noellesroles.id("machenxu"));
            }
        }
    }

    public boolean soulDevour(Player target) {
        if (!(player instanceof ServerPlayer owner) || otherworldActive || !canCast() || !isEnemy(target)
                || target.hasEffect(ModEffects.GHOST_WARD) || now() < hitReadyAt
                || player.distanceToSqr(target) > SackGhostRules.REALM_ATTACK_RANGE * SackGhostRules.REALM_ATTACK_RANGE
                || !owner.hasLineOfSight(target) || SREPlayerMoodComponent.KEY.get(target).getMood() > 0.15F) return false;
        hitReadyAt = now() + SackGhostRules.REALM_ATTACK_INTERVAL;
        GameUtils.forceKillPlayer(target, true, owner, Noellesroles.id("machenxu"));
        if (GameUtils.isPlayerAliveAndSurvival(target)) return false;
        // 法器由统一死亡掉落钩子先行掉出，不能随魂噬清包被删除。
        if (target instanceof ServerPlayer victim) SackGhostItems.dropRelics(victim);
        target.getInventory().clearContent();
        MoneyUtils.addToBalance(owner, 50);
        ParticleFx.burst(owner.serverLevel(), ParticleTypes.SCULK_SOUL, target.getX(), target.getY() + 1, target.getZ(), 16, 0.4, 0.7, 0.4, 0.03);
        return true;
    }

    private void refreshRealmEffects(ServerPlayer owner) {
        lease(owner, ModEffects.OTHERWORLD_AURA, otherworldDuration + 2, stage >= 4 ? 1 : 0);
        // 布袋鬼本人不授予常驻青色互透，避免绕过法器的20格私有照诡。
        lease(owner, MobEffects.MOVEMENT_SPEED, otherworldDuration + 2, 1);
        for (ServerPlayer target : owner.serverLevel().players()) {
            if (!isEnemy(target)) continue;
            lease(target, MobEffects.MOVEMENT_SPEED, otherworldDuration + 2, 2);
            lease(target, ModEffects.OTHERWORLD_AURA, otherworldDuration + 2, stage >= 4 ? 1 : 0);
            lease(target, ModEffects.BACKWORLD_OUTLINE, otherworldDuration + 2, 0);
            lease(target, ModEffects.INFINITE_STAMINA, otherworldDuration + 2, 0);
            lease(target, ModEffects.LOW_SAN_SHADER_RESISTANCE, otherworldDuration + 2, 10);
            lease(target, ModEffects.MOOD_DRAIN_REDUCTION, otherworldDuration + 2, 1);
        }
        for (UUID id : verdicts.snapshot()) {
            ServerPlayer target = owner.server.getPlayerList().getPlayer(id);
            if (target != null && isEnemy(target)) lease(target, ModEffects.GHOST_CONDEMNED, otherworldDuration + 10, 0);
        }
    }

    private void lease(ServerPlayer target, Holder<MobEffect> effect, int duration, int amplifier) {
        var effects = realmEffects.computeIfAbsent(target.getUUID(), ignored -> new HashMap<>());
        Lease old = effects.get(effect);
        MobEffectInstance previous = old == null ? target.getEffect(effect) : old.previous();
        if (old == null) {
            for (var owner : target.server.getPlayerList().getPlayers()) {
                var data = SackGhostItems.data(owner);
                if (data == null || data == this || !data.otherworldActive) continue;
                var shared = data.realmEffects.get(target.getUUID());
                if (shared != null && shared.containsKey(effect)) {
                    previous = previousAt(target, effect, shared.get(effect));
                    break;
                }
            }
        }
        if (effect == ModEffects.OTHERWORLD_AURA || effect == ModEffects.GHOST_CONDEMNED) previous = null;
        long captured = old == null ? target.level().getGameTime() : old.capturedAt();
        effects.put(effect, new Lease(previous == null ? null : new MobEffectInstance(previous), captured,
                amplifier, Math.max(target.level().getGameTime() + duration, old == null ? 0 : old.expectedEnd()),
                now() + duration));
        target.addEffect(new MobEffectInstance(effect, duration, amplifier, false, false, false));
    }

    private MobEffectInstance previousAt(ServerPlayer target, Holder<MobEffect> effect, Lease lease) {
        var previous = lease.previous();
        if (previous == null) return null;
        int left = SackGhostRules.remainingEffectTicks(previous.getDuration(), target.level().getGameTime() - lease.capturedAt());
        return left == 0 ? null : new MobEffectInstance(effect, left, previous.getAmplifier(),
                previous.isAmbient(), previous.isVisible(), previous.showIcon());
    }

    private void restoreLease(ServerPlayer target, Holder<MobEffect> effect, Lease lease) {
        var current = target.getEffect(effect);
        if (current != null && !current.isInfiniteDuration() && current.getAmplifier() == lease.amplifier()
                && current.getDuration() <= Math.max(2, lease.expectedEnd() - target.level().getGameTime() + 2)) target.removeEffect(effect);
        var previous = previousAt(target, effect, lease);
        if (previous != null) target.addEffect(previous);
    }

    /** 原版实体仍tick时，给暂停中的本领域效果小幅续租；有效时钟到期则精确撤销。 */
    private void maintainRealmEffects(ServerPlayer owner) {
        for (var entity : realmEffects.entrySet()) {
            ServerPlayer target = owner.server.getPlayerList().getPlayer(entity.getKey());
            if (target == null) continue;
            for (var iterator = entity.getValue().entrySet().iterator(); iterator.hasNext();) {
                var entry = iterator.next();
                Lease saved = entry.getValue();
                int left = SackGhostRules.remaining(now(), saved.activeUntil());
                if (left == 0) {
                    restoreLease(target, entry.getKey(), saved);
                    iterator.remove();
                    continue;
                }
                var current = target.getEffect(entry.getKey());
                int refresh = SackGhostRules.effectRefreshDuration(left, current == null ? 0 : current.getDuration());
                if (refresh > 0) {
                    target.addEffect(new MobEffectInstance(entry.getKey(), refresh, saved.amplifier(), false, false, false));
                    entry.setValue(new Lease(saved.previous(), saved.capturedAt(), saved.amplifier(),
                            target.level().getGameTime() + refresh, saved.activeUntil()));
                }
            }
        }
    }

    private void restoreRealmEffects(ServerPlayer owner) {
        for (var entity : realmEffects.entrySet()) {
            ServerPlayer target = owner.server.getPlayerList().getPlayer(entity.getKey());
            if (target != null) entity.getValue().forEach((effect, lease) -> restoreLease(target, effect, lease));
        }
        realmEffects.clear();
    }

    /** 领域临时增益不进入原版回溯快照，包括Entity无敌标志；职业成长仍可正常保存。 */
    public void filterRewindSnapshot(ServerPlayer target, CompoundTag state) {
        if (target == player && otherworldActive) state.putBoolean("Invulnerable", previousInvulnerable);
        var effects = realmEffects.get(target.getUUID());
        if (effects == null) return;
        for (var entry : effects.entrySet()) {
            var current = target.getEffect(entry.getKey());
            if (current == null || current.isInfiniteDuration() || current.getAmplifier() != entry.getValue().amplifier()
                    || current.getDuration() > Math.max(2, entry.getValue().expectedEnd() - target.level().getGameTime() + 2)) continue;
            SackGhostRewind.removeSnapshotEffect(state, entry.getKey());
            var previous = previousAt(target, entry.getKey(), entry.getValue());
            if (previous != null) {
                ListTag baseline = state.getList("active_effects", Tag.TAG_COMPOUND);
                baseline.add(previous.save());
                state.put("active_effects", baseline);
            }
        }
    }

    /** 平滑回溯的临时旁观不等于死亡：取消临时追击，不删除阶段、会话或绑定物品。 */
    public void cancelForRewind() {
        if (player instanceof ServerPlayer owner) {
            endOtherworld(false);
            clearEffigy(owner);
        }
        parasites.clear();
        verdicts.clear();
    }

    private Player rayTarget(ServerPlayer owner, double range) {
        Vec3 from = owner.getEyePosition(), end = from.add(owner.getLookAngle().scale(range));
        Player best = null;
        double bestDistance = range * range;
        for (Player target : owner.serverLevel().getEntitiesOfClass(Player.class, new AABB(from, end).inflate(1), this::isEnemy)) {
            var hit = target.getBoundingBox().inflate(0.2).clip(from, end);
            if (hit.isEmpty() || !owner.hasLineOfSight(target)) continue;
            double distance = from.distanceToSqr(hit.get());
            if (distance <= bestDistance) { best = target; bestDistance = distance; }
        }
        return best;
    }

    private boolean castVeil(ServerPlayer owner) {
        Vec3 from = owner.getEyePosition(), end = from.add(owner.getLookAngle().scale(9));
        var hit = owner.serverLevel().clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        Vec3 center = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
        for (Player target : owner.serverLevel().getEntitiesOfClass(Player.class, new AABB(center, center).inflate(3), this::isEnemy)) {
            if (target.position().distanceToSqr(center) > 9 || !owner.hasLineOfSight(target)) continue;
            drainSan(target, 12);
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false, true));
        }
        ParticleFx.burst(owner.serverLevel(), ParticleTypes.LARGE_SMOKE, center.x, center.y, center.z, 18, 1.2, 0.8, 1.2, 0.02);
        return true;
    }

    private boolean castPush(ServerPlayer owner) {
        for (Player target : owner.serverLevel().getEntitiesOfClass(Player.class, owner.getBoundingBox().inflate(5), this::isEnemy)) {
            if (owner.distanceToSqr(target) > 25) continue;
            Vec3 direction = target.position().subtract(owner.position()).multiply(1, 0, 1).normalize();
            target.setDeltaMovement(direction.scale(1.4).add(0, 0.25, 0));
            target.hurtMarked = true;
        }
        owner.serverLevel().playSound(null, owner.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.8F, 0.7F);
        return true;
    }

    private boolean castEffigy(ServerPlayer owner) {
        Vec3 from = owner.getEyePosition(), end = from.add(owner.getLookAngle().scale(4));
        var hit = owner.serverLevel().clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        Vec3 position = hit.getType() == HitResult.Type.MISS ? end.add(0, -owner.getEyeHeight(), 0)
                : hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.35));
        ArmorStand decoy = new ArmorStand(owner.serverLevel(), position.x, position.y, position.z);
        if (!safeLanding(owner.serverLevel(), decoy, position)) return fail("message.noellesroles.sack.no_landing");
        clearEffigy(owner);
        CompoundTag config = new CompoundTag();
        config.putInt("DisabledSlots", 0x3F3F3F);
        decoy.readAdditionalSaveData(config);
        decoy.setNoGravity(true);
        decoy.setInvulnerable(true);
        decoy.setNoBasePlate(true);
        decoy.setShowArms(true);
        decoy.setSilent(true);
        decoy.setYRot(owner.getYRot());
        ItemStack head = Items.PLAYER_HEAD.getDefaultInstance();
        head.set(DataComponents.PROFILE, new ResolvableProfile(owner.getGameProfile()));
        decoy.setItemSlot(EquipmentSlot.HEAD, head);
        decoy.setItemSlot(EquipmentSlot.CHEST, Items.LEATHER_CHESTPLATE.getDefaultInstance());
        if (!owner.serverLevel().addFreshEntity(decoy)) return false;
        effigyEntity = decoy.getUUID();
        effigyLevel = owner.serverLevel();
        effigyPosition = position;
        effigyTicks = 600;
        ParticleFx.burst(owner.serverLevel(), ParticleTypes.SCULK_SOUL, position.x, position.y + 1, position.z, 8, 0.3, 0.5, 0.3, 0.01);
        return true;
    }

    private void clearEffigy(ServerPlayer owner) {
        if (effigyEntity != null) {
            Entity entity = effigyLevel == null ? null : effigyLevel.getEntity(effigyEntity);
            if (entity != null) entity.discard();
        }
        effigyEntity = null;
        effigyLevel = null;
        effigyPosition = null;
        effigyTicks = 0;
    }

    private boolean safeLanding(ServerLevel level, Entity entity, Vec3 destination) {
        BlockPos pos = BlockPos.containing(destination);
        return level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos)
                && level.getFluidState(pos).isEmpty()
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()
                && level.noCollision(entity, entity.getBoundingBox().move(destination.subtract(entity.position())));
    }

    private boolean clearPath(ServerLevel level, Entity entity, Vec3 destination) {
        Vec3 delta = destination.subtract(entity.position());
        int steps = Math.max(1, (int) Math.ceil(delta.length() * 4));
        for (int i = 1; i <= steps; i++) {
            Vec3 offset = delta.scale(i / (double) steps);
            if (!level.hasChunkAt(BlockPos.containing(entity.position().add(offset)))
                    || !level.noCollision(entity, entity.getBoundingBox().move(offset))) return false;
        }
        return true;
    }

    private boolean castWail(ServerPlayer owner) {
        for (Player target : owner.serverLevel().getEntitiesOfClass(Player.class, owner.getBoundingBox().inflate(8), this::isEnemy)) {
            if (owner.distanceToSqr(target) > 64 || !owner.hasLineOfSight(target)) continue;
            drainSan(target, 15);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false, true));
        }
        owner.serverLevel().playSound(null, owner.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 0.8F, 0.7F);
        return true;
    }

    private boolean castParasite(ServerPlayer owner) {
        Player target = rayTarget(owner, 8);
        if (target == null || parasites.containsKey(target.getUUID()) || target.hasEffect(ModEffects.GHOST_WARD)) return fail("tip.noellesroles.no_target");
        parasites.put(target.getUUID(), 200);
        target.sendSystemMessage(Component.translatable("message.noellesroles.sack.parasite"));
        return true;
    }

    private boolean castSeize(ServerPlayer owner) {
        Player target = rayTarget(owner, 10);
        if (target == null) return fail("tip.noellesroles.no_target");
        if (SREPlayerMoodComponent.KEY.get(target).getMood() > 0.35F || target.hasEffect(ModEffects.GHOST_WARD)) return fail("message.noellesroles.ma_chen_xu.seize.too_high");
        Vec3 direction = new Vec3(owner.getLookAngle().x, 0, owner.getLookAngle().z).normalize();
        Vec3 destination = owner.position().add(direction.scale(1.6));
        if (!safeLanding(owner.serverLevel(), target, destination) || !clearPath(owner.serverLevel(), target, destination)) return fail("message.noellesroles.sack.no_landing");
        target.teleportTo(destination.x, destination.y, destination.z);
        target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 20, 0, false, false, true));
        drainSan(target, 10);
        return true;
    }

    private boolean castEcho(ServerPlayer owner) {
        Vec3 direction = new Vec3(owner.getLookAngle().x, 0, owner.getLookAngle().z).normalize();
        if (direction.lengthSqr() < 0.1) return fail("message.noellesroles.sack.no_landing");
        Vec3 destination = null;
        for (double distance = 0.25; distance <= 8; distance += 0.25) {
            Vec3 candidate = owner.position().add(direction.scale(distance));
            if (!owner.serverLevel().hasChunkAt(BlockPos.containing(candidate))
                    || !owner.serverLevel().noCollision(owner, owner.getBoundingBox().move(candidate.subtract(owner.position())))) break;
            if (distance >= 1 && safeLanding(owner.serverLevel(), owner, candidate)) destination = candidate;
        }
        if (destination == null) return fail("message.noellesroles.sack.no_landing");
        owner.teleportTo(destination.x, destination.y, destination.z);
        owner.playNotifySound(SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 0.75F);
        return true;
    }

    private void tickTricks(ServerPlayer owner) {
        if (effigyEntity != null && effigyLevel != owner.serverLevel()) clearEffigy(owner);
        if (effigyTicks > 0 && effigyPosition != null) {
            effigyTicks--;
            if (owner.tickCount % 10 == 0) {
                // 诱饵本身是可见实体，不再逐次广播维持粒子。
                for (Player target : owner.serverLevel().getEntitiesOfClass(Player.class, new AABB(effigyPosition, effigyPosition).inflate(3), this::isEnemy)) {
                    if (target.position().distanceToSqr(effigyPosition) > 9) continue;
                    drainSan(target, 18);
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false, true));
                    target.playSound(SoundEvents.GHAST_SCREAM, 0.7F, 0.7F);
                    effigyTicks = 0;
                    break;
                }
            }
            if (effigyTicks <= 0) clearEffigy(owner);
        }
        for (var iterator = parasites.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            ServerPlayer target = owner.server.getPlayerList().getPlayer(entry.getKey());
            if (target == null || !isEnemy(target) || target.hasEffect(ModEffects.GHOST_WARD)) { iterator.remove(); continue; }
            int left = entry.getValue() - 1;
            if (left % 20 == 0) drainSan(target, 2);
            if (left <= 0) iterator.remove(); else entry.setValue(left);
        }
    }

    private void updateCooldowns() {
        generationCooldown = SackGhostRules.remaining(now(), generationReadyAt);
        castCooldown = SackGhostRules.remaining(now(), castReadyAt);
        ultimateCooldown = SackGhostRules.remaining(now(), realmReadyAt);
        realmHitCooldown = SackGhostRules.remaining(now(), hitReadyAt);
    }

    @Override public void serverTick() {
        if (!(player instanceof ServerPlayer owner) || stage <= 0) return;
        if (!isActiveMaChenXu()) { clear(); return; }
        if (TimeRewind.isSmoothRewinding(owner)) { cancelForRewind(); return; }
        if (otherworldActive && realmLevel != owner.serverLevel()) endOtherworld(false);
        if (!matchActive() || !GameUtils.isPlayerAliveAndSurvival(owner)) { clear(); return; }
        if (!started) initializeRound(owner);
        if (clockRunning()) activeTicks++;
        updateCooldowns();
        if (otherworldActive) maintainRealmEffects(owner);
        if (otherworldActive && owner.tickCount % 20 == 0) {
            refreshRealmEffects(owner);
            SackGhostPackets.publish(owner, this);
        }
        if (!clockRunning()) return;
        tickErosion();
        tickTricks(owner);
        if (otherworldActive) {
            otherworldTimer++;
            otherworldDuration--;
            if (otherworldTimer % GLOW_INTERVAL == 0) {
                for (ServerPlayer target : owner.serverLevel().players()) if (isEnemy(target)) lease(target, MobEffects.GLOWING, 80, 0);
            }
            if (otherworldDuration <= 0) endOtherworld(true);
        }
        if (owner.tickCount % 20 == 0) {
            for (UUID id : moodDirty) {
                var target = owner.server.getPlayerList().getPlayer(id);
                if (target != null) SREPlayerMoodComponent.KEY.get(target).sync();
            }
            moodDirty.clear();
            sync();
        }
    }

    @Override public void clientTick() {
        if (!clockRunning()) return;
        activeTicks++;
        updateCooldowns();
        if (otherworldActive && otherworldDuration > 0) { otherworldDuration--; otherworldTimer++; }
    }

    @Override public void clear() {
        if (player instanceof ServerPlayer owner) {
            endOtherworld(false);
            SackGhostItems.clearOwnerArts(owner);
            clearEffigy(owner);
        }
        stage = 0;
        parasites.clear();
        moodDirty.clear();
        effigyTicks = 0;
    }

    private boolean fail(String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED), true);
        return false;
    }

    @Override public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        updateCooldowns();
        tag.putInt("stage", stage);
        tag.putInt("totalSanLoss", totalSanLoss);
        tag.putDouble("fear", fear);
        tag.putInt("population", initialPlayers);
        tag.putBoolean("started", started);
        tag.putUUID("session", sessionId);
        tag.putInt("draw", generationCooldown);
        tag.putInt("cast", castCooldown);
        tag.putInt("realmCd", ultimateCooldown);
        tag.putInt("hit", realmHitCooldown);
        tag.putBoolean("realm", otherworldActive);
        tag.putInt("remaining", otherworldDuration);
        tag.putInt("elapsed", otherworldTimer);
        tag.putInt("duration", otherworldTotalTicks);
        ListTag marks = new ListTag();
        for (UUID id : verdicts.snapshot()) marks.add(NbtUtils.createUUID(id));
        tag.put("marks", marks);
    }

    @Override public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        if (!tag.contains("stage")) return;
        stage = Math.clamp(tag.getInt("stage"), 0, 4);
        totalSanLoss = Math.max(0, tag.getInt("totalSanLoss"));
        fear = Math.max(totalSanLoss, tag.getDouble("fear"));
        initialPlayers = Math.max(1, tag.getInt("population"));
        started = tag.getBoolean("started");
        if (tag.hasUUID("session")) sessionId = tag.getUUID("session");
        STAGE_2_THRESHOLD = SackGhostRules.stageThreshold(2, initialPlayers);
        STAGE_3_THRESHOLD = SackGhostRules.stageThreshold(3, initialPlayers);
        STAGE_4_THRESHOLD = SackGhostRules.stageThreshold(4, initialPlayers);
        generationReadyAt = now() + Math.max(0, tag.getInt("draw"));
        castReadyAt = now() + Math.max(0, tag.getInt("cast"));
        realmReadyAt = now() + Math.max(0, tag.getInt("realmCd"));
        hitReadyAt = now() + Math.max(0, tag.getInt("hit"));
        otherworldActive = tag.getBoolean("realm");
        otherworldDuration = Math.max(0, tag.getInt("remaining"));
        otherworldTimer = Math.max(0, tag.getInt("elapsed"));
        otherworldTotalTicks = Math.max(0, tag.getInt("duration"));
        List<UUID> restoredMarks = new ArrayList<>();
        ListTag marks = tag.getList("marks", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < Math.min(128, marks.size()); i++) restoredMarks.add(NbtUtils.loadUUID(marks.get(i)));
        verdicts.restore(restoredMarks);
        refreshSkills();
        updateCooldowns();
    }

    @Override public void readFromRewindNbt(CompoundTag tag, HolderLookup.Provider provider) {
        boolean interrupted = otherworldActive;
        long liveRealmReadyAt = realmReadyAt;
        if (player instanceof ServerPlayer owner) {
            endOtherworld(false);
            clearEffigy(owner);
        }
        readFromSyncNbt(tag, provider);
        // 回溯可恢复成长，但不能重新打开已清理的领域或重复执行旧标记。
        if (player instanceof ServerPlayer) {
            otherworldActive = false;
            otherworldDuration = 0;
            verdicts.clear();
            parasites.clear();
            effigyTicks = 0;
            realmReadyAt = Math.max(realmReadyAt, liveRealmReadyAt);
            if (interrupted || tag.getBoolean("realm")) realmReadyAt = Math.max(realmReadyAt, now() + SackGhostRules.REALM_COOLDOWN);
            updateCooldowns();
        }
    }
}
