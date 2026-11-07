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
import io.wifi.starrailexpress.cca.AreasWorldComponent;
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

    /** 固定优先级调度，每次只允许一个行为提交移动意图。 */
    static void tick(ServerLevel level, ServerPlayer body, FakeSteveAgentState state) {
        long now = level.getGameTime();
        SRERole role = SREGameWorldComponent.KEY.get(level).getRole(body);
        boolean psycho = role != null && SREPlayerPsychoComponent.KEY.get(body).inPsycho();
        boolean derringer = findDerringerSlot(body) >= 0;
        boolean berserk = psycho || derringer || FakeSteveDirector.isHuntPhase(level);
        int cadence = berserk ? 1 : 2;
        if (Math.floorMod(now + body.getId(), cadence) != 0) return;
        state.tickStep = state.lastTickAt == 0 ? cadence : (int) Math.min(20, Math.max(1, now - state.lastTickAt));
        state.lastTickAt = now;
        holsterKnifeIfReady(body, state, now);
        if (state.mode == AgentMode.RECOVER && now < state.nextDecisionTick) {
            idleHold(level, body, state, now);
            return;
        }
        ServerPlayer focus = engageable(level, state.focusTarget);
        if (focus != null && (body.distanceToSqr(focus) > 48.0D * 48.0D || !isHuman(focus)
                || focus.getUUID().equals(state.unreachableTarget) && now < state.unreachableTargetUntil)) {
            focus = null;
        }
        if (focus == null && state.focusTarget != null) {
            clearFocus(state);
            state.brain.disengage();
        }
        if (berserk) {
            clearFocus(state);
            state.brain.disengage();
        } else if (now >= state.nextSocialTick && state.mode != AgentMode.STARE && state.mode != AgentMode.STALK) {
            ServerPlayer facing = facingHuman(level, body);
            if (facing != null) {
                if (!facing.getUUID().equals(state.focusTarget)) state.faceTicks = 0;
                state.focusTarget = facing.getUUID();
                state.faceTicks += state.tickStep;
                if (FakeSteveRules.hasFaceToFaceCommunication(state.faceTicks)) beginStare(state, facing);
            } else {
                state.faceTicks = 0;
            }
        }
        focus = engageable(level, state.focusTarget);
        boolean killerRole = role != null && role.canUseKiller();
        boolean armed = psycho && findPsychoWeaponSlot(body, role) >= 0
                || findUsableKnifeSlot(body) >= 0 || findUsableGunSlot(body) >= 0;
        ServerPlayer prey = killerRole || berserk
                ? choosePrey(level, body, state, now, derringer, armed, berserk) : null;
        concealWeaponIfExposed(level, body, state, berserk);
        if (!berserk) {
            prepareShop(level, body, state, role, killerRole, prey, psycho);
            maybeSpeak(level, body, state);
        }
        if (prey != null && (berserk || state.mode != AgentMode.STARE)) {
            int strike = tryArmedAttack(level, body, prey, state, psycho, derringer);
            if (strike == STRIKE_BUSY) return;
            if (strike == STRIKE_KILLED) {
                cancelKnifeCharge(body, state);
                clearFocus(state);
                state.committedTarget = null;
                state.brain.disengage();
                setMode(state, AgentMode.RECOVER, now);
                state.nextDecisionTick = now + FakeSteveKillerPolicy.recoveryTicksAfterKill(berserk,
                        findUsableKnifeSlot(body) >= 0 && findUsableGunSlot(body) >= 0);
                idleHold(level, body, state, now);
                return;
            }
        }
        boolean huntReady = prey != null && (berserk || armed && isOpportunity(level, body, prey, true));
        ServerPlayer isolated = !berserk ? isolatedTarget(level, body) : null;
        if (isolated != null && isolated.getUUID().equals(state.unreachableTarget)
                && now < state.unreachableTargetUntil) isolated = null;
        boolean targetLooking = focus != null && body.distanceToSqr(focus) < 64.0D
                && visible(focus, body) && faces(focus, body, FACE_COS);
        boolean backstab = focus != null && body.distanceToSqr(focus) <= 4.0D
                && visible(body, focus) && behind(body, focus) && !witnessed(level, body, focus, berserk);
        var intent = state.brain.tick(new FakeSteveBrain.PerceptionSnapshot(state.tickStep, false,
                state.pendingEngagement, focus != null, targetLooking, backstab,
                isolated != null, !berserk && now >= state.patrolUntilTick
                        && FakeSteveTaskPlanner.hasCompletableTask(body, state), huntReady));
        state.pendingEngagement = false;
        setMode(state, intent.mode(), now);
        if (state.mode == AgentMode.HUNT && prey != null) {
            pursue(level, body, state, prey, 0.26D);
            return;
        }
        if (state.mode == AgentMode.STARE && focus != null) {
            if (now - state.modeStartedTick > 160L) {
                clearFocus(state);
                state.brain.disengage();
                state.nextSocialTick = now + 200L;
                setMode(state, AgentMode.DISGUISE_IDLE, now);
            } else {
                lookAt(body, state, focus.getEyePosition());
                return;
            }
        }
        if (state.mode == AgentMode.STALK && focus != null) {
            if (intent.attack() && backstabAssimilate(body, focus)) {
                clearFocus(state);
                state.brain.disengage();
                setMode(state, AgentMode.RECOVER, now);
                state.nextDecisionTick = now + 40L;
                idleHold(level, body, state, now);
            } else {
                pursue(level, body, state, focus, 0.22D);
            }
            return;
        }
        if (state.mode == AgentMode.ASSIMILATE && isolated != null) {
            if (!isolated.getUUID().equals(state.focusTarget)) state.assimilationTicks = 0;
            state.focusTarget = isolated.getUUID();
            if (body.distanceToSqr(isolated) <= 9.0D && visible(body, isolated)) {
                state.assimilationTicks += state.tickStep;
                lookAt(body, state, isolated.getEyePosition());
                if (FakeSteveRules.canAssimilate(livingFakesNear(level, isolated, 12),
                        otherLivingHumansNear(level, isolated, 12), state.assimilationTicks)) {
                    FakeSteveDirector.replace(isolated, ReplacementCause.ASSIMILATION);
                    clearFocus(state);
                }
            } else {
                state.assimilationTicks = 0;
                follow(level, body, isolated.blockPosition(), state, 0.18D);
            }
            return;
        }
        state.assimilationTicks = 0;
        if (!berserk && shouldFlee(level, body, false)) {
            flee(level, body, state);
            return;
        }
        if (state.mode == AgentMode.DISGUISE_TASK && FakeSteveTaskPlanner.tick(level, body, state)) return;
        patrol(level, body, state, now, berserk);
    }

    private static void setMode(FakeSteveAgentState state, AgentMode mode, long now) {
        if (state.mode == mode) return;
        state.mode = mode;
        state.modeStartedTick = now;
        state.path.clear();
        state.pathGoal = null;
        state.lastMoveTick = 0;
        state.plannedGoal = null;
        state.navigationNode = null;
        state.nextPathTick = now;
        state.pathFailureCount = 0;
    }

    /** 接近至攻击/交互距离即停步，不把目标身体当作必须穿过的路点。 */
    private static void pursue(ServerLevel level, ServerPlayer body, FakeSteveAgentState state,
            ServerPlayer target, double speed) {
        if (body.distanceToSqr(target) <= 2.8D && visible(body, target)) {
            lookAt(body, state, target.getEyePosition());
        } else {
            follow(level, body, target.blockPosition(), state, speed);
        }
    }

    /** 普通巡逻目的地保持到抵达、超时或确认不可达，而非每几秒随机掉头。 */
    private static void patrol(ServerLevel level, ServerPlayer body, FakeSteveAgentState state,
            long now, boolean hurried) {
        setMode(state, AgentMode.DISGUISE_IDLE, now);
        boolean arrived = state.pathGoal != null
                && body.distanceToSqr(FakeSteveNavigator.point(level, state.pathGoal)) < 1.0D;
        if (arrived) {
            state.lastWanderGoal = state.pathGoal;
            state.pathGoal = null;
            state.path.clear();
            state.nextDecisionTick = now + (hurried ? 2 : 15 + Math.floorMod(body.getId(), 20));
            state.patrolUntilTick = now;
        }
        if (state.pathGoal == null && now >= state.nextDecisionTick) {
            state.pathGoal = wanderGoal(level, body, state);
            state.nextDecisionTick = now + 40L;
            state.patrolUntilTick = now + 400L;
            state.modeStartedTick = now;
            if (state.pathGoal != null) state.lastWanderGoal = state.pathGoal;
        }
        if (state.pathGoal != null && now - state.modeStartedTick > 600L) {
            state.unreachableGoals.put(state.pathGoal, now + 200L);
            state.pathGoal = null;
            state.path.clear();
            state.nextDecisionTick = now;
        }
        if (state.pathGoal != null) follow(level, body, state.pathGoal, state, hurried ? 0.24D : 0.16D);
        else idleHold(level, body, state, now);
    }

    /** 失心症只复用新导航，不触发伪人的凝视、感染和攻击。 */
    public static void tickWanderOnly(ServerLevel level, ServerPlayer body, FakeSteveAgentState state) {
        long now = level.getGameTime();
        state.tickStep = state.lastTickAt == 0 ? 5 : (int) Math.max(1, Math.min(20, now - state.lastTickAt));
        state.lastTickAt = now;
        FakeSteveMotionController.applyServerMotion(body, state);
        patrol(level, body, state, now, false);
    }

    /** 怯懦只复用新导航，逃跑点在有效期间保持不变。 */
    public static void tickFlee(ServerLevel level, ServerPlayer body, FakeSteveAgentState state, BlockPos awayFrom) {
        long now = level.getGameTime();
        state.tickStep = state.lastTickAt == 0 ? 5 : (int) Math.max(1, Math.min(20, now - state.lastTickAt));
        state.lastTickAt = now;
        state.mode = AgentMode.DISGUISE_IDLE;
        FakeSteveMotionController.applyServerMotion(body, state);
        if (state.pathGoal == null) state.pathGoal = fleeGoal(level, body, state, awayFrom);
        if (state.pathGoal != null) follow(level, body, state.pathGoal, state, 0.28D);
        else idleHold(level, body, state, now);
    }

    public static boolean hasReachedFleeGoal(ServerPlayer body, FakeSteveAgentState state) {
        return body != null && state != null && state.pathGoal != null
                && body.distanceToSqr(FakeSteveNavigator.point(body.serverLevel(), state.pathGoal)) < 2.25D;
    }

    private static BlockPos fleeGoal(ServerLevel level, ServerPlayer body, FakeSteveAgentState state,
            BlockPos awayFrom) {
        Vec3 away = body.position().subtract(Vec3.atCenterOf(awayFrom == null ? body.blockPosition() : awayFrom));
        if (away.horizontalDistanceSqr() < 0.01D) away = new Vec3(1, 0, 0);
        away = new Vec3(away.x, 0, away.z).normalize();
        for (int distance : new int[] {12, 8, 4}) {
            BlockPos candidate = BlockPos.containing(body.position().add(away.scale(distance)));
            if (FakeSteveNavigator.safeStand(level, candidate)) return FakeSteveNavigator.normalize(level, candidate);
        }
        return wanderGoal(level, body, state);
    }

    private static void idleHold(ServerLevel level, ServerPlayer body, FakeSteveAgentState state, long now) {
        // 以固定锚点小幅环顾，不能把每次扫视结果当成新锚点而累积转圈。
        if (!state.hasStableRouteYaw) {
            state.stableRouteYaw = body.getYRot();
            state.hasStableRouteYaw = true;
        }
        FakeSteveMotionController.hold(body, state,
                FakeSteveMotionPolicy.idleScanYaw(now, body.getUUID().hashCode(), state.stableRouteYaw),
                FakeSteveMotionPolicy.walkingPitch(now, body.getUUID().hashCode()));
    }

    /** 会议或手动控制结束后重新规划，不把暂停时间计为卡住。 */
    static void resetStuckTracking(ServerPlayer body, FakeSteveAgentState state, long now) {
        if (state == null) return;
        state.path.clear();
        state.plannedGoal = null;
        state.navigationNode = null;
        state.navigationProgressTick = now;
        state.nextPathTick = now;
        state.pathFailureCount = 0;
        state.lastMoveTick = 0;
        state.crowdedTicks = 0;
        state.crowdStrafe = 0;
        state.hasStableRouteYaw = false;
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
                .filter(FakeSteveAi::isEngageable).filter(p -> p.distanceToSqr(body) <= 144.0 && visible(body, p))
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

    private static boolean isPrey(ServerLevel level, ServerPlayer player) {
        if (FakeSteveDirector.isHuntPhase(level)) return isHuman(player) && isEngageable(player);
        return FakeSteveKillerPolicy.canActivelyHunt(FakeSteveDirector.isReplaced(player),
                isKillerRole(level, player), isKillerNeutral(level, player));
    }

    /** 目标承诺、不可达退避及群体分工；昂贵的机会评估最多每半秒一次。 */
    private static ServerPlayer choosePrey(ServerLevel level, ServerPlayer body,
            FakeSteveAgentState state, long now, boolean derringer, boolean armed, boolean berserk) {
        ServerPlayer committed = engageable(level, state.committedTarget);
        double range = FakeSteveKillerPolicy.seekRadiusSqr(berserk, derringer);
        if (committed != null && (!isPrey(level, committed) || !isHuman(committed)
                || body.distanceToSqr(committed) > range * 2.25D
                || committed.getUUID().equals(state.unreachableTarget) && now < state.unreachableTargetUntil)) {
            committed = null;
            state.committedTarget = null;
        }
        if (now < state.nextPerceptionTick) return committed;
        state.nextPerceptionTick = now + 10L;
        if (committed != null && now < state.committedUntilTick) return committed;
        ServerPlayer selected = level.players().stream().filter(FakeSteveAi::isHuman)
                .filter(FakeSteveAi::isEngageable).filter(p -> isPrey(level, p))
                .filter(p -> !p.getUUID().equals(state.unreachableTarget) || now >= state.unreachableTargetUntil)
                .filter(p -> body.distanceToSqr(p) <= range)
                .filter(p -> berserk || isOpportunity(level, body, p, armed)
                        || body.distanceToSqr(p) <= FakeSteveKillerPolicy.STRIKE_RADIUS_SQR)
                .min(Comparator.comparingDouble(p -> preyScore(level, body, p))).orElse(null);
        if (committed != null && (selected == null
                || preyScore(level, body, selected) >= preyScore(level, body, committed) * 0.45D)) {
            commitPrey(state, committed, now);
            return committed;
        }
        if (selected != null) commitPrey(state, selected, now);
        else state.committedTarget = null;
        return selected;
    }

    private static double preyScore(ServerLevel level, ServerPlayer body, ServerPlayer target) {
        long chasers = level.players().stream().filter(p -> p != body && isEngageable(p))
                .map(p -> FakeSteveDirector.agent(level, p.getUUID()))
                .filter(agent -> agent != null && target.getUUID().equals(agent.committedTarget)).count();
        return body.distanceToSqr(target) + chasers * 24.0D;
    }

    private static boolean isOpportunity(ServerLevel level, ServerPlayer body, ServerPlayer target, boolean armed) {
        return FakeSteveKillerPolicy.isKillOpportunity(witnessed(level, body, target, false) ? 1 : 0,
                otherLivingHumansNear(level, target, 12.0) + 1, armed);
    }

    private static void commitPrey(FakeSteveAgentState state, ServerPlayer prey, long now) {
        state.committedTarget = prey.getUUID();
        state.committedUntilTick = now + PREY_COMMIT_TICKS;
    }

    /** 从任务区域、历史活动区域和附近地面中挑选真正可达的巡逻目的地。 */
    private static BlockPos wanderGoal(ServerLevel level, ServerPlayer body, FakeSteveAgentState state) {
        long now = level.getGameTime();
        state.unreachableGoals.entrySet().removeIf(entry -> entry.getValue() <= now);
        List<BlockPos> candidates = new ArrayList<>();
        if (GameUtils.taskBlocks != null) {
            for (BlockPos pos : GameUtils.taskBlocks.keySet()) {
                if (body.distanceToSqr(Vec3.atCenterOf(pos)) > 48.0D * 48.0D) continue;
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    BlockPos stand = FakeSteveNavigator.normalize(level, pos.relative(direction));
                    if (FakeSteveNavigator.standable(level, stand)) {
                        candidates.add(stand);
                        break;
                    }
                }
            }
        }
        if (level.dimension().equals(state.trailDimension)) {
            int stride = Math.max(1, state.trail.size() / 16);
            for (int i = 0; i < state.trail.size(); i += stride) candidates.add(state.trail.get(i));
        }
        for (int i = 0; i < 16; i++) {
            candidates.add(body.blockPosition().offset(level.random.nextInt(25) - 12, 0,
                    level.random.nextInt(25) - 12));
        }
        // 候选点先用一次「便宜的」站立判定筛掉，再按距离排序取最近的几个。
        // 注意这里不能对每个候选都跑一次完整 A*：旧实现最多会连跑 4 次全量搜索，
        // 每次都要读上万次方块状态，是伪人 AI 最贵的一条路径。
        List<BlockPos> ordered = candidates.stream()
                .map(pos -> FakeSteveNavigator.normalize(level, pos)).distinct()
                .filter(pos -> body.distanceToSqr(Vec3.atCenterOf(pos)) >= 9.0D
                        && body.distanceToSqr(Vec3.atCenterOf(pos)) <= 48.0D * 48.0D)
                .filter(pos -> state.unreachableGoals.getOrDefault(pos, 0L) <= now)
                .filter(pos -> state.lastWanderGoal == null || !pos.closerThan(state.lastWanderGoal, 3))
                .filter(pos -> FakeSteveNavigator.standable(level, pos))
                .sorted(Comparator.comparingDouble(pos -> Math.abs(body.distanceToSqr(Vec3.atCenterOf(pos)) - 144)
                        + Math.floorMod(pos.hashCode() ^ body.getId() ^ (int) (now / 200), 80)))
                .limit(3).toList();
        // 共享同一个 SearchWorld：候选之间大量方块重叠，缓存可以复用，
        // 也让「失败的候选」不会重复付出整份方块读取成本。
        FakeSteveNavigator.SearchWorld world = new FakeSteveNavigator.SearchWorld(level, body);
        for (BlockPos candidate : ordered) {
            FakeSteveNavigator.Result result =
                    FakeSteveNavigator.search(world, candidate, false);
            if (result.reached()) {
                state.path.clear();
                state.path.addAll(result.path());
                state.pathReached = true;
                state.pathSearchVisited = result.visited();
                state.plannedGoal = candidate;
                state.nextPathTick = now + 30;
                state.navigationNode = null;
                state.pathFailureCount = 0;
                return candidate;
            }
            state.unreachableGoals.put(candidate, now + 200L);
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

    /** 所有行为共用导航执行器；不再直接驱动历史轨迹或用传送掩盖寻路失败。 */
    static void follow(ServerLevel level, ServerPlayer body, BlockPos goal,
            FakeSteveAgentState state, double speed) {
        if (goal != null) FakeSteveNavigation.follow(level, body, goal, state, speed);
    }
    static boolean openDoorsOnApproach(ServerLevel level, ServerPlayer body, BlockPos next) {
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
        return !GameUtils.isPlayerAliveAndSurvival(target);
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
        long now = level.getGameTime();
        if (state.fleeDestination == null || now >= state.fleeUntilTick) {
            ServerPlayer threat = level.players().stream().filter(FakeSteveAi::isHuman)
                    .filter(FakeSteveAi::isEngageable)
                    .min(Comparator.comparingDouble(body::distanceToSqr)).orElse(null);
            if (threat == null) {
                idleHold(level, body, state, now);
                return;
            }
            state.fleeDestination = fleeGoal(level, body, state, threat.blockPosition());
            state.fleeUntilTick = now + 60L;
        }
        state.sprintUntilTick = now + 20;
        if (state.fleeDestination != null) follow(level, body, state.fleeDestination, state, 0.24D);
        else idleHold(level, body, state, now);
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
        ServerPlayer player = id == null ? null : level.getServer().getPlayerList().getPlayer(id);
        return player != null && player.serverLevel() == level ? player : null;
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
        long now = target.serverLevel().getGameTime();
        if (now < state.nextSocialTick || state.mode == AgentMode.HUNT) return;
        state.focusTarget = target.getUUID();
        state.pendingEngagement = true;
        state.faceTicks = 0;
        state.nextSocialTick = now + 300L;
    }

    private static void clearFocus(FakeSteveAgentState state) {
        state.focusTarget = null;
        state.pendingEngagement = false;
        state.faceTicks = 0;
        state.assimilationTicks = 0;
        state.ambushGoal = null;
        state.ambushTarget = null;
    }
}
