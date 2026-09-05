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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.role_data.neutral;

import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerControlled;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnTeammateKilledTeammate;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 幻灵：好人方中立。外形为悦灵，无碰撞，离地 1.6 格悬浮；空格可跳但会沉回悬浮高度。
 */
public class PhantomSpiritRoleData extends SimpleRoleData {

    public static final ResourceLocation SKILL_ID = Noellesroles.id("phantom_spirit_possess");

    public static final int POSSESS_SECONDS = 20;
    public static final int COOLDOWN_SECONDS = 60;
    public static final int REVEAL_SECONDS = 2;
    public static final double REVEAL_RANGE = 10.0;
    public static final double POSSESS_RANGE = 8.0;
    public static final double HOVER_HEIGHT = 1.6;

    private static final int DISGUISE_RESYNC_INTERVAL = 20;
    private static final int DISGUISE_RESYNC_WINDOW = 100;
    private static final double LOOK_DOT_MIN = 0.35;
    private static final double HOVER_SEARCH_DISTANCE = 256.0;
    private static final float HOVER_STEP_HEIGHT = 1.25F;
    private static final float DEFAULT_STEP_HEIGHT = 0.6F;
    private static final double HOVER_FLOOR_EPSILON = 0.06;
    private static final double HOVER_SINK_SPEED = 0.28;

    /** 伪装成悦灵（对所有客户端同步） */
    public boolean disguised = true;
    public UUID hostUuid;
    public long possessUntilGameTime;
    public long revealUntilGameTime;
    public boolean usedReveal;
    private int disguiseResyncTicks = 0;

    /** 代死过程中避免递归击杀 */
    private static boolean sacrificing;

    public PhantomSpiritRoleData(RoleDataContext context) {
        super(context);
    }

    public boolean isDisguised() {
        return disguised;
    }

    public static boolean isDisguised(Player player) {
        PhantomSpiritRoleData data = RoleData.getNullable(PhantomSpiritRoleData.class, player);
        return data != null && data.disguised;
    }

    public static boolean isRidingPlayer(Player player) {
        return player.getVehicle() instanceof Player;
    }

    public static boolean shouldStayRiding(Player rider) {
        PhantomSpiritRoleData data = RoleData.getNullable(PhantomSpiritRoleData.class, rider);
        if (data == null || data.hostUuid == null) {
            return false;
        }
        if (!(rider.getVehicle() instanceof Player host)) {
            return false;
        }
        return data.hostUuid.equals(host.getUUID()) && rider.level().getGameTime() < data.possessUntilGameTime
                && GameUtils.isPlayerAliveAndSurvival(rider);
    }

    public static boolean isRevealActiveFor(Player viewer) {
        PhantomSpiritRoleData selfData = RoleData.getNullable(PhantomSpiritRoleData.class, viewer);
        if (selfData != null && selfData.isRevealActive()) {
            return true;
        }
        Entity passenger = viewer.getFirstPassenger();
        if (passenger instanceof Player rider) {
            PhantomSpiritRoleData riderData = RoleData.getNullable(PhantomSpiritRoleData.class, rider);
            return riderData != null && riderData.isRevealActive() && viewer.getUUID().equals(riderData.hostUuid);
        }
        return false;
    }

    public boolean isPossessing() {
        return hostUuid != null && player.level().getGameTime() < possessUntilGameTime;
    }

    public boolean isRevealActive() {
        return player.level().getGameTime() < revealUntilGameTime;
    }

