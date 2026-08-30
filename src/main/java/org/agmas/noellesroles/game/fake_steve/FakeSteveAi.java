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

/** Server-side controller for a replaced player body. */
public class FakeSteveAi {
    private static final double FACE_COS = Math.cos(Math.toRadians(30.0));
    private static final ResourceLocation BACKSTAB = GameConstants.DeathReasons.FAKE_AI_BACKSTAB;
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
        if ((now + Math.floorMod(body.getUUID().hashCode(), 5)) % 5L != 0L)
            return;
        holsterKnifeIfReady(body, state, now);

        ServerPlayer focus = player(level, state.focusTarget);
        if (focus != null && (!isHuman(focus) || !GameUtils.isPlayerAliveAndSurvival(focus))) {
            clearFocus(state);
            focus = null;
        }

        if (state.mode != AgentMode.STARE && state.mode != AgentMode.STALK) {
            ServerPlayer facing = facingHuman(level, body);
            if (facing != null) {
                if (facing.getUUID().equals(state.focusTarget)) {
                    state.faceTicks += 5;
                } else {
                    state.focusTarget = facing.getUUID();
                    state.faceTicks = 5;
                }
                if (FakeSteveRules.hasFaceToFaceCommunication(state.faceTicks)) {
                    beginStare(state, facing);
                    focus = facing;
                }
            } else {
                state.faceTicks = 0;
            }
        }

        focus = player(level, state.focusTarget);
        ServerPlayer isolated = FakeSteveDirector.isEnabled() ? isolatedTarget(level, body) : null;
        boolean taskAvailable = FakeSteveTaskPlanner.hasCompletableTask(body, state);
        SRERole originalRole = SREGameWorldComponent.KEY.get(level).getRole(body);
        ServerPlayer prey = originalRole != null && originalRole.canUseKiller()
                ? safestPrey(level, body) : null;
        if (originalRole != null && originalRole.canUseKiller()) {
            prepareKiller(level, body, state, originalRole, prey);
        }
        boolean psychoArmed = originalRole != null && SREPlayerPsychoComponent.KEY.get(body).inPsycho()
                && findPsychoWeaponSlot(body, originalRole) >= 0;
        boolean armed = psychoArmed || findKnifeSlot(body) >= 0 || findGunSlot(body) >= 0;
        boolean interruptTask = prey != null && FakeSteveKillerPolicy.shouldInterruptTask(
                taskAvailable, armed, !hasWitness(level, body, prey), body.distanceTo(prey));
        maybeSpeak(level, body, state);
        boolean targetLooking = focus != null && visible(focus, body)
                && faces(focus, body, FACE_COS);
        boolean safeBackstab = focus != null && body.distanceToSqr(focus) <= 144.0D
                && visible(body, focus) && behind(body, focus)
                && !hasWitness(level, body, focus);
        boolean recovering = state.mode == AgentMode.RECOVER && now < state.nextDecisionTick;

        FakeSteveBrain.BrainIntent intent = state.brain.tick(new FakeSteveBrain.PerceptionSnapshot(
                5, recovering, state.pendingEngagement, focus != null,
                targetLooking, safeBackstab, isolated != null,
                taskAvailable && !interruptTask, prey != null));
        if (!intent.recover()) {
            state.pendingEngagement = false;
        }
        state.mode = intent.mode();
        if (state.mode != AgentMode.DISGUISE_IDLE) {
            state.idleTicks = 0;
        }

