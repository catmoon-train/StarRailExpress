package org.agmas.noellesroles.game.c4;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.cca.C4BackComponent;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 钳子拆除管理器 - 管理C4拆除过程
 */
public final class PliersDefuseManager {
    private static final int DEFUSE_TICKS = 60;
    private static final double MAX_DEFUSE_DISTANCE_SQ = 4.0D * 4.0D;
    private static final Map<UUID, DefuseAttempt> ATTEMPTS = new HashMap<>();

    private PliersDefuseManager() {}

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(PliersDefuseManager::tick);
    }

    public static InteractionResult beginPlayerDefuse(ItemStack stack, Player user, Player target, InteractionHand hand) {
        if (!(user instanceof ServerPlayer serverUser)) return InteractionResult.SUCCESS;
        if (target == null) return InteractionResult.PASS;
        C4BackComponent comp = C4BackComponent.KEY.getNullable(serverUser.level());
        if (comp == null || !comp.hasC4(target.getUUID())) return InteractionResult.FAIL;
        if (!stack.is(ModItems.PLIERS)) return InteractionResult.FAIL;
        start(serverUser, hand, target.getUUID(), null);
        return InteractionResult.CONSUME;
    }

    public static InteractionResult beginBlockDefuse(ServerPlayer user, ItemStack stack, ItemEntity charge, InteractionHand hand) {
        if (user == null || charge == null || !C4Detonation.isDefusableBlockCharge(charge)) return InteractionResult.PASS;
        if (!stack.is(ModItems.PLIERS)) return InteractionResult.FAIL;
        start(user, hand, null, charge.getUUID());
        return InteractionResult.CONSUME;
    }

    private static void start(ServerPlayer user, InteractionHand hand, UUID playerTargetId, UUID blockChargeId) {
        ATTEMPTS.put(user.getUUID(), new DefuseAttempt(user.getUUID(), hand, playerTargetId, blockChargeId,
            user.position(), user.level().getGameTime()));
        user.stopUsingItem();
        user.displayClientMessage(Component.translatable("c4.defusing"), true);
        user.level().playSound(null, user.getX(), user.getY(), user.getZ(),
            SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, 0.65F, 1.45F);
    }

    private static void tick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD || ATTEMPTS.isEmpty()) return;
        MinecraftServer server = level.getServer();
        Iterator<Map.Entry<UUID, DefuseAttempt>> iterator = ATTEMPTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, DefuseAttempt> entry = iterator.next();
            DefuseAttempt attempt = entry.getValue();
            ServerPlayer defuser = server.getPlayerList().getPlayer(attempt.defuserId());
            if (defuser == null || defuser.level() != level || !isStillHoldingPliers(defuser, attempt.hand())) {
                iterator.remove();
                continue;
            }

            DefuseTarget target = locateTarget(level, server, attempt);
            if (target == null || defuser.distanceToSqr(target.pos()) > MAX_DEFUSE_DISTANCE_SQ
                    || defuser.position().distanceToSqr(attempt.startPos()) > MAX_DEFUSE_DISTANCE_SQ) {
                defuser.displayClientMessage(Component.translatable("c4.defuse_cancelled"), true);
                iterator.remove();
                continue;
            }

            long elapsed = level.getGameTime() - attempt.startedAt();
            if (elapsed >= DEFUSE_TICKS) {
                complete(level, defuser, attempt, target);
                iterator.remove();
                continue;
            }

            showProgress(level, defuser, target, elapsed);
        }
    }

    private static boolean isStillHoldingPliers(ServerPlayer player, InteractionHand hand) {
        return player.getItemInHand(hand).is(ModItems.PLIERS);
    }

    private static DefuseTarget locateTarget(ServerLevel level, MinecraftServer server, DefuseAttempt attempt) {
        if (attempt.playerTargetId() != null) {
            ServerPlayer target = server.getPlayerList().getPlayer(attempt.playerTargetId());
            if (target == null || target.level() != level || target.isRemoved()) return null;
            C4BackComponent comp = C4BackComponent.KEY.getNullable(level);
            if (comp == null || !comp.hasC4(target.getUUID())) return null;
            return new DefuseTarget(target.getUUID(), target.position().add(0.0D, 1.0D, 0.0D), true);
        }
        if (attempt.blockChargeId() == null) return null;
        if (!(level.getEntity(attempt.blockChargeId()) instanceof ItemEntity charge)
                || !C4Detonation.isDefusableBlockCharge(charge)) {
            return null;
        }
        return new DefuseTarget(charge.getUUID(), charge.position(), false);
    }

    private static void showProgress(ServerLevel level, ServerPlayer defuser, DefuseTarget target, long elapsed) {
        int remainingTicks = Math.max(0, DEFUSE_TICKS - (int) elapsed);
        double seconds = remainingTicks / 20.0D;
        defuser.displayClientMessage(Component.translatable("c4.defusing_progress", String.format("%.1f",seconds)), true);
        Vec3 pos = target.pos();
        level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y + 0.1D, pos.z,
            4, 0.12D, 0.12D, 0.12D, 0.02D);
        if (elapsed % 10L == 0L) {
            level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.35F, 1.8F);
        }
    }

    private static void complete(ServerLevel level, ServerPlayer defuser, DefuseAttempt attempt, DefuseTarget target) {
        // 钳工拆除C4必定成功，且不消耗耐久
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(defuser.level());
        boolean isFitter = gameWorld.isRole(defuser, ModRoles.FITTER);
        boolean clippedWrongWire = isFitter ? false : level.getRandom().nextInt(100) < 20;
        if (target.playerTarget()) {
            completePlayerDefuse(level, defuser, attempt, clippedWrongWire);
        } else {
            completeBlockDefuse(level, defuser, attempt, clippedWrongWire);
        }
        if (!isFitter) {
            consumePliers(defuser, attempt.hand());
        }
    }

    private static void completePlayerDefuse(ServerLevel level, ServerPlayer defuser,
            DefuseAttempt attempt, boolean clippedWrongWire) {
        MinecraftServer server = level.getServer();
        ServerPlayer target = server.getPlayerList().getPlayer(attempt.playerTargetId());
        C4BackComponent comp = C4BackComponent.KEY.getNullable(level);
        if (target == null || comp == null || !comp.hasC4(target.getUUID())) return;
        comp.removeC4(target.getUUID());
        if (clippedWrongWire) {
            C4Detonation.detonateAt(level, target, defuser);
            target.displayClientMessage(Component.translatable("c4.wrong_wire_explode"), false);
            defuser.displayClientMessage(Component.translatable("c4.you_cut_wrong_wire"), false);
            return;
        }
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
            SoundEvents.TRIPWIRE_CLICK_OFF, SoundSource.PLAYERS, 0.9F, 1.2F);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
            SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.2F);
        target.displayClientMessage(Component.translatable("c4.defuse_success"), false);
        defuser.displayClientMessage(Component.translatable("c4.defuse_success_self"), false);
        // 记录拆弹成功事件（低频关键事件）
        io.wifi.starrailexpress.SRE.REPLAY_MANAGER.recordBombDefuse(defuser.getUUID(), target.getUUID());
    }

    private static void completeBlockDefuse(ServerLevel level, ServerPlayer defuser,
            DefuseAttempt attempt, boolean clippedWrongWire) {
        if (!(level.getEntity(attempt.blockChargeId()) instanceof ItemEntity charge)) return;
        if (clippedWrongWire) {
            C4Detonation.misfireBlockCharge(level, charge, defuser);
        } else {
            C4Detonation.defuseBlockCharge(defuser, charge);
            // 记录拆弹成功事件（拆除地面/方块电荷，无炸弹携带者）
            io.wifi.starrailexpress.SRE.REPLAY_MANAGER.recordBombDefuse(defuser.getUUID(), null);
        }
    }

    private static void consumePliers(ServerPlayer player, InteractionHand hand) {
        if (player.getAbilities().instabuild) return;
        player.getItemInHand(hand).shrink(1);
    }

    private record DefuseAttempt(UUID defuserId, InteractionHand hand, UUID playerTargetId, UUID blockChargeId,
            Vec3 startPos, long startedAt) {}

    private record DefuseTarget(UUID targetId, Vec3 pos, boolean playerTarget) {}
}