    public boolean useSkill(RoleSkillContext context) {
        ServerPlayer sp = context.player();
        if (sp.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (!gameWorld.isRunning() || !gameWorld.isRole(sp, ModRoles.PHANTOM_SPIRIT)) {
            return false;
        }
        if (isPossessing()) {
            return tryReveal(sp);
        }
        if (!context.skillReady()) {
            return false;
        }
        return tryPossess(sp, context.target());
    }

    private boolean tryPossess(ServerPlayer sp, @Nullable UUID crosshair) {
        if (crosshair != null && sp.level().getPlayerByUUID(crosshair) instanceof ServerPlayer looked
                && isInvisibleTarget(looked)) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.phantom_spirit.invisible_target")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ServerPlayer target = findPossessTarget(sp, crosshair);
        if (target == null) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.phantom_spirit.no_target")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!AllowPlayerControlled.EVENT.invoker().allowControlled(sp, target)) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.phantom_spirit.blocked")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        stopHover(sp);
        if (!sp.startRiding(target, true)) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.phantom_spirit.blocked")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        hostUuid = target.getUUID();
        possessUntilGameTime = sp.level().getGameTime() + POSSESS_SECONDS * 20L;
        revealUntilGameTime = 0;
        usedReveal = false;
        sync();
        sp.serverLevel().playSound(null, target.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.PLAYERS,
                0.8f, 1.2f);
        sp.displayClientMessage(Component.translatable("message.noellesroles.phantom_spirit.possess_start",
                target.getDisplayName()).withStyle(ChatFormatting.AQUA), true);
        target.displayClientMessage(Component.translatable("message.noellesroles.phantom_spirit.host_possessed")
                .withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    private boolean tryReveal(ServerPlayer sp) {
        if (usedReveal) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.phantom_spirit.reveal_already")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        usedReveal = true;
        revealUntilGameTime = sp.level().getGameTime() + REVEAL_SECONDS * 20L;
        sync();
        sp.displayClientMessage(Component.translatable("message.noellesroles.phantom_spirit.reveal")
                .withStyle(ChatFormatting.AQUA), true);
        Player host = sp.level().getPlayerByUUID(hostUuid);
        if (host instanceof ServerPlayer hostPlayer) {
            hostPlayer.displayClientMessage(Component.translatable("message.noellesroles.phantom_spirit.reveal_host")
                    .withStyle(ChatFormatting.AQUA), true);
        }
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.ALLAY_AMBIENT_WITH_ITEM, SoundSource.PLAYERS,
                0.7f, 1.4f);
        return true;
    }

    @Nullable
    private ServerPlayer findPossessTarget(ServerPlayer sp, @Nullable UUID crosshair) {
        if (crosshair != null && sp.level().getPlayerByUUID(crosshair) instanceof ServerPlayer looked
                && isValidPossessTarget(sp, looked)
                && sp.distanceToSqr(looked) <= POSSESS_RANGE * POSSESS_RANGE) {
            return looked;
        }
        ServerPlayer best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3 look = sp.getLookAngle();
        Vec3 eye = sp.getEyePosition();
        for (ServerPlayer candidate : sp.serverLevel().players()) {
            if (!isValidPossessTarget(sp, candidate)) {
                continue;
            }
            double distSq = sp.distanceToSqr(candidate);
            if (distSq > POSSESS_RANGE * POSSESS_RANGE) {
                continue;
            }
            Vec3 to = candidate.getEyePosition().subtract(eye);
            double len = to.length();
            if (len < 1.0e-4) {
                continue;
            }
            double dot = look.dot(to.scale(1.0 / len));
            if (dot < LOOK_DOT_MIN) {
                continue;
            }
            double score = distSq * (2.0 - dot);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    public static boolean isInvisibleTarget(Player target) {
        return target.isInvisible() || target.hasEffect(MobEffects.INVISIBILITY);
    }

    private static boolean isValidPossessTarget(ServerPlayer spirit, ServerPlayer target) {
        if (target == spirit || !GameUtils.isPlayerAliveAndSurvival(target) || isInvisibleTarget(target)) {
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(spirit.level());
        if (gameWorld.isRole(target, ModRoles.PHANTOM_SPIRIT)) {
            return false;
        }
        Entity existing = target.getFirstPassenger();
        return existing == null || existing == spirit;
    }

    public void endPossess(boolean notify) {
        if (hostUuid == null && !player.isPassenger()) {
            return;
        }
        UUID previousHost = hostUuid;
        hostUuid = null;
        possessUntilGameTime = 0;
        revealUntilGameTime = 0;
        usedReveal = false;
        if (player.isPassenger()) {
            player.stopRiding();
        }
        if (player instanceof ServerPlayer sp && notify && GameUtils.isPlayerAliveAndSurvival(sp)) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.phantom_spirit.possess_end")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        Player host = previousHost != null ? player.level().getPlayerByUUID(previousHost) : null;
        if (notify && host instanceof ServerPlayer hostPlayer) {
            hostPlayer.displayClientMessage(Component.translatable("message.noellesroles.phantom_spirit.possess_end")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        if (player instanceof ServerPlayer) {
            sync();
        }
    }

    @Nullable
    public static PhantomSpiritRoleData findPossessingSpirit(Player host) {
        for (Entity passenger : host.getPassengers()) {
            if (passenger instanceof Player rider) {
                PhantomSpiritRoleData data = RoleData.getNullable(PhantomSpiritRoleData.class, rider);
                if (data != null && host.getUUID().equals(data.hostUuid) && data.isPossessing()) {
                    return data;
                }
            }
        }
        if (!(host instanceof ServerPlayer serverHost)) {
            return null;
        }
        for (ServerPlayer other : serverHost.serverLevel().players()) {
            PhantomSpiritRoleData data = RoleData.getNullable(PhantomSpiritRoleData.class, other);
            if (data != null && host.getUUID().equals(data.hostUuid) && data.isPossessing()) {
                return data;
            }
        }
        return null;
    }

    private static boolean sacrificeForHost(Player victim, @Nullable Player killer, ResourceLocation reason) {
        if (sacrificing || !(victim instanceof ServerPlayer)) {
            return true;
        }
        PhantomSpiritRoleData spiritData = findPossessingSpirit(victim);
        if (spiritData == null || !(spiritData.player instanceof ServerPlayer spirit)) {
            return true;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(spirit)) {
            return true;
        }
        sacrificing = true;
        try {
            spiritData.endPossess(false);
            victim.displayClientMessage(Component.translatable("message.noellesroles.phantom_spirit.sacrificed")
                    .withStyle(ChatFormatting.GOLD), true);
            spirit.displayClientMessage(Component.translatable("message.noellesroles.phantom_spirit.sacrificed_self")
                    .withStyle(ChatFormatting.RED), true);
            GameUtils.killPlayer(spirit, true, killer, reason);
        } finally {
            sacrificing = false;
        }
        return false;
    }

    private static void notifyInnocentTeamKill(Player victim, Player killer, ResourceLocation deathReason) {
        if (!(victim instanceof ServerPlayer serverVictim) || !(killer instanceof ServerPlayer serverKiller)) {
            return;
        }
        if (serverVictim.getUUID().equals(serverKiller.getUUID())) {
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverVictim.level());
        if (!gameWorld.isRunning()) {
            return;
        }
        boolean victimSpirit = gameWorld.isRole(serverVictim, ModRoles.PHANTOM_SPIRIT);
        boolean killerSpirit = gameWorld.isRole(serverKiller, ModRoles.PHANTOM_SPIRIT);
        if (victimSpirit == killerSpirit) {
            return;
        }
        SRERole otherRole = gameWorld.getRole(victimSpirit ? serverKiller : serverVictim);
        if (otherRole == null || !otherRole.isInnocent()) {
            return;
        }
        OnTeammateKilledTeammate.EVENT.invoker().playerKilled(serverVictim, serverKiller, true, deathReason);
    }

    public static void registerEvents() {
        AllowPlayerDeathWithKiller.EVENT.register(PhantomSpiritRoleData::sacrificeForHost);
        OnPlayerDeathWithKiller.EVENT.register(PhantomSpiritRoleData::notifyInnocentTeamKill);
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, bound) -> {
            PhantomSpiritRoleData data = RoleData.getNullable(PhantomSpiritRoleData.class, sender);
            if (data == null) {
                return true;
            }
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sender.level());
            if (!gameWorld.isRunning() || !GameUtils.isPlayerAliveAndSurvival(sender)) {
                return true;
            }
            if (isRidingPlayer(sender)) {
                return true;
            }
            sender.displayClientMessage(Component.translatable("message.noellesroles.phantom_spirit.cannot_chat")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        });
    }

    @Override
    public void init() {
        setDisguised(false);
        hostUuid = null;
        possessUntilGameTime = 0;
        revealUntilGameTime = 0;
        usedReveal = false;
    }

    @Override
    public void initOnClient() {
        setDisguised(false);
    }

    @Override
    public void clear() {
        if (player instanceof ServerPlayer sp) {
            endPossess(false);
            sp.removeEffect(ModEffects.NO_COLLIDE);
        }
        stopHover(player);
        disableFlying(player);
        init();
    }

    private void setDisguised(boolean value) {
        if (disguised == value) {
            return;
        }
        disguised = value;
        player.refreshDimensions();
    }

    private static boolean isFreeFlightMode(Player target) {
        return target.isSpectator() || target.isCreative();
    }

    private static void disableFlying(Player target) {
        if (isFreeFlightMode(target)) {
            return;
        }
        var abilities = target.getAbilities();
        if (!abilities.mayfly && !abilities.flying) {
            return;
        }
        abilities.mayfly = false;
        abilities.flying = false;
        abilities.setFlyingSpeed(0.05F);
        target.onUpdateAbilities();
    }

    private static void stopHover(Player target) {
        target.setNoGravity(false);
        setStepHeight(target, DEFAULT_STEP_HEIGHT);
    }

    private static void setStepHeight(Player target, float height) {
        var attribute = target.getAttribute(Attributes.STEP_HEIGHT);
        if (attribute != null && attribute.getBaseValue() != height) {
            attribute.setBaseValue(height);
        }
    }

    private static void applyNoCollide(ServerPlayer sp) {
        sp.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, 40, 0, true, false, false));
    }

    @Nullable
    private static Double findGroundY(Player target) {
        double startY = target.getY() + Math.max(0.2, target.getBbHeight() * 0.5);
        Vec3 start = new Vec3(target.getX(), startY, target.getZ());
        Vec3 end = new Vec3(target.getX(), target.getY() - HOVER_SEARCH_DISTANCE, target.getZ());
        BlockHitResult hit = target.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, target));
        if (hit.getType() == HitResult.Type.MISS) {
            return null;
        }
        return hit.getLocation().y;
    }

