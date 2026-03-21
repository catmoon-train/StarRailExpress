package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.compat.CrosshairaddonsCompat;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMParticles;
import io.wifi.starrailexpress.item.KnifeItem;
import io.wifi.starrailexpress.network.original.KnifeStabPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.blood.BloodMain;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

public class StalkerKnifeItem extends KnifeItem {

    public StalkerKnifeItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext useOnContext) {
        if (useOnContext.getHand()==InteractionHand.OFF_HAND)return InteractionResult.PASS;
        return super.useOn(useOnContext);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (user.isSpectator()) {
            return;
        }
        Integer i = stack.get(SREDataComponentTypes.WEAPON_USED_TIME);
        if (i == null)return;
        if (remainingUseTicks >= this.getUseDuration(stack, user) - i.intValue() || !(user instanceof Player attacker)
                || !world.isClientSide)
            return;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(world);
        final var role = game.getRole(attacker);
        if (role != null) {
            if (!role.onUseKnife(attacker)) {
                return;
            }
        }
        HitResult collision = getKnifeTarget(attacker);
        if (collision instanceof EntityHitResult entityHitResult) {
            Entity target = entityHitResult.getEntity();
            if (SRE.REPLAY_MANAGER != null) {
                SRE.REPLAY_MANAGER.recordItemUse(user.getUUID(), BuiltInRegistries.ITEM.getKey(this));
            }
            ClientPlayNetworking.send(new KnifeStabPayload(target.getId()));
            CrosshairaddonsCompat.onAttack(target);
            
            // ── 客户端炫酷击中特效 ────────────────────────────────────────
            spawnHitEffects(world, attacker, target);

        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, @NotNull Player user, InteractionHand hand)
    {
        if (hand == InteractionHand.OFF_HAND)return InteractionResultHolder.pass(user.getItemInHand(hand));
        if (user.isCrouching()){
            user.getMainHandItem().set(SREDataComponentTypes.WEAPON_USED_TIME,3);
        }else user.getMainHandItem().set(SREDataComponentTypes.WEAPON_USED_TIME,10);
        return super.use(world, user, hand);
    }

    @Override
    public String getItemSkinType() {
        return "knife";
    }

    @Override
    public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int i, boolean bl) {
        
        super.inventoryTick(itemStack, level, entity, i, bl);
    }
    
    /**
     * 生成炫酷的击中特效（客户端）
     * 包含：血液飞溅、红色粒子爆发、冲击波效果、音效
     */
    private void spawnHitEffects(Level world, Player attacker, Entity target) {
        if (!(world instanceof net.minecraft.client.multiplayer.ClientLevel clientWorld)) {
            return;
        }
        
        Random rand = new Random();
        Vec3 targetPos = target.getEyePosition();
        
        // 1. 播放刺击音效（增强版）
        target.playSound(io.wifi.starrailexpress.index.TMMSounds.ITEM_KNIFE_STAB, 1.2f, 0.8f + rand.nextFloat() * 0.4f);
        
        // 2. 生成定向血液飞溅效果
        spawnBloodSplash(clientWorld, targetPos, target);
        
        // 3. 生成红色粒子爆发效果
        spawnRedParticleBurst(clientWorld, targetPos, rand);
        
        // 4. 生成冲击波环状粒子
        spawnShockwaveRing(clientWorld, targetPos, rand);
        
        // 5. 生成烟雾/尘埃粒子
        spawnDustParticles(clientWorld, targetPos, rand);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        Integer i = stack.get(SREDataComponentTypes.WEAPON_USED_TIME);
        if (i ==null ){
            return UseAnim.SPEAR;
        }else if (i.intValue()==2){
            return UseAnim.BOW;
        }else return UseAnim.SPEAR;
    }

    /**
     * 生成血液飞溅效果
     */
    private void spawnBloodSplash(net.minecraft.client.multiplayer.ClientLevel world, Vec3 pos, Entity target) {
        int bloodAmount = 5 + target.level().random.nextInt(8);
        
        for (int i = 0; i < bloodAmount; i++) {
            double angle = Math.random() * Math.PI * 2;
            double speed = 0.3 + Math.random() * 0.5;
            double upwardVel = 0.2 + Math.random() * 0.3;
            
            double velX = Math.cos(angle) * speed * (0.5 + Math.random());
            double velY = upwardVel;
            double velZ = Math.sin(angle) * speed * (0.5 + Math.random());
            
            // 使用血液粒子
            world.addParticle(
                org.agmas.noellesroles.blood.BloodMain.BLOOD_PARTICLE,
                true,
                pos.x + (Math.random() - 0.5) * 0.5,
                pos.y + (Math.random() - 0.5) * 0.5,
                pos.z + (Math.random() - 0.5) * 0.5,
                velX, velY, velZ
            );
        }
    }
    
    /**
     * 生成红色粒子爆发效果
     */
    private void spawnRedParticleBurst(net.minecraft.client.multiplayer.ClientLevel world, Vec3 pos, Random rand) {
        int particleCount = 40;
        
        for (int i = 0; i < particleCount; i++) {
            // 球形扩散
            double theta = rand.nextDouble() * Math.PI * 2;
            double phi = Math.acos(2 * rand.nextDouble() - 1);
            double speed = 0.2 + rand.nextDouble() * 0.6;
            
            double velX = Math.sin(phi) * Math.cos(theta) * speed;
            double velY = Math.cos(phi) * speed;
            double velZ = Math.sin(phi) * Math.sin(theta) * speed;
            
            // 使用末地烛粒子（红色调）
            world.addParticle(
                ParticleTypes.END_ROD,
                pos.x + (rand.nextDouble() - 0.5) * 0.3,
                pos.y + (rand.nextDouble() - 0.5) * 0.3,
                pos.z + (rand.nextDouble() - 0.5) * 0.3,
                velX * 0.5, velY * 0.5, velZ * 0.5
            );
        }
    }
    
    /**
     * 生成冲击波环状粒子效果
     */
    private void spawnShockwaveRing(net.minecraft.client.multiplayer.ClientLevel world, Vec3 pos, Random rand) {
        int ringParticles = 10;
        double ringRadius = 1;
        
        for (int ring = 0; ring < 2; ring++) {
            double yOffset = ring * 0.3;
            for (int i = 0; i < ringParticles; i++) {
                double angle = (2 * Math.PI * i) / ringParticles;
                double x = pos.x + Math.cos(angle) * ringRadius;
                double z = pos.z + Math.sin(angle) * ringRadius;
                
                // 使用烟雾粒子模拟冲击波
                world.addParticle(
                    ParticleTypes.CLOUD,
                    x,
                    pos.y + yOffset,
                    z,
                    0, 0.02, 0
                );
            }
            ringRadius += 0.3;
        }
    }
    
    /**
     * 生成烟雾/尘埃粒子效果
     */
    private void spawnDustParticles(net.minecraft.client.multiplayer.ClientLevel world, Vec3 pos, Random rand) {
        int dustCount = 12;
        
        for (int i = 0; i < dustCount; i++) {
            double offsetX = (rand.nextDouble() - 0.5) * 0.6;
            double offsetY = (rand.nextDouble() - 0.5) * 0.6;
            double offsetZ = (rand.nextDouble() - 0.5) * 0.6;
            
            world.addParticle(
                ParticleTypes.SMOKE,
                pos.x + offsetX,
                pos.y + offsetY,
                pos.z + offsetZ,
                0, 0.05, 0
            );
        }
    }
    
    /**
     * 击中目标后向前突进一小段距离
     * @param world 游戏世界
     * @param attacker 攻击者
     * @param target 被击中的目标
     */
    public static void performDashOnHit(Level world, Player attacker, Entity target) {
        if (target==null)return;
        if (!world.isClientSide) {
            // 服务端处理位移和同步
            if (attacker instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                // 计算朝向目标的水平方向向量
                Vec3 toTarget = target.position().subtract(attacker.position()).normalize();
                Vec3 horizontalDash = new Vec3(toTarget.x, 0, toTarget.z).normalize();
                
                // 突进距离（格）
                double dashDistance = 1;
                
                // 设置水平位移
                Vec3 dashVector = horizontalDash.scale(dashDistance);
                
                // 应用位移
                attacker.setDeltaMovement(dashVector.x, attacker.getDeltaMovement().y, dashVector.z);
                
                // 同步给客户端
                serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer.getId(), dashVector.scale(0.75f)));
                
                // 清除坠落距离，避免摔落伤害
                attacker.fallDistance = 0;
            }
        } else {
            // 客户端播放突进粒子效果
            Vec3 dashDir = target.position().subtract(attacker.position()).normalize();
            
            // 突进轨迹粒子
            for (int i = 0; i < 8; i++) {
                double progress = (double) i / 8.0;
                world.addParticle(
                    ParticleTypes.END_ROD,
                    attacker.getX() + dashDir.x * progress * 0.8,
                    attacker.getY() + 0.5,
                    attacker.getZ() + dashDir.z * progress * 0.8,
                    0, 0.02, 0
                );
            }
        }
    }
    
    /**
     * 尝试执行蓄力冲刺（仅在蓄力时可用）
     * 特性：
     * - 必须在地面上才能冲刺
     * - 支持 Y 轴方向冲刺（向上/向下看决定）
     * - 冷却时间 3 秒
     * - 消耗蓄力状态
     * 
     * @param player 玩家实体
     * @param stack 当前物品栈
     * @param world 游戏世界
     * @return 是否成功执行冲刺
     */
    public boolean tryDashAttack(Player player, ItemStack stack, Level world) {
        if (!(player instanceof ServerPlayer serverPlayer))return false;
        // 检查是否在地面上
        if (!player.onGround()) {
            return false;
        }
        
//        // 检查是否在蓄力中
//        if (!player.isUsingItem() || player.getUseItem() != stack) {
//            return false;
//        }
        


//        // 获取蓄力进度（0.0 - 1.0）
//        int ticksUsingItem = player.getTicksUsingItem();
//        Integer maxDuration = stack.get(SREDataComponentTypes.WEAPON_USED_TIME);
//        if (maxDuration == null || maxDuration <= 0) {
//            return false;
//        }
        
//        float chargeProgress = Math.min((float) ticksUsingItem / maxDuration, 1.0f);
        
        // 至少需要 50% 蓄力才能触发冲刺
//        if (chargeProgress < 0.1f) {
//            if (world.isClientSide) {
//                player.displayClientMessage(
//                    net.minecraft.network.chat.Component.translatable(
//                        "message.stalkerknife.dash_need_charge"
//                    ),
//                    true
//                );
//            }
//            return false;
//        }
        

        

        
        // 计算冲刺方向
        // 根据玩家的抬头/低头角度决定 Y 轴分量
        float xRot = player.getXRot();
        double forwardFactor = Math.abs(Math.cos(Math.toRadians(xRot)));
        double verticalFactor = -Math.sin(Math.toRadians(xRot));
        
        // 基础冲刺速度
        double dashSpeed = 2.5;
        
        // 根据蓄力程度增加冲刺距离
        dashSpeed *= (0.7 + 1 * 0.6); // 0.7x - 1.3x
        
        // 计算冲刺向量
        net.minecraft.world.phys.Vec3 lookVec = player.getViewVector(1.0f);
        net.minecraft.world.phys.Vec3 dashVector = new net.minecraft.world.phys.Vec3(
            lookVec.x * forwardFactor * dashSpeed,
            verticalFactor * dashSpeed * 0.8, // Y 轴分量稍微减弱
            lookVec.z * forwardFactor * dashSpeed
        );
        
        // 应用冲量
        player.setDeltaMovement(dashVector);
        serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer.getId(), dashVector.scale(0.75f)));
        
        // 播放冲刺音效和粒子效果

            // 冲刺音效
            player.playSound(io.wifi.starrailexpress.index.TMMSounds.ITEM_KNIFE_PREPARE, 0.8f, 1.5f);
            
            // 生成冲刺轨迹粒子
            for (int i = 0; i < 20; i++) {
                double progress = (double) i / 20.0;
                ((ServerLevel) world).sendParticles(
                    ParticleTypes.END_ROD,
                    player.getX() - dashVector.x * progress,
                    player.getY() - dashVector.y * progress + player.getBbHeight() * 0.5,
                    player.getZ() - dashVector.z * progress,
                    1,
                    0, 0, 0, 0
                );
            }
            
            // 生成冲击波粒子
            for (int i = 0; i < 12; i++) {
                double angle = (2 * Math.PI * i) / 12;
                double radius = 0.5;
                ((ServerLevel) world).sendParticles(
                    ParticleTypes.CLOUD,
                    player.getX() + Math.cos(angle) * radius,
                    player.getY(),
                    player.getZ() + Math.sin(angle) * radius,
                    1,
                    0, 0.1, 0, 0
                );
            }

        
        return true;
    }
}
