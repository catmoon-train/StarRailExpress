package org.agmas.noellesroles.game.fake_steve;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.block.PlatterBlock;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;

import java.util.Comparator;
import java.util.UUID;

/** Server-side controller for a replaced player body. */
public class FakeSteveAi {
    private static final double FACE_COS = Math.cos(Math.toRadians(30.0));
    private static final ResourceLocation BACKSTAB = Noellesroles.id("fake_steve_backstab");
    private static boolean registered;

    private FakeSteveAi() {
    }

    static void register() {
        if (registered)
            return;
        registered = true;
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, bound) -> {
            onChat(sender);
            return true;
        });
    }

    static void tick(ServerLevel level, ServerPlayer body, FakeSteveAgentState state) {
        long now = level.getGameTime();
        if ((now + Math.floorMod(body.getUUID().hashCode(), 5)) % 5L != 0L)
            return;

        ServerPlayer focus = player(level, state.focusTarget);
        if (focus != null && (!isHuman(focus) || !GameUtils.isPlayerAliveAndSurvival(focus))) {
            clearFocus(state);
            focus = null;
        }

        ServerPlayer isolated = FakeSteveDirector.isEnabled() ? isolatedTarget(level, body) : null;
        if (isolated != null) {
            state.mode = AgentMode.ASSIMILATE;
            state.focusTarget = isolated.getUUID();
            state.assimilationTicks += 5;
            lookAt(body, isolated.getEyePosition());
            if (FakeSteveRules.canAssimilate(livingFakesNear(level, isolated, 12.0),
                    otherLivingHumansNear(level, isolated, 12.0), state.assimilationTicks)) {
                FakeSteveDirector.replace(isolated, ReplacementCause.ASSIMILATION);
                clearFocus(state);
            }
            return;
        }
        state.assimilationTicks = 0;

        if (state.mode == AgentMode.STARE && focus != null) {
            lookAt(body, focus.getEyePosition());
            if (visible(body, focus))
                state.lostSightTicks = 0;
            else if ((state.lostSightTicks += 5) >= 20) {
                state.mode = AgentMode.STALK;
                state.path.clear();
            }
            return;
        }

        if (state.mode == AgentMode.STALK && focus != null) {
            if (body.distanceToSqr(focus) <= 144.0 && visible(body, focus)
                    && behind(body, focus) && !hasWitness(level, body, focus)) {
                kill(body, focus, false);
                clearFocus(state);
                state.mode = AgentMode.RECOVER;
                state.nextDecisionTick = now + 40L;
                return;
            }
            follow(level, body, focus.blockPosition(), state, 0.19);
            return;
        }

        if (state.mode == AgentMode.RECOVER && now < state.nextDecisionTick)
            return;

        ServerPlayer facing = facingHuman(level, body);
        if (facing != null) {
            if (facing.getUUID().equals(state.focusTarget))
                state.faceTicks += 5;
            else {
                state.focusTarget = facing.getUUID();
                state.faceTicks = 5;
            }
            if (FakeSteveRules.hasFaceToFaceCommunication(state.faceTicks)) {
                beginStare(state, facing);
                return;
            }
        } else
            state.faceTicks = 0;

        SRERole originalRole = SREGameWorldComponent.KEY.get(level).getRole(body);
        if (originalRole != null && originalRole.canUseKiller()) {
            ServerPlayer prey = safestPrey(level, body);
            if (prey != null) {
                state.mode = AgentMode.HUNT;
                state.focusTarget = prey.getUUID();
                if (tryArmedAttack(level, body, prey)) {
                    state.mode = AgentMode.RECOVER;
                    state.nextDecisionTick = now + 40L;
                } else
                    follow(level, body, prey.blockPosition(), state, 0.20);
                return;
            }
        }

        state.mode = AgentMode.ROAM;
        if (now >= state.nextDecisionTick) {
            state.nextDecisionTick = now + 40L + level.getRandom().nextInt(80);
            if (!tryInteract(level, body)) {
                state.pathGoal = body.blockPosition().offset(level.getRandom().nextInt(17) - 8,
                        0, level.getRandom().nextInt(17) - 8);
                state.path.clear();
            }
        }
        if (state.pathGoal != null)
            follow(level, body, state.pathGoal, state, 0.15);
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

    private static void onChat(ServerPlayer sender) {
        if (!FakeSteveDirector.isActive(sender.serverLevel()) || !isHuman(sender))
            return;
        ServerPlayer nearest = sender.serverLevel().players().stream()
                .filter(FakeSteveDirector::isReplaced).filter(GameUtils::isPlayerAliveAndSurvival)
                .filter(fake -> fake.distanceToSqr(sender) <= 64.0)
                .filter(fake -> sender.hasLineOfSight(fake) && faces(sender, fake, FACE_COS))
                .min(Comparator.comparingDouble(sender::distanceToSqr)).orElse(null);
        if (nearest != null) {
            FakeSteveAgentState state = FakeSteveDirector.agent(nearest.serverLevel(), nearest.getUUID());
            if (state != null)
                beginStare(state, sender);
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
                .filter(p -> !hasWitness(level, body, p))
                .min(Comparator.comparingDouble(body::distanceToSqr)).orElse(null);
    }

    private static boolean tryArmedAttack(ServerLevel level, ServerPlayer body, ServerPlayer target) {
        int knife = findSlot(body, TMMItems.KNIFE);
        if (knife >= 0 && body.distanceToSqr(target) <= 9.0 && behind(body, target)
                && !hasWitness(level, body, target)) {
            select(body, knife);
            return kill(body, target, false);
        }
        int gun = findGunSlot(body);
        double distance = body.distanceTo(target);
        if (gun >= 0 && distance >= 4.0 && distance <= 18.0 && visible(body, target)
                && !hasWitness(level, body, target)) {
            select(body, gun);
            return kill(body, target, true);
        }
        return false;
    }

    private static boolean kill(ServerPlayer attacker, ServerPlayer target, boolean gun) {
        SRERole role = SREGameWorldComponent.KEY.get(attacker.level()).getRole(attacker);
        if (role != null && !(gun ? role.onUseGun(attacker) && role.onGunHit(attacker, target)
                : role.onUseKnife(attacker) && role.onUseKnifeHit(attacker, target)))
            return false;
        if (gun) {
            attacker.level().playSound(null, attacker.blockPosition(), TMMSounds.ITEM_REVOLVER_SHOOT,
                    SoundSource.PLAYERS, 5.0f, 1.0f);
            attacker.getCooldowns().addCooldown(attacker.getMainHandItem().getItem(),
                    GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.REVOLVER, 600));
            GameUtils.killPlayer(target, true, attacker, GameConstants.DeathReasons.REVOLVER);
        } else {
            target.playSound(TMMSounds.ITEM_KNIFE_STAB, 1.0f, 1.0f);
            attacker.getCooldowns().addCooldown(TMMItems.KNIFE,
                    GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.KNIFE, 600));
            GameUtils.killPlayer(target, true, attacker, BACKSTAB);
        }
        attacker.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    private static boolean tryInteract(ServerLevel level, ServerPlayer body) {
        for (int slot = 0; slot < 9; slot++) {
            var stack = body.getInventory().getItem(slot);
            UseAnim animation = stack.getItem().getUseAnimation(stack);
            if (stack.has(DataComponents.FOOD) || animation == UseAnim.EAT || animation == UseAnim.DRINK) {
                select(body, slot);
                body.gameMode.useItem(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND);
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
            return true;
        }
        return false;
    }

    private static void follow(ServerLevel level, ServerPlayer body, BlockPos goal,
            FakeSteveAgentState state, double speed) {
        long now = level.getGameTime();
        if (state.path.isEmpty() || state.pathGoal == null || !state.pathGoal.closerThan(goal, 3.0)
                || now >= state.nextPathTick) {
            state.pathGoal = goal.immutable();
            state.path.clear();
            state.path.addAll(FakeSteveNavigator.find(level, body.blockPosition(), goal));
            state.nextPathTick = now + 20L;
        }
        BlockPos next = state.path.peekFirst();
        if (next == null)
            return;
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
        Vec3 step = new Vec3(delta.x, 0.0, delta.z).normalize().scale(speed);
        double targetY = Math.abs(delta.y) <= 1.1 ? Mth.clamp(delta.y, -0.25, 0.25) : 0.0;
        float yaw = (float) (Mth.atan2(-step.x, step.z) * Mth.RAD_TO_DEG);
        body.connection.teleport(body.getX() + step.x, body.getY() + targetY,
                body.getZ() + step.z, yaw, body.getXRot());
        body.setYHeadRot(yaw);
        body.setYBodyRot(yaw);
    }

    private static void openDoor(ServerLevel level, ServerPlayer body, BlockPos next) {
        for (BlockPos pos : new BlockPos[] { next, next.above() }) {
            if (!(level.getBlockState(pos).getBlock() instanceof DoorBlock))
                continue;
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            body.gameMode.useItemOn(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
            return;
        }
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

    private static void lookAt(ServerPlayer body, Vec3 position) {
        Vec3 delta = position.subtract(body.getEyePosition());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) (Mth.atan2(-delta.x, delta.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);
        body.connection.teleport(body.getX(), body.getY(), body.getZ(), yaw, pitch);
        body.setYHeadRot(yaw);
        body.setYBodyRot(yaw);
    }

    private static int findSlot(ServerPlayer player, Item item) {
        for (int i = 0; i < 9; i++)
            if (player.getInventory().getItem(i).is(item))
                return i;
        return -1;
    }

    private static int findGunSlot(ServerPlayer player) {
        int slot = findSlot(player, TMMItems.REVOLVER);
        return slot >= 0 ? slot : findSlot(player, TMMItems.STANDARD_REVOLVER);
    }

    private static int firstEmptyHotbarSlot(ServerPlayer player) {
        for (int i = 0; i < 9; i++)
            if (player.getInventory().getItem(i).isEmpty())
                return i;
        return -1;
    }

    private static void select(ServerPlayer player, int slot) {
        player.getInventory().selected = slot;
        player.connection.send(new ClientboundSetCarriedItemPacket(slot));
    }

    private static ServerPlayer player(ServerLevel level, UUID id) {
        return id == null ? null : level.getServer().getPlayerList().getPlayer(id);
    }

    private static boolean isHuman(ServerPlayer player) {
        return !FakeSteveDirector.isReplaced(player);
    }

    private static void beginStare(FakeSteveAgentState state, ServerPlayer target) {
        state.mode = AgentMode.STARE;
        state.focusTarget = target.getUUID();
        state.lostSightTicks = 0;
        state.faceTicks = 0;
        state.path.clear();
    }

    private static void clearFocus(FakeSteveAgentState state) {
        state.focusTarget = null;
        state.faceTicks = 0;
        state.lostSightTicks = 0;
        state.assimilationTicks = 0;
        state.path.clear();
    }
}
