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

package org.agmas.noellesroles.game.fake_steve;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.cca.DynamicShopComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.content.block.PlatterBlock;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.content.block_entity.PlateTrayBlockEntity;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.KillerKnifeDurability;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.TrainWeapon;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.agmas.noellesroles.content.entity.LockEntityManager;
import io.wifi.starrailexpress.content.item.GrenadeItem;

/** Server-side controller for a replaced player body. */
public class FakeSteveAi {
    private static final double FACE_COS = Math.cos(Math.toRadians(30.0));
    private static final ResourceLocation BACKSTAB = GameConstants.DeathReasons.FAKE_AI_BACKSTAB;
    /** How long a body stays committed to one prey before re-evaluating. */
    private static final long PREY_COMMIT_TICKS = 80L;
    private static final int STRIKE_NONE = 0;
    private static final int STRIKE_KILLED = 1;
    private static final int STRIKE_BUSY = 2;
    private static final double GRENADE_MIN_RANGE = 4.0D;
    private static final double GRENADE_MAX_RANGE = 16.0D;
    private static final long GRENADE_CHARGE_TICKS = 12L;
    private static boolean registered;

    private FakeSteveAi() {
    }

    static void register() {
        if (registered)
            return;
        registered = true;
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, bound) -> {
            onChat(sender, message.signedContent());
            return true;
        });
    }

    static void tick(ServerLevel level, ServerPlayer body, FakeSteveAgentState state) {
        long now = level.getGameTime();
        SRERole originalRole = SREGameWorldComponent.KEY.get(level).getRole(body);
        boolean psychoActive = originalRole != null
                && SREPlayerPsychoComponent.KEY.get(body).inPsycho();
        boolean derringerBerserk = FakeSteveKillerPolicy.entersDerringerBerserk(
                findDerringerSlot(body) >= 0);
        boolean berserkActive = FakeSteveKillerPolicy.isBerserk(psychoActive, derringerBerserk);
        int cadence = FakeSteveKillerPolicy.decisionCadenceTicks(berserkActive);
        if (cadence > 1
                && (now + Math.floorMod(body.getUUID().hashCode(), cadence)) % cadence != 0L) {
            return;
        }
        if (state.lastTickAt == 0L) {
            state.modeStartedTick = now;
        }
        int elapsed = state.lastTickAt == 0L ? cadence
                : (int) Math.max(1L, Math.min(20L, now - state.lastTickAt));
        state.lastTickAt = now;
        state.tickStep = elapsed;

        updateStuck(level, body, state, now);
        holsterKnifeIfReady(body, state, now);

        ServerPlayer focus = engageable(level, state.focusTarget);
        if (focus == null && state.focusTarget != null) {
            clearFocus(state);
        }

        if (derringerBerserk) {
            // A carried Derringer deliberately blows the cover. It must never be
            // diverted into a conversation, task, or stare state while hunting.
            clearFocus(state);
            state.pendingEngagement = false;
            state.brain.disengage();
        } else if (!berserkActive && state.mode != AgentMode.STARE
                && state.mode != AgentMode.STALK
                && state.mode != AgentMode.HUNT) {
            ServerPlayer facing = facingHuman(level, body);
            if (facing != null) {
                if (facing.getUUID().equals(state.focusTarget)) {
                    state.faceTicks += elapsed;
                } else {
                    state.focusTarget = facing.getUUID();
                    state.faceTicks = elapsed;
                }
                if (FakeSteveRules.hasFaceToFaceCommunication(state.faceTicks)) {
                    beginStare(state, facing);
                    focus = facing;
                }
            } else {
                state.faceTicks = 0;
            }
        }
        focus = engageable(level, state.focusTarget);
        concealWeaponIfExposed(level, body, state, berserkActive);

        boolean huntPhase = FakeSteveDirector.isHuntPhase(level);
        boolean killerRole = originalRole != null && originalRole.canUseKiller();
        boolean psychoArmed = psychoActive && findPsychoWeaponSlot(body, originalRole) >= 0;
        boolean armed = psychoArmed || findUsableKnifeSlot(body) >= 0 || findUsableGunSlot(body) >= 0;
        ServerPlayer prey = killerRole || derringerBerserk || huntPhase || berserkActive
                ? choosePrey(level, body, state, now, derringerBerserk, armed,
                        berserkActive) : null;
        focus = engageable(level, state.focusTarget);

        ServerPlayer isolated = !derringerBerserk && FakeSteveDirector.isEnabled()
                ? isolatedTarget(level, body) : null;
        boolean taskAvailable = !derringerBerserk && FakeSteveTaskPlanner.hasCompletableTask(body, state);
        if (!berserkActive) {
            prepareShop(level, body, state, originalRole, killerRole, prey, psychoActive);
        }
        boolean preyWitnessed = prey != null && witnessed(level, body, prey, berserkActive);
        boolean killOpportunity = prey != null && (berserkActive
                || FakeSteveKillerPolicy.isKillOpportunity(preyWitnessed ? 1 : 0,
                        otherLivingHumansNear(level, prey, 12.0) + 1, armed));
        boolean interruptTask = derringerBerserk && prey != null
                || FakeSteveKillerPolicy.shouldPsychoInterruptTask(
                psychoArmed, prey != null)
                || prey != null && FakeSteveKillerPolicy.shouldSkipTaskForStrike(
                        taskAvailable, armed, !preyWitnessed,
                        body.distanceTo(prey), killOpportunity);
        boolean huntReady = prey != null
                && FakeSteveKillerPolicy.shouldSeekPrey(
                        killerRole || huntPhase || berserkActive, armed,
                        berserkActive, killOpportunity);
        if (!derringerBerserk) {
            maybeSpeak(level, body, state);
        }
        boolean targetLooking = focus != null && visible(focus, body)
                && faces(focus, body, FACE_COS);
        boolean safeBackstab = focus != null && body.distanceToSqr(focus) <= 144.0D
                && visible(body, focus) && behind(body, focus)
                && !witnessed(level, body, focus, berserkActive);
        boolean recovering = state.mode == AgentMode.RECOVER && now < state.nextDecisionTick;

        // No dedicated hunt: only targets that are already within reach get struck,
        // and an isolated human is replaced instead of butchered. A psycho body
        // ignores the assimilation nicety and simply kills whoever is close.
        int strike = STRIKE_NONE;
        if (prey != null && !recovering
                && (berserkActive || (isolated == null && state.mode != AgentMode.STARE))) {
            strike = tryArmedAttack(level, body, prey, state, psychoActive, derringerBerserk);
        }
        if (strike == STRIKE_BUSY) {
            return;
        }
        if (strike == STRIKE_KILLED) {
            state.focusTarget = null;
            state.pendingEngagement = false;
            state.committedTarget = null;
            state.committedUntilTick = 0L;
            state.ambushGoal = null;
            state.ambushTarget = null;
            state.brain.disengage();
            boolean dualWield = findUsableKnifeSlot(body) >= 0 && findUsableGunSlot(body) >= 0;
            int recoveryTicks = FakeSteveKillerPolicy.recoveryTicksAfterKill(berserkActive, dualWield);
            if (recoveryTicks > 0) {
                state.mode = AgentMode.RECOVER;
                state.nextDecisionTick = now + recoveryTicks;
            } else {
                state.mode = AgentMode.DISGUISE_IDLE;
                state.nextDecisionTick = now;
            }
            state.modeStartedTick = now;
            return;
        }

        if (derringerBerserk && prey != null) {
            state.mode = AgentMode.DISGUISE_IDLE;
            state.modeStartedTick = now;
            state.sprintUntilTick = Math.max(state.sprintUntilTick, now + 40L);
            follow(level, body, prey.blockPosition(), state, 0.28D);
            return;
        }

        FakeSteveBrain.BrainIntent intent = state.brain.tick(new FakeSteveBrain.PerceptionSnapshot(
                elapsed, recovering, state.pendingEngagement, focus != null,
                targetLooking, safeBackstab, !psychoActive && isolated != null,
                !psychoActive && taskAvailable && !interruptTask, huntReady));
        if (!intent.recover()) {
            state.pendingEngagement = false;
        }
        AgentMode previousMode = state.mode;
        state.mode = intent.mode();
        if (state.mode != previousMode) {
            state.modeStartedTick = now;
        }
        if (state.mode != AgentMode.DISGUISE_IDLE) {
            state.idleTicks = 0;
        }
        // Nothing may run forever: a body waving a knife in one spot is not human.
        if (FakeSteveKillerPolicy.modeExpired(now, state.modeStartedTick,
                FakeSteveKillerPolicy.modeBudgetTicks(state.mode, psychoActive))) {
            abandonBehaviour(level, body, state, now);
        }

        if (intent.recover()) {
            idleHold(level, body, state, now);
            return;
        }
        if (state.mode == AgentMode.STARE && focus != null) {
            if (psychoActive && body.distanceToSqr(focus) > 4.0D) {
                // A frenzied body does not freeze in a staring contest: it closes in.
                follow(level, body, focus.blockPosition(), state, 0.22D);
                return;
            }
            lookAt(body, state, focus.getEyePosition());
            return;
        }
        if (state.mode == AgentMode.HUNT) {
            ServerPlayer huntTarget = prey != null ? prey : engageable(level, state.committedTarget);
            if (huntTarget == null) {
                state.mode = AgentMode.DISGUISE_IDLE;
                state.committedTarget = null;
                state.committedUntilTick = 0L;
            } else {
                BlockPos huntGoal = berserkActive
                        ? huntTarget.blockPosition()
                        : ambushGoal(level, state, huntTarget, now);
                follow(level, body, huntGoal, state, berserkActive ? 0.26D : 0.20D);
                return;
            }
        }
        if (state.mode == AgentMode.STALK && focus != null) {
            if (!psychoActive && intent.attack() && backstabAssimilate(body, focus)) {
                clearFocus(state);
                state.mode = AgentMode.RECOVER;
                state.nextDecisionTick = now + 40L;
                state.modeStartedTick = now;
                return;
            }
            follow(level, body,
                    psychoActive ? focus.blockPosition()
                            : ambushGoal(level, state, focus, now),
                    state, psychoActive ? 0.24D : 0.19D);
            return;
        }
        if (state.mode == AgentMode.ASSIMILATE && isolated != null) {
            state.focusTarget = isolated.getUUID();
            state.assimilationTicks += elapsed;
            if (body.distanceToSqr(isolated) > 9.0D) {
                follow(level, body, isolated.blockPosition(), state, 0.17D);
            } else {
                lookAt(body, state, isolated.getEyePosition());
            }
            if (FakeSteveRules.canAssimilate(livingFakesNear(level, isolated, 12.0),
                    otherLivingHumansNear(level, isolated, 12.0), state.assimilationTicks)) {
                FakeSteveDirector.replace(isolated, ReplacementCause.ASSIMILATION);
                clearFocus(state);
            }
            return;
        }
        state.assimilationTicks = 0;

        if (shouldFlee(level, body, berserkActive)
                && state.mode != AgentMode.STARE && state.mode != AgentMode.STALK
                && state.mode != AgentMode.HUNT) {
            flee(level, body, state);
            return;
        }

        // 「生前轨迹」是纯移动辅助，但优先级高于伪装任务：玩家被替换后身上几乎总是还有未完成任务，
        // 若让任务先抢走移动权，AI 会一直用 A* 跑去任务点（表现为漫无目的地乱跑），永远走不到轨迹上。
        // 唯一的例外是狂暴/精神错乱正在追人时，那时的移动不属于「空闲巡逻」。
        if (!(berserkActive && prey != null)
                && trailPatrol(level, body, state, now, psychoActive)) {
            return;
        }

        if (state.mode == AgentMode.DISGUISE_TASK
                && FakeSteveTaskPlanner.tick(level, body, state)) {
            return;
        }

        if (berserkActive && prey != null) {
            follow(level, body, prey.blockPosition(), state, 0.26D);
            return;
        }

        state.mode = AgentMode.DISGUISE_IDLE;
        state.idleTicks += elapsed;
        if (FakeSteveMotionPolicy.shouldSprint(false, state.idleTicks,
                body.getUUID().hashCode() + (int) (now / 20L))) {
            state.sprintUntilTick = Math.max(state.sprintUntilTick, now + 30L + level.getRandom().nextInt(30));
            state.idleTicks = 0;
        }
        boolean reselectWander = FakeSteveWanderPolicy.shouldReselectNow(
                state.pathGoal == null, state.pathFailureCount, now >= state.nextDecisionTick)
                || (psychoActive && state.pathGoal == null);
        if (reselectWander) {
            state.nextDecisionTick = now + (psychoActive
                    ? 10L + level.getRandom().nextInt(15)
                    : 40L + level.getRandom().nextInt(80));
            boolean interacted = !psychoActive && tryInteract(level, body, state, now);
            if (!interacted) {
                state.pathGoal = wanderGoal(level, body, state);
                state.path.clear();
                state.pathFailureCount = 0;
            }
        }
        if (state.pathGoal != null) {
            follow(level, body, state.pathGoal, state, psychoActive ? 0.22D : 0.15D);
        } else {
            idleHold(level, body, state, now);
        }
    }

    /**
     * Wander-only subset of {@link #tick} for 失心症.
     * Reuses Fake Steve locomotion without stare, hunt, assimilate, or impostor tells.
     */
    public static void tickWanderOnly(ServerLevel level, ServerPlayer body, FakeSteveAgentState state) {
        long now = level.getGameTime();
        if (state.lastTickAt == 0L) {
            state.modeStartedTick = now;
        }
        int elapsed = state.lastTickAt == 0L ? 5
                : (int) Math.max(1L, Math.min(20L, now - state.lastTickAt));
        state.lastTickAt = now;
        state.tickStep = elapsed;
        state.mode = AgentMode.DISGUISE_IDLE;
        updateStuck(level, body, state, now);
        FakeSteveMotionController.applyServerMotion(body, state);

        state.idleTicks += elapsed;
        if (FakeSteveMotionPolicy.shouldSprint(false, state.idleTicks,
                body.getUUID().hashCode() + (int) (now / 20L))) {
            state.sprintUntilTick = Math.max(state.sprintUntilTick,
                    now + 30L + level.getRandom().nextInt(30));
            state.idleTicks = 0;
        }
        boolean reselectWander = FakeSteveWanderPolicy.shouldReselectNow(
                state.pathGoal == null, state.pathFailureCount, now >= state.nextDecisionTick);
        if (reselectWander) {
            state.nextDecisionTick = now + 40L + level.getRandom().nextInt(80);
            state.pathGoal = wanderGoal(level, body, state);
            state.path.clear();
            state.pathFailureCount = 0;
        }
        if (state.pathGoal != null) {
            follow(level, body, state.pathGoal, state, 0.15D);
        } else {
            idleHold(level, body, state, now);
        }
    }

    /**
     * One-shot flee toward a standable point away from {@code awayFrom}.
     * Used by 怯懦; reuses Fake Steve pathing without hunt or disguise tells.
     */
    public static void tickFlee(ServerLevel level, ServerPlayer body, FakeSteveAgentState state,
            BlockPos awayFrom) {
        long now = level.getGameTime();
        if (state.lastTickAt == 0L) {
            state.modeStartedTick = now;
        }
        int elapsed = state.lastTickAt == 0L ? 5
                : (int) Math.max(1L, Math.min(20L, now - state.lastTickAt));
        state.lastTickAt = now;
        state.tickStep = elapsed;
        state.mode = AgentMode.DISGUISE_IDLE;
        updateStuck(level, body, state, now);
        FakeSteveMotionController.applyServerMotion(body, state);
        state.sprintUntilTick = Math.max(state.sprintUntilTick, now + 20L);
        if (state.pathGoal == null || now >= state.nextDecisionTick) {
            state.nextDecisionTick = now + 30L;
            BlockPos goal = fleeGoal(level, body, state, awayFrom);
            if (goal != null) {
                state.pathGoal = goal.immutable();
                state.path.clear();
                state.pathFailureCount = 0;
            }
        }
        if (state.pathGoal != null) {
            follow(level, body, state.pathGoal, state, 0.28D);
        } else {
            idleHold(level, body, state, now);
        }
    }

    public static boolean hasReachedFleeGoal(ServerPlayer body, FakeSteveAgentState state) {
        return body != null && state != null && state.pathGoal != null
                && body.blockPosition().closerThan(state.pathGoal, 2.0D);
    }

    private static BlockPos fleeGoal(ServerLevel level, ServerPlayer body, FakeSteveAgentState state,
            BlockPos awayFrom) {
        Vec3 origin = body.position();
        Vec3 away = origin.subtract(Vec3.atCenterOf(awayFrom == null ? body.blockPosition() : awayFrom));
        if (away.horizontalDistanceSqr() < 0.01D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        away = new Vec3(away.x, 0.0D, away.z).normalize();
        for (int dist : new int[] { 28, 20, 14, 8 }) {
            for (int attempt = 0; attempt < 8; attempt++) {
                double yaw = (level.getRandom().nextDouble() - 0.5D) * 1.2D;
                double cos = Math.cos(yaw);
                double sin = Math.sin(yaw);
                Vec3 dir = new Vec3(away.x * cos - away.z * sin, 0.0D, away.x * sin + away.z * cos);
                BlockPos candidate = BlockPos.containing(origin.add(dir.scale(dist)));
                if (FakeSteveNavigator.safeStand(level, candidate)) {
                    state.lastWanderGoal = candidate.immutable();
                    return state.lastWanderGoal;
                }
            }
        }
        return wanderGoal(level, body, state);
    }

    /** Periodic gaze sweep so a waiting body never looks like a frozen statue. */
    private static void idleHold(ServerLevel level, ServerPlayer body,
            FakeSteveAgentState state, long now) {
        float anchor = state.hasStableRouteYaw ? state.stableRouteYaw : body.getYRot();
        FakeSteveMotionController.hold(body, state,
                FakeSteveMotionPolicy.idleScanYaw(now, body.getUUID().hashCode(), anchor),
                FakeSteveMotionPolicy.walkingPitch(now, body.getUUID().hashCode()));
    }

    /** Drops whatever the body was doing so the next tick starts a fresh decision. */
    private static void abandonBehaviour(ServerLevel level, ServerPlayer body,
            FakeSteveAgentState state, long now) {
        cancelKnifeCharge(body, state);
        cancelGrenadeCharge(body, state);
        clearFocus(state);
        state.brain.disengage();
        state.mode = AgentMode.DISGUISE_IDLE;
        state.modeStartedTick = now;
        state.nextDecisionTick = now;
        state.nextPathTick = now;
        state.pathRetryAfterTick = 0L;
        state.pathGoal = null;
        state.path.clear();
        state.hasStableRouteYaw = false;
        state.crowdedTicks = 0;
        state.crowdStrafe = 0.0F;
        state.rejectedMotionPackets = 0;
        state.stuckTicks = 0;
        state.lastPathDistanceSqr = Double.MAX_VALUE;
        state.lastPathProgressTick = now;
        state.pathFailureCount = 0;
        if (state.taskType != null) {
            FakeSteveTaskPlanner.abandon(body, state);
        }
    }

    /** A body that wants to move but does not is recalculated instead of grinding. */
    private static void updateStuck(ServerLevel level, ServerPlayer body,
            FakeSteveAgentState state, long now) {
        // Stationary disguise work (sleeping, meditating) is not being stuck.
        boolean wantsMovement = state.mode != AgentMode.STARE && state.mode != AgentMode.RECOVER
                && state.pathGoal != null
                && body.distanceToSqr(Vec3.atBottomCenterOf(state.pathGoal)) > 2.25D;
        if (!wantsMovement) {
            state.stuckTicks = 0;
            state.lastMoveX = body.getX();
            state.lastMoveY = body.getY();
            state.lastMoveZ = body.getZ();
            state.lastMoveTick = 0L;
            return;
        }
        if (state.lastMoveTick == 0L) {
            state.lastMoveX = body.getX();
            state.lastMoveY = body.getY();
            state.lastMoveZ = body.getZ();
            state.lastMoveTick = now;
            return;
        }
        long sampleGap = now - state.lastMoveTick;
        if (sampleGap < 8L) {
            return;
        }
        double dx = body.getX() - state.lastMoveX;
        double dy = body.getY() - state.lastMoveY;
        double dz = body.getZ() - state.lastMoveZ;
        state.lastMoveX = body.getX();
        state.lastMoveY = body.getY();
        state.lastMoveZ = body.getZ();
        state.lastMoveTick = now;
        boolean climbing = FakeStevePathPolicy.countsAsClimbing(
                Math.abs(dy) >= 0.08D, body.isInWater() || body.isUnderWater());
        if (FakeStevePathPolicy.isStuck(dx * dx + dz * dz, sampleGap, climbing)) {
            state.stuckTicks++;
        } else {
            state.stuckTicks = 0;
        }
        if (FakeStevePathPolicy.needsRecalculation(state.stuckTicks)) {
            recalculateRoute(level, body, state, now);
        }
    }

    private static void recalculateRoute(ServerLevel level, ServerPlayer body,
            FakeSteveAgentState state, long now) {
        state.stuckTicks = 0;
        state.path.clear();
        state.hasStableRouteYaw = false;
        state.crowdedTicks = 0;
        state.crowdStrafe = 0.0F;
        state.lastPathDistanceSqr = Double.MAX_VALUE;
        state.lastPathProgressTick = now;
        state.sprintUntilTick = Math.max(state.sprintUntilTick, now + 20L);
        state.pathFailureCount++;
        if (FakeStevePathPolicy.shouldAbandonIdleGoal(state.pathFailureCount)
                && (state.mode == AgentMode.DISGUISE_IDLE || state.mode == AgentMode.HUNT)) {
            state.pathGoal = null;
            state.pathRetryAfterTick = 0L;
            state.nextPathTick = now;
            state.nextDecisionTick = now;
            return;
        }
        state.pathRetryAfterTick = now + 10L;
        state.nextPathTick = now + 10L;
        BlockPos goal = state.pathGoal;
        if (goal != null && state.mode == AgentMode.DISGUISE_TASK) {
            BlockPos nudged = goal.offset(level.getRandom().nextInt(5) - 2, 0,
                    level.getRandom().nextInt(5) - 2);
            if (FakeSteveNavigator.safeStand(level, nudged)) {
                state.pathGoal = nudged.immutable();
            }
        }
        if (state.pathFailureCount >= 6 && state.taskType != null) {
            FakeSteveTaskPlanner.abandon(body, state);
            state.mode = AgentMode.DISGUISE_IDLE;
            state.nextDecisionTick = now;
        }
    }

    private static boolean isEngageable(ServerPlayer player) {
        return player != null && FakeStevePathPolicy.canTrackPlayer(player.isAlive(),
                player.isSpectator(), player.isCreative(), GameUtils.isPlayerAliveAndSurvival(player));
    }

    /** Spectators and creative-mode players are never a valid focus, prey or witness. */
    private static ServerPlayer engageable(ServerLevel level, UUID id) {
        ServerPlayer player = player(level, id);
        if (!isEngageable(player) || player.serverLevel() != level) {
            return null;
        }
        return player;
    }

    /** Berserk bodies never pay for witness or risk evaluation. */
    private static boolean witnessed(ServerLevel level, ServerPlayer body,
            ServerPlayer target, boolean berserkActive) {
        if (FakeSteveKillerPolicy.ignoresRisk(berserkActive)) {
            return false;
        }
        return hasWitness(level, body, target);
    }

    private static boolean exposedToHumans(ServerLevel level, ServerPlayer body) {
        return level.players().stream().filter(FakeSteveAi::isHuman)
                .filter(FakeSteveAi::isEngageable).filter(p -> p.distanceToSqr(body) <= 400.0D)
                .anyMatch(p -> visible(p, body) || visible(body, p));
    }

    private static boolean isWeapon(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(TMMItemTags.GUNS) || stack.getItem() instanceof TrainWeapon);
    }

    /** Weapons are only ever drawn for a strike; a seen weapon breaks the disguise. */
    private static void concealWeaponIfExposed(ServerLevel level, ServerPlayer body,
            FakeSteveAgentState state, boolean psychoActive) {
        if (FakeSteveKillerPolicy.ignoresRisk(psychoActive)) {
            return;
        }
        if (!FakeSteveKillerPolicy.shouldConcealWeapon(isWeapon(body.getMainHandItem()),
                exposedToHumans(level, body), state.knifeChargeTarget != null)) {
            return;
        }
        int safe = findSafeHolsterSlot(body);
        if (safe < 0) {
            safe = firstEmptyHotbarSlot(body);
        }
        if (safe >= 0 && safe != body.getInventory().selected) {
            select(body, safe);
        }
    }

    static void onLoudVoice(ServerPlayer speaker) {
        if (!FakeSteveDirector.isActive(speaker.serverLevel()) || !isHuman(speaker))
            return;
        ServerPlayer fake = nearestFacingFake(speaker.serverLevel(), speaker, 8.0);
        if (fake != null) {
            FakeSteveAgentState state = FakeSteveDirector.agent(fake.serverLevel(), fake.getUUID());
            if (state != null)
                beginStare(state, speaker);
        }
    }

    private static void onChat(ServerPlayer sender, String message) {
        if (!FakeSteveDirector.isActive(sender.serverLevel()) || !isHuman(sender))
            return;
        ServerPlayer nearest = sender.serverLevel().players().stream()
                .filter(FakeSteveDirector::isReplaced).filter(FakeSteveAi::isEngageable)
                .filter(fake -> fake.distanceToSqr(sender) <= 64.0)
                .filter(fake -> sender.hasLineOfSight(fake) && faces(sender, fake, FACE_COS))
                .min(Comparator.comparingDouble(sender::distanceToSqr)).orElse(null);
        if (nearest != null) {
            FakeSteveAgentState state = FakeSteveDirector.agent(nearest.serverLevel(), nearest.getUUID());
            if (state != null) {
                beginStare(state, sender);
                if (FakeSteveDialogue.isDirectedRoleQuestion(message)) {
                    state.directedReplyPending = true;
                    state.nextDialogueTick = sender.serverLevel().getGameTime()
                            + 12L + sender.getRandom().nextInt(25);
                }
            }
        }
    }

    private static ServerPlayer facingHuman(ServerLevel level, ServerPlayer fake) {
        return level.players().stream().filter(FakeSteveAi::isHuman)
                .filter(FakeSteveAi::isEngageable).filter(p -> p.distanceToSqr(fake) <= 64.0)
                .filter(p -> visible(fake, p) && faces(fake, p, FACE_COS) && faces(p, fake, FACE_COS))
                .min(Comparator.comparingDouble(fake::distanceToSqr)).orElse(null);
    }

    private static ServerPlayer nearestFacingFake(ServerLevel level, ServerPlayer human, double range) {
        return level.players().stream().filter(FakeSteveDirector::isReplaced)
                .filter(FakeSteveAi::isEngageable).filter(p -> p.distanceToSqr(human) <= range * range)
                .filter(p -> visible(p, human) && faces(p, human, FACE_COS) && faces(human, p, FACE_COS))
                .min(Comparator.comparingDouble(human::distanceToSqr)).orElse(null);
    }

    private static ServerPlayer isolatedTarget(ServerLevel level, ServerPlayer body) {
        ServerPlayer nearest = level.players().stream().filter(FakeSteveAi::isHuman)
                .filter(FakeSteveAi::isEngageable).filter(p -> p.distanceToSqr(body) <= 144.0)
                .filter(p -> livingFakesNear(level, p, 12.0) >= 2)
                .filter(p -> otherLivingHumansNear(level, p, 12.0) == 0)
                .min(Comparator.comparingDouble(body::distanceToSqr)).orElse(null);
        if (nearest == null)
            return null;
        ServerPlayer closestFake = level.players().stream().filter(FakeSteveDirector::isReplaced)
                .filter(FakeSteveAi::isEngageable).filter(p -> p.distanceToSqr(nearest) <= 144.0)
                .min(Comparator.comparingDouble(nearest::distanceToSqr)).orElse(null);
        return closestFake == body ? nearest : null;
    }

    private static int livingFakesNear(ServerLevel level, ServerPlayer target, double range) {
        return (int) level.players().stream().filter(FakeSteveDirector::isReplaced)
                .filter(FakeSteveAi::isEngageable)
                .filter(p -> p.distanceToSqr(target) <= range * range).count();
    }

    private static int otherLivingHumansNear(ServerLevel level, ServerPlayer target, double range) {
        return (int) level.players().stream().filter(p -> p != target).filter(FakeSteveAi::isHuman)
                .filter(FakeSteveAi::isEngageable)
                .filter(p -> p.distanceToSqr(target) <= range * range).count();
    }

    private static ServerPlayer nearestPrey(ServerLevel level, ServerPlayer body, double rangeSqr) {
        return level.players().stream().filter(FakeSteveAi::isHuman)
                .filter(FakeSteveAi::isEngageable)
                .filter(p -> p.distanceToSqr(body) <= rangeSqr)
                .filter(p -> isPrey(level, p))
                .min(Comparator.comparingDouble(body::distanceToSqr)).orElse(null);
    }

    private static boolean isPrey(ServerLevel level, ServerPlayer player) {
        if (FakeSteveDirector.isHuntPhase(level)) {
            return isHuman(player) && isEngageable(player);
        }
        return FakeSteveKillerPolicy.canActivelyHunt(FakeSteveDirector.isReplaced(player),
                isKillerRole(level, player), isKillerNeutral(level, player));
    }

    /**
     * Picks the strike target with commitment and hysteresis. Without this a body
     * standing between two humans re-targets every tick and spins on the spot.
     */
    private static ServerPlayer choosePrey(ServerLevel level, ServerPlayer body,
            FakeSteveAgentState state, long now, boolean derringerBerserk,
            boolean armed, boolean berserkActive) {
        double huntRadiusSqr = FakeSteveKillerPolicy.seekRadiusSqr(berserkActive, derringerBerserk);
        double strikeRadiusSqr = FakeSteveKillerPolicy.STRIKE_RADIUS_SQR;
        ServerPlayer committed = engageable(level, state.committedTarget);
        if (berserkActive) {
            ServerPlayer nearest = nearestPrey(level, body, huntRadiusSqr);
            if (committed != null && isPrey(level, committed)
                    && (nearest == null
                    || body.distanceToSqr(committed) <= body.distanceToSqr(nearest) * 2.25D)) {
                return committed;
            }
            if (nearest != null) {
                commitPrey(state, nearest, now);
                return nearest;
            }
            state.committedTarget = null;
            state.committedUntilTick = 0L;
            return null;
        }
        if (committed != null && isPrey(level, committed)) {
            double committedDistance = body.distanceToSqr(committed);
            if (committedDistance <= huntRadiusSqr * 2.25D) {
                if (now < state.committedUntilTick
                        && (berserkActive || isOpportunity(level, body, committed, armed))) {
                    return committed;
                }
                ServerPlayer rival = nearestOpportunity(level, body, huntRadiusSqr, armed);
                if (rival == null || body.distanceToSqr(rival) > committedDistance * 0.36D) {
                    if (berserkActive || isOpportunity(level, body, committed, armed)
                            || committedDistance <= strikeRadiusSqr) {
                        return committed;
                    }
                }
            }
        }
        ServerPlayer focus = engageable(level, state.focusTarget);
        if (focus != null && isPrey(level, focus)
                && body.distanceToSqr(focus) <= huntRadiusSqr
                && (berserkActive || isOpportunity(level, body, focus, armed)
                        || body.distanceToSqr(focus) <= strikeRadiusSqr)) {
            commitPrey(state, focus, now);
            return focus;
        }
        ServerPlayer opportunity = nearestOpportunity(level, body, huntRadiusSqr, armed);
        if (opportunity != null) {
            commitPrey(state, opportunity, now);
            return opportunity;
        }
        ServerPlayer nearest = nearestPrey(level, body, strikeRadiusSqr);
        if (nearest == null) {
            state.committedTarget = null;
            state.committedUntilTick = 0L;
            return null;
        }
        commitPrey(state, nearest, now);
        return nearest;
    }

    private static boolean isOpportunity(ServerLevel level, ServerPlayer body,
            ServerPlayer target, boolean armed) {
        return FakeSteveKillerPolicy.isKillOpportunity(
                witnessed(level, body, target, false) ? 1 : 0,
                otherLivingHumansNear(level, target, 12.0) + 1, armed);
    }

    private static ServerPlayer nearestOpportunity(ServerLevel level, ServerPlayer body,
            double rangeSqr, boolean armed) {
        return level.players().stream().filter(FakeSteveAi::isHuman)
                .filter(FakeSteveAi::isEngageable)
                .filter(p -> p.distanceToSqr(body) <= rangeSqr)
                .filter(p -> isPrey(level, p))
                .filter(p -> isOpportunity(level, body, p, armed))
                .min(Comparator.comparingDouble(body::distanceToSqr)).orElse(null);
    }

    private static void commitPrey(FakeSteveAgentState state, ServerPlayer prey, long now) {
        state.committedTarget = prey.getUUID();
        state.committedUntilTick = now + PREY_COMMIT_TICKS;
    }

    /** Wander targets must be real floor; an unvalidated one walks into the void. */
    private static BlockPos wanderGoal(ServerLevel level, ServerPlayer body, FakeSteveAgentState state) {
        BlockPos origin = body.blockPosition();
        BlockPos previous = state.lastWanderGoal;
        BlockPos avoidedPlate = state.lastSnackPlate;
        BlockPos taskPoint = wanderTaskPoint(level, body, previous, avoidedPlate);
        if (taskPoint != null) {
            state.lastWanderGoal = taskPoint.immutable();
            return state.lastWanderGoal;
        }
        BlockPos social = wanderNearPlayer(level, body, previous);
        if (social != null) {
            state.lastWanderGoal = social.immutable();
            return state.lastWanderGoal;
        }
        for (int range : new int[] { 24, 16, 8 }) {
            for (int attempt = 0; attempt < 6; attempt++) {
                int dx = level.getRandom().nextInt(range * 2 + 1) - range;
                int dz = level.getRandom().nextInt(range * 2 + 1) - range;
                BlockPos candidate = origin.offset(dx, 0, dz);
                if (previous != null && candidate.closerThan(previous,
                        FakeSteveWanderPolicy.AVOID_LAST_DISTANCE)) {
                    continue;
                }
                if (FakeSteveNavigator.safeStand(level, candidate)) {
                    state.lastWanderGoal = candidate.immutable();
                    return state.lastWanderGoal;
                }
            }
        }
        for (int attempt = 0; attempt < 12; attempt++) {
            BlockPos candidate = origin.offset(level.getRandom().nextInt(17) - 8, 0,
                    level.getRandom().nextInt(17) - 8);
            if (previous != null && candidate.closerThan(previous, 2.0D)) {
                continue;
            }
            if (FakeSteveNavigator.safeStand(level, candidate)) {
                state.lastWanderGoal = candidate.immutable();
                return state.lastWanderGoal;
            }
        }
        return null;
    }

    private static BlockPos wanderTaskPoint(ServerLevel level, ServerPlayer body,
            BlockPos previous, BlockPos avoidedPlate) {
        if (GameUtils.taskBlocks == null || GameUtils.taskBlocks.isEmpty()) {
            return null;
        }
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : GameUtils.taskBlocks.keySet()) {
            BlockPos stand = standableNear(level, pos);
            if (stand == null) {
                continue;
            }
            double distanceSqr = body.distanceToSqr(Vec3.atCenterOf(stand));
            boolean nearLast = previous != null && stand.closerThan(previous,
                    FakeSteveWanderPolicy.AVOID_LAST_DISTANCE);
            boolean recentPlate = avoidedPlate != null && stand.closerThan(avoidedPlate,
                    FakeSteveWanderPolicy.AVOID_PLATE_DISTANCE);
            if (FakeSteveWanderPolicy.isUsableTaskPoint(distanceSqr, nearLast, recentPlate)) {
                candidates.add(stand.immutable());
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(Comparator.comparingDouble((BlockPos pos) ->
                body.distanceToSqr(Vec3.atCenterOf(pos))).reversed());
        int window = Math.max(1, candidates.size() / 2);
        return candidates.get(level.getRandom().nextInt(window));
    }

    private static BlockPos wanderNearPlayer(ServerLevel level, ServerPlayer body, BlockPos previous) {
        List<BlockPos> candidates = new ArrayList<>();
        for (ServerPlayer other : level.players()) {
            if (other == body || !isEngageable(other)) {
                continue;
            }
            for (int attempt = 0; attempt < 6; attempt++) {
                double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
                double radius = 6.0D + level.getRandom().nextDouble() * 4.0D;
                BlockPos candidate = BlockPos.containing(
                        other.getX() + Math.cos(angle) * radius,
                        other.getY(),
                        other.getZ() + Math.sin(angle) * radius);
                if (!FakeSteveNavigator.safeStand(level, candidate)) {
                    continue;
                }
                if (previous != null && candidate.closerThan(previous,
                        FakeSteveWanderPolicy.AVOID_LAST_DISTANCE)) {
                    continue;
                }
                if (FakeSteveWanderPolicy.isSocialStand(other.distanceToSqr(
                        Vec3.atBottomCenterOf(candidate)))) {
                    candidates.add(candidate.immutable());
                }
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(level.getRandom().nextInt(candidates.size()));
    }

    private static BlockPos standableNear(ServerLevel level, BlockPos pos) {
        if (FakeSteveNavigator.safeStand(level, pos)) {
            return pos.immutable();
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(direction);
            if (FakeSteveNavigator.safeStand(level, neighbor)) {
                return neighbor.immutable();
            }
        }
        return null;
    }

    /**
     * Opportunistic attack resolution. Returns {@link #STRIKE_BUSY} while the body
     * is aiming or charging so the caller does not overwrite the aim with a route.
     */
    private static int tryArmedAttack(ServerLevel level, ServerPlayer body,
            ServerPlayer target, FakeSteveAgentState state, boolean psychoActive,
            boolean derringerBerserk) {
        long now = level.getGameTime();
        SRERole role = SREGameWorldComponent.KEY.get(level).getRole(body);
        boolean berserkActive = FakeSteveKillerPolicy.isBerserk(psychoActive, derringerBerserk);
        boolean unseen = !witnessed(level, body, target, berserkActive);

        // The Derringer is the trigger and the first choice. It is intentionally
        // attempted before psycho/knife logic, so carrying one visibly breaks cover.
        int derringer = findUsableDerringerSlot(body);
        if (derringer >= 0) {
            int derringerStrike = tryGunAttack(level, body, target, state, derringer, unseen);
            if (derringerStrike != STRIKE_NONE) {
                return derringerStrike;
            }
        }
        if (psychoActive && !derringerBerserk) {
            // A frenzied body only swings the bat: no knife, no revolver, no gadgets.
            cancelKnifeCharge(body, state);
            int psychoWeapon = findPsychoWeaponSlot(body, role);
            if (psychoWeapon >= 0
                    && body.distanceToSqr(target) <= FakeSteveKillerPolicy.MELEE_RANGE_SQR
                    && visible(body, target)
                    && !body.getCooldowns().isOnCooldown(
                            body.getInventory().getItem(psychoWeapon).getItem())) {
                select(body, psychoWeapon);
                return killWithPsycho(body, target) ? STRIKE_KILLED : STRIKE_NONE;
            }
            return STRIKE_NONE;
        }
        int knife = findUsableKnifeSlot(body);
        if (knife >= 0) {
            if (FakeSteveKillerPolicy.knifeChargeExpired(now, state.knifeChargeStartedTick)
                    || state.knifeChargeTarget != null
                            && !target.getUUID().equals(state.knifeChargeTarget)) {
                cancelKnifeCharge(body, state);
            }
            if (body.distanceToSqr(target) <= FakeSteveKillerPolicy.MELEE_RANGE_SQR
                    && behind(body, target) && unseen) {
                if (state.knifeChargeTarget == null) {
                    state.knifeChargeTarget = target.getUUID();
                    state.knifeChargedAtTick = now + 8L;
                    state.knifeChargeStartedTick = now;
                    state.holsterSlot = body.getInventory().selected == knife
                            ? findSafeHolsterSlot(body) : body.getInventory().selected;
                    select(body, knife);
                    body.gameMode.useItem(body, level, body.getMainHandItem(),
                            InteractionHand.MAIN_HAND);
                    if (!body.isUsingItem()) {
                        body.startUsingItem(InteractionHand.MAIN_HAND);
                    }
                    lookAt(body, state, target.getEyePosition());
                    return STRIKE_BUSY;
                }
                if (!FakeSteveKillerPolicy.canStrikeWithKnife(now, state.knifeChargedAtTick)) {
                    lookAt(body, state, target.getEyePosition());
                    return STRIKE_BUSY;
                }
                select(body, knife);
                if (body.getCooldowns().isOnCooldown(body.getMainHandItem().getItem())) {
                    cancelKnifeCharge(body, state);
                    return STRIKE_NONE;
                }
                body.releaseUsingItem();
                cancelKnifeCharge(body, state);
                boolean killed = kill(body, target, false, true);
                if (killed) {
                    state.holsterAtTick = now + 8L;
                }
                return killed ? STRIKE_KILLED : STRIKE_NONE;
            }
        }
        cancelKnifeCharge(body, state);
        double distance = body.distanceTo(target);
        int gun = findUsableGunSlot(body);
        if (gun >= 0) {
            int gunStrike = tryGunAttack(level, body, target, state, gun, unseen);
            if (gunStrike != STRIKE_NONE) {
                return gunStrike;
            }
        }
        return tryThrowGrenade(level, body, target, state, unseen, distance, now);
    }

    /** Aim through the normal turn controller before releasing a server-authoritative shot. */
    private static int tryGunAttack(ServerLevel level, ServerPlayer body,
            ServerPlayer target, FakeSteveAgentState state, int slot, boolean unseen) {
        double distance = body.distanceTo(target);
        if (!FakeSteveKillerPolicy.canFireGun(distance, visible(body, target), unseen)) {
            return STRIKE_NONE;
        }
        select(body, slot);
        if (!faces(body, target, FakeSteveKillerPolicy.GUN_AIM_COSINE)) {
            lookAt(body, state, target.getEyePosition());
            return STRIKE_BUSY;
        }
        if (body.getCooldowns().isOnCooldown(body.getMainHandItem().getItem())) {
            int safe = findSafeHolsterSlot(body);
            if (safe >= 0) {
                select(body, safe);
            }
            return STRIKE_NONE;
        }
        return kill(body, target, true, true) ? STRIKE_KILLED : STRIKE_NONE;
    }

    /** A ranged area attack: wind up the grenade for a beat, then lob it. */
    private static int tryThrowGrenade(ServerLevel level, ServerPlayer body,
            ServerPlayer target, FakeSteveAgentState state, boolean unseen,
            double distance, long now) {
        if (!unseen || distance < GRENADE_MIN_RANGE || distance > GRENADE_MAX_RANGE
                || !visible(body, target)) {
            cancelGrenadeCharge(body, state);
            return STRIKE_NONE;
        }
        int grenade = findGrenadeSlot(body);
        if (grenade < 0) {
            cancelGrenadeCharge(body, state);
            return STRIKE_NONE;
        }
        if (state.grenadeChargeTarget != null
                && !target.getUUID().equals(state.grenadeChargeTarget)) {
            cancelGrenadeCharge(body, state);
            return STRIKE_NONE;
        }
        lookAt(body, state, target.getEyePosition());
        if (state.grenadeChargeTarget == null) {
            state.grenadeChargeTarget = target.getUUID();
            state.grenadeChargedAtTick = now + GRENADE_CHARGE_TICKS;
            select(body, grenade);
            body.startUsingItem(InteractionHand.MAIN_HAND);
            return STRIKE_BUSY;
        }
        if (now < state.grenadeChargedAtTick) {
            return STRIKE_BUSY;
        }
        body.releaseUsingItem();
        cancelGrenadeCharge(body, state);
        return STRIKE_NONE;
    }

    private static int findGrenadeSlot(ServerPlayer player) {
        if (GrenadeItem.isAnyGrenadeOnCooldown(player)) {
            return -1;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).is(TMMItems.GRENADE)) {
                return slot;
            }
        }
        return -1;
    }

    private static void cancelGrenadeCharge(ServerPlayer body, FakeSteveAgentState state) {
        if (state.grenadeChargeTarget != null && body.isUsingItem()) {
            body.releaseUsingItem();
        }
        state.grenadeChargeTarget = null;
        state.grenadeChargedAtTick = 0L;
    }

    private static boolean kill(ServerPlayer attacker, ServerPlayer target, boolean gun,
            boolean requireOriginalRolePermission) {
        SRERole role = SREGameWorldComponent.KEY.get(attacker.level()).getRole(attacker);
        if (requireOriginalRolePermission && !FakeSteveKillerPolicy.canActivelyHunt(
                FakeSteveDirector.isReplaced(target), isKillerRole(attacker.serverLevel(), target),
                isKillerNeutral(attacker.serverLevel(), target))) {
            return false;
        }
        boolean derringer = gun && org.agmas.noellesroles.content.item.DesperadoGunItem
                .isDerringerWeapon(attacker.getMainHandItem());
        if (requireOriginalRolePermission && role != null
                && !(gun ? (derringer ? role.onUseDerringer(attacker) : role.onUseGun(attacker))
                        && role.onGunHit(attacker, target)
                : role.onUseKnife(attacker) && role.onUseKnifeHit(attacker, target)))
            return false;
        if (gun) {
            ItemStack firedGun = attacker.getMainHandItem();
            attacker.level().playSound(null, attacker.blockPosition(), TMMSounds.ITEM_REVOLVER_SHOOT,
                    SoundSource.PLAYERS, 5.0f, 0.6f);
            attacker.getCooldowns().addCooldown(attacker.getMainHandItem().getItem(),
                    GameConstants.ITEM_COOLDOWNS.getOrDefault(attacker.getMainHandItem().getItem(),
                            GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.REVOLVER, 600)));
            if (derringer) {
                firedGun.set(SREDataComponentTypes.USED, true);
            }
            GameUtils.killPlayer(target, true, attacker, derringer
                    ? GameConstants.DeathReasons.DERRINGER : GameConstants.DeathReasons.REVOLVER);
            if (FakeSteveKillerPolicy.shouldDropKillerRevolver(
                    role != null && role.canUseKiller(), true, firedGun.is(TMMItems.REVOLVER))) {
                attacker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                var dropped = attacker.drop(TMMItems.REVOLVER.getDefaultInstance(), false, false);
                if (dropped != null) {
                    dropped.setPickUpDelay(10);
                    dropped.setThrower(attacker);
                }
            }
        } else {
            target.playSound(TMMSounds.ITEM_KNIFE_STAB, 1.0f, 1.0f);
            attacker.getCooldowns().addCooldown(TMMItems.KNIFE,
                    GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.KNIFE, 600));
            GameUtils.killPlayer(target, true, attacker, BACKSTAB);
            if (KillerKnifeDurability.isMarkedKnife(attacker.getMainHandItem())) {
                KillerKnifeDurability.consumeOne(attacker.getMainHandItem(), attacker);
            }
        }
        attacker.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    private static boolean tryInteract(ServerLevel level, ServerPlayer body,
            FakeSteveAgentState state, long now) {
        if (body.isUsingItem() && isConsumable(body.getMainHandItem())) {
            return true;
        }
        boolean onCooldown = now < state.nextSnackTick;
        boolean hungry = FakeSteveInteractionPolicy.isHungry(body.getFoodData().getFoodLevel());
        boolean maySnack = FakeSteveInteractionPolicy.shouldSnack(hungry, onCooldown, false);
        int foodSlot = findConsumableSlot(body);
        if (foodSlot >= 0 && maySnack) {
            select(body, foodSlot);
            body.gameMode.useItem(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND);
            if (!body.isUsingItem()) {
                body.startUsingItem(InteractionHand.MAIN_HAND);
            }
            state.nextSnackTick = now + FakeSteveInteractionPolicy.SNACK_COOLDOWN_TICKS;
            return true;
        }
        if (!maySnack) {
            return false;
        }
        BlockPos center = body.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -1, -4), center.offset(4, 2, 4))) {
            if (!(level.getBlockState(pos).getBlock() instanceof PlatterBlock)
                    || body.distanceToSqr(Vec3.atCenterOf(pos)) > 16.0)
                continue;
            boolean plateEmpty = level.getBlockEntity(pos) instanceof PlateTrayBlockEntity plate
                    && plate.getStoredItems().isEmpty();
            boolean alreadyUsed = state.lastSnackPlate != null
                    && pos.closerThan(state.lastSnackPlate, 1.5D);
            if (!FakeSteveInteractionPolicy.shouldTakeFromPlate(plateEmpty, onCooldown,
                    alreadyUsed, maySnack)) {
                continue;
            }
            int empty = firstEmptyHotbarSlot(body);
            if (empty < 0)
                return false;
            select(body, empty);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos.immutable(), false);
            body.gameMode.useItemOn(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
            body.swing(InteractionHand.MAIN_HAND, true);
            if (!isConsumable(body.getMainHandItem())) {
                continue;
            }
            state.lastSnackPlate = pos.immutable();
            body.gameMode.useItem(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND);
            if (!body.isUsingItem()) {
                body.startUsingItem(InteractionHand.MAIN_HAND);
            }
            state.nextSnackTick = now + FakeSteveInteractionPolicy.SNACK_COOLDOWN_TICKS;
            return true;
        }
        return false;
    }

    private static boolean isConsumable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        UseAnim animation = stack.getItem().getUseAnimation(stack);
        return stack.has(DataComponents.FOOD) || animation == UseAnim.EAT || animation == UseAnim.DRINK;
    }

    private static int findConsumableSlot(ServerPlayer body) {
        for (int slot = 0; slot < 9; slot++) {
            if (isConsumable(body.getInventory().getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    // ==================== 生前轨迹巡逻 ====================

    /** 轨迹巡逻：认为已抵达某个路点的距离（格）。 */
    private static final double TRAIL_REACH_DISTANCE = 1.2D;
    /**
     * 轨迹巡逻：单个路点尝试超过该 tick 数仍未抵达就跳过，避免在一个点上打转。
     *
     * <p>
     * 必须显著大于 {@link #TRAIL_STALL_TICKS}：跳过路点会重置进展统计，如果两者一样长，
     * 「撞墙 3 秒就折返去找可抵达记录点」这件事永远轮不到执行（跳过总是先发生）。
     * 于是它只剩兜底作用——「就在附近但怎么也踩不进 1.2 格」这类情况。
     */
    private static final long TRAIL_WAYPOINT_TIMEOUT_TICKS = 100L;
    /** 轨迹巡逻：目标路点远于该距离说明身体被传送过（如猎杀阶段拉回房间），重新吸附到最近的轨迹点。 */
    private static final double TRAIL_RESNAP_DISTANCE = 40.0D;
    /**
     * 轨迹巡逻：身体连续 3 秒（60 tick）没有实际走出 {@link #TRAIL_MIN_PROGRESS_DISTANCE} 格，
     * 就认定撞墙/被堵死，折返去最近一个「现在依然可抵达」的记录点。
     *
     * <p>必须用「身体实际位移」而不是「到路点的距离」——贴墙时的位置抖动会把后者反复刷成有进展。
     */
    private static final long TRAIL_STALL_TICKS = 60L;
    /** 轨迹巡逻：一次折返最多向外看多少个记录点（性能上限）。 */
    private static final int TRAIL_RESCAN_LIMIT = 32;
    /** 轨迹巡逻：两次「重新找可抵达记录点」之间的最小间隔，避免反复扫描。 */
    private static final long TRAIL_RESCAN_COOLDOWN_TICKS = 60L;
    /** 轨迹巡逻：两次改变方向（掉头 / 端点折返）之间的最小间隔，避免在两个方向之间来回抖。 */
    private static final long TRAIL_REVERSE_COOLDOWN_TICKS = 40L;
    /** 轨迹巡逻：身体至少实际走出这么多格才算「有进展」。 */
    private static final double TRAIL_MIN_PROGRESS_DISTANCE = 0.5D;
    /** 轨迹巡逻：正前方有障碍时的前进力度——轻轻试探一下即可，不硬磨墙。 */
    private static final float TRAIL_WALL_FORWARD = 0.3F;

    /**
     * 「生前轨迹」巡逻：作为移动辅助接管本次移动。
     *
     * <p>
     * 优先级高于伪装任务：{@link FakeSteveTaskPlanner#hasCompletableTask} 对几乎任何玩家都返回
     * true（开局就会派发任务），如果让任务先抢走移动权，AI 会一直用 A* 跑去任务点、表现为漫无目的
     * 地乱跑，永远也走不到轨迹上。
     *
     * <p>
     * 它只负责行走，并保留原本的伪装交互节奏；开门、追击、同化、任务交互等其它逻辑都不受影响
     * （不进入本方法时照旧执行，进入后也只是不走 A* 而已）。
     *
     * @return 是否已接管本次移动
     */
    private static boolean trailPatrol(ServerLevel level, ServerPlayer body,
            FakeSteveAgentState state, long now, boolean psychoActive) {
        if (!state.trailEnabled) {
            return false;
        }
        // 沿用原来的伪装交互节奏（偶尔从餐盘拿点吃的、吃点东西），只是不再重新挑徘徊目标。
        if (now >= state.nextDecisionTick) {
            state.nextDecisionTick = now + (psychoActive
                    ? 10L + level.getRandom().nextInt(15)
                    : 40L + level.getRandom().nextInt(80));
            if (!psychoActive && tryInteract(level, body, state, now)) {
                return true;
            }
        }
        // 走轨迹就不使用 A*：清掉任务/徘徊留下的目标与路径。
        state.pathGoal = null;
        state.path.clear();
        if (!driveTrail(level, body, state, psychoActive ? 0.22D : 0.15D)) {
            return false;
        }
        // 巡逻统一按「伪装空闲」记账，避免 DISGUISE_TASK 的 600 tick 预算反复重置状态。
        state.mode = AgentMode.DISGUISE_IDLE;
        return true;
    }

    /**
     * 沿被替换玩家「生前」的移动轨迹巡逻。这只是一层「移动辅助」。
     *
     * <p>
     * 记下来的这条路（例如 A→B→C）意味着「这段路玩家亲自走过，始终可行走」，所以伪人只需要
     * 沿点复现：从 C 返回 B、返回 A，再折返到 B、到 C，循环往复。<b>正常行走时不重新判断路可不可行、
     * 能不能走</b>——不跑 A*、不做绕人侧移、不做落点检查。
     *
     * <p>
     * 唯一的例外是「撞墙」：连续 {@link #TRAIL_STALL_TICKS}（3 秒）没能真的往前挪动，就折返到
     * 最近一个「现在依然可抵达」的记录点（见 {@link #resolveTrailStall}），而不是一直顶着障碍物。
     *
     * <p>
     * 它不会像 {@link #follow} 那样卡在原地转圈：A* 的「搜不到路」和侧移被移动租约整包拒绝
     * 这两档都不存在了；沿路线走时每一步都在靠近下一个路点，移动包必然通过。
     *
     * <p>
     * 其它行为一概不受影响：开门仍走 {@link #openDoorsOnApproach}（同一套规则，硬锁的门一样打不开），
     * 追击/潜行/同化/任务/伪装交互都在进入这里之前就已经决定好了，本方法只负责行走。
     *
     * @return 是否接管了本 tick 的移动（false = 调用方应回退到原来的徘徊逻辑）
     */
    static boolean driveTrail(ServerLevel level, ServerPlayer body,
            FakeSteveAgentState state, double speed) {
        if (!state.trailEnabled || state.trail.size() < 2) {
            return false;
        }
        long now = level.getGameTime();
        BlockPos target = advanceTrailTarget(level, body, state, now);
        if (target == null) {
            return false;
        }
        // 进入路点前先把门打开，避免身体贴着门原地跳。
        openDoorsOnApproach(level, body, target);

        Vec3 delta = Vec3.atBottomCenterOf(target).subtract(body.position());
        double horizontalSqr = delta.x * delta.x + delta.z * delta.z;
        if (horizontalSqr < 0.01D) {
            if (needsVerticalSwim(body, target.getY() + 0.1D)) {
                driveVerticalSwim(body, state, target, now, speed);
                return true;
            }
            // 已经站在这一格上：立刻推进到下一个路点，不做无意义的停留。
            state.trailRefTick = 0L;
            state.trailProgressTick = now;
            state.trailWaypointTick = now - TRAIL_WAYPOINT_TIMEOUT_TICKS;
            advanceTrailTarget(level, body, state, now);
            idleHold(level, body, state, now);
            return true;
        }

        Vec3 direction = new Vec3(delta.x, 0.0D, delta.z).normalize();
        // wallAhead 只看「鼻子前面那一格是不是实体」，只用来把前进力度放轻（别顶着方块空转），
        // 不参与任何路径可行性判断——这条路是玩家自己走出来的，视为始终可走。
        // 真正决定要不要折返的是下面这段「身体实际位移」判定：贴墙抖动、被别人堵住、
        // 门被锁上导致走不过去，都会在这里被抓到。
        boolean blocked = FakeSteveNavigator.wallAhead(level, body.position(),
                direction.scale(0.9D));
        if (state.trailRefTick == 0L) {
            state.trailRefX = body.getX();
            state.trailRefZ = body.getZ();
            state.trailRefTick = now;
            state.trailProgressTick = now;
        } else {
            double movedX = body.getX() - state.trailRefX;
            double movedZ = body.getZ() - state.trailRefZ;
            if (movedX * movedX + movedZ * movedZ
                    >= TRAIL_MIN_PROGRESS_DISTANCE * TRAIL_MIN_PROGRESS_DISTANCE) {
                state.trailRefX = body.getX();
                state.trailRefZ = body.getZ();
                state.trailProgressTick = now;
            }
        }
        // 撞墙判定：wallAhead 只看正前方、斜穿门口时会瞬时误判，所以真正决定折返的是
        // 「连续 3 秒没有真的往前走」（贴墙抖动、被别人堵住、门被锁上都算）。
        if (now - state.trailProgressTick >= TRAIL_STALL_TICKS) {
            int resumed = resolveTrailStall(level, state, now);
            if (resumed >= 0) {
                // 折返成功：直接面向要退回去的那个记录点，原地转身，下一 tick 就沿原路返回。
                Vec3 back = Vec3.atBottomCenterOf(state.trail.get(resumed)).subtract(body.position());
                float holdYaw = (float) (Mth.atan2(-back.x, back.z) * Mth.RAD_TO_DEG);
                state.stableRouteYaw = holdYaw;
                state.hasStableRouteYaw = true;
                // 本 tick 先停下转身，不硬顶障碍物。
                FakeSteveMotionController.hold(body, state, holdYaw,
                        FakeSteveMotionPolicy.walkingPitch(now, body.getUUID().hashCode(), true));
                return true;
            }
            // 冷却中、或这一段路上一个能站的记录点都没有：本 tick 按原方向继续走，
            // 交给路点超时和下一次重扫处理，不至于原地冻住。
        }
        float nodeYaw = (float) (Mth.atan2(-direction.x, direction.z) * Mth.RAD_TO_DEG);
        if (!state.hasStableRouteYaw) {
            state.stableRouteYaw = nodeYaw;
            state.hasStableRouteYaw = true;
        } else {
            state.stableRouteYaw = FakeSteveMotionPolicy.walkingHeading(state.stableRouteYaw, nodeYaw);
        }
        boolean stepAhead = FakeSteveNavigator.isStepBlock(level.getBlockState(target))
                || FakeSteveNavigator.isStepBlock(level.getBlockState(target.below()));
        boolean ascends = delta.y > 0.20D || stepAhead;
        boolean jump = wantsJumpOrSwim(level, body, state, target.getY() + 0.1D, ascends, now);
        boolean sprint = now < state.sprintUntilTick || speed >= 0.22D;
        // 正前方有障碍时只轻轻试探一下，不硬磨墙；真被挡住会在上面判定后掉头。
        float forward = blocked ? TRAIL_WALL_FORWARD : 1.0F;
        FakeSteveMotionController.drive(body, state, forward, 0.0F, jump, sprint, false,
                state.stableRouteYaw,
                FakeSteveMotionPolicy.walkingPitch(now, body.getUUID().hashCode(), true),
                target);
        return true;
    }

    /**
     * 撞墙/被堵满 3 秒后的处理：折返，并退到最近一个「现在依然可抵达」的记录点。
     *
     * <p>
     * 记录点是玩家生前亲自踩过的位置，所以它们本身就是「可用路径」上的点；这里只需要做一次很便宜的
     * 「现在还能不能站上去」判定（门被锁死、有人往地上放了方块、地板被打掉都会让它失效），
     * 不需要 A*，也不需要扫描整张地图。
     *
     * <p>
     * 搜索顺序：优先沿「来时的方向」往回找（那段路身体刚刚走过，最可能还是通的），
     * 其次才向前跨过被堵住的那一小段。每个方向最多看 {@link #TRAIL_RESCAN_LIMIT} 个点，且整次搜索只在
     * 「连续 {@link #TRAIL_STALL_TICKS} 没走动」这个低频时刻发生一次，常规每 tick 开销仍然为零。
     *
     * @return 新的目标记录点下标（同时把巡逻方向改成朝它走）；本次不能折返时返回 -1
     */
    private static int resolveTrailStall(ServerLevel level, FakeSteveAgentState state, long now) {
        int size = state.trail.size();
        if (size < 2 || now < state.trailRescanCooldownUntilTick) {
            return -1;
        }
        int from = Mth.clamp(state.trailIndex, 0, size - 1);
        int picked = -1;
        for (int step = 1; step <= TRAIL_RESCAN_LIMIT && picked < 0; step++) {
            int back = from - state.trailDirection * step;
            if (back >= 0 && back < size && trailPointUsable(level, state.trail.get(back))) {
                picked = back;
                break;
            }
            int forward = from + state.trailDirection * step;
            if (forward >= 0 && forward < size && trailPointUsable(level, state.trail.get(forward))) {
                picked = forward;
                break;
            }
        }
        if (picked < 0) {
            // 这一段路上一个能站的记录点都没有：先按原方向继续走，等冷却过去再试。
            state.trailRescanCooldownUntilTick = now + TRAIL_RESCAN_COOLDOWN_TICKS;
            return -1;
        }
        // 朝这个点走过去（它可能在身后，也可能在被堵住的那段前方），并重置进展统计。
        state.trailDirection = picked < from ? -1 : 1;
        state.trailIndex = picked;
        state.trailWaypointTick = now;
        state.trailReverseCooldownUntilTick = now + TRAIL_REVERSE_COOLDOWN_TICKS;
        state.trailRescanCooldownUntilTick = now + TRAIL_RESCAN_COOLDOWN_TICKS;
        state.trailRefTick = 0L;
        state.trailProgressTick = now;
        state.hasStableRouteYaw = false;
        return picked;
    }

    /**
     * 记录点现在是否还能站上去。
     *
     * <p>
     * 记录时它一定是可站的（见 {@link FakeSteveTrailRecorder}），运行时再判一次是因为中间可能变了：
     * 门被锁死、有人在地上放了方块、地板被打掉。判定只是几次方块读取，而且只在「跳点 / 撞墙重扫」
     * 这种低频时刻调用。
     */
    private static boolean trailPointUsable(ServerLevel level, BlockPos pos) {
        return FakeSteveNavigator.standable(level, pos);
    }

    /**
     * 当前路点已经被环境改掉、站不上去了：直接跳到同一方向上下一个「现在依然可抵达」的记录点。
     *
     * <p>
     * 正常前进到新路点时顺手做的一次判定（最多看 {@link #TRAIL_RESCAN_LIMIT} 个点，通常第一个就通过），
     * 所以「伪人总是尝试去可抵达的记录位置」这件事在常规巡逻里也是成立的，不用等撞墙。
     */
    private static void skipUnusableTrailPoints(ServerLevel level, FakeSteveAgentState state) {
        int size = state.trail.size();
        for (int step = 0; step <= TRAIL_RESCAN_LIMIT; step++) {
            int index = state.trailIndex + state.trailDirection * step;
            if (index < 0 || index >= size) {
                return;
            }
            if (trailPointUsable(level, state.trail.get(index))) {
                state.trailIndex = index;
                return;
            }
        }
    }

    // ==================== 卡死保底（最后一层） ====================

    /** 卡死保底：连续这么久（12 秒）位置都没变化超过 {@link #STUCK_RADIUS} 格，就强制传送回路径点。 */
    private static final long STUCK_TICKS = 240L;

    /** 卡死保底：判定「位置确实变化过」的半径（格）。 */
    private static final double STUCK_RADIUS = 2.0D;

    /**
     * 伪人行为的最后一层保底：连续 12 秒位置都没变化超过 2 格，就直接把它传送到一个记录路径点上。
     *
     * <p>
     * 前面的机制（撞墙折返、A* 重试、路点超时）本质都还是「让它自己走回来」，遇到真的走不出去的情况
     * （被锁在房间里、被挤进死角、卡在方块缝里）能做的只有原地打转。这一层不再指望它自己解决：
     * 直接落回玩家生前走过的某个地面点上，从那里继续巡逻。
     *
     * <p>
     * 少数「故意站着不动」的行为（盯着人看、同化、击杀后恢复）不参与计时，否则正常的表演动作会被判成
     * 卡死，凭空传送一下反而穿帮。
     *
     * <p>
     * 成本：每 tick 只有两次坐标相减；只有判定成立时才做一次有上界的路径点扫描。
     */
    static void rescueIfStuck(ServerLevel level, ServerPlayer body, FakeSteveAgentState state) {
        long now = level.getGameTime();
        if (state.mode == AgentMode.STARE || state.mode == AgentMode.ASSIMILATE
                || state.mode == AgentMode.RECOVER) {
            resetStuckAnchor(state, body, now);
            return;
        }
        if (state.stuckAnchorTick == 0L) {
            resetStuckAnchor(state, body, now);
            return;
        }
        double dx = body.getX() - state.stuckAnchorX;
        double dz = body.getZ() - state.stuckAnchorZ;
        if (dx * dx + dz * dz > STUCK_RADIUS * STUCK_RADIUS) {
            // 位置变化超过 2 格：重新锚定，12 秒重新计时。
            resetStuckAnchor(state, body, now);
            return;
        }
        if (now - state.stuckAnchorTick < STUCK_TICKS) {
            return;
        }
        // 到点了：先重新锚定再传送，无论成功与否都不会每 tick 重试。
        long stuckTicks = now - state.stuckAnchorTick;
        resetStuckAnchor(state, body, now);
        teleportToUsableTrailPoint(level, body, state, now, stuckTicks);
    }

    private static void resetStuckAnchor(FakeSteveAgentState state, ServerPlayer body, long now) {
        state.stuckAnchorX = body.getX();
        state.stuckAnchorZ = body.getZ();
        state.stuckAnchorTick = now;
    }

    /**
     * 把身体直接放回最近一个「现在依然可抵达」的记录路径点。
     *
     * <p>
     * 只挑离身体至少 {@link #STUCK_RADIUS} 格的点（原地传送没有意义，也会让保底计时立刻再次命中），
     * 并且要求它现在还能站住，所以落地后不会卡在方块里、也不会掉到地图外。
     */
    private static void teleportToUsableTrailPoint(ServerLevel level, ServerPlayer body,
            FakeSteveAgentState state, long now, long stuckTicks) {
        if (!state.trailEnabled || state.trail.size() < 2) {
            return;
        }
        if (state.trailDimension != null && !state.trailDimension.equals(level.dimension())) {
            return;
        }
        BlockPos origin = body.blockPosition();
        double minSqr = STUCK_RADIUS * STUCK_RADIUS;
        double bestSqr = Double.MAX_VALUE;
        int picked = -1;
        for (int i = 0; i < state.trail.size(); i++) {
            BlockPos pos = state.trail.get(i);
            double distanceSqr = pos.distSqr(origin);
            if (distanceSqr < minSqr || distanceSqr >= bestSqr) {
                continue;
            }
            if (trailPointUsable(level, pos)) {
                bestSqr = distanceSqr;
                picked = i;
            }
        }
        if (picked < 0) {
            return;
        }
        BlockPos target = state.trail.get(picked);
        body.stopRiding();
        body.stopSleeping();
        body.teleportTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D);
        // 传送后把移动 / 寻路 / 轨迹进度全部作废，否则会带着旧目标原地打转。
        FakeSteveMotionController.clear(body, state);
        state.trailIndex = picked;
        state.trailWaypointTick = now;
        state.trailReverseCooldownUntilTick = now + TRAIL_REVERSE_COOLDOWN_TICKS;
        state.trailRescanCooldownUntilTick = now + TRAIL_RESCAN_COOLDOWN_TICKS;
        state.trailRefTick = 0L;
        state.trailProgressTick = now;
        state.hasStableRouteYaw = false;
        state.pathGoal = null;
        state.path.clear();
        state.pathRetryAfterTick = 0L;
        SRE.LOGGER.info("[Fake Steve] " + body.getName().getString() + " stayed inside "
                + STUCK_RADIUS + " blocks for " + stuckTicks + " ticks (rescue), teleported to trail point "
                + picked + " " + target.toShortString());
    }

    /**
     * 取出当前要走的轨迹点，并在「抵达 / 超时」后推进下标（到端点折返，实现重复巡逻）。
     */
    private static BlockPos advanceTrailTarget(ServerLevel level, ServerPlayer body,
            FakeSteveAgentState state, long now) {
        int size = state.trail.size();
        if (size < 2) {
            return null;
        }
        if (state.trailIndex < 0 || state.trailIndex >= size) {
            state.trailIndex = Mth.clamp(state.trailIndex, 0, size - 1);
            state.trailWaypointTick = now;
        }
        BlockPos current = state.trail.get(state.trailIndex);
        // 身体被传送过（猎杀阶段拉回房间等）时重新吸附到最近的轨迹点，避免横穿整张图。
        if (current.distSqr(body.blockPosition()) > TRAIL_RESNAP_DISTANCE * TRAIL_RESNAP_DISTANCE) {
            int snapped = FakeSteveTrailRecorder.nearestIndex(state.trail, body.blockPosition());
            if (snapped != state.trailIndex) {
                state.trailIndex = snapped;
                state.trailWaypointTick = now;
                state.trailRefTick = 0L;
                state.trailProgressTick = now;
                state.hasStableRouteYaw = false;
                current = state.trail.get(state.trailIndex);
            }
        }
        if (state.trailWaypointTick == 0L) {
            state.trailWaypointTick = now;
        }
        boolean reached = body.blockPosition().closerThan(current, TRAIL_REACH_DISTANCE);
        boolean timedOut = now - state.trailWaypointTick > TRAIL_WAYPOINT_TIMEOUT_TICKS;
        if (!reached && !timedOut) {
            return current;
        }
        int nextIndex = state.trailIndex + state.trailDirection;
        if (nextIndex < 0 || nextIndex >= size) {
            // 走到路线端点要折返。折返同样受掉头冷却约束，否则会出现
            // 「撞墙掉头 → 正好落在端点 → 立刻又折回去」这种原地看着来回抖的情况；
            // 冷却还没到就停在端点等一会儿（也像自然的巡逻停顿）。
            if (now < state.trailReverseCooldownUntilTick) {
                return current;
            }
            state.trailDirection = -state.trailDirection;
            state.trailReverseCooldownUntilTick = now + TRAIL_REVERSE_COOLDOWN_TICKS;
            state.hasStableRouteYaw = false;
            state.trailLoops++;
            nextIndex = state.trailIndex + state.trailDirection;
        }
        state.trailIndex = Mth.clamp(nextIndex, 0, size - 1);
        state.trailWaypointTick = now;
        // 路点本身被环境改掉（门锁死、地上被放方块）后永远到不了：顺手跳到同方向下一个还能站的记录点。
        skipUnusableTrailPoints(level, state);
        // 目标换成新路点：进展统计同步重置，否则会被上一段路的位移误判为「卡住」。
        state.trailRefTick = 0L;
        state.trailProgressTick = now;
        return state.trail.get(state.trailIndex);
    }

    static void follow(ServerLevel level, ServerPlayer body, BlockPos goal,
            FakeSteveAgentState state, double speed) {
        long now = level.getGameTime();
        boolean changedGoal = state.pathGoal == null || !state.pathGoal.closerThan(goal, 3.0);
        if (changedGoal) {
            state.hasStableRouteYaw = false;
            state.crowdedTicks = 0;
            state.crowdStrafe = 0.0F;
            state.pathRetryAfterTick = 0L;
            state.lastPathDistanceSqr = Double.MAX_VALUE;
            state.lastPathProgressTick = now;
            state.pathFailureCount = 0;
        }
        if (!changedGoal && now < state.pathRetryAfterTick) {
            FakeSteveMotionController.hold(body, state, body.getYRot(),
                    FakeSteveMotionPolicy.walkingPitch(now, body.getUUID().hashCode(), true));
            return;
        }
        if (state.path.isEmpty() || changedGoal
                || now >= state.nextPathTick) {
            state.pathGoal = goal.immutable();
            state.path.clear();
            boolean explicitTarget = state.mode == AgentMode.STALK
                    || state.mode == AgentMode.HUNT
                    || state.mode == AgentMode.ASSIMILATE;
            state.path.addAll(FakeSteveNavigator.find(level, body, goal, explicitTarget));
            state.nextPathTick = now + 20L;
            state.lastPathDistanceSqr = Double.MAX_VALUE;
            state.lastPathProgressTick = now;
        }
        BlockPos next = state.path.peekFirst();
        if (next == null) {
            if (body.blockPosition().closerThan(goal, 1.0D)
                    && !needsVerticalSwim(body, goal.getY() + 0.1D)) {
                FakeSteveMotionController.hold(body, state, body.getYRot(),
                        FakeSteveMotionPolicy.walkingPitch(now, body.getUUID().hashCode(), true));
                return;
            }
            if (tryStepToward(level, body, goal, state, now, speed)) {
                return;
            }
            if (FakeStevePathPolicy.shouldAbandonIdleGoal(state.pathFailureCount + 1)) {
                backOffPath(level, body, state, now);
            } else {
                state.pathFailureCount++;
                state.nextPathTick = now + 8L;
                state.pathRetryAfterTick = now + 8L;
            }
            FakeSteveMotionController.hold(body, state, body.getYRot(),
                    FakeSteveMotionPolicy.walkingPitch(now, body.getUUID().hashCode(), true));
            return;
        }
        // Open the node while it is still in the route.  A closed door used to be
        // removed as "reached" first, which left the client jumping against it.
        boolean doorAhead = openDoorsOnApproach(level, body, next);
        if (body.blockPosition().closerThan(next, 1.0)) {
            state.path.removeFirst();
            next = state.path.peekFirst();
            if (next == null)
                return;
        }
        doorAhead |= openDoorsOnApproach(level, body, next);
        Vec3 delta = Vec3.atBottomCenterOf(next).subtract(body.position());
        if (delta.horizontalDistanceSqr() < 0.01) {
            if (needsVerticalSwim(body, next.getY() + 0.1D)) {
                driveVerticalSwim(body, state, next, now, speed);
            }
            return;
        }
        double distanceSqr = delta.lengthSqr();
        if (distanceSqr < state.lastPathDistanceSqr - 0.15D) {
            state.lastPathDistanceSqr = distanceSqr;
            state.lastPathProgressTick = now;
            state.pathFailureCount = 0;
        } else if (FakeStevePathPolicy.hasStalled(state.lastPathDistanceSqr, distanceSqr,
                state.lastPathProgressTick, now)) {
            state.path.clear();
            state.nextPathTick = now;
            state.pathFailureCount++;
            if (FakeStevePathPolicy.shouldAbandonIdleGoal(state.pathFailureCount)) {
                backOffPath(level, body, state, now);
            }
            FakeSteveMotionController.hold(body, state, body.getYRot(),
                    FakeSteveMotionPolicy.walkingPitch(now, body.getUUID().hashCode(), true));
            return;
        }
        Vec3 direction = new Vec3(delta.x, 0.0, delta.z).normalize();
        boolean pursuingHuman = state.mode == AgentMode.STALK || state.mode == AgentMode.HUNT;
        boolean psychoActive = SREPlayerPsychoComponent.KEY.get(body).inPsycho();
        List<FakeSteveCrowdAvoidance.NearbyPlayer> nearbyPlayers = psychoActive ? List.of()
                : level.players().stream()
                .filter(player -> player != body && player.isAlive() && !player.isSpectator())
                .filter(player -> !isPursuitTarget(state, player.getUUID(), pursuingHuman))
                .filter(player -> player.distanceToSqr(body) <= 16.0D)
                .map(player -> new FakeSteveCrowdAvoidance.NearbyPlayer(player.getX(), player.getZ()))
                .toList();
        FakeSteveCrowdAvoidance.Decision avoidance = FakeSteveCrowdAvoidance.decide(
                body.getX(), body.getZ(), next.getX() + 0.5D, next.getZ() + 0.5D,
                nearbyPlayers, state.crowdedTicks);
        if (avoidance.crowded()) {
            if (state.crowdStrafe == 0.0F) {
                state.crowdStrafe = avoidance.strafe();
            }
            state.crowdedTicks += state.tickStep;
        } else {
            state.crowdedTicks = 0;
            state.crowdStrafe = 0.0F;
        }
        if (avoidance.shouldRepath()) {
            state.path.clear();
            state.nextPathTick = now;
            state.crowdedTicks = 0;
            FakeSteveMotionController.hold(body, state, body.getYRot(),
                    FakeSteveMotionPolicy.walkingPitch(now, body.getUUID().hashCode(), true));
            return;
        }
        float nodeYaw = (float) (Mth.atan2(-direction.x, direction.z) * Mth.RAD_TO_DEG);
        FakeSteveMotionPolicy.Point lookPoint = FakeSteveMotionPolicy.lookAheadPoint(
                lookAheadNodes(state.path),
                new FakeSteveMotionPolicy.Point(goal.getX() + 0.5D, goal.getZ() + 0.5D));
        float lookYaw = FakeSteveMotionPolicy.shouldUseLookAhead(
                lookPoint.x() - body.getX(), lookPoint.z() - body.getZ())
                ? FakeSteveMotionPolicy.yawTo(body.getX(), body.getZ(), lookPoint.x(), lookPoint.z())
                : nodeYaw;
        if (doorAhead || FakeStevePathPolicy.shouldFaceNextNode(lookYaw, nodeYaw)) {
            lookYaw = nodeYaw;
        }
        if (!state.hasStableRouteYaw) {
            state.stableRouteYaw = lookYaw;
            state.hasStableRouteYaw = true;
        } else if (doorAhead || FakeStevePathPolicy.shouldFaceNextNode(
                state.stableRouteYaw, nodeYaw)) {
            state.stableRouteYaw = nodeYaw;
        } else {
            state.stableRouteYaw = FakeSteveMotionPolicy.walkingHeading(
                    state.stableRouteYaw, lookYaw);
        }
        boolean sprint = FakeStevePathPolicy.shouldSprintForPursuit(
                pursuingHuman, psychoActive, avoidance.crowded())
                || (!avoidance.crowded() && (now < state.sprintUntilTick || speed >= 0.22D));
        float requestedStrafe = avoidance.crowded()
                ? state.crowdStrafe : avoidance.strafe();
        float pathStrafe = canStrafePast(level, body, direction, requestedStrafe)
                ? requestedStrafe : 0.0F;
        if (pathStrafe != 0.0F) {
            double side = 1.15D * Math.signum(pathStrafe);
            Vec3 lane = new Vec3(direction.z, 0.0D, -direction.x).multiply(side, 0.0D, side);
            if (!FakeSteveNavigator.stepSafe(level, body.position(), lane)) {
                pathStrafe = 0.0F;
            }
        }
        float pathForward = pathStrafe == 0.0F && avoidance.crowded()
                ? 0.0F : avoidance.forwardScale();
        if (pathForward > 0.0F && !FakeSteveNavigator.stepSafe(level, body.position(),
                direction.multiply(1.15D, 0.0D, 1.15D))) {
            pathForward = 0.0F;
            if (pathStrafe == 0.0F) {
                state.path.clear();
                state.nextPathTick = Math.min(state.nextPathTick, now + 10L);
                state.hasStableRouteYaw = false;
                state.sprintUntilTick = 0L;
            }
        }
        double worldX = direction.x * pathForward + direction.z * pathStrafe;
        double worldZ = direction.z * pathForward - direction.x * pathStrafe;
        FakeSteveMotionPolicy.LocalMove local = FakeSteveMotionPolicy.toLocal(
                state.stableRouteYaw, worldX, worldZ);
        boolean stepAhead = FakeSteveNavigator.isStepBlock(level.getBlockState(next))
                || FakeSteveNavigator.isStepBlock(level.getBlockState(next.below()));
        boolean ascends = delta.y > 0.20D || stepAhead;
        boolean jump = wantsJumpOrSwim(level, body, state, next.getY() + 0.1D, ascends, now);
        FakeSteveMotionController.drive(body, state, local.forward(), local.strafe(), jump, sprint,
                false, state.stableRouteYaw,
                FakeSteveMotionPolicy.walkingPitch(now, body.getUUID().hashCode(), true), next);
    }

    private static boolean isPursuitTarget(FakeSteveAgentState state, UUID playerId,
            boolean pursuingHuman) {
        if (!pursuingHuman) {
            return false;
        }
        return playerId.equals(state.focusTarget) || playerId.equals(state.committedTarget);
    }

    private static List<FakeSteveMotionPolicy.Point> lookAheadNodes(Iterable<BlockPos> path) {
        List<FakeSteveMotionPolicy.Point> nodes = new ArrayList<>();
        for (BlockPos node : path) {
            nodes.add(new FakeSteveMotionPolicy.Point(node.getX() + 0.5D, node.getZ() + 0.5D));
        }
        return nodes;
    }

    /** Walk or step-up toward the goal when A* has no remaining node, instead of freezing. */
    private static boolean tryStepToward(ServerLevel level, ServerPlayer body, BlockPos goal,
            FakeSteveAgentState state, long now, double speed) {
        Vec3 target = Vec3.atBottomCenterOf(goal);
        Vec3 delta = target.subtract(body.position());
        Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
        if (horizontal.lengthSqr() < 0.01D) {
            if (needsVerticalSwim(body, goal.getY() + 0.1D)) {
                driveVerticalSwim(body, state, goal, now, speed);
                return true;
            }
            if (Math.abs(delta.y) < 0.45D) {
                return false;
            }
        }
        if (horizontal.lengthSqr() >= 0.01D) {
            horizontal = horizontal.normalize();
        } else {
            horizontal = new Vec3(0.0D, 0.0D, 1.0D);
        }
        if (!FakeSteveNavigator.stepSafe(level, body.position(), horizontal.scale(1.15D))) {
            return false;
        }
        BlockPos ahead = BlockPos.containing(body.position().add(horizontal.scale(1.0D)));
        boolean stepAhead = FakeSteveNavigator.isStepBlock(level.getBlockState(ahead))
                || FakeSteveNavigator.isStepBlock(level.getBlockState(goal))
                || FakeSteveNavigator.isStepBlock(level.getBlockState(goal.below()));
        float yaw = (float) (Mth.atan2(-horizontal.x, horizontal.z) * Mth.RAD_TO_DEG);
        boolean jump = wantsJumpOrSwim(level, body, state, goal.getY() + 0.1D,
                delta.y > 0.20D || stepAhead, now);
        boolean sprint = now < state.sprintUntilTick || speed >= 0.22D;
        FakeSteveMotionController.drive(body, state, 1.0F, 0.0F, jump, sprint, false, yaw,
                FakeSteveMotionPolicy.walkingPitch(now, body.getUUID().hashCode(), true),
                goal);
        return true;
    }

    private static boolean inWater(ServerPlayer body) {
        return body.isInWater() || body.isUnderWater();
    }

    private static boolean needsVerticalSwim(ServerPlayer body, double targetY) {
        return FakeStevePathPolicy.shouldHoldSwim(inWater(body), body.onGround(),
                body.getY(), targetY);
    }

    private static boolean wantsJumpOrSwim(ServerLevel level, ServerPlayer body,
            FakeSteveAgentState state, double targetY, boolean ascends, long now) {
        boolean jumpsAllowed = SREGameWorldComponent.KEY.get(level).isJumpAvailable();
        boolean jump = FakeStevePathPolicy.shouldJump(jumpsAllowed, body.onGround(),
                ascends, now, state.nextJumpTick)
                || needsVerticalSwim(body, targetY);
        if (jump && !inWater(body)) {
            state.nextJumpTick = now + 12L;
        }
        return jump;
    }

    private static void driveVerticalSwim(ServerPlayer body, FakeSteveAgentState state,
            BlockPos goal, long now, double speed) {
        boolean sprint = now < state.sprintUntilTick || speed >= 0.22D;
        FakeSteveMotionController.drive(body, state, 0.0F, 0.0F, true, sprint, false,
                body.getYRot(),
                FakeSteveMotionPolicy.walkingPitch(now, body.getUUID().hashCode(), true),
                goal);
    }

    private static void backOffPath(ServerLevel level, ServerPlayer body,
                                    FakeSteveAgentState state, long now) {
        state.path.clear();
        state.lastPathDistanceSqr = Double.MAX_VALUE;
        state.lastPathProgressTick = now;
        state.pathFailureCount++;
        if (state.pathFailureCount >= 3 && state.mode == AgentMode.DISGUISE_TASK
                && state.taskType != null) {
            state.taskBackoffUntil.put(state.taskType, now + 10L * 20L);
            FakeSteveTaskPlanner.abandon(body, state);
        }
        if (FakeStevePathPolicy.shouldAbandonIdleGoal(state.pathFailureCount)
                && (state.mode == AgentMode.DISGUISE_IDLE || state.mode == AgentMode.HUNT)) {
            state.pathGoal = null;
            state.pathRetryAfterTick = 0L;
            state.nextPathTick = now;
            state.nextDecisionTick = now;
            return;
        }
        if (state.mode == AgentMode.DISGUISE_IDLE || state.mode == AgentMode.HUNT) {
            state.pathRetryAfterTick = now + 8L;
            state.nextPathTick = now + 8L;
            return;
        }
        state.pathRetryAfterTick = now + 30L + level.getRandom().nextInt(20);
        state.nextPathTick = state.pathRetryAfterTick;
    }

    private static boolean canStrafePast(ServerLevel level, ServerPlayer body,
                                         Vec3 direction, float strafe) {
        if (strafe == 0.0F) {
            return false;
        }
        Vec3 left = new Vec3(direction.z, 0.0D, -direction.x)
                .scale(Math.copySign(0.7D, strafe));
        return level.noCollision(body, body.getBoundingBox().move(left));
    }

    private static boolean openDoorsOnApproach(ServerLevel level, ServerPlayer body, BlockPos next) {
        Vec3 bodyPosition = body.position();
        Vec3 route = Vec3.atBottomCenterOf(next);
        Vec3 approach = route.subtract(bodyPosition);
        if (approach.horizontalDistance() > 3.0D) {
            approach = new Vec3(approach.x, 0.0D, approach.z).normalize().scale(3.0D);
            route = bodyPosition.add(approach);
        }
        BlockPos scanEnd = BlockPos.containing(route);
        int minX = Math.min(body.blockPosition().getX(), scanEnd.getX()) - 1;
        int maxX = Math.max(body.blockPosition().getX(), scanEnd.getX()) + 1;
        int minY = Math.min(body.blockPosition().getY(), scanEnd.getY()) - 1;
        int maxY = Math.max(body.blockPosition().getY(), scanEnd.getY()) + 2;
        int minZ = Math.min(body.blockPosition().getZ(), scanEnd.getZ()) - 1;
        int maxZ = Math.max(body.blockPosition().getZ(), scanEnd.getZ()) + 1;
        boolean detected = false;
        for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            var blockState = level.getBlockState(pos);
            if (!FakeSteveDoorAccess.isOpenablePassage(blockState)
                    || !FakeSteveDoorAccess.isInsideApproachCorridor(
                            body.getX(), body.getZ(), route.x, route.z,
                            pos.getX() + 0.5D, pos.getZ() + 0.5D)) {
                continue;
            }
            detected = true;
            if (openDoorAt(level, body, pos.immutable(), blockState)) {
                return true;
            }
        }
        return detected;
    }

    private static boolean openDoorAt(ServerLevel level, ServerPlayer body,
                                      BlockPos pos, net.minecraft.world.level.block.state.BlockState blockState) {
        if (blockState.getBlock() instanceof SmallDoorBlock door) {
            BlockPos lower = door.getLowerHalfPos(blockState, pos);
            if (level.getBlockEntity(lower) instanceof SmallDoorBlockEntity entity) {
                var lowerState = level.getBlockState(lower);
                boolean hardLocked = entity.isJammed() || entity.isBlasted() || hasExternalDoorLock(lower);
                if (FakeStevePathPolicy.shouldAutoOpenSmallDoor(
                        lowerState.getValue(DoorBlock.OPEN), hardLocked)) {
                    door.toggleDoor(lowerState, level, entity, lower);
                    body.swing(InteractionHand.MAIN_HAND, true);
                    return true;
                }
                return false;
            }
        }
        if (FakeSteveDoorAccess.isOpen(blockState)) {
            return false;
        }
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        body.gameMode.useItemOn(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        body.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    private static boolean hasExternalDoorLock(BlockPos lower) {
        BlockPos anchor = lower.above();
        if (LockEntityManager.getInstance().getLockEntity(anchor) != null) {
            return true;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (LockEntityManager.getInstance().getLockEntity(anchor.relative(direction)) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean killWithPsycho(ServerPlayer attacker, ServerPlayer target) {
        SRERole role = SREGameWorldComponent.KEY.get(attacker.level()).getRole(attacker);
        if (!FakeSteveDirector.isHuntPhase(attacker.serverLevel())
                && role != null && (!role.onUseKnife(attacker) || !role.onUseKnifeHit(attacker, target))) {
            return false;
        }
        target.playSound(TMMSounds.ITEM_KNIFE_STAB, 1.0F, 1.0F);
        GameUtils.killPlayer(target, true, attacker, BACKSTAB);
        attacker.getCooldowns().addCooldown(attacker.getMainHandItem().getItem(),
                FakeSteveKillerPolicy.psychoAttackCooldownTicks());
        attacker.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    private static boolean backstabAssimilate(ServerPlayer attacker, ServerPlayer target) {
        if (!isHuman(target) || !isEngageable(target)) {
            return false;
        }
        target.playSound(TMMSounds.ITEM_KNIFE_STAB, 1.0F, 1.0F);
        attacker.swing(InteractionHand.MAIN_HAND, true);
        return FakeSteveDirector.replace(target, ReplacementCause.ASSIMILATION);
    }

    private static void cancelKnifeCharge(ServerPlayer body, FakeSteveAgentState state) {
        if (state.knifeChargeTarget != null && body.isUsingItem()) {
            body.releaseUsingItem();
        }
        state.knifeChargeTarget = null;
        state.knifeChargedAtTick = 0L;
        state.knifeChargeStartedTick = 0L;
    }

    private static void holsterKnifeIfReady(ServerPlayer body, FakeSteveAgentState state, long now) {
        if (!FakeSteveKillerPolicy.shouldHolsterAfterKnifeKill(now, state.holsterAtTick)) {
            return;
        }
        int slot = state.holsterSlot >= 0 ? state.holsterSlot : findSafeHolsterSlot(body);
        if (slot >= 0) {
            select(body, slot);
        }
        state.holsterAtTick = 0L;
        state.holsterSlot = -1;
    }

    private static int findPsychoWeaponSlot(ServerPlayer player, SRERole role) {
        if (role == null) {
            return findSlot(player, TMMItems.BAT);
        }
        for (var weapon : role.getPsychoSupportedWeapons(player)) {
            int slot = findSlot(player, weapon);
            if (slot >= 0) {
                return slot;
            }
        }
        return -1;
    }

    private static int findSafeHolsterSlot(ServerPlayer player) {
        for (int slot = 0; slot < 9; slot++) {
            var stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && !stack.is(TMMItemTags.GUNS)
                    && !(stack.getItem() instanceof TrainWeapon)) {
                return slot;
            }
        }
        return -1;
    }

    private static void prepareShop(ServerLevel level, ServerPlayer body,
            FakeSteveAgentState state, SRERole role, boolean killerRole,
            ServerPlayer prey, boolean psychoActive) {
        long now = level.getGameTime();
        int nearbyHumans = nearbyHumans(level, body, 18.0D);
        if (now >= state.nextShopTick) {
            state.nextShopTick = now + 6L * 20L + level.getRandom().nextInt(6 * 20);
            if (killerRole) {
                if (!tryBuyKillerCrowdTools(body, role, nearbyHumans)) {
                    tryBuyKillerTool(body, role);
                }
            } else {
                tryBuyCivilianTool(body, role);
            }
        }
        if (!killerRole || prey == null) {
            return;
        }
        if (now >= state.nextTacticalItemTick) {
            state.nextTacticalItemTick = now + 12L * 20L + level.getRandom().nextInt(12 * 20);
            tryUseTacticalItem(level, body);
        }
        if (now >= state.nextSkillTick && FakeSteveKillerPolicy.shouldUseSkill(
                true, !witnessed(level, body, prey, psychoActive), prey != null)) {
            state.nextSkillTick = now + 18L * 20L + level.getRandom().nextInt(18 * 20);
            RoleSkill.beginUse(body, prey.getUUID(), -1, RoleSkill.Phase.PRESS, false, true);
        }
    }

    /** Innocent possessed bodies also make use of whatever their role shop sells. */
    private static void tryBuyCivilianTool(ServerPlayer body, SRERole role) {
        List<io.wifi.starrailexpress.util.ShopEntry> entries = ShopContent.getShopEntries(role, body);
        if (entries.isEmpty()) {
            return;
        }
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(body);
        for (var entry : entries) {
            Item item = entry.stack().getItem();
            if (entry.stack().isEmpty() || owns(body, item)) {
                continue;
            }
            if (!entry.canDisplay(body) || !entry.canBuy(body)) {
                continue;
            }
            int price = DynamicShopComponent.KEY.get(body).effectivePrice(entry);
            // Keep at least half the balance in reserve.
            if (shop.balance < price * 2) {
                continue;
            }
            for (int index = 0; index < entries.size(); index++) {
                if (entries.get(index) == entry) {
                    shop.tryBuy(index);
                    return;
                }
            }
        }
    }

    private static void tryBuyKillerTool(ServerPlayer body, SRERole role) {
        List<io.wifi.starrailexpress.util.ShopEntry> entries = ShopContent.getShopEntries(role, body);
        if (entries.isEmpty()) {
            return;
        }
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(body);
        boolean hasKnife = findKnifeSlot(body) >= 0;
        boolean hasGun = findGunSlot(body) >= 0;

        // 核心武器优先：缺刀买刀、缺枪买枪；买不起就攒钱，不碰消耗品。
        if (!hasKnife) {
            if (tryBuy(body, entries, shop, TMMItems.KNIFE)) {
                return;
            }
            return; // 攒钱买刀
        }
        if (!hasGun) {
            if (tryBuy(body, entries, shop, TMMItems.REVOLVER)) {
                return;
            }
            return; // 攒钱买枪
        }

        // 刀枪齐了才在余额富余（>=2 倍价格）时各补一个一次性道具，避免反复买关灯。
        if (!owns(body, TMMItems.BLACKOUT) && canAffordExtra(body, entries, TMMItems.BLACKOUT, shop)) {
            if (tryBuy(body, entries, shop, TMMItems.BLACKOUT)) {
                return;
            }
        }
        if (!owns(body, TMMItems.PSYCHO_MODE) && canAffordExtra(body, entries, TMMItems.PSYCHO_MODE, shop)) {
            tryBuy(body, entries, shop, TMMItems.PSYCHO_MODE);
        }
    }

    private static boolean owns(ServerPlayer body, Item item) {
        for (int slot = 0; slot < body.getInventory().getContainerSize(); slot++) {
            if (body.getInventory().getItem(slot).is(item)) {
                return true;
            }
        }
        return false;
    }

    /** A consumable is only worth buying when the balance is at least twice its price. */
    private static boolean canAffordExtra(ServerPlayer body,
            List<io.wifi.starrailexpress.util.ShopEntry> entries, Item item,
            SREPlayerShopComponent shop) {
        for (var entry : entries) {
            if (!entry.stack().is(item)) {
                continue;
            }
            int price = DynamicShopComponent.KEY.get(body).effectivePrice(entry);
            return shop.balance >= price * 2;
        }
        return false;
    }

    private static boolean tryBuy(ServerPlayer body,
            List<io.wifi.starrailexpress.util.ShopEntry> entries, SREPlayerShopComponent shop,
            Item item) {
        if (body.getCooldowns().isOnCooldown(item)) {
            return false;
        }
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.get(index);
            if (!entry.stack().is(item)) {
                continue;
            }
            int price = DynamicShopComponent.KEY.get(body).effectivePrice(entry);
            if (shop.balance >= price && entry.canDisplay(body) && entry.canBuy(body)) {
                shop.tryBuy(index);
                return true;
            }
        }
        return false;
    }

    private static boolean tryBuyKillerCrowdTools(ServerPlayer body, SRERole role, int nearbyHumans) {
        List<FakeSteveKillerPolicy.Purchase> desired = FakeSteveKillerPolicy.crowdPurchasePlan(nearbyHumans);
        if (desired.isEmpty()) {
            return false;
        }
        // 核心武器还没齐就先攒钱，别把钱砸在一次性道具上。
        if (findKnifeSlot(body) < 0 || findGunSlot(body) < 0) {
            return false;
        }
        List<io.wifi.starrailexpress.util.ShopEntry> entries = ShopContent.getShopEntries(role, body);
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(body);
        boolean purchased = false;
        for (FakeSteveKillerPolicy.Purchase purchase : desired) {
            Item item = purchase == FakeSteveKillerPolicy.Purchase.PSYCHO
                    ? TMMItems.PSYCHO_MODE : TMMItems.BLACKOUT;
            if (owns(body, item) || body.getCooldowns().isOnCooldown(item)
                    || !canAffordExtra(body, entries, item, shop)) {
                continue;
            }
            if (tryBuy(body, entries, shop, item)) {
                purchased = true;
            }
        }
        return purchased;
    }

    private static void tryUseTacticalItem(ServerLevel level, ServerPlayer body) {
        int nearbyHumans = nearbyHumans(level, body, 18.0D);
        if (nearbyHumans < 2) {
            return;
        }
        for (Item item : new Item[] { TMMItems.PSYCHO_MODE, TMMItems.BLACKOUT }) {
            int slot = findSlot(body, item);
            if (slot < 0 || body.getCooldowns().isOnCooldown(item)) {
                continue;
            }
            select(body, slot);
            body.gameMode.useItem(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND);
            body.swing(InteractionHand.MAIN_HAND, true);
            return;
        }
    }

    private static int nearbyHumans(ServerLevel level, ServerPlayer body, double range) {
        return (int) level.players().stream().filter(FakeSteveAi::isHuman)
                .filter(FakeSteveAi::isEngageable)
                .filter(player -> player.distanceToSqr(body) <= range * range).count();
    }

    private static boolean shouldFlee(ServerLevel level, ServerPlayer body, boolean psychoActive) {
        // A frenzied body does not weigh risk, so it never breaks off to run.
        if (psychoActive) {
            return false;
        }
        if (body.hurtTime > 0) {
            return true;
        }
        return level.players().stream().filter(FakeSteveAi::isHuman)
                .filter(FakeSteveAi::isEngageable)
                .filter(player -> player.distanceToSqr(body) <= 36.0D)
                .filter(player -> findKnifeSlot(player) >= 0 || findGunSlot(player) >= 0)
                .filter(player -> faces(player, body, 0.5D))
                .anyMatch(player -> visible(body, player));
    }

    private static void flee(ServerLevel level, ServerPlayer body, FakeSteveAgentState state) {
        ServerPlayer threat = level.players().stream().filter(FakeSteveAi::isHuman)
                .filter(FakeSteveAi::isEngageable)
                .min(Comparator.comparingDouble(body::distanceToSqr)).orElse(null);
        if (threat == null) {
            return;
        }
        Vec3 away = body.position().subtract(threat.position());
        if (away.horizontalDistanceSqr() < 0.01D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        away = new Vec3(away.x, 0.0D, away.z).normalize().scale(9.0D);
        BlockPos escape = BlockPos.containing(body.position().add(away));
        state.sprintUntilTick = level.getGameTime() + 60L;
        state.hasStableRouteYaw = false;
        state.pathGoal = escape;
        state.path.clear();
        follow(level, body, escape, state, 0.24D);
    }

    private static void maybeSpeak(ServerLevel level, ServerPlayer body, FakeSteveAgentState state) {
        long now = level.getGameTime();
        if (!state.directedReplyPending
                && state.mode != AgentMode.DISGUISE_IDLE && state.mode != AgentMode.DISGUISE_TASK) {
            return;
        }
        if (state.nextDialogueTick == 0L) {
            state.nextDialogueTick = now + 30L * 20L + level.getRandom().nextInt(45 * 20);
            return;
        }
        if (now < state.nextDialogueTick) {
            return;
        }
        boolean humanNearby = level.players().stream().filter(FakeSteveAi::isHuman)
                .filter(FakeSteveAi::isEngageable)
                .anyMatch(player -> player.distanceToSqr(body) <= 12.0D * 12.0D);
        state.nextDialogueTick = now + 35L * 20L + level.getRandom().nextInt(70 * 20);
        if (!humanNearby) {
            return;
        }
        int seed = body.getUUID().hashCode() ^ (int) now ^ level.getRandom().nextInt();
        String line = state.directedReplyPending
                ? FakeSteveDialogue.directedRoleReply(seed)
                : FakeSteveDialogue.commonPhrase(seed);
        state.directedReplyPending = false;
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("<" + body.getGameProfile().getName() + "> " + line), false);
    }

    private static int findMatchingKey(ServerPlayer body, String keyName) {
        if (keyName == null || keyName.isEmpty()) {
            return -1;
        }
        String normalized = keyName.replace("alarmed:", "").replace("reinforced:", "");
        for (int slot = 0; slot < 9; slot++) {
            var stack = body.getInventory().getItem(slot);
            if (!stack.is(TMMItems.KEY)) {
                continue;
            }
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore != null && !lore.lines().isEmpty()
                    && lore.lines().getFirst().getString().equals(normalized)) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean visible(ServerPlayer observer, ServerPlayer target) {
        if (!observer.hasLineOfSight(target))
            return false;
        HitResult hit = observer.level().clip(new ClipContext(observer.getEyePosition(), target.getEyePosition(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, observer));
        return hit.getType() == HitResult.Type.MISS
                || hit.getLocation().distanceToSqr(target.getEyePosition()) < 1.0;
    }

    private static boolean faces(Player observer, Player target, double cosine) {
        Vec3 direction = target.getEyePosition().subtract(observer.getEyePosition()).normalize();
        return observer.getLookAngle().normalize().dot(direction) >= cosine;
    }

    private static boolean behind(Player attacker, Player target) {
        Vec3 toAttacker = attacker.position().subtract(target.position()).normalize();
        return target.getLookAngle().normalize().dot(toAttacker) <= -0.5;
    }

    private static boolean hasWitness(ServerLevel level, ServerPlayer attacker, ServerPlayer target) {
        return level.players().stream().filter(p -> p != attacker && p != target)
                .filter(FakeSteveAi::isEngageable)
                .filter(p -> FakeSteveKillerPolicy.countsAsHostileWitness(
                        FakeSteveDirector.isReplaced(p), isKillerRole(level, p),
                        isKillerNeutral(level, p)))
                .filter(p -> p.distanceToSqr(target) <= 144.0)
                .anyMatch(p -> visible(p, attacker) || visible(p, target));
    }

    private static void lookAt(ServerPlayer body, FakeSteveAgentState state, Vec3 position) {
        Vec3 delta = position.subtract(body.getEyePosition());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) (Mth.atan2(-delta.x, delta.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);
        FakeSteveMotionController.hold(body, state, yaw, pitch);
    }

    private static int findSlot(ServerPlayer player, Item item) {
        for (int i = 0; i < 9; i++)
            if (player.getInventory().getItem(i).is(item))
                return i;
        return -1;
    }

    private static int findGunSlot(ServerPlayer player) {
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).is(TMMItemTags.GUNS)) {
                return slot;
            }
        }
        return -1;
    }

    /** A carried Derringer keeps the berserk state, but a spent one cannot block a fallback weapon. */
    private static int findUsableDerringerSlot(ServerPlayer player) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (org.agmas.noellesroles.content.item.DesperadoGunItem.isDerringerWeapon(stack)
                    && !stack.getOrDefault(SREDataComponentTypes.USED, false)
                    && !player.getCooldowns().isOnCooldown(stack.getItem())) {
                return slot;
            }
        }
        return -1;
    }

    /** Includes every ready gun except a Derringer that has already fired. */
    private static int findUsableGunSlot(ServerPlayer player) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(TMMItemTags.GUNS)
                    || player.getCooldowns().isOnCooldown(stack.getItem())) {
                continue;
            }
            if (!org.agmas.noellesroles.content.item.DesperadoGunItem.isDerringerWeapon(stack)
                    || !stack.getOrDefault(SREDataComponentTypes.USED, false)) {
                return slot;
            }
        }
        return -1;
    }

    private static int findDerringerSlot(ServerPlayer player) {
        int slot = findSlot(player, TMMItems.DERRINGER);
        return slot >= 0 ? slot : findSlot(player, org.agmas.noellesroles.init.ModItems.DESPERADO_GUN);
    }

    private static BlockPos ambushBehind(ServerPlayer target) {
        Vec3 look = target.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.lengthSqr() < 0.01D) {
            return target.blockPosition();
        }
        return BlockPos.containing(target.position().subtract(horizontal.normalize().scale(2.0D)));
    }

    /**
     * The ambush point is cached for a moment. Recomputing it every tick made a
     * turning target drag the body back and forth across the same doorway.
     */
    private static BlockPos ambushGoal(ServerLevel level, FakeSteveAgentState state,
            ServerPlayer target, long now) {
        BlockPos cached = state.ambushGoal;
        if (cached != null && target.getUUID().equals(state.ambushTarget)
                && now - state.ambushGoalTick < 30L
                && cached.closerThan(target.blockPosition(), 6.0)) {
            return cached;
        }
        BlockPos computed = ambushBehind(target);
        if (!FakeSteveNavigator.safeStand(level, computed)) {
            computed = target.blockPosition();
        }
        state.ambushGoal = computed.immutable();
        state.ambushTarget = target.getUUID();
        state.ambushGoalTick = now;
        return state.ambushGoal;
    }

    private static int findKnifeSlot(ServerPlayer player) {
        for (int slot = 0; slot < 9; slot++) {
            var stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof TrainWeapon && !stack.is(TMMItemTags.GUNS)
                    && !KillerKnifeDurability.isDepleted(stack)) {
                return slot;
            }
        }
        return -1;
    }

    /** A knife still on cooldown is not a weapon: never charge or wave it. */
    private static int findUsableKnifeSlot(ServerPlayer player) {
        for (int slot = 0; slot < 9; slot++) {
            var stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof TrainWeapon && !stack.is(TMMItemTags.GUNS)
                    && !KillerKnifeDurability.isDepleted(stack)
                    && !player.getCooldowns().isOnCooldown(stack.getItem())) {
                return slot;
            }
        }
        return -1;
    }

    private static int firstEmptyHotbarSlot(ServerPlayer player) {
        for (int i = 0; i < 9; i++)
            if (player.getInventory().getItem(i).isEmpty())
                return i;
        return -1;
    }

    static void select(ServerPlayer player, int slot) {
        player.getInventory().selected = slot;
        player.connection.send(new ClientboundSetCarriedItemPacket(slot));
    }

    private static ServerPlayer player(ServerLevel level, UUID id) {
        return id == null ? null : level.getServer().getPlayerList().getPlayer(id);
    }

    private static boolean isHuman(ServerPlayer player) {
        return player != null && !player.isSpectator() && !FakeSteveDirector.isReplaced(player);
    }

    private static boolean isKillerRole(ServerLevel level, ServerPlayer player) {
        SRERole role = SREGameWorldComponent.KEY.get(level).getRole(player);
        return role != null && role.isKiller();
    }

    private static boolean isKillerNeutral(ServerLevel level, ServerPlayer player) {
        SRERole role = SREGameWorldComponent.KEY.get(level).getRole(player);
        return role != null && role.isNeutralForKiller();
    }

    private static void beginStare(FakeSteveAgentState state, ServerPlayer target) {
        state.focusTarget = target.getUUID();
        state.pendingEngagement = true;
        state.faceTicks = 0;
        state.path.clear();
    }

    private static void clearFocus(FakeSteveAgentState state) {
        state.focusTarget = null;
        state.pendingEngagement = false;
        state.faceTicks = 0;
        state.assimilationTicks = 0;
        state.ambushGoal = null;
        state.ambushTarget = null;
        state.path.clear();
    }
}
