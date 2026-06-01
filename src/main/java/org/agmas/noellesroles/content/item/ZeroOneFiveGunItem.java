package org.agmas.noellesroles.content.item;

import io.wifi.StarRailExpressID;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.particle.HandParticle;
import io.wifi.starrailexpress.client.render.TMMRenderLayers;
import io.wifi.starrailexpress.compat.CrosshairaddonsCompat;
import io.wifi.starrailexpress.content.item.SkinableItem;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.network.PacketTracker;
import io.wifi.starrailexpress.network.original.ShootMuzzleS2CPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/** 延迟执行的射击任务 */
record DelayedShotTask(long executeTick, ServerPlayer shooter, ServerPlayer target, boolean needsCooldown) {}

/**
 * 零一五 - 双发手枪
 * 
 * 右键开枪，开枪后2秒自动开第二枪
 * 一枪命中只会给3秒缓慢2
 * 同一玩家被命中两次则造成击杀
 * 冷却15秒，射程30格
 */
public class ZeroOneFiveGunItem extends SkinableItem {
    
    /** 第一次命中标记的持续时间（刻） = 3秒 */
    private static final int HIT_MARK_DURATION = 3 * 20;
    /** 射程30格 */
    private static final float RANGE = 30.0f;
    /** 冷却时间（刻） = 15秒 */
    private static final int COOLDOWN = 15 * 20;
    
    /** 记录每个玩家被零一五命中的目标 <攻击者UUID, <目标UUID, 剩余标记时间>> */
    private static final Map<UUID, Map<UUID, Integer>> HIT_MARKS = new HashMap<>();
    
    /** 延迟执行的射击任务列表 */
    private static final java.util.List<DelayedShotTask> DELAYED_SHOTS = new java.util.ArrayList<>();
    
    public ZeroOneFiveGunItem(Item.Properties settings) {
        super(settings);
    }
    
