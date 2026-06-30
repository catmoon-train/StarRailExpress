package org.agmas.noellesroles.game.roles.innocence.photographer;

import io.github.mortuusars.exposure.fabric.api.event.ModifyFrameExtraDataCallback;
import io.github.mortuusars.exposure.util.ExtraData;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.entity.PhotographFrameEntity;
import io.github.mortuusars.exposure.world.item.PhotographItem;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role.ModRoles;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 摄影师"画框传送"功能的服务端核心逻辑。
 *
 * <ul>
 *   <li>拍照时，若持机者是摄影师，则把拍摄位置写入 exposure 照片自带的 {@link Frame} 数据
 *       （{@link Frame#POSITION} 等）。该字段 exposure 原版从不写入，因此其存在与否本身即可作为
 *       "这是摄影师拍的传送照片"的可靠标记。</li>
 *   <li>玩家穿过装有此类照片的画框时，传送到拍摄地点，并施加失明与冷却。</li>
 * </ul>
 */
public final class PhotographerFrameEvents {

    /** 玩家 UUID -> 上次穿越时的世界 gameTime（用于冷却）。 */
    private static final Map<UUID, Long> LAST_TELEPORT_TICK = new ConcurrentHashMap<>();

    private PhotographerFrameEvents() {
    }

    public static void register() {
        // 拍照即把拍摄位置写入照片的 Frame 数据（仅摄影师）
        ModifyFrameExtraDataCallback.EVENT.register((holder, camera, params, blocks, entities, extraData) -> {
            if (holder == null) {
                return;
            }
            if (holder.asHolderEntity() instanceof ServerPlayer sp && isPhotographer(sp)) {
                extraData.put(Frame.POSITION, sp.position());
                extraData.put(Frame.DIMENSION, sp.level().dimension().location());
                extraData.put(Frame.PITCH, sp.getXRot());
                extraData.put(Frame.YAW, sp.getYRot());
            }
        });

        // 仅在玩家主动右键画框时才传送（不再因走过/穿过画框自动触发）。
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide || hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            if (entity instanceof PhotographFrameEntity frame && player instanceof ServerPlayer sp) {
                if (tryTeleport(frame, sp)) {
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        });
    }

    public static boolean isPhotographer(Player player) {
        if (player == null) {
            return false;
        }
        // 注意：放置画框走 useOn，两端都会执行，需在客户端也能判定身份，
        // 否则冒险模式下客户端预测放置失败、体验上像“放不下去”。
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(player.level());
        return gw != null && gw.isRole(player, ModRoles.PHOTOGRAPHER);
    }

    /**
     * 由右键画框事件调用：尝试把右键画框的玩家传送到照片拍摄地点。
     *
     * @return 是否实际发生了传送（用于在右键事件中决定是否消费交互）。
     */
    public static boolean tryTeleport(PhotographFrameEntity frame, ServerPlayer player) {
        // 仅在游戏进行中允许传送（开局加载阶段、大厅均不允许）。
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(player.level());
        if (gw == null || !gw.isRunning()) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel) || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();
        // 画框传送次数上限：用尽后该画框不再触发传送。
        SrePhotographerFrame frameState = (SrePhotographerFrame) frame;
        if (cfg.photographerFrameMaxTeleports > 0
                && frameState.sre$getTeleportCount() >= cfg.photographerFrameMaxTeleports) {
            return false;
        }
        long now = player.level().getGameTime();
        Long last = LAST_TELEPORT_TICK.get(player.getUUID());
        if (last != null && now - last < (long) cfg.photographerFrameCooldownSeconds * 20L) {
            return false;
        }

        ItemStack photo = frame.getItem();
        if (photo.isEmpty() || !(photo.getItem() instanceof PhotographItem photographItem)) {
            return false;
        }
        Frame frameData = photographItem.getFrame(photo);
        if (frameData == null) {
            return false;
        }
        ExtraData data = frameData.getExtraDataForReading();
        Optional<Vec3> posOpt = data.get(Frame.POSITION);
        if (posOpt.isEmpty()) {
            return false;
        }
        Vec3 pos = posOpt.get();

        ServerLevel targetLevel = (ServerLevel) player.level();
        Optional<ResourceLocation> dimOpt = data.get(Frame.DIMENSION);
        if (dimOpt.isPresent() && player.getServer() != null) {
            ServerLevel resolved = player.getServer()
                    .getLevel(ResourceKey.create(Registries.DIMENSION, dimOpt.get()));
            if (resolved != null) {
                targetLevel = resolved;
            }
        }

        // 距离限制（仅同维度时判定）：水平超距 / Y 轴超距均拒绝，避免跨层、垂直滥用。
        if (targetLevel == player.level()) {
            double dx = pos.x - player.getX();
            double dz = pos.z - player.getZ();
            double horiz = Math.sqrt(dx * dx + dz * dz);
            double dy = Math.abs(pos.y - player.getY());
            if ((cfg.photographerFrameMaxDistance > 0 && horiz > cfg.photographerFrameMaxDistance)
                    || (cfg.photographerFrameMaxYDistance > 0 && dy > cfg.photographerFrameMaxYDistance)) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.photographer.frame_too_far")
                                .withStyle(ChatFormatting.RED),
                        true);
                return false;
            }
        }

        // 目的地有效性：避免传送进方块内部或世界之外的奇怪位置。
        if (!isSafeDestination(targetLevel, pos)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.photographer.frame_blocked")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        float yaw = data.get(Frame.YAW).orElse(player.getYRot());
        float pitch = data.get(Frame.PITCH).orElse(player.getXRot());

        LAST_TELEPORT_TICK.put(player.getUUID(), now);
        frameState.sre$setTeleportCount(frameState.sre$getTeleportCount() + 1);
        player.teleportTo(targetLevel, pos.x, pos.y, pos.z, yaw, pitch);
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,
                cfg.photographerFrameBlindSeconds * 20, 0, false, false, true));
        targetLevel.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.0f);
        player.displayClientMessage(
                Component.translatable("message.noellesroles.photographer.frame_teleport")
                        .withStyle(ChatFormatting.AQUA),
                true);
        return true;
    }

    /**
     * 判定传送落点是否安全：在世界高度范围内，且脚部与头部所在方块没有碰撞箱
     * （不会卡进墙体），从而避免传送到“奇奇怪怪”的非法位置。
     */
    private static boolean isSafeDestination(ServerLevel level, Vec3 pos) {
        if (pos.y < level.getMinBuildHeight() + 1 || pos.y > level.getMaxBuildHeight() - 1) {
            return false;
        }
        BlockPos feet = BlockPos.containing(pos.x, pos.y, pos.z);
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) {
            return false;
        }
        BlockPos head = feet.above();
        return level.getBlockState(head).getCollisionShape(level, head).isEmpty();
    }
}