    private static void applyHover(Player target) {
        if (isFreeFlightMode(target) || target.isPassenger()) {
            stopHover(target);
            return;
        }
        disableFlying(target);
        setStepHeight(target, HOVER_STEP_HEIGHT);
        target.fallDistance = 0;

        Double groundY = findGroundY(target);
        if (groundY == null) {
            stopHover(target);
            return;
        }

        target.setNoGravity(true);
        double hoverY = groundY + HOVER_HEIGHT;
        double y = target.getY();
        Vec3 vel = target.getDeltaMovement();
        boolean rising = vel.y > 0.02;

        if (y < hoverY - HOVER_FLOOR_EPSILON) {
            target.setPos(target.getX(), hoverY, target.getZ());
            if (vel.y < 0) {
                vel = new Vec3(vel.x, 0.0, vel.z);
            }
            target.setOnGround(true);
        } else if (y > hoverY + HOVER_FLOOR_EPSILON) {
            if (!rising) {
                vel = new Vec3(vel.x, Math.min(vel.y, -HOVER_SINK_SPEED), vel.z);
                if (y + vel.y <= hoverY) {
                    target.setPos(target.getX(), hoverY, target.getZ());
                    vel = new Vec3(vel.x, 0.0, vel.z);
                    target.setOnGround(true);
                } else {
                    target.setOnGround(false);
                }
            } else {
                target.setOnGround(false);
            }
        } else if (!rising) {
            target.setPos(target.getX(), hoverY, target.getZ());
            vel = new Vec3(vel.x, 0.0, vel.z);
            target.setOnGround(true);
        } else {
            target.setOnGround(false);
        }
        target.setDeltaMovement(vel);
    }