    /** 注册服务端tick事件 */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ZeroOneFiveGunItem::onServerTick);
    }
    
    private static void onServerTick(MinecraftServer server) {
        // 处理延迟射击任务
        processDelayedShots(server.getTickCount());
        // 每刻清理过期的命中标记
        tickCleanup();
    }
    
    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, 
            @NotNull InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        
        if (world.isClientSide) {
            SREGameWorldComponent gameComponent = SREClient.gameComponent;
            if (gameComponent != null) {
                SRERole role = gameComponent.getRole(user);
                if (role != null && !role.onUseGun(user)) {
                    return InteractionResultHolder.fail(stack);
                }
            }
            
            HitResult collision = getGunTarget(user);
            if (collision instanceof EntityHitResult entityHitResult) {
                Entity target = entityHitResult.getEntity();
                ClientPlayNetworking.send(new ZeroOneFiveShootPayload(target.getId(), false));
                CrosshairaddonsCompat.arrowHit();
            } else {
                ClientPlayNetworking.send(new ZeroOneFiveShootPayload(-1, false));
            }
            
            user.setXRot(user.getXRot() - 4.0F);
            spawnHandParticle();
            
            // 播放枪响音效
            world.playSound(user, user.getX(), user.getY(), user.getZ(),
                    TMMSounds.ITEM_REVOLVER_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);
        } else {
            SREGameWorldComponent gameComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY.get(world);
            SRERole role = gameComponent.getRole(user);
            if (role != null && !role.onUseGun(user)) {
                return InteractionResultHolder.fail(stack);
            }
            // 冷却在第二枪后添加，不在第一枪时
        }
        
        return InteractionResultHolder.consume(stack);
    }
    
    /**
     * 处理命中逻辑
     */
    public static void onHit(ServerPlayer shooter, ServerPlayer target) {
        UUID shooterUUID = shooter.getUUID();
        UUID targetUUID = target.getUUID();
        
        // 检查目标是否已经有标记
        Map<UUID, Integer> shooterMarks = HIT_MARKS.computeIfAbsent(shooterUUID, k -> new HashMap<>());
        
        if (shooterMarks.containsKey(targetUUID)) {
            // 第二次命中，直接击杀
            GameUtils.killPlayer(target, true, shooter, GameConstants.DeathReasons.ZERO_ONE_FIVE);
            shooterMarks.remove(targetUUID);
        } else {
            // 第一次命中，给3秒缓慢2
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, HIT_MARK_DURATION, 1, false, false));
            target.serverLevel().sendParticles(
                    ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                    target.getX(), target.getY()+1, target.getZ(),
                    5,0.2,0.2,0.2,0.35
            );
            // 标记目标
            shooterMarks.put(targetUUID, HIT_MARK_DURATION);
            
            // 0.45秒后自动开第二枪，添加到延迟队列
            long currentTick = target.level().getServer().getTickCount();
            DELAYED_SHOTS.add(new DelayedShotTask(currentTick + 9, shooter, target, true));
        }
    }



    public static int getCooldown() {
        return COOLDOWN;
    }
    
    /**
     * 自动开第二枪
     */
    private static void shootSecondShot(ServerPlayer shooter, ServerPlayer target) {
        // 播放射击音效
        shooter.level().playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(),
                TMMSounds.ITEM_REVOLVER_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);
        
        // 发送枪口闪光给所有追踪者（包括自己）
        for (ServerPlayer tracking : PlayerLookup.tracking(shooter)) {
            PacketTracker.sendToClient(tracking, new ShootMuzzleS2CPayload(shooter.getId()));
        }
        PacketTracker.sendToClient(shooter, new ShootMuzzleS2CPayload(shooter.getId()));
        
        // 生成弹道粒子效果
        HitResult hitResult = getGunTarget(shooter);
        Vec3 hitPos;
        if (hitResult.getType() != HitResult.Type.MISS) {
            hitPos = hitResult.getLocation();
        } else {
            Vec3 eyePos = shooter.getEyePosition(1.0F);
            Vec3 lookVec = shooter.getViewVector(1.0F);
            hitPos = eyePos.add(lookVec.scale(RANGE));
        }
        spawnBulletTrail(shooter, hitPos);
        
        // 如果目标仍然存活且在范围内，造成击杀
        if (GameUtils.isPlayerAliveAndSurvival(target)) {
            double distSq = shooter.distanceToSqr(target);
            if (distSq <= RANGE * RANGE) {
                GameUtils.killPlayer(target, true, shooter, GameConstants.DeathReasons.ZERO_ONE_FIVE);
                
                // 移除标记
                UUID shooterUUID = shooter.getUUID();
                UUID targetUUID = target.getUUID();
                Map<UUID, Integer> shooterMarks = HIT_MARKS.get(shooterUUID);
                if (shooterMarks != null) {
                    shooterMarks.remove(targetUUID);
                }
            }
        }

        // 两枪后进入15秒冷却
    }
    
    /**
     * 在子弹路径上生成粒子轨迹
     */
    private static void spawnBulletTrail(ServerPlayer shooter, Vec3 hitPos) {
        Vec3 startPos = shooter.getEyePosition(1.0F);
        Vec3 direction = hitPos.subtract(startPos);
        double distance = direction.length();
        if (distance <= 0) return;
        direction = direction.normalize();
        
        double stepSize = 0.5;
        int particleCount = (int) (distance / stepSize);
        
        for (int i = 0; i < particleCount; i++) {
            double ratio = (double) i / particleCount;
            Vec3 particlePos = startPos.add(
                    direction.x * distance * ratio,
                    direction.y * distance * ratio,
                    direction.z * distance * ratio);
            
            double offsetX = (shooter.level().getRandom().nextFloat() - 0.5) * 0.2;
            double offsetY = (shooter.level().getRandom().nextFloat() - 0.5) * 0.2;
            double offsetZ = (shooter.level().getRandom().nextFloat() - 0.5) * 0.2;
            
            ((ServerLevel) shooter.level()).sendParticles(
                    ParticleTypes.SMOKE,
                    particlePos.x + offsetX,
                    particlePos.y + offsetY,
                    particlePos.z + offsetZ,
                    1, 0, 0.02, 0, 0.01);
        }
    }
    
    /**
     * 清理过期标记（每刻调用）
     */
    public static void tickCleanup() {
        // 清理过期标记
        HIT_MARKS.entrySet().removeIf(entry -> {
            entry.getValue().entrySet().removeIf(mark -> {
                int remaining = mark.getValue() - 1;
                if (remaining <= 0) {
                    return true;
                }
                mark.setValue(remaining);
                return false;
            });
            return entry.getValue().isEmpty();
        });
    }
    
    /**
     * 处理延迟射击（在世界tick时调用）
     */
    public static void processDelayedShots(long currentTick) {
        Iterator<DelayedShotTask> iterator = DELAYED_SHOTS.iterator();
        while (iterator.hasNext()) {
            DelayedShotTask task = iterator.next();
            if (currentTick >= task.executeTick()) {
                iterator.remove();
                ServerPlayer shooter = task.shooter();
                if (GameUtils.isPlayerAliveAndSurvival(shooter)) {
                    // 检查是否持有零一五枪，没持有则取消第二次自动开枪
                    ItemStack mainHand = shooter.getMainHandItem();
                    ItemStack offHand = shooter.getOffhandItem();
                    if (!mainHand.is(ModItems.ZERO_ONE_FIVE_GUN) && !offHand.is(ModItems.ZERO_ONE_FIVE_GUN)) {
                        continue;
                    }
                    
                    // 射线检测当前瞄准的目标
                    ServerPlayer currentTarget = findCurrentTarget(shooter);
                    if (currentTarget != null) {
                        // 自动射击（不受冷却影响）
                        shootSecondShot(shooter, currentTarget);
                    }
                }
            }
        }
    }
    
    /**
     * 查找当前瞄准的目标
     * 直接射线检测当前瞄准的目标
     */
    private static ServerPlayer findCurrentTarget(ServerPlayer shooter) {
        HitResult hitResult = getGunTarget(shooter);
        if (hitResult instanceof EntityHitResult entityHitResult) {
            Entity entity = entityHitResult.getEntity();
            if (entity instanceof ServerPlayer target && GameUtils.isPlayerAliveAndSurvival(target)) {
                return target;
            }
        }
        return null;
    }
    
    /**
     * 清理玩家数据
     */
    public static void clearPlayerData(UUID playerUUID) {
        HIT_MARKS.remove(playerUUID);
        for (Map<UUID, Integer> marks : HIT_MARKS.values()) {
            marks.remove(playerUUID);
        }
    }
    
    public static HitResult getGunTarget(Player user) {
        return ProjectileUtil.getHitResultOnViewVector(user, entity -> {
            if (entity instanceof Player player) {
                return GameUtils.isPlayerAliveAndSurvival(player);
            }
            return false;
        }, RANGE);
    }
    
    public static void spawnHandParticle() {
        HandParticle handParticle = (new HandParticle())
                .setTexture(StarRailExpressID.watheId("textures/particle/gunshot.png"))
                .setPos(0.1F, 0.275F, -0.2F).setMaxAge(3.0F).setSize(0.5F).setVelocity(0.0F, 0.0F, 0.0F)
                .setLight(15, 15).setAlpha(new float[] { 1.0F, 0.1F }).setRenderLayer(TMMRenderLayers::additive);
        SREClient.handParticleManager.spawn(handParticle);
    }
    
    @Override
    public String getItemSkinType() {
        return "revolver"; // 沿用一次性手枪的材质
    }
}
