package org.agmas.noellesroles.game.roles.neutral.monokuma;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.joml.Vector3f;

import java.util.List;

/**
 * 阴阳剑 - 黑白狂暴前奏阶段武器
 *
 * - Q键（技能键）：黑白粒子突进（向前冲刺击杀）
 * - 右键点按一下：开始1秒蓄力，自动释放范围伤害
 */
public class YinYangSwordItem extends Item {

    /** 右键蓄力时间 tick (1秒) */
    public static final int CHARGE_TIME = 20;
    /** 右键范围伤害半径 */
    private static final double AOE_RANGE = 3.2;
    /** Q键突进距离 */
    private static final double DASH_DISTANCE = 6.0;

    public YinYangSwordItem(Item.Properties settings) {
        super(settings);
    }

    // ==================== 右键：点按启动蓄力 ====================

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (!(user instanceof ServerPlayer sp))
            return InteractionResultHolder.pass(stack);
        if (sp.isSpectator())
            return InteractionResultHolder.pass(stack);
        if (sp.getCooldowns().isOnCooldown(this))
            return InteractionResultHolder.pass(stack);

        // 启动蓄力计时器（在 MonokumaPlayerComponent 中管理）
        var comp = MonokumaPlayerComponent.KEY.maybeGet(sp).orElse(null);
        if (comp != null && comp.phase == 2 && comp.aoeChargeTimer <= 0) {
            comp.aoeChargeTimer = CHARGE_TIME;
            comp.sync();

            // 蓄力开始时给予缓慢效果(255级,1秒)
            user.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    20, // 1秒
                    254, // 等级255 (实际等级=显示等级-1)
                    true, false, true));

            // 蓄力开始音效
            world.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.5f);
        }

        return InteractionResultHolder.consume(stack);
    }

    /**
     * 蓄力过程的持续黑白粒子与音效。
     */
    public static void spawnChargeParticles(ServerPlayer sp, int chargeTimer) {
        ServerLevel serverLevel = sp.serverLevel();
        float progress = 1.0f - ((float) chargeTimer / CHARGE_TIME);
        double radius = 0.65 + progress * 1.35;
        int particleCount = 6 + (int) (progress * 18.0f);
        double baseY = sp.getY() + 1.0 + progress * 0.35;

        for (int i = 0; i < particleCount; i++) {
            double angle = (serverLevel.random.nextDouble() * Math.PI * 2.0) + serverLevel.getGameTime() * 0.12;
            double spiral = radius * (0.55 + serverLevel.random.nextDouble() * 0.45);
            double px = sp.getX() + Math.cos(angle) * spiral;
            double py = baseY + (serverLevel.random.nextDouble() - 0.5) * 1.4;
            double pz = sp.getZ() + Math.sin(angle) * spiral;

            serverLevel.sendParticles(
                    new DustParticleOptions(new Vector3f(0.02f, 0.02f, 0.02f), 1.0f + progress * 1.2f),
                    px, py, pz,
                    1,
                    -Math.cos(angle) * 0.04,
                    0.02 + progress * 0.04,
                    -Math.sin(angle) * 0.04,
                    0.0);

            serverLevel.sendParticles(
                    new DustParticleOptions(new Vector3f(1.0f, 1.0f, 1.0f), 0.9f + progress),
                    px * 0.35 + sp.getX() * 0.65,
                    py,
                    pz * 0.35 + sp.getZ() * 0.65,
                    1,
                    0.0,
                    0.01 + progress * 0.03,
                    0.0,
                    0.0);
        }

        // 中心聚能火花
        int flashCount = 1 + (int) (progress * 4.0f);
        serverLevel.sendParticles(ParticleTypes.END_ROD,
                sp.getX(), sp.getY() + 1.15, sp.getZ(),
                flashCount,
                0.18 + progress * 0.3,
                0.28 + progress * 0.2,
                0.18 + progress * 0.3,
                0.01);

        if (chargeTimer == CHARGE_TIME / 2 || chargeTimer == 4) {
            serverLevel.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                    0.55f + progress * 0.25f, 0.7f + progress * 0.45f);
        }
    }

    // ==================== AOE 释放（由 component tick 调用） ====================

    @Override
    public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int i, boolean bl) {
        if (level.isClientSide && entity instanceof Player player) {
            if (player.getMainHandItem().is(itemStack.getItem())) {
                if (!player.getCooldowns().isOnCooldown(itemStack.getItem())) {
                    player.displayClientMessage(Component
                            .translatable("message.noellesroles.yinyang_dash_tip",
                                    Component.keybind("key.drop").withStyle(ChatFormatting.GREEN))
                            .withStyle(ChatFormatting.GOLD), true);
                }
            }
        }
    }

    /**
     * 蓄力完成后自动释放范围攻击并向前冲刺
     */
    public static void releaseAOE(ServerPlayer sp) {
        ServerLevel serverLevel = sp.serverLevel();
        AABB aoe = sp.getBoundingBox().inflate(AOE_RANGE);
        List<Player> targets = serverLevel.getEntitiesOfClass(Player.class, aoe,
                p -> p != sp && GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p));
        int count = 0;
        for (Player target : targets) {
            count++;
            if (count >= 2) // 最多杀2人，避免超模
                break;
            GameUtils.killPlayer(target, true, sp, Noellesroles.id("yinyang_sword_aoe"));
        }

        spawnReleaseBurst(serverLevel, sp);

        // 音效
        serverLevel.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.85f, 1.35f);
        serverLevel.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.8f, 0.35f);

        // 执行向前冲刺
        performDashAfterCharge(serverLevel, sp);

        // 冷却 3秒
        sp.getCooldowns().addCooldown(sp.getMainHandItem().getItem(), 60);
    }

    /**
     * 蓄力完成后向前冲刺
     * 参考 StalkerKnifeItem 的冲刺逻辑
     */
    private static void performDashAfterCharge(ServerLevel serverLevel, ServerPlayer sp) {
        var comp = MonokumaPlayerComponent.KEY.maybeGet(sp).orElse(null);
        if (comp != null) {
            comp.dashAnimTimer = 8;
            comp.sync();
        }

        // 计算冲刺方向（基于玩家朝向）
        Vec3 lookVec = sp.getViewVector(1.0f);

        // 基础冲刺速度
        double dashSpeed = 1.75;

        // 计算冲刺向量（只考虑水平方向）
        Vec3 horizontalLook = new Vec3(lookVec.x, 0, lookVec.z).normalize();
        Vec3 dashVector = horizontalLook.scale(dashSpeed);

        // 应用位移
        sp.setDeltaMovement(dashVector.x, sp.getDeltaMovement().y, dashVector.z);

        // 同步给客户端
        sp.hurtMarked = true;

        // 清除坠落距离，避免摔落伤害
        sp.fallDistance = 0;

        spawnDashTrail(serverLevel, sp, dashVector, true);

        // 播放冲刺音效
        serverLevel.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    // ==================== Q键：粒子突进 ====================

    /**
     * 由技能键(Q键)触发的突进攻击（仅冲刺，不击杀）
     */
    public static void performDashAttack(Player user) {
        if (!(user instanceof ServerPlayer sp))
            return;
        if (sp.getCooldowns().isOnCooldown(user.getMainHandItem().getItem()))
            return;

        var comp = MonokumaPlayerComponent.KEY.maybeGet(sp).orElse(null);
        if (comp != null) {
            comp.dashAnimTimer = 8;
            comp.sync();
        }

        // 计算冲刺方向（基于玩家朝向）
        Vec3 lookVec = sp.getViewVector(1.0f);

        // 基础冲刺速度
        double dashSpeed = 2.5;

        // 计算冲刺向量（只考虑水平方向）
        Vec3 horizontalLook = new Vec3(lookVec.x, 0, lookVec.z).normalize();
        Vec3 dashVector = horizontalLook.scale(dashSpeed);

        // 应用位移
        sp.setDeltaMovement(dashVector.x, sp.getDeltaMovement().y, dashVector.z);

        // 同步给客户端
        sp.connection.send(new ClientboundSetEntityMotionPacket(sp.getId(), dashVector.scale(0.75f)));

        // 清除坠落距离，避免摔落伤害
        sp.fallDistance = 0;

        ServerLevel serverLevel = sp.serverLevel();

        spawnDashTrail(serverLevel, sp, dashVector, false);

        // 播放冲刺音效
        serverLevel.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.2f);

        // 突进冷却 2秒
        sp.getCooldowns().addCooldown(sp.getMainHandItem().getItem(), 40);
    }

    private static void spawnReleaseBurst(ServerLevel serverLevel, ServerPlayer sp) {
        double centerX = sp.getX();
        double centerY = sp.getY() + 1.0;
        double centerZ = sp.getZ();

        // 中心闪光爆发（增加数量）
        serverLevel.sendParticles(ParticleTypes.FLASH,
                centerX, centerY, centerZ,
                4, 0.0, 0.0, 0.0, 0.0);
        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                centerX, centerY, centerZ,
                8, 0.5, 0.35, 0.5, 0.0);

        // 扩展环数从4增加到6，扩大范围
        for (int ring = 0; ring < 6; ring++) {
            double radius = 1.2 + ring * 1.05;
            int points = 32 + ring * 14; // 增加每环的粒子点数
            double speed = 0.12 + ring * 0.035;
            for (int i = 0; i < points; i++) {
                double angle = (Math.PI * 2.0 * i) / points;
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                double px = centerX + cos * radius;
                double pz = centerZ + sin * radius;

                // 黑色粒子层
                serverLevel.sendParticles(
                        new DustParticleOptions(new Vector3f(0.02f, 0.02f, 0.02f), 1.05f + ring * 0.18f),
                        px, centerY + ring * 0.06, pz,
                        1, cos * speed, 0.01, sin * speed, 0.0);
                // 白色粒子层
                serverLevel.sendParticles(
                        new DustParticleOptions(new Vector3f(1.0f, 1.0f, 1.0f), 0.95f + ring * 0.16f),
                        centerX + cos * (radius * 0.82), centerY + 0.08 + ring * 0.06, centerZ + sin * (radius * 0.82),
                        1, cos * speed * 0.8, 0.02, sin * speed * 0.8, 0.0);
            }
        }

        // 增加末地棒粒子数量和扩散范围
        serverLevel.sendParticles(ParticleTypes.END_ROD,
                centerX, centerY, centerZ,
                10, 0.75, 0.5, 0.75, 0.08);

        // 添加额外的爆炸冲击波效果
        for (int wave = 0; wave < 3; wave++) {
            double waveRadius = 2.0 + wave * 1.5;
            int wavePoints = 12;
            for (int i = 0; i < wavePoints; i++) {
                double angle = (Math.PI * 2.0 * i) / wavePoints + (wave * 0.3);
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                double px = centerX + cos * waveRadius;
                double py = centerY + (wave - 1) * 0.3;
                double pz = centerZ + sin * waveRadius;

                serverLevel.sendParticles(
                        new DustParticleOptions(new Vector3f(0.5f, 0.5f, 0.5f), 0.8f),
                        px, py, pz,
                        1, cos * 0.15, 0.05, sin * 0.15, 0.0);
            }
        }
    }

    private static void spawnDashTrail(ServerLevel serverLevel, ServerPlayer sp, Vec3 dashVector, boolean empowered) {
        int trailCount = empowered ? 28 : 18;
        double sideSpread = empowered ? 0.42 : 0.28;

        for (int i = 0; i < trailCount; i++) {
            double progress = (double) i / trailCount;
            double offsetX = (sp.getRandom().nextDouble() - 0.5) * sideSpread;
            double offsetY = (sp.getRandom().nextDouble() - 0.5) * sideSpread;
            double offsetZ = (sp.getRandom().nextDouble() - 0.5) * sideSpread;

            serverLevel.sendParticles(
                    new DustParticleOptions(new Vector3f(0.0f, 0.0f, 0.0f), empowered ? 1.3f : 1.0f),
                    sp.getX() - dashVector.x * progress + offsetX,
                    sp.getY() + sp.getBbHeight() * 0.5 - dashVector.y * progress + offsetY,
                    sp.getZ() - dashVector.z * progress + offsetZ,
                    1, 0, 0, 0, 0.05);

            serverLevel.sendParticles(
                    new DustParticleOptions(new Vector3f(1.0f, 1.0f, 1.0f), empowered ? 1.25f : 1.0f),
                    sp.getX() - dashVector.x * progress + offsetX * 0.6,
                    sp.getY() + sp.getBbHeight() * 0.5 - dashVector.y * progress + offsetY * 0.6,
                    sp.getZ() - dashVector.z * progress + offsetZ * 0.6,
                    1, 0, 0, 0, 0.05);
        }

        if (empowered) {
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    sp.getX(), sp.getY() + 1.1, sp.getZ(),
                    10, 0.35, 0.25, 0.35, 0.05);
        }
    }
}
