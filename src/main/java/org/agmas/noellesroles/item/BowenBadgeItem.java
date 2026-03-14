package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.AdventureUsable;
import io.wifi.starrailexpress.SRE;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.agmas.noellesroles.Noellesroles;

import io.wifi.starrailexpress.block.SmallDoorBlock;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.FunnyItems;

public class BowenBadgeItem extends Item implements AdventureUsable{

    public BowenBadgeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);
        player.startUsingItem(usedHand);
        // 蓄力时没有声音
        return InteractionResultHolder.consume(itemStack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 10 * 20;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!(entity instanceof Player player))
            return;
        if (level.isClientSide)
            return;

        // 只在冲刺旋转期间检测碰撞
        if (!player.isAutoSpinAttack())
            return;

        Vec3 playerPos = player.position();
        Vec3 movement = player.getDeltaMovement();

        // 以当前移动方向为碰撞检测朝向
        Vec3 dashDir = movement.multiply(1, 0, 1).normalize();
        Vec3 frontCenter = playerPos.add(dashDir.scale(0.8));
        AABB collisionBox = new AABB(frontCenter, frontCenter).inflate(0.6, 0.9, 0.6);

        for (var e : level.getEntities(player, collisionBox)) {
            if (!(e instanceof Player targetPlayer))
                continue;

            // 撞到目标：停止水平移动并推开旁边的人
            player.setDeltaMovement(0, player.getDeltaMovement().y, 0);

            // 计算击退向量（从玩家指向目标）
            Vec3 knockbackDir = targetPlayer.position().subtract(playerPos).multiply(1, 0, 1).normalize();
            // 施加击退效果，将目标推开
            targetPlayer.push(knockbackDir.x * 2.5, 0.5, knockbackDir.z * 2.5);

            if (GameUtils.isPlayerAliveAndSurvival(targetPlayer)) {
                GameUtils.killPlayer(targetPlayer, true, player, Noellesroles.id("bowen"));
            }
            break;
        }
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack itemStack, int remainingUseDuration) {
        if (level instanceof ServerLevel serverLevel && livingEntity instanceof Player player) {
            // 计算蓄力进度 (0.0 - 1.0)，总时长为 getUseDuration 返回的 ticks
            int maxDuration = 40;
            int usedTicks = maxDuration - remainingUseDuration;
            float progress = Math.min(1.0f, (float) usedTicks / maxDuration);

            // 获取玩家位置和朝向
            double x = player.getX();
            double y = player.getY() + 1.5; // 从胸部/头部高度发射
            double z = player.getZ();

            float yaw = player.getYRot();
            float pitch = player.getXRot();

            // 将角度转换为弧度
            float yawRad = yaw * ((float) Math.PI / 180F);
            // float pitchRad = pitch * ((float) Math.PI / 180F);

            // 计算前方偏移量，随蓄力时间增加范围
            float radius = 0.5f + (progress * 1.5f);
            int particleCount = 2 + (int) (progress * 4); // 粒子数量随蓄力增加

            for (int i = 0; i < particleCount; i++) {
                // 在玩家周围随机分布，但偏向移动/朝向方向
                float angle = (level.random.nextFloat() * 360F) * ((float) Math.PI / 180F);
                float horizontalOffset = radius * Mth.cos(angle);
                float verticalOffset = (level.random.nextFloat() - 0.5f) * 1.5f;
                float depthOffset = radius * Mth.sin(angle);

                // 旋转偏移量以匹配玩家朝向 (简单的水平环绕效果)
                double offsetX = horizontalOffset * Mth.cos(yawRad) - depthOffset * Mth.sin(yawRad);
                double offsetZ = horizontalOffset * Mth.sin(yawRad) + depthOffset * Mth.cos(yawRad);

                double particleX = x + offsetX;
                double particleY = y + verticalOffset;
                double particleZ = z + offsetZ;

                // 发送炫酷的粒子特效 (使用 END_ROD 或 CRIT 作为能量聚集效果，颜色可自定义若需更复杂)
                // 这里使用 PORTAL 粒子模拟紫色/蓝色的能量波动，或者 CRIT 表示锐利能量
                serverLevel.sendParticles(
                        net.minecraft.core.particles.ParticleTypes.PORTAL,
                        particleX,
                        particleY,
                        particleZ,
                        1,
                        0.0, 0.0, 0.0,
                        0.05 * progress // 速度/扩散随蓄力变化
                );

                // 偶尔添加闪光粒子
                if (level.random.nextInt(100) == 0) {
                    serverLevel.sendParticles(
                            net.minecraft.core.particles.ParticleTypes.FLASH,
                            particleX,
                            particleY,
                            particleZ,
                            1,
                            0.0, 0.0, 0.0,
                            0.0);
                }
            }
        }
        super.onUseTick(level, livingEntity, itemStack, remainingUseDuration);
    }

    @Override
    public void releaseUsing(ItemStack itemStack, Level level, LivingEntity livingEntity, int remainingUseDuration) {
        if (!(livingEntity instanceof Player player))
            return;
        if (this.getUseDuration(itemStack, livingEntity) - remainingUseDuration < 20 * 2) {
            return;
        }
        if (livingEntity.isSpectator()) {
            return;
        }
        var holder = EnchantmentHelper
                .pickHighestLevel(itemStack, EnchantmentEffectComponents.TRIDENT_SOUND)
                .orElse(SoundEvents.TRIDENT_THROW);
        player.awardStat(Stats.ITEM_USED.get(this));

        float f = 1f;
        float g = player.getYRot();

        // 水平朝向向量
        float k = -Mth.sin(g * ((float) Math.PI / 180F));
        float m = Mth.cos(g * ((float) Math.PI / 180F));
        float horizLen = Mth.sqrt(k * k + m * m);
        float kNorm = k / horizLen;
        float mNorm = m / horizLen;

        // ── 击退周围的玩家（排除正前方碰撞目标）──
        Vec3 playerPos = player.position();
        AABB nearbyBox = new AABB(playerPos, playerPos).inflate(3.0, 1.5, 3.0);

        Vec3 dashFront = playerPos.add(kNorm * 2.5, 0, mNorm * 2.5);
        AABB collisionBox = new AABB(dashFront, dashFront).inflate(0.8, 1.0, 0.8);

        for (var entity : level.getEntities(player, nearbyBox)) {
            if (!(entity instanceof LivingEntity target))
                continue;
            if (collisionBox.intersects(target.getBoundingBox()))
                continue;

            Vec3 knockback = target.position()
                    .subtract(playerPos)
                    .multiply(1, 0, 1)
                    .normalize()
                    .scale(1.5);
            target.push(knockback.x, 0, knockback.z);

            // 被击退实体产生冲击波粒子
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                        target.getX(), target.getY() + 1, target.getZ(),
                        1, 0, 0, 0, 0);
            }
        }

        // ── 启动平飞冲刺与炫酷粒子特效 ──
        player.push(kNorm * f, 0.0, mNorm * f);
        player.startAutoSpinAttack(20, 8.0F, itemStack);

        if (player.onGround()) {
            player.move(MoverType.SELF, new Vec3(0.0, 1.2, 0.0));
        }

        // 生成更炫酷的冲刺粒子效果
        if (level instanceof ServerLevel serverLevel) {
            // 1. 核心爆发粒子 (END_ROD 模拟能量束)
            for (int i = 0; i < 20; i++) {
                double angle = level.random.nextDouble() * Math.PI * 2;
                double radius = 0.5 + level.random.nextDouble() * 0.5;
                double px = player.getX() + Math.cos(angle) * radius;
                double pz = player.getZ() + Math.sin(angle) * radius;
                double py = player.getY() + 1.0 + level.random.nextDouble() * 0.5;

                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                        px, py, pz,
                        1,
                        (level.random.nextDouble() - 0.5) * 0.2,
                        (level.random.nextDouble() - 0.5) * 0.2,
                        (level.random.nextDouble() - 0.5) * 0.2,
                        0.05);
            }

            // 2. 环形冲击波 (PORTAL 粒子模拟波纹扩散)
            for (int ring = 0; ring < 3; ring++) {
                int count = 15 + ring * 5;
                for (int i = 0; i < count; i++) {
                    double angle = (i / (double) count) * Math.PI * 2;
                    double r = 1.0 + ring * 0.8;
                    double px = player.getX() + Math.cos(angle) * r;
                    double pz = player.getZ() + Math.sin(angle) * r;
                    double py = player.getY() + 1.0;

                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                            px, py, pz,
                            1,
                            0, 0, 0,
                            0.1);
                }
            }

            // 3. 拖尾火花 (FLAME 或 CRIT)
            for (int i = 0; i < 10; i++) {
                double offsetX = (level.random.nextDouble() - 0.5) * 0.5;
                double offsetZ = (level.random.nextDouble() - 0.5) * 0.5;
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                        player.getX() + offsetX, player.getY() + 1.5, player.getZ() + offsetZ,
                        1, 0, 0, 0, 0.2);
            }
        }

        level.playSound(null, player, holder.value(), SoundSource.PLAYERS, 1.0F,
                0.8F + level.random.nextFloat() * 0.4F);
        if (GameUtils.isPlayerAliveAndSurvival(player)) {
            applyCooldownToItem(player, itemStack);
        }
    }

    // 攻击方式:
    // 初始获得波纹勋章，蓄力3s挥出一拳，击飞旁边的人，目标死亡,向前冲刺，有小脑惩罚,冷却60s
    private void applyCooldownToItem(Player player, ItemStack stack) {
        var cooldowns = player.getCooldowns();
        if (!stack.isEmpty() && !cooldowns.isOnCooldown(stack.getItem())) {
            cooldowns.addCooldown(stack.getItem(), 20 * 30);
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = world.getBlockState(pos);
        if (player.getCooldowns().isOnCooldown(FunnyItems.BOWEN_BADGE))
            return InteractionResult.PASS;
        player.getCooldowns().addCooldown(FunnyItems.BOWEN_BADGE, 20);
        if (state.getBlock() instanceof SmallDoorBlock) {
            BlockPos lowerPos = state.getValue(SmallDoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            if (world.getBlockEntity(lowerPos) instanceof SmallDoorBlockEntity entity) {
                if (player.isShiftKeyDown()) {
                    entity.jam();

                    if (!player.isCreative()) {
                        if (SRE.REPLAY_MANAGER != null) {
                            SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(), BuiltInRegistries.ITEM.getKey(this));
                        }

                    }

                    if (!world.isClientSide)
                        world.playSound(null, lowerPos.getX() + .5f, lowerPos.getY() + 1, lowerPos.getZ() + .5f,
                                TMMSounds.ITEM_LOCKPICK_DOOR, SoundSource.BLOCKS, 1f, 1f);
                    return InteractionResult.SUCCESS;
                }
            }

            return InteractionResult.PASS;
        }

        return super.useOn(context);
    }
    // public InteractionResult useOn(UseOnContext context) {
    // Player player = context.getPlayer();
    // Level world = context.getLevel();
    // BlockPos pos = context.getClickedPos();
    // BlockState state = world.getBlockState(pos);
    // if (state.getBlock() instanceof SmallDoorBlock) {
    // return InteractionResult.PASS;
    // } else {
    // if (player != null) {
    // context.getItemInHand().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
    // }
    // return super.useOn(context);
    // }
    // }
}
