package org.agmas.noellesroles.game.roles.killer.youlu;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.content.entity.YouluAnchorEntity;
import org.agmas.noellesroles.content.entity.YouluSmokeBallEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.packet.YouluFreeCamS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

/**
 * 幽露组件（杀手阵营）。
 *
 * <p>技能一览：
 * <ul>
 *   <li><b>不请自来</b>（物品）：放置一个沿地面向前滑行的球形锚点（仅自己可见），
 *       再次使用传送到锚点位置，随后进入 30s 物品冷却。</li>
 *   <li><b>遮天闭目</b>（物品）：向前释放穿墙烟雾，锥形范围内玩家陷入 8s 失明+黑暗，60s 物品冷却。</li>
 *   <li><b>魂游</b>（G 键）：摄像机进入自由模式（本体禁止移动/攻击），再按 G 在摄像机处
 *       生成持续 12s 的球烟（内部视野迷雾 1 级且雾为黑色），冷却 45s；ESC 取消（不进冷却）。</li>
 * </ul>
 *
 * <p>自由摄像机位置由客户端周期性上报（{@code YouluCamPosC2SPacket}），服务端按
 * {@link NoellesRolesConfig#youluCamMaxDistance} 校验并保存，第二次按 G 时以最后上报位置生成球烟。
 */
