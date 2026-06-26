package org.agmas.noellesroles.game.roles.innocent.photographer;

import io.github.mortuusars.exposure.fabric.api.event.ModifyFrameExtraDataCallback;
import io.github.mortuusars.exposure.util.ExtraData;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.entity.PhotographFrameEntity;
import io.github.mortuusars.exposure.world.item.PhotographItem;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
     * 由画框实体 tick mixin 调用：尝试把穿过画框的玩家传送到照片拍摄地点。
     */
    public static void tryTeleport(PhotographFrameEntity frame, ServerPlayer player) {
        // 仅在游戏进行中允许传送（开局加载阶段 isStartingGame=true、大厅均不允许）。
        // 旧实现误用 !isStartingGame：该标记只在开局加载窗口为 true，正式对局中恒为 false，
        // 导致对局中穿过画框永远不触发传送。
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(player.level());
        if (gw == null || !gw.isRunning()) {
            return;
        }
        if (!(player.level() instanceof ServerLevel) || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }
        NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();
        long now = player.level().getGameTime();
        Long last = LAST_TELEPORT_TICK.get(player.getUUID());
        if (last != null && now - last < (long) cfg.photographerFrameCooldownSeconds * 20L) {
            return;
        }

        ItemStack photo = frame.getItem();
        if (photo.isEmpty() || !(photo.getItem() instanceof PhotographItem photographItem)) {
            return;
        }
        Frame frameData = photographItem.getFrame(photo);
        if (frameData == null) {
            return;
        }
        ExtraData data = frameData.getExtraDataForReading();
        Optional<Vec3> posOpt = data.get(Frame.POSITION);
        if (posOpt.isEmpty()) {
            return;
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
        float yaw = data.get(Frame.YAW).orElse(player.getYRot());
        float pitch = data.get(Frame.PITCH).orElse(player.getXRot());

        LAST_TELEPORT_TICK.put(player.getUUID(), now);
        player.teleportTo(targetLevel, pos.x, pos.y, pos.z, yaw, pitch);
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,
                cfg.photographerFrameBlindSeconds * 20, 0, false, false, true));
        targetLevel.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.0f);
        player.displayClientMessage(
                Component.translatable("message.noellesroles.photographer.frame_teleport")
                        .withStyle(ChatFormatting.AQUA),
                true);
    }
}