        if (intent.recover()) {
            FakeSteveMotionController.hold(body, state, body.getYRot(),
                    FakeSteveMotionPolicy.walkingPitch(now, body.getUUID().hashCode()));
            return;
        }
        if (state.mode == AgentMode.STARE && focus != null) {
            lookAt(body, state, focus.getEyePosition());
            return;
        }
        if (state.mode == AgentMode.STALK && focus != null) {
            if (intent.attack() && backstabAssimilate(body, focus)) {
                clearFocus(state);
                state.mode = AgentMode.RECOVER;
                state.nextDecisionTick = now + 40L;
                return;
            }
            follow(level, body, ambushGoal(focus), state, 0.19D);
            return;
        }
        if (state.mode == AgentMode.ASSIMILATE && isolated != null) {
            state.focusTarget = isolated.getUUID();
            state.assimilationTicks += 5;
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

        if (shouldFlee(level, body) && state.mode != AgentMode.STARE && state.mode != AgentMode.STALK) {
            flee(level, body, state);
            return;
        }

        if (state.mode == AgentMode.DISGUISE_TASK
                && FakeSteveTaskPlanner.tick(level, body, state)) {
            return;
        }
        if (state.mode == AgentMode.HUNT && prey != null) {
            state.focusTarget = prey.getUUID();
            if (tryArmedAttack(level, body, prey, state)) {
                state.mode = AgentMode.RECOVER;
                state.nextDecisionTick = now + 40L;
            } else {
                follow(level, body, ambushGoal(prey), state, 0.20D);
            }
            return;
        }

        state.mode = AgentMode.DISGUISE_IDLE;
        state.idleTicks += 5;
        if (FakeSteveMotionPolicy.shouldSprint(false, state.idleTicks,
                body.getUUID().hashCode() + (int) (now / 20L))) {
            state.sprintUntilTick = Math.max(state.sprintUntilTick, now + 30L + level.getRandom().nextInt(30));
            state.idleTicks = 0;
        }
        if (now >= state.nextDecisionTick) {
            state.nextDecisionTick = now + 40L + level.getRandom().nextInt(80);
            if (!tryInteract(level, body)) {
                state.pathGoal = body.blockPosition().offset(level.getRandom().nextInt(17) - 8,
                        0, level.getRandom().nextInt(17) - 8);
                state.path.clear();
            }
        }
        if (state.pathGoal != null) {
            follow(level, body, state.pathGoal, state, 0.15D);
        } else {
            FakeSteveMotionController.hold(body, state, body.getYRot(),
                    FakeSteveMotionPolicy.walkingPitch(now, body.getUUID().hashCode()));
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
                .filter(FakeSteveDirector::isReplaced).filter(GameUtils::isPlayerAliveAndSurvival)
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
                .filter(GameUtils::isPlayerAliveAndSurvival).filter(p -> p.distanceToSqr(fake) <= 64.0)
                .filter(p -> visible(fake, p) && faces(fake, p, FACE_COS) && faces(p, fake, FACE_COS))
                .min(Comparator.comparingDouble(fake::distanceToSqr)).orElse(null);
    }

    private static ServerPlayer nearestFacingFake(ServerLevel level, ServerPlayer human, double range) {
        return level.players().stream().filter(FakeSteveDirector::isReplaced)
                .filter(GameUtils::isPlayerAliveAndSurvival).filter(p -> p.distanceToSqr(human) <= range * range)
                .filter(p -> visible(p, human) && faces(p, human, FACE_COS) && faces(human, p, FACE_COS))
                .min(Comparator.comparingDouble(human::distanceToSqr)).orElse(null);
    }

    private static ServerPlayer isolatedTarget(ServerLevel level, ServerPlayer body) {
        ServerPlayer nearest = level.players().stream().filter(FakeSteveAi::isHuman)
                .filter(GameUtils::isPlayerAliveAndSurvival).filter(p -> p.distanceToSqr(body) <= 144.0)
                .filter(p -> livingFakesNear(level, p, 12.0) >= 2)
                .filter(p -> otherLivingHumansNear(level, p, 12.0) == 0)
                .min(Comparator.comparingDouble(body::distanceToSqr)).orElse(null);
        if (nearest == null)
            return null;
        ServerPlayer closestFake = level.players().stream().filter(FakeSteveDirector::isReplaced)
                .filter(GameUtils::isPlayerAliveAndSurvival).filter(p -> p.distanceToSqr(nearest) <= 144.0)
                .min(Comparator.comparingDouble(nearest::distanceToSqr)).orElse(null);
        return closestFake == body ? nearest : null;
    }

    private static int livingFakesNear(ServerLevel level, ServerPlayer target, double range) {
        return (int) level.players().stream().filter(FakeSteveDirector::isReplaced)
                .filter(GameUtils::isPlayerAliveAndSurvival)
                .filter(p -> p.distanceToSqr(target) <= range * range).count();
    }

    private static int otherLivingHumansNear(ServerLevel level, ServerPlayer target, double range) {
        return (int) level.players().stream().filter(p -> p != target).filter(FakeSteveAi::isHuman)
                .filter(GameUtils::isPlayerAliveAndSurvival)
                .filter(p -> p.distanceToSqr(target) <= range * range).count();
    }

    private static ServerPlayer safestPrey(ServerLevel level, ServerPlayer body) {
        return level.players().stream().filter(FakeSteveAi::isHuman)
                .filter(GameUtils::isPlayerAliveAndSurvival).filter(p -> p.distanceToSqr(body) <= 324.0)
                .filter(p -> FakeSteveKillerPolicy.canActivelyHunt(
                        FakeSteveDirector.isReplaced(p), isKillerRole(level, p)))
                .filter(p -> !hasWitness(level, body, p))
                .min(Comparator.comparingDouble(body::distanceToSqr)).orElse(null);
    }

    private static boolean tryArmedAttack(ServerLevel level, ServerPlayer body,
                                          ServerPlayer target, FakeSteveAgentState state) {
        SRERole role = SREGameWorldComponent.KEY.get(level).getRole(body);
        if (SREPlayerPsychoComponent.KEY.get(body).inPsycho()) {
            int psychoWeapon = findPsychoWeaponSlot(body, role);
            if (psychoWeapon >= 0 && body.distanceToSqr(target) <= 9.0D
                    && behind(body, target) && !hasWitness(level, body, target)) {
                select(body, psychoWeapon);
                return killWithPsycho(body, target);
            }
        }
        int knife = findKnifeSlot(body);
        if (knife >= 0 && body.distanceToSqr(target) <= 9.0 && behind(body, target)
                && !hasWitness(level, body, target)) {
            if (!target.getUUID().equals(state.knifeChargeTarget)) {
                state.knifeChargeTarget = target.getUUID();
                state.knifeChargedAtTick = level.getGameTime() + 8L;
                state.holsterSlot = body.getInventory().selected == knife
                        ? findSafeHolsterSlot(body) : body.getInventory().selected;
                select(body, knife);
                body.gameMode.useItem(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND);
                if (!body.isUsingItem()) {
                    body.startUsingItem(InteractionHand.MAIN_HAND);
                }
                return false;
            }
            if (!FakeSteveKillerPolicy.canStrikeWithKnife(level.getGameTime(), state.knifeChargedAtTick)) {
                return false;
            }
            select(body, knife);
            if (body.getCooldowns().isOnCooldown(body.getMainHandItem().getItem())) {
                cancelKnifeCharge(body, state);
                return false;
            }
            body.releaseUsingItem();
            cancelKnifeCharge(body, state);
            boolean killed = kill(body, target, false, true);
            if (killed) {
                state.holsterAtTick = level.getGameTime() + 8L;
            }
            return killed;
        }
        cancelKnifeCharge(body, state);
        int gun = findGunSlot(body);
        double distance = body.distanceTo(target);
        if (gun >= 0 && distance >= 4.0 && distance <= 18.0 && visible(body, target)
                && !hasWitness(level, body, target)) {
            select(body, gun);
            if (!faces(body, target, 0.96D)) {
                lookAt(body, state, target.getEyePosition());
                return false;
            }
            if (body.getCooldowns().isOnCooldown(body.getMainHandItem().getItem())) {
                return false;
            }
            return kill(body, target, true, true);
        }
        return false;
    }

    private static boolean kill(ServerPlayer attacker, ServerPlayer target, boolean gun,
            boolean requireOriginalRolePermission) {
        SRERole role = SREGameWorldComponent.KEY.get(attacker.level()).getRole(attacker);
        if (requireOriginalRolePermission && !FakeSteveKillerPolicy.canActivelyHunt(
                FakeSteveDirector.isReplaced(target), isKillerRole(attacker.serverLevel(), target))) {
            return false;
        }
        if (requireOriginalRolePermission && role != null
                && !(gun ? role.onUseGun(attacker) && role.onGunHit(attacker, target)
                : role.onUseKnife(attacker) && role.onUseKnifeHit(attacker, target)))
            return false;
        if (gun) {
            ItemStack firedGun = attacker.getMainHandItem();
            attacker.level().playSound(null, attacker.blockPosition(), TMMSounds.ITEM_REVOLVER_SHOOT,
                    SoundSource.PLAYERS, 5.0f, 1.0f);
            attacker.getCooldowns().addCooldown(attacker.getMainHandItem().getItem(),
                    GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.REVOLVER, 600));
            GameUtils.killPlayer(target, true, attacker, GameConstants.DeathReasons.REVOLVER);
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
            state.path.addAll(FakeSteveNavigator.find(level, body, goal));
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
        openDoor(level, body, next);
        if (body.blockPosition().closerThan(next, 1.0)) {
            state.path.removeFirst();
            next = state.path.peekFirst();
            if (next == null)
                return;
        }
        openDoor(level, body, next);
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
        List<FakeSteveCrowdAvoidance.NearbyPlayer> nearbyPlayers = level.players().stream()
                .filter(player -> player != body && player.isAlive() && !player.isSpectator())
                .filter(player -> player.distanceToSqr(body) <= 16.0D)
                .map(player -> new FakeSteveCrowdAvoidance.NearbyPlayer(player.getX(), player.getZ()))
                .toList();
        FakeSteveCrowdAvoidance.Decision avoidance = FakeSteveCrowdAvoidance.decide(
                body.getX(), body.getZ(), next.getX() + 0.5D, next.getZ() + 0.5D,
                nearbyPlayers, state.crowdedTicks);
        if (avoidance.crowded()) {
            if (state.crowdedTicks == 0) {
                state.crowdStrafe = avoidance.strafe();
            }
            state.crowdedTicks += 5;
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
        if (!state.hasStableRouteYaw) {
            state.stableRouteYaw = candidateYaw;
            state.hasStableRouteYaw = true;
        } else {
            state.stableRouteYaw = FakeSteveMotionPolicy.stableHeading(state.stableRouteYaw, candidateYaw);
        }
        boolean pursuingHuman = state.mode == AgentMode.HUNT || state.mode == AgentMode.STALK;
        boolean psychoActive = SREPlayerPsychoComponent.KEY.get(body).inPsycho();
        boolean sprint = FakeStevePathPolicy.shouldSprintForPursuit(
                pursuingHuman, psychoActive, avoidance.crowded())
                || (!avoidance.crowded() && (now < state.sprintUntilTick || speed >= 0.22D));
        float requestedStrafe = avoidance.crowded()
                ? state.crowdStrafe : avoidance.strafe();
        float strafe = canStrafePast(level, body, direction, requestedStrafe)
                ? requestedStrafe : 0.0F;
        float forward = strafe == 0.0F && avoidance.crowded()
                ? 0.0F : avoidance.forwardScale();
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
                FakeSteveMotionPolicy.walkingPitch(now, body.getUUID().hashCode()), next);
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
            state.taskType = null;
            state.taskGoal = null;
            state.taskInteractTarget = null;
            state.pathGoal = null;
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

    private static void openDoor(ServerLevel level, ServerPlayer body, BlockPos next) {
        for (BlockPos pos : new BlockPos[] { next, next.above() }) {
            var blockState = level.getBlockState(pos);
            if (!FakeSteveDoorAccess.isOpenablePassage(blockState))
                continue;
            if (blockState.getBlock() instanceof SmallDoorBlock door) {
                BlockPos lower = door.getLowerHalfPos(blockState, pos);
                if (level.getBlockEntity(lower) instanceof SmallDoorBlockEntity entity) {
                    var lowerState = level.getBlockState(lower);
                    boolean hardLocked = entity.isJammed() || entity.isBlasted() || hasExternalDoorLock(lower);
                    if (FakeStevePathPolicy.shouldAutoOpenSmallDoor(lowerState.getValue(DoorBlock.OPEN), hardLocked)) {
                        door.toggleDoor(lowerState, level, entity, lower);
                        body.swing(InteractionHand.MAIN_HAND, true);
                    }
                    return;
                }
            }
            if (FakeSteveDoorAccess.isOpen(blockState)) {
                return;
            }
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            body.gameMode.useItemOn(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
            body.swing(InteractionHand.MAIN_HAND, true);
            return;
        }
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
        if (role != null && (!role.onUseKnife(attacker) || !role.onUseKnifeHit(attacker, target))) {
            return false;
        }
        target.playSound(TMMSounds.ITEM_KNIFE_STAB, 1.0F, 1.0F);
        GameUtils.killPlayer(target, true, attacker, BACKSTAB);
        attacker.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    private static boolean backstabAssimilate(ServerPlayer attacker, ServerPlayer target) {
        if (!isHuman(target)) {
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

    private static void prepareKiller(ServerLevel level, ServerPlayer body,
            FakeSteveAgentState state, SRERole role, ServerPlayer prey) {
        long now = level.getGameTime();
        int nearbyHumans = nearbyHumans(level, body, 18.0D);
        if (now >= state.nextShopTick) {
            state.nextShopTick = now + 6L * 20L + level.getRandom().nextInt(6 * 20);
            if (!tryBuyKillerCrowdTools(body, role, nearbyHumans)) {
                tryBuyKillerTool(body, role);
            }
        }
        if (prey == null) {
            return;
        }
        if (now >= state.nextTacticalItemTick) {
            state.nextTacticalItemTick = now + 12L * 20L + level.getRandom().nextInt(12 * 20);
            tryUseTacticalItem(level, body);
        }
        if (now >= state.nextSkillTick && FakeSteveKillerPolicy.shouldUseSkill(
                true, !hasWitness(level, body, prey), prey != null)) {
            state.nextSkillTick = now + 18L * 20L + level.getRandom().nextInt(18 * 20);
            RoleSkill.beginUse(body, prey.getUUID(), -1, RoleSkill.Phase.PRESS, false, true);
        }
    }

    private static void tryBuyKillerTool(ServerPlayer body, SRERole role) {
        List<io.wifi.starrailexpress.util.ShopEntry> entries = ShopContent.getShopEntries(role, body);
        if (entries.isEmpty()) {
            return;
        }
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(body);
        for (FakeSteveKillerPolicy.Purchase desired : FakeSteveKillerPolicy.purchasePriority()) {
            Item priority = switch (desired) {
                case PSYCHO -> TMMItems.PSYCHO_MODE;
                case BLACKOUT -> TMMItems.BLACKOUT;
                case KNIFE -> TMMItems.KNIFE;
                case GUN -> TMMItems.REVOLVER;
            };
            if ((priority == TMMItems.KNIFE || priority == TMMItems.REVOLVER)
                    && (priority == TMMItems.KNIFE ? findKnifeSlot(body) >= 0 : findGunSlot(body) >= 0)) {
                continue;
            }
            if (body.getCooldowns().isOnCooldown(priority)) {
                continue;
            }
            for (int index = 0; index < entries.size(); index++) {
                var entry = entries.get(index);
                if (!entry.stack().is(priority)) {
                    continue;
                }
                int price = DynamicShopComponent.KEY.get(body).effectivePrice(entry);
                if (shop.balance >= price && entry.canDisplay(body) && entry.canBuy(body)) {
                    shop.tryBuy(index);
                    return;
                }
            }
        }
    }

    private static boolean tryBuyKillerCrowdTools(ServerPlayer body, SRERole role, int nearbyHumans) {
        List<FakeSteveKillerPolicy.Purchase> desired = FakeSteveKillerPolicy.crowdPurchasePlan(nearbyHumans);
        if (desired.isEmpty()) {
            return false;
        }
        List<io.wifi.starrailexpress.util.ShopEntry> entries = ShopContent.getShopEntries(role, body);
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(body);
        boolean purchased = false;
        for (FakeSteveKillerPolicy.Purchase purchase : desired) {
            Item item = purchase == FakeSteveKillerPolicy.Purchase.PSYCHO
                    ? TMMItems.PSYCHO_MODE : TMMItems.BLACKOUT;
            if (body.getCooldowns().isOnCooldown(item)) {
                continue;
            }
            for (int index = 0; index < entries.size(); index++) {
                var entry = entries.get(index);
                int price = DynamicShopComponent.KEY.get(body).effectivePrice(entry);
                if (entry.stack().is(item) && shop.balance >= price
                        && entry.canDisplay(body) && entry.canBuy(body)) {
                    shop.tryBuy(index);
                    purchased = true;
                    break;
                }
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
                .filter(GameUtils::isPlayerAliveAndSurvival)
                .filter(player -> player.distanceToSqr(body) <= range * range).count();
    }

    private static boolean shouldFlee(ServerLevel level, ServerPlayer body) {
        if (body.hurtTime > 0) {
            return true;
        }
        return level.players().stream().filter(FakeSteveAi::isHuman)
                .filter(GameUtils::isPlayerAliveAndSurvival)
                .filter(player -> player.distanceToSqr(body) <= 36.0D)
                .filter(player -> findKnifeSlot(player) >= 0 || findGunSlot(player) >= 0)
                .filter(player -> faces(player, body, 0.5D))
                .anyMatch(player -> visible(body, player));
    }

    private static void flee(ServerLevel level, ServerPlayer body, FakeSteveAgentState state) {
        ServerPlayer threat = level.players().stream().filter(FakeSteveAi::isHuman)
                .filter(GameUtils::isPlayerAliveAndSurvival)
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
                .filter(GameUtils::isPlayerAliveAndSurvival)
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
                .filter(FakeSteveAi::isHuman).filter(GameUtils::isPlayerAliveAndSurvival)
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

    private static BlockPos ambushGoal(ServerPlayer target) {
        Vec3 look = target.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.lengthSqr() < 0.01D) {
            return target.blockPosition();
        }
        return BlockPos.containing(target.position().subtract(horizontal.normalize().scale(2.0D)));
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
        return !FakeSteveDirector.isReplaced(player);
    }

    private static boolean isKillerRole(ServerLevel level, ServerPlayer player) {
        SRERole role = SREGameWorldComponent.KEY.get(level).getRole(player);
        return role != null && role.canUseKiller();
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
        state.path.clear();
    }
}
