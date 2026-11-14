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
import org.agmas.noellesroles.game.roles.killer.ma_chen_xu.SackGhostActions;
import org.agmas.noellesroles.game.roles.killer.ma_chen_xu.SackGhostActors;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.world.damagesource.DamageSource;
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
    public int STAGE_2_THRESHOLD = SackGhostRules.stageThreshold(2, initialPlayers);
    public int STAGE_3_THRESHOLD = SackGhostRules.stageThreshold(3, initialPlayers);
    public int STAGE_4_THRESHOLD = SackGhostRules.stageThreshold(4, initialPlayers);
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
    public int escapeCooldown, escapeTicks, shroudCooldown, shroudTicks, echoTicks, threat;
    public boolean bodyThreat;

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
    private UUID effigyEntity;
    private ServerLevel effigyLevel;
    private final Set<UUID> moodDirty = new HashSet<>();
    private final Map<UUID, Integer> parasites = new HashMap<>();
    private final Map<UUID, Map<Holder<MobEffect>, Lease>> realmEffects = new LinkedHashMap<>();
    private record Lease(MobEffectInstance previous, long capturedAt, int amplifier, long expectedEnd, long activeUntil) {}
    private long escapeReadyAt, escapeUntil, shroudReadyAt, shroudUntil, echoUntil, wailUntil;
    private boolean escapePreviousInvulnerable, bodyDeathCheck, echoConsumes;
    private SackGhostActors.Body body;
    private ServerLevel formLevel, echoLevel;
    private Vec3 echoPosition;
    private UUID echoToken;
    private final Map<Holder<MobEffect>, Lease> formEffects = new HashMap<>();
    private final Map<UUID, Integer> wailExposure = new HashMap<>();
    private final Map<UUID, Motion> motions = new HashMap<>();
    private record Motion(ServerLevel level, Vec3 from, Vec3 to, int duration, int elapsed, boolean seize) {}

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

    private boolean canUseSkill() {
        return isActiveMaChenXu() && clockRunning() && GameUtils.isPlayerAliveAndSurvival(player)
                && now() >= escapeUntil && !player.hasEffect(ModEffects.SKILL_BANED)
                && !player.hasEffect(ModEffects.SKILL_FREEZED);
    }

    private boolean canCast() { return canUseSkill() && !player.hasEffect(ModEffects.USED_BANED); }

    public boolean isEnemy(Player target) {
        return target != player && target.level() == player.level() && GameUtils.isPlayerAliveAndSurvival(target)
                && !SREGameWorldComponent.KEY.get(player.level()).isKillerTeam(target);
    }

    /** 中立与假心情职业没有可削减的真实SAN，改走虚拟血量；真实心情职业仍削SAN。 */
    public boolean usesVirtualHealth(Player target) {
        var role = SREGameWorldComponent.KEY.get(player.level()).getAllegianceRole(target);
        return SackGhostRules.usesVirtualHealth(role != null && role.isNeutrals(),
                role == null || role.getMoodType() != io.wifi.starrailexpress.api.SRERole.MoodType.REAL);
    }

    /** 目标当前虚拟血量；仅对走虚拟血量结算的目标有意义。 */
    public int virtualHealth(Player target) {
        return org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent.KEY.get(target)
                .getEffectiveHealth(target.level().getGameTime());
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
        if (!(player instanceof ServerPlayer owner) || !canUseSkill()) return false;
        updateCooldowns();
        if (generationCooldown > 0) return fail("message.noellesroles.sack.cooldown", (generationCooldown + 19) / 20);
        if (SackGhostItems.artCount(owner) >= SackGhostRules.MAX_ART_ITEMS) return fail("message.noellesroles.sack.full");
        var pool = GhostArt.drawPool(stage, SackGhostItems.hasDrawnShroud(owner));
        GhostArt art = pool.get(owner.getRandom().nextInt(pool.size()));
        if (!giveArt(owner, art)) return fail("message.noellesroles.sack.inventory_full");
        if (art == GhostArt.SHROUD) SackGhostItems.recordShroudDraw(owner);
        generationReadyAt = now() + SackGhostRules.generationCooldownSeconds(initialPlayers) * 20L;
        owner.displayClientMessage(Component.translatable("message.noellesroles.sack.drawn", Component.translatable(art.nameKey())), true);
        owner.playNotifySound(SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.7F, 0.75F);
        updateCooldowns();
        sync();
        return true;
    }

    public boolean useArt(ItemStack stack, GhostArt art) {
        if (!(player instanceof ServerPlayer owner) || !canCast() || SackGhostActions.itemsBlocked(owner)
                || art.stage > stage || !SackGhostItems.validArt(owner, stack, sessionId)) return false;
        if (SackGhostItems.artCount(owner) > SackGhostRules.MAX_ART_ITEMS) return fail("message.noellesroles.sack.full");
        updateCooldowns();
        // 解除隐身和回响折返是同次施法的收尾，不再消耗或重置共用冷却。
        if (art == GhostArt.SHROUD && shroudTicks > 0) { breakShroud(); return true; }
        if (art == GhostArt.ECHO && echoTicks > 0 && echoToken != null
                && echoToken.equals(SackGhostItems.tag(stack).getUUID("Token"))) return returnEcho(owner);
        if (castCooldown > 0) return fail("message.noellesroles.sack.cast_wait");
        boolean used = switch (art) {
            case VEIL -> castVeil(owner);
            case PUSH -> castPush(owner);
            case SHROUD -> castShroud(owner);
            case EFFIGY -> castEffigy(owner);
            case ECHO -> castEcho(owner, stack);
            case WAIL -> castWail(owner);
            case PARASITE -> castParasite(owner);
            case SEIZE -> castSeize(owner);
        };
        if (!used) return false;
        if (art != GhostArt.ECHO && SackGhostRules.consumesOnCast(art, otherworldActive)) SackGhostItems.consumeArt(owner, stack);
        castReadyAt = now() + SackGhostRules.castInterval(otherworldActive);
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
        if (!(player instanceof ServerPlayer owner) || !canUseSkill() || otherworldActive) return false;
        if (stage < 3) return fail("message.noellesroles.sack.realm_locked");
        updateCooldowns();
        if (ultimateCooldown > 0) return fail("message.noellesroles.sack.cooldown", (ultimateCooldown + 19) / 20);
        body = SackGhostActors.createBody(owner);
        if (body == null) return fail("message.noellesroles.sack.no_landing");
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
        fx(owner, "realm_depart", body.position(), 25, owner.getId());
        publishForm(owner);
        SackGhostPackets.publish(owner, this);
        SRE.REPLAY_MANAGER.recordCustomEvent(Component.translatable("replay.event.sackghost.open_shadow", GameReplayUtils.getReplayPlayerDisplayText(owner, true)));
        sync();
        return true;
    }

    /** 里世界近战只结算本职业伤害；虚血和SAN归零都登记同一份延迟处死标记。 */
    public boolean markPlayer(Player target) {
        if (!(player instanceof ServerPlayer owner) || !(target instanceof ServerPlayer victim)
                || !otherworldActive || !canCast() || SackGhostActions.attacksBlocked(owner) || !isEnemy(victim)
                || victim.hasEffect(ModEffects.GHOST_WARD) || otherworldTimer < OPENING_LOCK
                || !withinRealmReach(owner, victim) || !owner.hasLineOfSight(victim)
                || now() < hitReadyAt) return false;
        hitReadyAt = now() + SackGhostRules.REALM_ATTACK_INTERVAL;
        breakShroud();
        boolean alreadyMarked = verdicts.contains(victim.getUUID());
        boolean virtual = usesVirtualHealth(victim);
        int amount = virtual ? SackGhostRules.realmAttackVirtual(stage) : SackGhostRules.realmAttackSan(stage);
        String message = virtual ? "message.noellesroles.sack.realm_hit_virtual" : "message.noellesroles.sack.realm_hit";
        if (!alreadyMarked) {
            if (virtual) {
                var health = org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent.KEY.get(victim);
                if (!health.hurtDeferred(owner, amount, Noellesroles.id("machenxu"), () -> registerRealmMark(victim))) return false;
            } else {
                drainSan(victim, amount);
                if (SackGhostRules.isCondemned(SREPlayerMoodComponent.KEY.get(victim).getMood())) registerRealmMark(victim);
            }
        }
        fx(owner, "realm_hit", victim.position(), 16, victim.getId(), owner.getId(), verdicts.contains(victim.getUUID()) ? 1 : 0);
        owner.playNotifySound(SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, 0.6F);
        victim.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 0.8F, 0.8F);
        if (!alreadyMarked) victim.displayClientMessage(Component.translatable(message, amount), true);
        updateCooldowns();
        sync();
        return true;
    }

    private void registerRealmMark(ServerPlayer target) {
        if (!otherworldActive || !isActiveMaChenXu() || !isEnemy(target) || target.hasEffect(ModEffects.GHOST_WARD)
                || !verdicts.markAfterVirtualHit(target.getUUID(), 0)) return;
        lease(target, ModEffects.GHOST_CONDEMNED, otherworldDuration + 10, 0);
        target.sendSystemMessage(Component.translatable("message.noellesroles.sack.condemned"));
        sync();
    }

    private static boolean withinRealmReach(ServerPlayer owner, Player target) {
        Vec3 eye = owner.getEyePosition();
        AABB box = target.getBoundingBox();
        double dx = eye.x - Math.clamp(eye.x, box.minX, box.maxX);
        double dy = eye.y - Math.clamp(eye.y, box.minY, box.maxY);
        double dz = eye.z - Math.clamp(eye.z, box.minZ, box.maxZ);
        return dx * dx + dy * dy + dz * dz <= SackGhostRules.REALM_ATTACK_RANGE * SackGhostRules.REALM_ATTACK_RANGE;
    }

    public boolean cleanse(Player target) {
        boolean changed = parasites.remove(target.getUUID()) != null;
        if (changed && player instanceof ServerPlayer owner) fx(owner, "parasite", target.position(), 0, target.getId(), owner.getId());
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
        castReadyAt = Math.min(castReadyAt, now() + SackGhostRules.CAST_INTERVAL);
        owner.setInvulnerable(previousInvulnerable);
        restoreRealmEffects(owner);
        if (body != null) {
            var returning = body;
            body = null;
            SackGhostActors.restoreBody(owner, returning);
            fx(owner, "realm_return", owner.position(), 25, owner.getId());
        }
        SackGhostPackets.publish(realmLevel == null ? owner.serverLevel() : realmLevel, owner, this);
        realmLevel = null;
        publishForm(owner);
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
        if (!(player instanceof ServerPlayer owner) || otherworldActive || !canCast() || SackGhostActions.attacksBlocked(owner) || !isEnemy(target)
                || target.hasEffect(ModEffects.GHOST_WARD) || now() < hitReadyAt
                || player.distanceToSqr(target) > SackGhostRules.REALM_ATTACK_RANGE * SackGhostRules.REALM_ATTACK_RANGE
                || !owner.hasLineOfSight(target)
                || SREPlayerMoodComponent.KEY.get(target).getMood() > 0.15F) return false;
        hitReadyAt = now() + SackGhostRules.REALM_ATTACK_INTERVAL;
        breakShroud();
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
        if (target == player && now() < escapeUntil) state.putBoolean("Invulnerable", escapePreviousInvulnerable);
        Map<Holder<MobEffect>, Lease> effects = new HashMap<>();
        var realm = realmEffects.get(target.getUUID());
        if (realm != null) effects.putAll(realm);
        if (target == player) effects.putAll(formEffects);
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
            endEscape(owner);
            breakShroud();
            clearTricks(owner);
        }
        verdicts.clear();
    }

    public boolean combatClockRunning() { return isActiveMaChenXu() && clockRunning(); }

    /** 本体调用正常死亡流程时，不能被鬼影本身的临时无敌误拦。 */
    public boolean protectsFromDeath() {
        return now() < escapeUntil || otherworldActive && !bodyDeathCheck;
    }

    /** 在普通护盾与角色护盾处理后调用；强制死亡不消费替死次数。 */
    public boolean afterShieldsAllowDeath() {
        if (GameUtils.isForcedDeathInProgress()) return true;
        if (protectsFromDeath()) return false;
        if (tryEscape()) return false;
        if (bodyDeathCheck && otherworldActive) endOtherworld(false);
        return true;
    }

    public void onBodyHit(PuppeteerBodyEntity hitBody, DamageSource source) {
        if (!(player instanceof ServerPlayer owner) || body == null || body.entity() != hitBody || !otherworldActive
                || bodyDeathCheck || !GameUtils.isPlayerAliveAndSurvival(owner)) return;
        bodyDeathCheck = true;
        try {
            Player killer = source.getEntity() instanceof Player attacker ? attacker : null;
            GameUtils.killPlayer(owner, true, killer, GameConstants.DeathReasons.PUPPETEER_BODY_DEATH);
        } finally {
            bodyDeathCheck = false;
        }
    }

    private boolean tryEscape() {
        if (!(player instanceof ServerPlayer owner) || !isActiveMaChenXu() || !matchActive()
                || now() < escapeReadyAt || now() < escapeUntil) return false;
        endOtherworld(false);
        breakShroud();
        finishEcho(owner);
        owner.stopUsingItem();
        escapeReadyAt = now() + SackGhostRules.ESCAPE_COOLDOWN;
        escapeUntil = now() + SackGhostRules.ESCAPE_DURATION;
        escapePreviousInvulnerable = owner.isInvulnerable();
        owner.setInvulnerable(true);
        owner.setHealth(Math.max(1, owner.getHealth()));
        leaseForm(owner, MobEffects.MOVEMENT_SPEED, SackGhostRules.ESCAPE_DURATION, 2);
        updateCooldowns();
        fx(owner, "escape", owner.position(), SackGhostRules.ESCAPE_DURATION, owner.getId());
        owner.displayClientMessage(Component.translatable("message.noellesroles.sack.escape_triggered"), true);
        publishForm(owner);
        sync();
        return true;
    }

    private void endEscape(ServerPlayer owner) {
        if (escapeUntil == 0) return;
        escapeUntil = 0;
        owner.setInvulnerable(escapePreviousInvulnerable);
        fx(owner, "escape", owner.position(), 0, owner.getId());
        restoreFormEffect(owner, MobEffects.MOVEMENT_SPEED);
        updateCooldowns();
        publishForm(owner);
    }

    public void breakShroud() {
        if (!(player instanceof ServerPlayer owner) || shroudUntil == 0) return;
        shroudUntil = 0;
        shroudReadyAt = now() + SackGhostRules.SHROUD_COOLDOWN;
        restoreFormEffect(owner, MobEffects.INVISIBILITY);
        updateCooldowns();
        publishForm(owner);
        sync();
    }

    private boolean castShroud(ServerPlayer owner) {
        if (now() < shroudReadyAt) return fail("message.noellesroles.sack.cooldown", (shroudCooldown + 19) / 20);
        shroudUntil = now() + SackGhostRules.SHROUD_DURATION;
        leaseForm(owner, MobEffects.INVISIBILITY, SackGhostRules.SHROUD_DURATION, 0);
        updateCooldowns();
        publishForm(owner);
        return true;
    }

    private void leaseForm(ServerPlayer owner, Holder<MobEffect> effect, int ticks, int amplifier) {
        Lease old = formEffects.get(effect);
        var previous = old == null ? owner.getEffect(effect) : old.previous();
        formEffects.put(effect, new Lease(previous == null ? null : new MobEffectInstance(previous),
                old == null ? owner.level().getGameTime() : old.capturedAt(), amplifier,
                owner.level().getGameTime() + ticks, now() + ticks));
        owner.addEffect(new MobEffectInstance(effect, ticks, amplifier, false, false, false));
    }

    private void restoreFormEffect(ServerPlayer owner, Holder<MobEffect> effect) {
        Lease lease = formEffects.remove(effect);
        if (lease != null) restoreLease(owner, effect, lease);
    }

    private void maintainForms(ServerPlayer owner) {
        if (escapeUntil != 0 && now() >= escapeUntil) endEscape(owner);
        if (shroudUntil != 0 && now() >= shroudUntil) breakShroud();
        for (var entry : formEffects.entrySet()) {
            Lease lease = entry.getValue();
            int left = SackGhostRules.remaining(now(), lease.activeUntil());
            var current = owner.getEffect(entry.getKey());
            int refresh = SackGhostRules.effectRefreshDuration(left, current == null ? 0 : current.getDuration());
            if (refresh > 0) {
                owner.addEffect(new MobEffectInstance(entry.getKey(), refresh, lease.amplifier(), false, false, false));
                entry.setValue(new Lease(lease.previous(), lease.capturedAt(), lease.amplifier(),
                        owner.level().getGameTime() + refresh, lease.activeUntil()));
            }
        }
        if (now() < escapeUntil) owner.setInvulnerable(true);
    }

    public int formFlags() {
        return (otherworldActive ? SackGhostPackets.FORM_REALM : 0)
                | (now() < escapeUntil ? SackGhostPackets.FORM_ESCAPE : 0)
                | (now() < shroudUntil ? SackGhostPackets.FORM_SHROUD : 0);
    }

    public int formRemaining() { return Math.max(otherworldDuration, Math.max(escapeTicks, shroudTicks)); }

    private void publishForm(ServerPlayer owner) {
        if (formLevel != null && formLevel != owner.serverLevel()) SackGhostPackets.publishForm(formLevel, owner, 0, 0);
        formLevel = owner.serverLevel();
        SackGhostPackets.publishForm(formLevel, owner, formFlags(), formRemaining());
    }

    private void fx(ServerPlayer owner, String name, Vec3 origin, int duration, float... params) {
        ParticleFx.sendCustom(owner.serverLevel(), Noellesroles.id("sack_ghost/" + name), origin, duration, params);
    }

    private void finishEcho(ServerPlayer owner) {
        if (echoToken == null) return;
        if (echoConsumes) SackGhostItems.consumeToken(owner, echoToken, echoLevel == null ? owner.serverLevel() : echoLevel);
        if (echoLevel == owner.serverLevel() && echoPosition != null) fx(owner, "echo_depart", echoPosition, 0, owner.getId());
        echoToken = null;
        echoPosition = null;
        echoLevel = null;
        echoUntil = 0;
        echoTicks = 0;
        echoConsumes = false;
    }

    private boolean returnEcho(ServerPlayer owner) {
        if (echoLevel != owner.serverLevel() || echoPosition == null || !safeLanding(owner.serverLevel(), owner, echoPosition))
            return fail("message.noellesroles.sack.no_landing");
        Vec3 destination = echoPosition;
        finishEcho(owner);
        owner.teleportTo(destination.x, destination.y, destination.z);
        for (ServerPlayer target : owner.serverLevel().players()) if (isEnemy(target)
                && target.distanceToSqr(destination) <= SackGhostRules.ECHO_RADIUS * SackGhostRules.ECHO_RADIUS
                && owner.hasLineOfSight(target)) drainSan(target, SackGhostRules.ECHO_SAN);
        fx(owner, "echo_return", destination, 25, owner.getId(), (float) SackGhostRules.ECHO_RADIUS);
        owner.containerMenu.broadcastChanges();
        updateCooldowns();
        sync();
        return true;
    }

    public void onEffigyHit(ServerPlayer target) {
        if (!(player instanceof ServerPlayer owner) || !combatClockRunning() || !isEnemy(target)) return;
        drainSan(target, SackGhostRules.EFFIGY_SAN);
        SackGhostActions.root(target, 40);
        fx(owner, "effigy", target.position(), 20, owner.getId(), target.getId());
    }

    private void updateThreat(ServerPlayer owner) {
        int next = 0;
        boolean previousBody = bodyThreat;
        bodyThreat = otherworldActive && body != null;
        Vec3 center = bodyThreat ? body.position() : owner.position();
        for (ServerPlayer target : owner.serverLevel().players()) {
            if (!isEnemy(target) || target.position().distanceToSqr(center) > SackGhostRules.THREAT_RANGE * SackGhostRules.THREAT_RANGE) continue;
            boolean held = target.getMainHandItem().is(TMMItemTags.GUNS) || target.getOffhandItem().is(TMMItemTags.GUNS);
            boolean carried = held || target.containerMenu.getCarried().is(TMMItemTags.GUNS);
            if (!carried) for (int i = 0; i < target.getInventory().getContainerSize(); i++) {
                if (target.getInventory().getItem(i).is(TMMItemTags.GUNS)) { carried = true; break; }
            }
            next = Math.max(next, SackGhostRules.gunThreat(true, target.position().distanceToSqr(center), carried, held));
            if (next == 2) break;
        }
        if (next != threat || previousBody != bodyThreat) { threat = next; sync(); }
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
        Vec3 from = owner.getEyePosition(), end = from.add(owner.getLookAngle().scale(SackGhostRules.VEIL_RANGE));
        var hit = owner.serverLevel().clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        Vec3 center = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
        for (ServerPlayer target : owner.serverLevel().players()) {
            if (!isEnemy(target) || target.position().distanceToSqr(center) > SackGhostRules.VEIL_RADIUS * SackGhostRules.VEIL_RADIUS
                    || !owner.hasLineOfSight(target)) continue;
            drainSan(target, SackGhostRules.VEIL_SAN);
            SackGhostActions.veil(target, SackGhostRules.VEIL_DURATION);
        }
        fx(owner, "veil", center, SackGhostRules.VEIL_DURATION, (float) SackGhostRules.VEIL_RADIUS);
        return true;
    }

    private boolean castPush(ServerPlayer owner) {
        for (ServerPlayer target : owner.serverLevel().players()) {
            if (!isEnemy(target) || owner.distanceToSqr(target) > SackGhostRules.PUSH_RADIUS * SackGhostRules.PUSH_RADIUS) continue;
            Vec3 direction = target.position().subtract(owner.position()).multiply(1, 0, 1).normalize();
            if (direction.lengthSqr() < 0.1) direction = new Vec3(0, 0, 1);
            Vec3 destination = target.position();
            for (double distance = 0.25; distance <= SackGhostRules.PUSH_DISTANCE; distance += 0.25) {
                Vec3 candidate = target.position().add(direction.scale(distance));
                if (!collisionFree(owner.serverLevel(), target, candidate)) break;
                destination = candidate;
            }
            drainSan(target, SackGhostRules.PUSH_SAN);
            if (destination.distanceToSqr(target.position()) > 0.01)
                motions.put(target.getUUID(), new Motion(owner.serverLevel(), target.position(), destination, 6, 0, false));
        }
        fx(owner, "push", owner.position(), 25, (float) SackGhostRules.PUSH_RADIUS, owner.getId());
        owner.serverLevel().playSound(null, owner.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.8F, 0.7F);
        return true;
    }

    private boolean castEffigy(ServerPlayer owner) {
        Vec3 from = owner.getEyePosition(), end = from.add(owner.getLookAngle().scale(4));
        var hit = owner.serverLevel().clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        Vec3 position = hit.getType() == HitResult.Type.MISS ? end.add(0, -owner.getEyeHeight(), 0)
                : hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.35));
        if (!safeLanding(owner.serverLevel(), owner, position)) return fail("message.noellesroles.sack.no_landing");
        var decoy = SackGhostActors.spawnEffigy(owner, position);
        if (decoy == null) return fail("message.noellesroles.sack.no_landing");
        clearEffigy(owner);
        effigyEntity = decoy.getUUID();
        effigyLevel = owner.serverLevel();
        fx(owner, "effigy", position, 20, owner.getId(), -1);
        return true;
    }

    private void clearEffigy(ServerPlayer owner) {
        if (effigyEntity != null) {
            Entity entity = effigyLevel == null ? null : effigyLevel.getEntity(effigyEntity);
            if (entity != null) entity.discard();
        }
        effigyEntity = null;
        effigyLevel = null;
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
                    || level.getBlockCollisions(entity, entity.getBoundingBox().move(offset).deflate(1.0E-7)).iterator().hasNext()) return false;
        }
        return true;
    }

    private boolean castWail(ServerPlayer owner) {
        wailUntil = now() + SackGhostRules.WAIL_DURATION;
        wailExposure.clear();
        fx(owner, "wail", owner.position(), SackGhostRules.WAIL_DURATION, owner.getId(), (float) SackGhostRules.WAIL_RADIUS);
        owner.serverLevel().playSound(null, owner.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 0.8F, 0.7F);
        return true;
    }

    private boolean castParasite(ServerPlayer owner) {
        Player aimed = rayTarget(owner, SackGhostRules.PARASITE_RANGE);
        if (!(aimed instanceof ServerPlayer target) || parasites.containsKey(target.getUUID())
                || target.hasEffect(ModEffects.GHOST_WARD)) return fail("tip.noellesroles.no_target");
        parasites.put(target.getUUID(), SackGhostRules.PARASITE_DURATION);
        target.sendSystemMessage(Component.translatable("message.noellesroles.sack.parasite"));
        fx(owner, "parasite", target.position(), SackGhostRules.PARASITE_DURATION, target.getId(), owner.getId());
        if (SackGhostRules.parasiteShouldExecute(SREGameWorldComponent.KEY.get(target.level()).isRole(target, ModRoles.GLITCH_ROBOT),
                SREPlayerMoodComponent.KEY.get(target).getMood())) executeParasite(owner, target);
        return true;
    }

    /** 普通致死允许护盾拦截；先移除寄生，避免护盾挡住后下一tick再次尝试。 */
    private void executeParasite(ServerPlayer owner, ServerPlayer target) {
        parasites.remove(target.getUUID());
        fx(owner, "parasite", target.position(), 0, target.getId(), owner.getId());
        GameUtils.killPlayer(target, true, owner, Noellesroles.id("ghost_parasite"));
    }

    private boolean castSeize(ServerPlayer owner) {
        Player aimed = rayTarget(owner, SackGhostRules.SEIZE_RANGE);
        if (!(aimed instanceof ServerPlayer target)) return fail("tip.noellesroles.no_target");
        Vec3 direction = new Vec3(owner.getLookAngle().x, 0, owner.getLookAngle().z).normalize();
        if (direction.lengthSqr() < 0.1) return fail("message.noellesroles.sack.no_landing");
        Vec3 destination = owner.position().add(direction.scale(2));
        if (!safeLanding(owner.serverLevel(), target, destination) || !clearPath(owner.serverLevel(), target, destination))
            return fail("message.noellesroles.sack.no_landing");
        motions.put(target.getUUID(), new Motion(owner.serverLevel(), target.position(), destination, 8, 0, true));
        fx(owner, "seize", owner.position(), 10, target.getId(), owner.getId());
        return true;
    }

    private boolean castEcho(ServerPlayer owner, ItemStack stack) {
        Vec3 direction = new Vec3(owner.getLookAngle().x, 0, owner.getLookAngle().z).normalize();
        if (direction.lengthSqr() < 0.1 || echoToken != null) return fail("message.noellesroles.sack.no_landing");
        Vec3 destination = null;
        for (double distance = 0.25; distance <= SackGhostRules.ECHO_DISTANCE; distance += 0.25) {
            Vec3 candidate = owner.position().add(direction.scale(distance));
            if (!collisionFree(owner.serverLevel(), owner, candidate)) break;
            if (distance >= 1 && safeLanding(owner.serverLevel(), owner, candidate)) destination = candidate;
        }
        if (destination == null) return fail("message.noellesroles.sack.no_landing");
        echoPosition = owner.position();
        echoLevel = owner.serverLevel();
        echoToken = SackGhostItems.tag(stack).getUUID("Token");
        echoConsumes = SackGhostRules.consumesOnCast(GhostArt.ECHO, otherworldActive);
        echoUntil = now() + SackGhostRules.ECHO_WINDOW;
        fx(owner, "echo_depart", echoPosition, SackGhostRules.ECHO_WINDOW, owner.getId());
        owner.teleportTo(destination.x, destination.y, destination.z);
        owner.playNotifySound(SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 0.75F);
        return true;
    }

    private boolean collisionFree(ServerLevel level, Entity entity, Vec3 destination) {
        BlockPos pos = BlockPos.containing(destination);
        return level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos)
                && !level.getBlockCollisions(entity, entity.getBoundingBox().move(destination.subtract(entity.position())).deflate(1.0E-7)).iterator().hasNext();
    }

    private void tickMotions(ServerPlayer owner) {
        for (var iterator = motions.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            Motion motion = entry.getValue();
            ServerPlayer target = owner.server.getPlayerList().getPlayer(entry.getKey());
            if (target == null || motion.level() != owner.serverLevel() || !isEnemy(target)) { iterator.remove(); continue; }
            int elapsed = motion.elapsed() + 1;
            Vec3 next = motion.from().lerp(motion.to(), Math.min(1, elapsed / (double) motion.duration()));
            if (!clearPath(motion.level(), target, next) || !collisionFree(motion.level(), target, next)) { iterator.remove(); continue; }
            target.stopRiding();
            target.setDeltaMovement(Vec3.ZERO);
            target.connection.teleport(next.x, next.y, next.z, target.getYRot(), target.getXRot());
            target.hurtMarked = true;
            if (elapsed >= motion.duration()) {
                iterator.remove();
                if (motion.seize()) {
                    drainSan(target, SackGhostRules.SEIZE_SAN);
                    SackGhostActions.root(target, 40);
                }
            } else entry.setValue(new Motion(motion.level(), motion.from(), motion.to(), motion.duration(), elapsed, motion.seize()));
        }
    }

    private void tickWail(ServerPlayer owner) {
        if (wailUntil == 0) return;
        if (now() > wailUntil) { wailUntil = 0; wailExposure.clear(); return; }
        Set<UUID> exposed = new HashSet<>();
        for (ServerPlayer target : owner.serverLevel().players()) {
            if (!isEnemy(target) || owner.distanceToSqr(target) > SackGhostRules.WAIL_RADIUS * SackGhostRules.WAIL_RADIUS
                    || !owner.hasLineOfSight(target)) continue;
            exposed.add(target.getUUID());
            int ticks = wailExposure.merge(target.getUUID(), 1, Integer::sum);
            if ((ticks - 1) % 10 == 0) SackGhostActions.slow(target, 25, SackGhostRules.wailSlowAmplifier(ticks));
            if ((now() - (wailUntil - SackGhostRules.WAIL_DURATION)) % 20 == 0) drainSan(target, SackGhostRules.WAIL_SAN);
        }
        wailExposure.keySet().retainAll(exposed);
        if (now() >= wailUntil) { wailUntil = 0; wailExposure.clear(); }
    }

    private void tickTricks(ServerPlayer owner) {
        if (effigyEntity != null && (effigyLevel != owner.serverLevel() || effigyLevel.getEntity(effigyEntity) == null)) clearEffigy(owner);
        if (echoToken != null && (now() >= echoUntil || echoLevel != owner.serverLevel())) finishEcho(owner);
        tickMotions(owner);
        tickWail(owner);
        // 死亡/净雨回调也会清理寄生，使用键快照避免回调修改正在遍历的Map。
        for (UUID id : new ArrayList<>(parasites.keySet())) {
            Integer remaining = parasites.get(id);
            if (remaining == null) continue;
            ServerPlayer target = owner.server.getPlayerList().getPlayer(id);
            if (target == null || !isEnemy(target) || target.hasEffect(ModEffects.GHOST_WARD)) {
                if (target != null) cleanse(target); else parasites.remove(id);
                continue;
            }
            int left = remaining - 1;
            if (left % 20 == 0) drainSan(target, SackGhostRules.PARASITE_SAN);
            if (SackGhostRules.parasiteShouldExecute(SREGameWorldComponent.KEY.get(target.level()).isRole(target, ModRoles.GLITCH_ROBOT),
                    SREPlayerMoodComponent.KEY.get(target).getMood())) {
                executeParasite(owner, target);
            } else if (left <= 0) cleanse(target); else parasites.put(id, left);
        }
    }

    private void clearTricks(ServerPlayer owner) {
        finishEcho(owner);
        for (UUID id : new ArrayList<>(parasites.keySet())) {
            ServerPlayer target = owner.server.getPlayerList().getPlayer(id);
            if (target != null) cleanse(target);
        }
        parasites.clear();
        if (wailUntil != 0) fx(owner, "wail", owner.position(), 0, owner.getId(), (float) SackGhostRules.WAIL_RADIUS);
        wailUntil = 0;
        wailExposure.clear();
        motions.clear();
        clearEffigy(owner);
    }

    private void updateCooldowns() {
        generationCooldown = SackGhostRules.remaining(now(), generationReadyAt);
        castCooldown = SackGhostRules.remaining(now(), castReadyAt);
        ultimateCooldown = SackGhostRules.remaining(now(), realmReadyAt);
        realmHitCooldown = SackGhostRules.remaining(now(), hitReadyAt);
        escapeCooldown = SackGhostRules.remaining(now(), escapeReadyAt);
        escapeTicks = SackGhostRules.remaining(now(), escapeUntil);
        shroudCooldown = SackGhostRules.remaining(now(), shroudReadyAt);
        shroudTicks = SackGhostRules.remaining(now(), shroudUntil);
        echoTicks = SackGhostRules.remaining(now(), echoUntil);
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
        maintainForms(owner);
        if (otherworldActive) {
            maintainRealmEffects(owner);
            if (body != null) SackGhostActors.tickBody(body);
            if (body == null || body.entity().isRemoved()) endOtherworld(false);
        }
        if (owner.tickCount % 5 == 0) updateThreat(owner);
        if (owner.tickCount % 20 == 0) {
            if (otherworldActive) {
                refreshRealmEffects(owner);
                SackGhostPackets.publish(owner, this);
            }
            if (formFlags() != 0 || formLevel != owner.serverLevel()) publishForm(owner);
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
            endEscape(owner);
            breakShroud();
            clearTricks(owner);
            for (var entry : new ArrayList<>(formEffects.entrySet())) restoreLease(owner, entry.getKey(), entry.getValue());
            formEffects.clear();
            if (body != null) SackGhostActors.removeBody(body);
            body = null;
            SackGhostItems.clearOwnerArts(owner);
            SackGhostPackets.publishForm(formLevel == null ? owner.serverLevel() : formLevel, owner, 0, 0);
            formLevel = null;
        }
        stage = 0;
        threat = 0;
        bodyThreat = false;
        parasites.clear();
        moodDirty.clear();
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
        tag.putInt("escapeCd", escapeCooldown);
        tag.putInt("escape", escapeTicks);
        tag.putInt("shroudCd", shroudCooldown);
        tag.putInt("shroud", shroudTicks);
        tag.putInt("echo", echoTicks);
        tag.putInt("threat", threat);
        tag.putBoolean("bodyThreat", bodyThreat);
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
        escapeReadyAt = now() + Math.max(0, tag.getInt("escapeCd"));
        escapeUntil = tag.getInt("escape") > 0 ? now() + tag.getInt("escape") : 0;
        shroudReadyAt = now() + Math.max(0, tag.getInt("shroudCd"));
        shroudUntil = tag.getInt("shroud") > 0 ? now() + tag.getInt("shroud") : 0;
        echoUntil = tag.getInt("echo") > 0 ? now() + tag.getInt("echo") : 0;
        threat = Math.clamp(tag.getInt("threat"), 0, 2);
        bodyThreat = tag.getBoolean("bodyThreat");
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
        if (player instanceof ServerPlayer owner) {
            endOtherworld(false);
            endEscape(owner);
            breakShroud();
            clearTricks(owner);
        }
        long liveRealmReadyAt = realmReadyAt, liveEscapeReadyAt = escapeReadyAt, liveShroudReadyAt = shroudReadyAt;
        readFromSyncNbt(tag, provider);
        // 成长可恢复，已消费的替死、隐身冷却与一次性令牌不能靠回溯重复获取。
        if (player instanceof ServerPlayer owner) {
            otherworldActive = false;
            otherworldDuration = 0;
            escapeUntil = shroudUntil = echoUntil = 0;
            verdicts.clear();
            parasites.clear();
            realmReadyAt = Math.max(realmReadyAt, liveRealmReadyAt);
            escapeReadyAt = Math.max(escapeReadyAt, liveEscapeReadyAt);
            shroudReadyAt = Math.max(shroudReadyAt, liveShroudReadyAt);
            if (interrupted || tag.getBoolean("realm")) realmReadyAt = Math.max(realmReadyAt, now() + SackGhostRules.REALM_COOLDOWN);
            updateCooldowns();
            publishForm(owner);
        }
    }
}