public class YouluPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<YouluPlayerComponent> KEY = ModComponents.YOULU;

    private final Player player;

    /** 当前存活的锚点实体 UUID（无则为 null）。 */
    public UUID anchorUuid = null;

    /** 自由摄像机是否激活（服务端权威状态）。 */
    public boolean freeCamActive = false;
    /** 自由摄像机激活的截止游戏刻（超时自动退出，见 ai_doc 推荐的到点触发方式）。 */
    private long freeCamDeadline = 0L;
    /** 客户端最后上报的摄像机位置（已按最大距离校验）。 */
    public Vec3 lastCamPos = null;

    public YouluPlayerComponent(Player player) {
        this.player = player;
    }

    @Override public Player getPlayer() { return player; }
    @Override public boolean shouldSyncWith(ServerPlayer p) { return p == player; }
    public void sync() { KEY.sync(player); }

    @Override
    public void init() {
        discardAnchor();
        if (freeCamActive && player instanceof ServerPlayer sp) {
            exitFreeCam(sp, false);
        }
        anchorUuid = null;
        freeCamActive = false;
        freeCamDeadline = 0L;
        lastCamPos = null;
        sync();
    }

    @Override public void clear() { init(); }

    @Override
    public void serverTick() {
        if (!freeCamActive) return;
        if (!(player instanceof ServerPlayer sp)) return;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        // 死亡/游戏结束/超时：强制退出自由摄像机（不进冷却）
        if (!gameWorld.isRunning() || !GameUtils.isPlayerAliveAndSurvival(sp)
                || player.level().getGameTime() >= freeCamDeadline) {
            exitFreeCam(sp, true);
        }
    }

    // ==================== G 键技能：自由摄像机 ====================

    /**
     * G 键技能入口（统一技能系统调用）。
     *
     * @return true 表示本次按键"生成球烟并退出"，应消耗 45s 冷却；
     *         false 表示本次按键只是进入自由摄像机（或前置校验失败），不消耗冷却。
     */
    public boolean useCamSkill(ServerPlayer sp) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) return false;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (!gameWorld.isRole(sp, ModRoles.YOULU)) return false;

        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        if (!freeCamActive) {
            // 第一次按 G：进入自由摄像机
            freeCamActive = true;
            lastCamPos = sp.getEyePosition();
            freeCamDeadline = sp.level().getGameTime() + (long) config.youluCamMaxSeconds * 20L;
            int banTicks = (config.youluCamMaxSeconds + 2) * 20;
            // 本体禁止移动与攻击/使用（视角仍可转动，用于控制摄像机朝向）
            sp.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, banTicks, 0, false, false, false));
            sp.addEffect(new MobEffectInstance(ModEffects.USED_BANED, banTicks, 0, false, false, false));
            ServerPlayNetworking.send(sp, new YouluFreeCamS2CPacket(true));
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.youlu.freecam_enter")
                            .withStyle(ChatFormatting.AQUA), true);
            return false;
        }

        // 第二次按 G：在摄像机位置生成球烟并退出，消耗冷却
        Vec3 pos = lastCamPos != null ? lastCamPos : sp.getEyePosition();
        sp.swing(InteractionHand.MAIN_HAND);
        spawnSmokeBall(sp, pos);
        exitFreeCam(sp, true);
        return true;
    }

    /** ESC 取消自由摄像机（客户端请求）：退出但不生成球烟、不进冷却。 */
    public void cancelFreeCam(ServerPlayer sp) {
        if (!freeCamActive) return;
        exitFreeCam(sp, true);
        sp.displayClientMessage(
                Component.translatable("message.noellesroles.youlu.freecam_cancel")
                        .withStyle(ChatFormatting.GRAY), true);
    }

    /** 客户端上报摄像机位置：按最大距离校验后保存。 */
    public void reportCamPos(ServerPlayer sp, Vec3 pos) {
        if (!freeCamActive) return;
        double max = NoellesRolesConfig.HANDLER.instance().youluCamMaxDistance;
        Vec3 origin = sp.getEyePosition();
        Vec3 offset = pos.subtract(origin);
        if (offset.length() > max) {
            pos = origin.add(offset.normalize().scale(max));
        }
        lastCamPos = pos;
    }

    private void exitFreeCam(ServerPlayer sp, boolean notifyClient) {
        freeCamActive = false;
        freeCamDeadline = 0L;
        sp.removeEffect(ModEffects.MOVE_BANED);
        sp.removeEffect(ModEffects.USED_BANED);
        if (notifyClient) {
            ServerPlayNetworking.send(sp, new YouluFreeCamS2CPacket(false));
        }
    }

    private void spawnSmokeBall(ServerPlayer sp, Vec3 pos) {
        if (!(sp.level() instanceof ServerLevel serverLevel)) return;
        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        YouluSmokeBallEntity ball = new YouluSmokeBallEntity(ModEntities.YOULU_SMOKE_BALL, serverLevel);
        ball.setup(sp.getUUID(), (float) config.youluSmokeBallRadius,
                GameConstants.getInTicks(0, config.youluSmokeBallSeconds));
        ball.setPos(pos.x, pos.y, pos.z);
        serverLevel.addFreshEntity(ball);
        serverLevel.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 0.6f);
        sp.displayClientMessage(
                Component.translatable("message.noellesroles.youlu.smoke_ball_placed")
                        .withStyle(ChatFormatting.AQUA), true);
    }

    // ==================== 不请自来：锚点 ====================

    /** 当前锚点实体（无/已消散则为 null）。 */
    public YouluAnchorEntity getAnchor() {
        if (anchorUuid == null) return null;
        if (!(player.level() instanceof ServerLevel serverLevel)) return null;
        Entity entity = serverLevel.getEntity(anchorUuid);
        if (entity instanceof YouluAnchorEntity anchor && anchor.isAlive()) {
            return anchor;
        }
        anchorUuid = null;
        return null;
    }

    public void discardAnchor() {
        YouluAnchorEntity anchor = getAnchor();
        if (anchor != null) {
            anchor.discard();
        }
        anchorUuid = null;
    }

    // ==================== NBT（局内状态，不落盘） ====================

    @Override public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider p) {
        tag.putBoolean("freeCamActive", freeCamActive);
    }
    @Override public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider p) {
        freeCamActive = tag.getBoolean("freeCamActive");
    }
    @Override public void writeToNbt(CompoundTag tag, HolderLookup.Provider p) {}
    @Override public void readFromNbt(CompoundTag tag, HolderLookup.Provider p) {}
}
