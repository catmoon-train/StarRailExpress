package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class PatrollerPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<PatrollerPlayerComponent> KEY = ModComponents.PATROLLER;
    private final Player player;
    private boolean hasTriggered = false;

    /** 窥视视野角度（度数） */
    public static final double GAZE_ANGLE = 65.0;

    /** 窥视最大距离（格） */
    public static final double GAZE_DISTANCE = 48.0;
    
    public static boolean isBoundTargetVisible(Player boundTarget, Player player) {

        if (boundTarget == null)
            return false;
        if (!GameUtils.isPlayerAliveAndSurvival(boundTarget))
            return false;

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookDir = player.getViewVector(1.0f);
        Vec3 targetPos = boundTarget.getEyePosition();

        double distance = eyePos.distanceTo(targetPos);
        if (distance > GAZE_DISTANCE)
            return false;

        // 视野角度检查（90度扇形，半角45度）
        Vec3 toTarget = targetPos.subtract(eyePos).normalize();
        double dot = lookDir.dot(toTarget);
        if (dot < Math.cos(Math.toRadians(GAZE_ANGLE)))
            return false;

        // 射线检测
        Level world = player.level();
        ClipContext context = new ClipContext(
                eyePos, targetPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player);
        BlockHitResult hit = world.clip(context);
        return hit.getType() == HitResult.Type.MISS ||
                hit.getLocation().distanceTo(targetPos) < 1.0;
    }

    public PatrollerPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void serverTick() {
        // 巡警的逻辑主要在死亡事件中触发，这里暂时不需要每tick运行
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return player == this.player || GameUtils.isPlayerAliveAndSurvival(player);
    }

    @Override
    public void init() {
        this.hasTriggered = false;
        sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public void onNearbyDeath() {
        var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isSkillAvailable) {
            // player.displayClientMessage(
            // Component.translatable("message.tip.skill_disabled").withStyle(ChatFormatting.RED),
            // true);
            return;
        }
        if (this.hasTriggered)
            return;
        if (player instanceof ServerPlayer serverPlayer) {
            if (gameWorldComponent != null) {
                if (gameWorldComponent.isRole(serverPlayer, ModRoles.PATROLLER)) {
                    serverPlayer.addItem(new ItemStack(ModItems.PATROLLER_REVOLVER));
                    // 给予乘务员钥匙 (master_key_p)
                    serverPlayer.addItem(new ItemStack(ModItems.MASTER_KEY_P));
                    this.hasTriggered = true;
                    sync();
                } else {
                    this.clear();
                }
            }
            // 给予左轮手枪

        }
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.hasTriggered = tag.getBoolean("hasTriggered");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("hasTriggered", this.hasTriggered);
    }
}