package org.agmas.noellesroles.game.roles.killer.manipulator;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants;
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

import java.util.Set;
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
        sync();
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

    /**
     * 将被操控者弹回最近的安全落点并清零速度（回弹）。
     * 供防虚空、危险液体（水/岩浆）、陷阱拦截等复用。
     */
    public void bounceToSafe() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        verticalVelocity = 0;
        sp.setDeltaMovement(0, 0, 0);
        sp.fallDistance = 0;
        if (hasSafePos) {
            sp.teleportTo(safeX, safeY, safeZ);
            sp.connection.teleport(safeX, safeY, safeZ, sp.getYRot(), sp.getXRot());
        }
    }

    /**
     * 若该玩家当前正被操纵师操控，则将其弹回安全落点并返回 {@code true}。
     * 供陷阱等外部逻辑拦截：被操控者不触发/不受陷阱伤害，而是被弹回。
     */
    public static boolean bounceBackIfControlled(Player player) {
        if (player == null) {
            return false;
        }
        InControlCCA cca = KEY.maybeGet(player).orElse(null);
        if (cca == null || !cca.isControlling) {
            return false;
        }
        cca.bounceToSafe();
        return true;
    }

    /**
     * 被操控者不应因此致死的「环境 / 陷阱 / 自然伤害」死因集合。
     *
     * <p>覆盖：列车碾压（坠车/出界）、摔落、溺水/深水、岩浆、冻结、黑暗、下水道窒息、
     * 钟乳石穿刺、滚石/滚木碾压、焚化炉推入、喷火装置、口渴/饥饿/脱水、迷失、远古啃咬。
     * 操控期间目标的移动完全由操纵师驱动，因此任何环境致死都视为操纵师所为，一律否决并回溯。
     */
    public static final Set<ResourceLocation> HAZARD_DEATHS = Set.of(
            GameConstants.DeathReasons.FELL_OUT_OF_TRAIN,
            GameConstants.DeathReasons.FALL_DAMAGE,
            GameConstants.DeathReasons.CANNOT_SWIM,
            GameConstants.DeathReasons.DROWNED,
            GameConstants.DeathReasons.LAVA,
            GameConstants.DeathReasons.FROZEN,
            GameConstants.DeathReasons.DEATH_IN_DARKNESS,
            GameConstants.DeathReasons.MANHOLE_SUFFOCATION,
            GameConstants.DeathReasons.STALACTITE_IMPALE,
            GameConstants.DeathReasons.BOULDER_CRUSH,
            GameConstants.DeathReasons.LOG_CRUSH,
            GameConstants.DeathReasons.INCINERATOR_PUSHED,
            GameConstants.DeathReasons.FLAMETHROWER_BURNED,
            GameConstants.DeathReasons.THIRST,
            GameConstants.DeathReasons.STARVED,
            GameConstants.DeathReasons.DRY_DEATH,
            GameConstants.DeathReasons.SELF_LOST,
            GameConstants.DeathReasons.ANCIENT_BITE);

    /**
     * 注册操纵师操控的死亡限制（优先用 API 事件实现）：
     * 被操控者因被拖入危险区/陷阱（水、岩浆、坠车/虚空、摔落、碾压等）而死时，否决其死亡并回溯到安全落点。
     *
     * <p>该事件只拦截「非强制」死亡；场景陷阱多走 {@code forceKillPlayer}（强制死亡）会绕过此事件，
     * 那部分由 {@code ManipulatorControlledDeathImmunityMixin} 在 {@link GameUtils#killPlayer} 入口兜底。
     */
    public static void registerEvents() {
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (!(victim instanceof ServerPlayer)) {
                return true;
            }
            return !blockHazardDeathIfControlled(victim, deathReason);
        });
    }

    /**
     * 若 {@code victim} 正被操控且死因属于环境/陷阱，则把它回溯到安全落点并返回 {@code true}
     * （表示应阻止此次死亡）。供否决事件与强制死亡拦截 mixin 共用。
     */
    public static boolean blockHazardDeathIfControlled(Player victim, ResourceLocation deathReason) {
        if (victim == null || !isHazardDeath(deathReason)) {
            return false;
        }
        InControlCCA cca = KEY.maybeGet(victim).orElse(null);
        if (cca == null || !cca.isControlling) {
            return false;
        }
        cca.bounceToSafe();
        return true;
    }

    /** 判断是否为"被操控者不应因此致死"的环境/陷阱死因。 */
    public static boolean isHazardDeath(ResourceLocation reason) {
        return reason != null && HAZARD_DEATHS.contains(reason);
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
        // 兜底：控制刚开始、还没记录过任何安全落点时，先用当前位置作为初始回溯点，
        // 保证「回溯到受到伤害之前的位置」在任何时刻都有可用目标（随后会被更严格的判定刷新）。
        if (!hasSafePos) {
            hasSafePos = true;
            safeX = sp.getX();
            safeY = sp.getY();
            safeZ = sp.getZ();
        }
        // 记录最近安全落点（在干燥地面、高于虚空阈值、且不在水/岩浆中）
        if (sp.onGround() && sp.getY() > sp.level().getMinBuildHeight() + 1
                && !sp.isInWater() && !sp.isUnderWater() && !sp.isInLava()) {
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

        // 危险液体（水/岩浆）：把被拖入者弹回安全落点，避免操纵师借水/岩浆致死
        if (sp.isInWater() || sp.isUnderWater() || sp.isInLava()) {
            bounceToSafe();
        }

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
