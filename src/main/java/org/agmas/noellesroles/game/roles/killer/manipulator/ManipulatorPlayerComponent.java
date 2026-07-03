
package org.agmas.noellesroles.game.roles.killer.manipulator;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.event.AllowPlayerControlled;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 操纵师组件（操控者侧）。
 *
 * <p>玩法：潜行盯着目标 4 秒进行标记（标记后目标短暂反胃、操纵师 +15 金币），可标记保存多个目标；
 * 之后在 100 格内，于背包点击任一已标记目标头像即可附身操控——相机绑定到目标、远程驱动其移动、
 * 可以目标身份释放目标技能（冷却记在目标身上）。附身期间操纵师本体被冻结并获得无敌保护。
 *
 * @see InControlCCA 被操控者侧
 */
public class ManipulatorPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<ManipulatorPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "manipulator"),
            ManipulatorPlayerComponent.class);
    public static final int DIRECT_CONTROL_RANGE = 200;

    @Override
    public Player getPlayer() {
        return player;
    }

    // ==================== 状态变量 ====================

    private final Player player;

    /** 当前正在操控的目标（附身中） */
    public UUID target;

    /** 已标记目标。旧数据仍会同步，但操控不再需要标记。 */
    public final Set<UUID> markedTargets = new LinkedHashSet<>();

    public boolean isControlling;

    public int cooldown;

    // 标记进度（服务端）
    private UUID staringAt;
    private int markProgressTicks;

    // 操控者本体冻结锚点
    private double anchorX, anchorY, anchorZ;

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return player == this.player;
    }

    public ManipulatorPlayerComponent(Player player) {
        this.player = player;
        this.target = null;
        this.isControlling = false;
        this.cooldown = 0;
    }

    private static NoellesRolesConfig config() {
        return NoellesRolesConfig.HANDLER.instance();
    }

    @Override
    public void init() {
        if (isControlling && player instanceof ServerPlayer sp) {
            sp.setInvulnerable(false);
        }
        this.target = null;
        this.markedTargets.clear();
        this.isControlling = false;
        this.cooldown = 0;
        this.staringAt = null;
        this.markProgressTicks = 0;
        this.sync();
    }

    @Override
    public void clear() {
        clearAll();
    }

    public void clearAll() {
        if (isControlling && player instanceof ServerPlayer sp) {
            sp.setInvulnerable(false);
        }
        this.target = null;
        this.markedTargets.clear();
        this.isControlling = false;
        this.cooldown = 0;
        this.staringAt = null;
        this.markProgressTicks = 0;
        this.sync();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public boolean canUseAbility() {
        return !isControlling;
    }

    /**
     * 尝试附身操控目标。包含：距离校验、{@link AllowPlayerControlled} 否决、冷却设置。
     */
    public void setTarget(UUID targetUuid) {
        if (!canUseAbility())
            return;
        if (!(player instanceof ServerPlayer sp))
            return;
        if (targetUuid == null || targetUuid.equals(player.getUUID()))
            return;

        SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(player);
        if (ability.cooldown > 0)
            return;

        Player targetPlayer = player.level().getPlayerByUUID(targetUuid);
        if (!(targetPlayer instanceof ServerPlayer))
            return;
        if (!GameUtils.isPlayerAliveAndSurvival(targetPlayer))
            return;

        if (sp.distanceTo(targetPlayer) > DIRECT_CONTROL_RANGE) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.manipulator.out_of_range")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        // 事件否决（其他职业/效果可豁免被操控）
        if (!AllowPlayerControlled.EVENT.invoker().allowControlled(player, targetPlayer)) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.manipulator.control_blocked")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        ConfigWorldComponent.onPlayerUsedSkill(sp);

        // 开始附身
        isControlling = true;
        this.target = targetUuid;
        int controlTime = GameConstants.getInTicks(0, config().manipulatorControlSeconds);
        final var inControlCCA = InControlCCA.KEY.get(targetPlayer);
        inControlCCA.isControlling = true;
        inControlCCA.controlTimer = controlTime;
        inControlCCA.controller = player.getUUID();
        inControlCCA.sync();

        // 冷却（记在操纵师身上）
        ability.cooldown = GameConstants.getInTicks(0, config().manipulatorCooldown);
        ability.sync();

        // 冻结 + 保护操纵师本体
        anchorX = sp.getX();
        anchorY = sp.getY();
        anchorZ = sp.getZ();
        sp.setInvulnerable(true);
        sp.setDeltaMovement(0, 0, 0);

        this.sync();

        sp.displayClientMessage(Component.translatable("message.noellesroles.manipulator.control_started",
                targetPlayer.getName()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /**
     * 停止附身。
     */
    public void stopControl(boolean timeout) {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        if (target != null) {
            Player targetPlayer = player.level().getPlayerByUUID(target);
            if (targetPlayer != null) {
                InControlCCA.KEY.get(targetPlayer).stopControl();
            }
        }

        isControlling = false;
        target = null;
        serverPlayer.setInvulnerable(false);

        if (timeout) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.manipulator.control_timeout")
                            .withStyle(ChatFormatting.YELLOW),
                    true);
        } else {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.manipulator.control_stopped")
                            .withStyle(ChatFormatting.GREEN),
                    true);
        }

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        this.sync();
    }

    public float getControlSeconds() {
        if (target == null)
            return 0f;
        Player targetPlayer = player.level().getPlayerByUUID(target);
        if (targetPlayer != null) {
            return InControlCCA.KEY.get(targetPlayer).controlTimer / 20.0f;
        }
        return 0f;
    }

    public float getCooldownSeconds() {
        if (player instanceof ServerPlayer || player.level().isClientSide) {
            return SREAbilityPlayerComponent.KEY.get(player).cooldown / 20.0f;
        }
        return cooldown / 20.0f;
    }

    /** 当前标记进度（0~1），供客户端 HUD 使用 */
    public float getMarkProgress() {
        int need = config().manipulatorMarkSeconds * 20;
        if (need <= 0)
            return 0f;
        return Math.min(1f, (float) markProgressTicks / need);
    }

    @Override
    public void serverTick() {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            if (this.isControlling) {
                this.stopControl(false);
            }
            return;
        }

        if (!(player instanceof ServerPlayer sp))
            return;

        if (isControlling) {
            // 冻结 + 保护本体
            sp.setInvulnerable(true);
            sp.setDeltaMovement(0, 0, 0);
            sp.fallDistance = 0;
            // 防止本体漂移过远（客户端会清零自身输入，这里作为兜底）
            if (sp.distanceToSqr(anchorX, anchorY, anchorZ) > 2.25) {
                sp.teleportTo(anchorX, anchorY, anchorZ);
            }

            // 目标有效性检查（每秒）
            if (sp.level().getGameTime() % 20 == 0) {
                if (target != null) {
                    Player targetPlayer = sp.level().getPlayerByUUID(target);
                    if (targetPlayer == null || !InControlCCA.KEY.get(targetPlayer).isControlling) {
                        stopControl(false);
                    }
                } else {
                    stopControl(false);
                }
            }
            return;
        }

        resetStare();
    }

    // ==================== 标记逻辑 ====================

    /** 当前玩家是否为存活的操纵师（服务端判定） */
    private boolean isActiveManipulator() {
        if (player == null || player.level().isClientSide())
            return false;
        return SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.MANIPULATOR);
    }

    private void resetStare() {
        staringAt = null;
        markProgressTicks = 0;
    }

    private void tickMarking(ServerPlayer sp) {
        if (!sp.isShiftKeyDown()) {
            resetStare();
            return;
        }
        ServerPlayer candidate = findStareCandidate(sp);
        if (candidate == null) {
            resetStare();
            return;
        }

        if (candidate.getUUID().equals(staringAt)) {
            markProgressTicks++;
        } else {
            staringAt = candidate.getUUID();
            markProgressTicks = 1;
        }

        int need = GameConstants.getInTicks(0, config().manipulatorMarkSeconds);
        if (markProgressTicks % 10 == 0 && markProgressTicks < need) {
            int remain = (need - markProgressTicks + 19) / 20;
            sp.displayClientMessage(Component.translatable("message.noellesroles.manipulator.mark_progress", remain)
                    .withStyle(ChatFormatting.GRAY), true);
        }

        if (markProgressTicks >= need) {
            // 避免对同一目标反复刷取（可保存多个目标）
            if (markedTargets.add(candidate.getUUID())) {
                candidate.addEffect(new MobEffectInstance(MobEffects.CONFUSION,
                        GameConstants.getInTicks(0, config().manipulatorMarkNauseaSeconds), 0));
                PlayerEconomyManager.addCoinNum(sp, config().manipulatorMarkReward);
                sp.displayClientMessage(Component.translatable("message.noellesroles.manipulator.mark_success",
                        candidate.getName()).withStyle(ChatFormatting.GREEN), true);
                sp.level().playSound(null, sp.blockPosition(),
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
                this.sync();
            }
            resetStare();
        }
    }

    /**
     * 在潜行状态下，寻找操纵师正盯着、在标记范围内、视线无遮挡的目标。
     */
    private ServerPlayer findStareCandidate(ServerPlayer sp) {
        double maxRange = config().manipulatorMarkRange;
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getViewVector(1.0f).normalize();

        ServerPlayer best = null;
        double bestDot = 0.96; // 约 16° 视锥

        for (Player p : sp.level().players()) {
            if (!(p instanceof ServerPlayer other))
                continue;
            if (other == sp)
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(other))
                continue;

            double dist = sp.distanceTo(other);
            if (dist > maxRange)
                continue;

            Vec3 targetEye = other.getEyePosition();
            Vec3 toTarget = targetEye.subtract(eye);
            if (toTarget.lengthSqr() < 1.0e-4)
                continue;
            Vec3 dirToTarget = toTarget.normalize();

            // 是否正盯着目标
            double dot = look.dot(dirToTarget);
            if (dot <= bestDot)
                continue;

            // 视线遮挡判定
            HitResult clip = sp.level().clip(new ClipContext(eye, targetEye,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
            if (clip.getType() == HitResult.Type.BLOCK)
                continue;

            best = other;
            bestDot = dot;
        }
        return best;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (this.target != null) {
            tag.putUUID("target", this.target);
        }
        ListTag markedList = new ListTag();
        for (UUID marked : this.markedTargets) {
            markedList.add(NbtUtils.createUUID(marked));
        }
        tag.put("markedTargets", markedList);
        tag.putBoolean("isControlling", this.isControlling);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.target = tag.hasUUID("target") ? tag.getUUID("target") : null;
        this.markedTargets.clear();
        ListTag markedList = tag.getList("markedTargets", net.minecraft.nbt.Tag.TAG_INT_ARRAY);
        for (int i = 0; i < markedList.size(); i++) {
            this.markedTargets.add(NbtUtils.loadUUID(markedList.get(i)));
        }
        this.isControlling = tag.contains("isControlling") && tag.getBoolean("isControlling");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