    @Override
    public void clientTick() {
        if (!player.isLocalPlayer()) {
            return;
        }
        if (isFreeFlightMode(player)) {
            stopHover(player);
            return;
        }
        if (!disguised || !GameUtils.isPlayerAliveAndSurvival(player)) {
            stopHover(player);
            disableFlying(player);
            return;
        }
        if (player.isPassenger()) {
            stopHover(player);
            disableFlying(player);
            return;
        }
        applyHover(player);
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        boolean shouldDisguise = gameWorld.isRunning() && GameUtils.isPlayerAliveAndSurvival(sp);
        if (shouldDisguise != disguised) {
            setDisguised(shouldDisguise);
            disguiseResyncTicks = DISGUISE_RESYNC_WINDOW;
            if (!shouldDisguise) {
                endPossess(false);
                stopHover(sp);
                disableFlying(sp);
                sp.removeEffect(ModEffects.NO_COLLIDE);
            }
            sync();
        } else if (disguiseResyncTicks > 0) {
            disguiseResyncTicks--;
            if (disguiseResyncTicks % DISGUISE_RESYNC_INTERVAL == 0) {
                sync();
            }
        }
        if (!shouldDisguise) {
            return;
        }

        applyNoCollide(sp);
        disableFlying(sp);

        if (isPossessing()) {
            Player host = sp.level().getPlayerByUUID(hostUuid);
            if (!(host instanceof ServerPlayer hostPlayer) || !GameUtils.isPlayerAliveAndSurvival(hostPlayer)) {
                endPossess(true);
                return;
            }
            if (isInvisibleTarget(hostPlayer)) {
                endPossess(false);
                sp.displayClientMessage(Component.translatable("message.noellesroles.phantom_spirit.host_invisible")
                        .withStyle(ChatFormatting.YELLOW), true);
                hostPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.phantom_spirit.host_invisible")
                                .withStyle(ChatFormatting.YELLOW),
                        true);
                return;
            }
            if (sp.getVehicle() != hostPlayer) {
                stopHover(sp);
                if (isInvisibleTarget(hostPlayer) || !sp.startRiding(hostPlayer, true)) {
                    endPossess(true);
                    return;
                }
            }
            hostPlayer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 10, 0, true, false, true));
            stopHover(sp);
            return;
        }

        if (hostUuid != null) {
            endPossess(true);
        }
        if (!sp.isPassenger()) {
            applyHover(sp);
        } else {
            stopHover(sp);
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        return true;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("disguised", disguised);
        if (hostUuid != null) {
            tag.putUUID("host", hostUuid);
        }
        tag.putLong("possessUntil", possessUntilGameTime);
        tag.putLong("revealUntil", revealUntilGameTime);
        tag.putBoolean("usedReveal", usedReveal);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        setDisguised(tag.getBoolean("disguised"));
        hostUuid = tag.hasUUID("host") ? tag.getUUID("host") : null;
        possessUntilGameTime = tag.getLong("possessUntil");
        revealUntilGameTime = tag.getLong("revealUntil");
        usedReveal = tag.getBoolean("usedReveal");
    }
}
