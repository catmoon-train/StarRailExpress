package org.agmas.noellesroles.game.fake_steve;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.cca.DynamicShopComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.content.block.PlatterBlock;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
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
        } else if (state.mode != AgentMode.STARE && state.mode != AgentMode.STALK) {
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
        ServerPlayer prey = killerRole || derringerBerserk || huntPhase
                ? choosePrey(level, body, state, now, derringerBerserk) : null;
        // A psycho body does not wait for eye contact: it locks onto prey at once.
        if (psychoActive && !derringerBerserk && focus == null && state.mode != AgentMode.STALK) {
            ServerPlayer nearest = prey != null ? prey : nearestVisibleHuman(level, body);
            if (nearest != null) {
                beginStare(state, nearest);
                focus = nearest;
            }
        }
        focus = engageable(level, state.focusTarget);

        ServerPlayer isolated = !derringerBerserk && FakeSteveDirector.isEnabled()
                ? isolatedTarget(level, body) : null;
        boolean taskAvailable = !derringerBerserk && FakeSteveTaskPlanner.hasCompletableTask(body, state);
        if (!berserkActive) {
            prepareShop(level, body, state, originalRole, killerRole, prey, psychoActive);
        }
        boolean psychoArmed = psychoActive
                && findPsychoWeaponSlot(body, originalRole) >= 0;
        boolean armed = psychoArmed || findUsableKnifeSlot(body) >= 0 || findUsableGunSlot(body) >= 0;
        boolean interruptTask = derringerBerserk && prey != null
                || FakeSteveKillerPolicy.shouldPsychoInterruptTask(
                psychoArmed, prey != null)
                || prey != null && FakeSteveKillerPolicy.shouldSkipTaskForStrike(
                        taskAvailable, armed, !witnessed(level, body, prey, berserkActive),
                        body.distanceTo(prey));
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
                !psychoActive && taskAvailable && !interruptTask));
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
                && state.mode != AgentMode.STARE && state.mode != AgentMode.STALK) {
            flee(level, body, state);
            return;
        }

        if (state.mode == AgentMode.DISGUISE_TASK
                && FakeSteveTaskPlanner.tick(level, body, state)) {
            return;
        }

        state.mode = AgentMode.DISGUISE_IDLE;
        state.idleTicks += elapsed;
        if (FakeSteveMotionPolicy.shouldSprint(false, state.idleTicks,
                body.getUUID().hashCode() + (int) (now / 20L))) {
            state.sprintUntilTick = Math.max(state.sprintUntilTick, now + 30L + level.getRandom().nextInt(30));
            state.idleTicks = 0;
        }
        if (now >= state.nextDecisionTick || (psychoActive && state.pathGoal == null)) {
            state.nextDecisionTick = now + (psychoActive
                    ? 10L + level.getRandom().nextInt(15)
                    : 40L + level.getRandom().nextInt(80));
            boolean interacted = !psychoActive && tryInteract(level, body);
            if (!interacted) {
                state.pathGoal = wanderGoal(level, body, state);
                state.path.clear();
            }
        }
        if (state.pathGoal != null) {
            follow(level, body, state.pathGoal, state, psychoActive ? 0.22D : 0.15D);
        } else {
            idleHold(level, body, state, now);
        }
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
            state.lastMoveZ = body.getZ();
            state.lastMoveTick = 0L;
            return;
        }
        if (state.lastMoveTick == 0L) {
            state.lastMoveX = body.getX();
            state.lastMoveZ = body.getZ();
            state.lastMoveTick = now;
            return;
        }
        long sampleGap = now - state.lastMoveTick;
        if (sampleGap < 4L) {
            return;
        }
        double dx = body.getX() - state.lastMoveX;
        double dz = body.getZ() - state.lastMoveZ;
        state.lastMoveX = body.getX();
        state.lastMoveZ = body.getZ();
        state.lastMoveTick = now;
        if (FakeStevePathPolicy.isStuck(dx * dx + dz * dz, sampleGap)) {
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
        // A short pause before the next A* run keeps a wedged body from
        // recomputing the same blocked route every single tick.
        state.pathRetryAfterTick = now + 10L;
        state.nextPathTick = now + 10L;
        state.hasStableRouteYaw = false;
        state.crowdedTicks = 0;
        state.crowdStrafe = 0.0F;
        state.lastPathDistanceSqr = Double.MAX_VALUE;
        state.lastPathProgressTick = now;
        state.sprintUntilTick = Math.max(state.sprintUntilTick, now + 20L);
        state.pathFailureCount++;
        BlockPos goal = state.pathGoal;
        if (goal != null) {
            // Nudge the goal so the next A* run cannot re-pick the same blocked
            // lane, but never onto a position that cannot hold a body.
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

    private static ServerPlayer nearestVisibleHuman(ServerLevel level, ServerPlayer body) {
        return level.players().stream().filter(FakeSteveAi::isHuman)
                .filter(FakeSteveAi::isEngageable).filter(p -> p.distanceToSqr(body) <= 400.0D)
                .filter(p -> visible(body, p))
                .min(Comparator.comparingDouble(body::distanceToSqr)).orElse(null);
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
            FakeSteveAgentState state, long now, boolean derringerBerserk) {
        double huntRadiusSqr = derringerBerserk
                ? FakeSteveKillerPolicy.MAX_GUN_RANGE * FakeSteveKillerPolicy.MAX_GUN_RANGE
                : FakeSteveKillerPolicy.STRIKE_RADIUS_SQR;
        ServerPlayer committed = engageable(level, state.committedTarget);
        if (committed != null && isPrey(level, committed)) {
            double committedDistance = body.distanceToSqr(committed);
            if (committedDistance <= huntRadiusSqr * 2.25D) {
                if (now < state.committedUntilTick) {
                    return committed;
                }
                ServerPlayer rival = nearestPrey(level, body, huntRadiusSqr);
                // Only a clearly closer human is worth abandoning the current one.
                if (rival == null || body.distanceToSqr(rival) > committedDistance * 0.36D) {
                    return committed;
                }
            }
        }
        ServerPlayer focus = engageable(level, state.focusTarget);
        if (focus != null && isPrey(level, focus)
                && body.distanceToSqr(focus) <= huntRadiusSqr) {
            commitPrey(state, focus, now);
            return focus;
        }
        ServerPlayer nearest = nearestPrey(level, body, huntRadiusSqr);
        if (nearest == null) {
            state.committedTarget = null;
            state.committedUntilTick = 0L;
            return null;
        }
        commitPrey(state, nearest, now);
        return nearest;
    }

    private static void commitPrey(FakeSteveAgentState state, ServerPlayer prey, long now) {
        state.committedTarget = prey.getUUID();
        state.committedUntilTick = now + PREY_COMMIT_TICKS;
    }

    /** Wander targets must be real floor; an unvalidated one walks into the void. */
    private static BlockPos wanderGoal(ServerLevel level, ServerPlayer body, FakeSteveAgentState state) {
        BlockPos origin = body.blockPosition();
        BlockPos previous = state.lastWanderGoal;
        // Prefer a fresh, clearly different spot so the body does not pace the
        // same few blocks. Try farther destinations first.
        for (int range : new int[] { 24, 16, 8 }) {
            for (int attempt = 0; attempt < 6; attempt++) {
                int dx = level.getRandom().nextInt(range * 2 + 1) - range;
                int dz = level.getRandom().nextInt(range * 2 + 1) - range;
                BlockPos candidate = origin.offset(dx, 0, dz);
                if (candidate.distSqr(origin) < 64.0D
                        || previous != null && candidate.closerThan(previous, 6.0D)) {
                    continue;
                }
                if (FakeSteveNavigator.safeStand(level, candidate)) {
                    state.lastWanderGoal = candidate.immutable();
                    return candidate.immutable();
                }
            }
        }
        // Fall back to any nearby standable tile.
        for (int attempt = 0; attempt < 12; attempt++) {
            BlockPos candidate = origin.offset(level.getRandom().nextInt(17) - 8, 0,
                    level.getRandom().nextInt(17) - 8);
            if (FakeSteveNavigator.safeStand(level, candidate)) {
                state.lastWanderGoal = candidate.immutable();
                return candidate.immutable();
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
        boolean derringer = gun && attacker.getMainHandItem().is(TMMItems.DERRINGER);
        if (requireOriginalRolePermission && role != null
                && !(gun ? (derringer ? role.onUseDerringer(attacker) : role.onUseGun(attacker))
                        && role.onGunHit(attacker, target)
                : role.onUseKnife(attacker) && role.onUseKnifeHit(attacker, target)))
            return false;
        if (gun) {
            ItemStack firedGun = attacker.getMainHandItem();
            attacker.level().playSound(null, attacker.blockPosition(), TMMSounds.ITEM_REVOLVER_SHOOT,
                    SoundSource.PLAYERS, 5.0f, 1.0f);
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

    private static boolean tryInteract(ServerLevel level, ServerPlayer body) {
        for (int slot = 0; slot < 9; slot++) {
            var stack = body.getInventory().getItem(slot);
            UseAnim animation = stack.getItem().getUseAnimation(stack);
            if (stack.has(DataComponents.FOOD) || animation == UseAnim.EAT || animation == UseAnim.DRINK) {
                select(body, slot);
                body.gameMode.useItem(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND);
                if (!body.isUsingItem()) {
                    body.startUsingItem(InteractionHand.MAIN_HAND);
                }
                return true;
            }
        }
        BlockPos center = body.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -1, -4), center.offset(4, 2, 4))) {
            if (!(level.getBlockState(pos).getBlock() instanceof PlatterBlock)
                    || body.distanceToSqr(Vec3.atCenterOf(pos)) > 16.0)
                continue;
            int empty = firstEmptyHotbarSlot(body);
            if (empty < 0)
                return false;
            select(body, empty);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos.immutable(), false);
            body.gameMode.useItemOn(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
            body.swing(InteractionHand.MAIN_HAND, true);
            return true;
        }
        return false;
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
                    FakeSteveMotionPolicy.walkingPitch(now, body.getUUID().hashCode()));
            return;
        }
        if (state.path.isEmpty() || changedGoal
                || now >= state.nextPathTick) {
            state.pathGoal = goal.immutable();
            state.path.clear();
            boolean explicitTarget = state.mode == AgentMode.STALK
                    || state.mode == AgentMode.ASSIMILATE;
            state.path.addAll(FakeSteveNavigator.find(level, body, goal, explicitTarget));
            state.nextPathTick = now + 20L;
            state.lastPathDistanceSqr = Double.MAX_VALUE;
            state.lastPathProgressTick = now;
        }
        BlockPos next = state.path.peekFirst();
        if (next == null) {
            if (!body.blockPosition().closerThan(goal, 1.0D)) {
                backOffPath(level, body, state, now);
            }
            FakeSteveMotionController.hold(body, state, body.getYRot(),
                    FakeSteveMotionPolicy.walkingPitch(now, body.getUUID().hashCode()));
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
        if (delta.horizontalDistanceSqr() < 0.01)
            return;
        double distanceSqr = delta.lengthSqr();
        if (distanceSqr < state.lastPathDistanceSqr - 0.15D) {
            state.lastPathDistanceSqr = distanceSqr;
            state.lastPathProgressTick = now;
            state.pathFailureCount = 0;
        } else if (FakeStevePathPolicy.hasStalled(state.lastPathDistanceSqr, distanceSqr,
                state.lastPathProgressTick, now)) {
            backOffPath(level, body, state, now);
            FakeSteveMotionController.hold(body, state, body.getYRot(),
                    FakeSteveMotionPolicy.walkingPitch(now, body.getUUID().hashCode()));
            return;
        }
        Vec3 direction = new Vec3(delta.x, 0.0, delta.z).normalize();
        boolean pursuingHuman = state.mode == AgentMode.STALK;
        boolean psychoActive = SREPlayerPsychoComponent.KEY.get(body).inPsycho();
        List<FakeSteveCrowdAvoidance.NearbyPlayer> nearbyPlayers = psychoActive ? List.of()
                : level.players().stream()
                .filter(player -> player != body && player.isAlive() && !player.isSpectator())
                .filter(player -> !pursuingHuman || state.focusTarget == null
                        || !player.getUUID().equals(state.focusTarget))
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
                    FakeSteveMotionPolicy.walkingPitch(now, body.getUUID().hashCode()));
            return;
        }
        float candidateYaw = (float) (Mth.atan2(-direction.x, direction.z) * Mth.RAD_TO_DEG);
        boolean straight = !avoidance.crowded() && !doorAhead
                && (!state.hasStableRouteYaw
                        || FakeSteveMotionPolicy.isStraightAhead(state.stableRouteYaw, candidateYaw));
        if (!state.hasStableRouteYaw) {
            state.stableRouteYaw = candidateYaw;
            state.hasStableRouteYaw = true;
        } else if (doorAhead) {
            // Doorways are narrow: face the route directly instead of carrying
            // the previous segment's smoothed heading into the door frame.
            state.stableRouteYaw = candidateYaw;
        } else {
            state.stableRouteYaw = FakeSteveMotionPolicy.stableHeading(
                    state.stableRouteYaw, candidateYaw, straight);
        }
        boolean sprint = FakeStevePathPolicy.shouldSprintForPursuit(
                pursuingHuman, psychoActive, avoidance.crowded())
                || (!avoidance.crowded() && (now < state.sprintUntilTick || speed >= 0.22D));
        float requestedStrafe = avoidance.crowded()
                ? state.crowdStrafe : avoidance.strafe();
        float strafe = canStrafePast(level, body, direction, requestedStrafe)
                ? requestedStrafe : 0.0F;
        // Sidestepping off the edge is just as fatal as walking straight off it.
        if (strafe != 0.0F) {
            double side = 1.15D * Math.signum(strafe);
            Vec3 lane = new Vec3(direction.z, 0.0D, -direction.x).multiply(side, 0.0D, side);
            if (!FakeSteveNavigator.stepSafe(level, body.position(), lane)) {
                strafe = 0.0F;
            }
        }
        float forward = strafe == 0.0F && avoidance.crowded()
                ? 0.0F : avoidance.forwardScale();
        // Never step into nothing: an unsupported tile ahead stops the body and
        // forces a new route instead of walking the deck edge into the void.
        if (forward > 0.0F && !FakeSteveNavigator.stepSafe(level, body.position(),
                direction.multiply(1.15D, 0.0D, 1.15D))) {
            forward = 0.0F;
            state.path.clear();
            state.nextPathTick = Math.min(state.nextPathTick, now + 10L);
            state.hasStableRouteYaw = false;
            state.sprintUntilTick = 0L;
        }
        boolean ascends = delta.y > 0.45D;
        boolean jumpsAllowed = SREGameWorldComponent.KEY.get(level).isJumpAvailable();
        boolean jump = FakeStevePathPolicy.shouldJump(jumpsAllowed, body.onGround(), ascends,
                now, state.nextJumpTick)
                || FakeStevePathPolicy.shouldSwimUp(body.isInWater(), body.getY(), next.getY() + 0.1D);
        if (jump && !body.isInWater()) {
            state.nextJumpTick = now + 12L;
        }
        FakeSteveMotionController.drive(body, state, forward, strafe, jump, sprint, false,
                state.stableRouteYaw,
                FakeSteveMotionPolicy.walkingPitch(now, body.getUUID().hashCode(), straight), next);
    }

    private static void backOffPath(ServerLevel level, ServerPlayer body,
                                    FakeSteveAgentState state, long now) {
        state.path.clear();
        state.pathRetryAfterTick = now + 30L + level.getRandom().nextInt(20);
        state.nextPathTick = state.pathRetryAfterTick;
        state.lastPathDistanceSqr = Double.MAX_VALUE;
        state.lastPathProgressTick = now;
        state.pathFailureCount++;
        if (state.pathFailureCount >= 3 && state.mode == AgentMode.DISGUISE_TASK
                && state.taskType != null) {
            state.taskBackoffUntil.put(state.taskType, now + 10L * 20L);
            FakeSteveTaskPlanner.abandon(body, state);
        }
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
        return role == null ? findSlot(player, TMMItems.BAT) : findSlot(player, role.getPsychoItem());
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
            if (stack.is(TMMItems.DERRINGER)
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
            if (!stack.is(TMMItems.DERRINGER)
                    || !stack.getOrDefault(SREDataComponentTypes.USED, false)) {
                return slot;
            }
        }
        return -1;
    }

    private static int findDerringerSlot(ServerPlayer player) {
        return findSlot(player, TMMItems.DERRINGER);
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
