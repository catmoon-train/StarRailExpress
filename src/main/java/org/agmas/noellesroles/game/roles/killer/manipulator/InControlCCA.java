package org.agmas.noellesroles.game.roles.killer.manipulator;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.packet.ManipulatorControlInputC2SPacket;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

/**
 * 被操控者组件（附身的目标侧）。
 *
 * <p>被操控期间：目标被施加 失明 / 禁止移动 / 禁止使用 / 禁止打开背包 / 禁止使用技能 等效果，
 * 移动改由操纵师通过 {@link ManipulatorControlInputC2SPacket} 远程驱动；
 * 当被驱动到游戏区域之外（主要是虚空 Y 轴判定）时，会被弹回上一个安全落点以避免自杀。
 */
public class InControlCCA implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<InControlCCA> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "in_control"),
            InControlCCA.class);

    public UUID controller;
    public Player player;
    public boolean isControlling = false;
    public int controlTimer;

    // ==================== 远程移动输入 ====================
    private int inputBits = 0;
    private float inputYaw;
    private float inputPitch;
    private int inputFreshTicks = 0;
    private double verticalVelocity = 0;
    // 使用/交互键边沿检测（避免一次按键反复触发，如反复开关门）
    private boolean useHeldLast = false;

    // ==================== 防虚空：最近安全落点 ====================
    private boolean hasSafePos = false;
    private double safeX, safeY, safeZ;

    public InControlCCA(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return player == this.player;
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void init() {
        this.controller = null;
        isControlling = false;
        controlTimer = 0;
        inputBits = 0;
        inputFreshTicks = 0;
        verticalVelocity = 0;
        useHeldLast = false;
        hasSafePos = false;
    }

    @Override
    public void clear() {
        this.init();
    }

    /**
     * 接收操纵师下发的移动输入（在服务端网络线程→服务端主线程后调用）。
     */
    public void applyControlInput(int bits, float yaw, float pitch) {
        this.inputBits = bits;
        this.inputYaw = yaw;
        this.inputPitch = pitch;
        this.inputFreshTicks = 5;
    }

    @Override
    public void readFromSyncNbt(CompoundTag compoundTag, HolderLookup.Provider provider) {
        isControlling = compoundTag.getBoolean("isControlling");
        controlTimer = compoundTag.getInt("controlTimer");
        if (compoundTag.hasUUID("controller"))
            controller = compoundTag.getUUID("controller");
    }

    @Override
    public void writeToSyncNbt(CompoundTag compoundTag, HolderLookup.Provider provider) {
        compoundTag.putBoolean("isControlling", isControlling);
        compoundTag.putInt("controlTimer", controlTimer);
        if (controller != null)
            compoundTag.putUUID("controller", controller);
    }

    public void stopControl() {
        this.isControlling = false;
        this.controlTimer = 0;
        this.controller = null;
        if (this.player != null) {
            this.player.displayClientMessage(Component.translatable("message.noellesroles.manipulator.control_ended")
                    .withStyle(ChatFormatting.GREEN), true);
        }
        this.init();
        this.sync();
    }

    public void stopControlFromUpstream(boolean isTimeout) {
        if (this.controller != null) {
            if ((player instanceof ServerPlayer sp)) {
                var controller_p = sp.level().getPlayerByUUID(this.controller);
                if (controller_p != null) {
                    var controllerComponent = ManipulatorPlayerComponent.KEY.get(controller_p);
                    if (controllerComponent != null) {
                        controllerComponent.stopControl(isTimeout);
                        this.controller = null;
                    }
                }
            }
        }
        this.stopControl();
        this.init();
    }

    @Override
    public void serverTick() {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            if (this.isControlling) {
                this.stopControlFromUpstream(false);
            }
            return;
        }
        if (isControlling) {
            if (controlTimer > 0) {
                // 失明 + 禁止移动/使用/打开背包/使用技能/转视角（每 tick 续期，短时长）
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 10, 0, true, false, true));
                player.addEffect(new MobEffectInstance(ModEffects.BLACK_MONITOR, 10, 0, true, false, true));
                player.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 10, 0, true, false, true));
                player.addEffect(new MobEffectInstance(ModEffects.USED_BANED, 10, 0, true, false, true));
                player.addEffect(new MobEffectInstance(ModEffects.INVENTORY_BANED, 10, 0, true, false, true));
                player.addEffect(new MobEffectInstance(ModEffects.TURN_BANED, 10, 0, true, false, true));
                player.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, 10, 0, true, false, true));

                if (player instanceof ServerPlayer sp) {
                    driveMovement(sp);
                }

                --controlTimer;
                if (controlTimer % 20 == 0) {
                    sync();
                }
            }

            if (controlTimer <= 0) {
                this.stopControlFromUpstream(true);
            }
        }
    }

    /**
     * 由操纵师输入驱动目标移动；包含重力/跳跃与防虚空弹回。
     */
    private void driveMovement(ServerPlayer sp) {
        // 记录最近安全落点（在地面且高于虚空阈值）
        if (sp.onGround() && sp.getY() > sp.level().getMinBuildHeight() + 1) {
            hasSafePos = true;
            safeX = sp.getX();
            safeY = sp.getY();
            safeZ = sp.getZ();
        }

        boolean fresh = inputFreshTicks > 0;
        if (fresh) {
            sp.setYRot(inputYaw);
            sp.setYHeadRot(inputYaw);
            sp.setXRot(inputPitch);
        }

        // 使用/交互（开门等）：仅在按键的上升沿触发一次
        boolean useHeld = fresh && (inputBits & ManipulatorControlInputC2SPacket.BIT_USE) != 0;
        if (useHeld && !useHeldLast) {
            tryUseInteraction(sp);
        }
        useHeldLast = useHeld;

        // 水平方向（基于操纵师朝向）
        double f = 0, l = 0;
        if (fresh) {
            if ((inputBits & ManipulatorControlInputC2SPacket.BIT_FORWARD) != 0) f += 1;
            if ((inputBits & ManipulatorControlInputC2SPacket.BIT_BACK) != 0) f -= 1;
            if ((inputBits & ManipulatorControlInputC2SPacket.BIT_LEFT) != 0) l += 1;
            if ((inputBits & ManipulatorControlInputC2SPacket.BIT_RIGHT) != 0) l -= 1;
        }
        boolean sprint = fresh && (inputBits & ManipulatorControlInputC2SPacket.BIT_SPRINT) != 0;
        double speed = sprint ? 0.26 : 0.20;

        double rad = Math.toRadians(inputYaw);
        double sin = Math.sin(rad), cos = Math.cos(rad);
        Vec3 fwd = new Vec3(-sin, 0, cos);   // W
        Vec3 left = new Vec3(cos, 0, sin);   // A
        Vec3 horizontal = fwd.scale(f).add(left.scale(l));
        if (horizontal.lengthSqr() > 1.0e-4) {
            horizontal = horizontal.normalize().scale(speed);
        } else {
            horizontal = Vec3.ZERO;
        }

        // 垂直方向：重力 / 跳跃
        if (sp.onGround()) {
            verticalVelocity = 0;
            if (fresh && (inputBits & ManipulatorControlInputC2SPacket.BIT_JUMP) != 0) {
                verticalVelocity = 0.42;
            }
        } else {
            verticalVelocity -= 0.08;
            verticalVelocity *= 0.98;
            if (verticalVelocity < -3.0) verticalVelocity = -3.0;
        }

        Vec3 delta = new Vec3(horizontal.x, verticalVelocity, horizontal.z);
        sp.move(MoverType.SELF, delta);
        sp.setDeltaMovement(horizontal.x, verticalVelocity, horizontal.z);

        // 防虚空：越界则弹回上一个安全落点
        if (sp.getY() < sp.level().getMinBuildHeight() + 1 && hasSafePos) {
            verticalVelocity = 0;
            sp.setDeltaMovement(0, 0, 0);
            sp.teleportTo(safeX, safeY, safeZ);
            sp.connection.teleport(safeX, safeY, safeZ, sp.getYRot(), sp.getXRot());
        } else if (delta.lengthSqr() > 1.0e-5) {
            // 推送权威位置到目标客户端（目标处于 MOVE_BANED，不会与之争抢）
            sp.connection.teleport(sp.getX(), sp.getY(), sp.getZ(), sp.getYRot(), sp.getXRot());
        }
        sp.hasImpulse = true;

        if (inputFreshTicks > 0) inputFreshTicks--;
    }

    /**
     * 以被操控者身份对其准星方向的方块进行一次"使用"交互（开门、按按钮、拉拉杆等）。
     *
     * <p>仅触发方块自身的交互（{@link net.minecraft.world.level.block.state.BlockState#useWithoutItem}），
     * 不会使用目标手中的物品（避免操纵师借此开枪/消耗目标道具）；同时由于直接走服务端逻辑，
     * 不受目标身上 USED_BANED（仅在客户端拦截按键）的影响。
     */
    private void tryUseInteraction(ServerPlayer sp) {
        double reach = 4.5;
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(reach));
        BlockHitResult hit = sp.level().clip(new ClipContext(eye, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, sp));
        if (hit.getType() != HitResult.Type.BLOCK)
            return;

        InteractionResult result = sp.level().getBlockState(hit.getBlockPos())
                .useWithoutItem(sp.level(), sp, hit);
        if (result.consumesAction()) {
            sp.swing(InteractionHand.MAIN_HAND, true);
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
