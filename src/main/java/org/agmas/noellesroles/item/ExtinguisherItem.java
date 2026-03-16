package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.AdventureUsable;
import io.wifi.starrailexpress.SRE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.ParticleTypes;

import java.util.List;

/**
 * 灭火器
 * <p>
 * - 5点耐久
 * - 右键对人喷射：每使用一次消耗1点耐久
 * - 长按右键持续喷射：最多持续5秒，持续消耗耐久
 * - 对人喷射效果：缓慢 + 失明（持续1.5秒）
 * - 持续喷射同一人会刷新效果时间
 * - 如果被喷射的人被纵火犯浇湿，则清除浇湿状态
 * </p>
 */
public class ExtinguisherItem extends Item implements AdventureUsable {
    private static final int MAX_DURABILITY = 5;
    private static final int MAX_USE_DURATION = 5 * 20; // 5秒
    private static final int EFFECT_DURATION = 30; // 1.5秒（30 ticks）
    private static final int SPRAY_RANGE = 2; // 喷射范围2格

    public ExtinguisherItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 检查耐久
        if (stack.getDamageValue() >= MAX_DURABILITY) {
            if (!world.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("item.noellesroles.extinguisher.empty")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return InteractionResultHolder.fail(stack);
        }

        // 查找前方的玩家
        Player target = findTargetPlayer(world, player);

        if (target == null) {
            // 没有目标，直接喷射不开始蓄力
            return InteractionResultHolder.pass(stack);
        }

        // 单次右键喷射
        if (!world.isClientSide) {
            spray(player, target, stack);
        } else {
            // 客户端生成粒子效果
            spawnCloudParticles(player, target);
        }

        // 开始持续喷射
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.CROSSBOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return MAX_USE_DURATION;
    }

    @Override
    public void onUseTick(Level world, LivingEntity user, ItemStack stack, int remainingUseDuration) {
        if (!(user instanceof Player player)) {
            return;
        }

        // 每秒喷射一次（20 ticks）
        int ticksPassed = MAX_USE_DURATION - remainingUseDuration;
        if (ticksPassed % 20 == 0 && ticksPassed > 0) {
            // 检查耐久
            if (stack.getDamageValue() >= MAX_DURABILITY) {
                user.stopUsingItem();
                return;
            }

            // 查找目标
            Player target = findTargetPlayer(world, player);
            if (target != null) {
                // 服务端处理逻辑
                if (!world.isClientSide) {
                    spray(player, target, stack);
                } else {
                    // 客户端生成粒子效果
                    spawnCloudParticles(player, target);
                }
            }
        }
    }

    /**
     * 查找前方喷射范围内的玩家
     */
    private Player findTargetPlayer(Level world, Player player) {
        Player closestPlayer = null;
        double closestDistance = Double.MAX_VALUE;

        // 获取玩家视线方向
        var viewVector = player.getViewVector(1.0f);

        for (Player target : world.players()) {
            // 跳过自己
            if (target == player) continue;

            // 跳过不在生存模式的玩家
            if (!GameUtils.isPlayerAliveAndSurvival(target)) continue;

            // 计算距离
            double distance = player.distanceTo(target);
            if (distance > SPRAY_RANGE) continue;

            // 计算向量到目标的向量
            var toTarget = target.position().subtract(player.position());

            // 计算向量点积，判断是否在前方视野内
            double dotProduct = viewVector.dot(toTarget.normalize());

            // 如果目标在前方（点积 > 0.5，大约60度角内）
            if (dotProduct > 0.5) {
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestPlayer = target;
                }
            }
        }

        return closestPlayer;
    }

    /**
     * 喷射目标玩家（服务端逻辑）
     */
    private void spray(Player player, Player target, ItemStack stack) {
        Level world = player.level();

        // 记录物品使用
        if (SRE.REPLAY_MANAGER != null) {
            SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(),
                    BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }

        // 播放声音
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundSource.PLAYERS, 1.0f, 1.2f);

        // 消耗耐久
        if (!player.isCreative()) {
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }

        // 给目标添加效果
        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
                EFFECT_DURATION, 0, false, false));

        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.BLINDNESS,
                EFFECT_DURATION, 0, false, false));

        // 如果目标被纵火犯浇湿，清除浇湿状态
        try {
            var dousedComponent = pro.fazeclan.river.stupid_express.role.arsonist.cca.DousedPlayerComponent.KEY.get(target);
            if (dousedComponent != null && dousedComponent.getDoused()) {
                dousedComponent.setDoused(false);
                if (!world.isClientSide) {
                    player.displayClientMessage(
                            Component.translatable("item.noellesroles.extinguisher.cleaned_fuel")
                                    .withStyle(ChatFormatting.GREEN),
                            true);
                }
            }
        } catch (Exception e) {
            // 如果StupidExpress不可用，忽略清除浇湿状态
        }
    }

    /**
     * 生成云粒子效果（客户端）
     */
    private void spawnCloudParticles(Player player, Player target) {
        Level world = player.level();

        // 生成云粒子效果
        for (int i = 0; i < 10; i++) {
            double offsetX = (world.random.nextDouble() - 0.5) * 0.5;
            double offsetY = (world.random.nextDouble() - 0.5) * 0.5;
            double offsetZ = (world.random.nextDouble() - 0.5) * 0.5;
            world.addParticle(ParticleTypes.CLOUD,
                    target.getX() + offsetX,
                    target.getY() + 1.0 + offsetY,
                    target.getZ() + offsetZ,
                    0.0, 0.1, 0.0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("item.noellesroles.extinguisher.tooltip.durability",
                MAX_DURABILITY - stack.getDamageValue(), MAX_DURABILITY)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.noellesroles.extinguisher.tooltip.use")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.noellesroles.extinguisher.tooltip.hold")
                .withStyle(ChatFormatting.AQUA));
        super.appendHoverText(stack, context, tooltip, type);
    }
}
