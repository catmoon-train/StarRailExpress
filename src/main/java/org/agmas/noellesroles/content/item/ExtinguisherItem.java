/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.AdventureUsable;
import io.wifi.starrailexpress.util.ParticleFx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.utils.ReplayEventLog;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SEEffects;
import pro.fazeclan.river.stupid_express.role.arsonist.ArsonistRoleData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 灭火器：60点耐久的蓄力喷雾道具。
 * <p>
 * - 右键立即消耗1点耐久并喷雾一次
 * - 蓄力期间每秒再消耗1点耐久，持续对前方5格圆锥内的所有玩家喷雾
 * - 喷雾施加缓慢与失明（1.5秒），清除纵火犯浇的汽油与点燃效果
 * - 被喷到的玩家5秒内免疫纵火犯的燃烧效果，燃烧值每秒降低20点
 * - 蓄力期间持续播放 entity.breeze.slide，并由客户端生成白色喷雾粒子
 * </p>
 */
public class ExtinguisherItem extends Item implements AdventureUsable {
    private static final int MAX_DURABILITY = 60;
    private static final int MAX_USE_DURATION = MAX_DURABILITY * 20;
    private static final int EFFECT_DURATION = 30;
    private static final int IMMUNITY_DURATION = 5 * 20;
    private static final int SPRAY_RANGE = 5;
    private static final double CONE_DOT = 0.5D;
    private static final int HEAT_REDUCTION = 20;

    public ExtinguisherItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getDamageValue() >= MAX_DURABILITY) {
            if (!world.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("item.noellesroles.extinguisher.empty")
                                .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (!world.isClientSide) {
            spray(player, stack);
            sendSprayParticles(player);
        }
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
        if (!(user instanceof Player player) || world.isClientSide) {
            return;
        }
        int ticksPassed = MAX_USE_DURATION - remainingUseDuration;
        if (ticksPassed > 0 && ticksPassed % 4 == 0) {
            sendSprayParticles(player);
        }
        if (ticksPassed > 0 && ticksPassed % 10 == 0) {
            playSpraySound(world, player);
        }
        if (ticksPassed <= 0 || ticksPassed % 20 != 0) {
            return;
        }
        if (stack.isEmpty() || stack.getDamageValue() >= MAX_DURABILITY) {
            user.stopUsingItem();
            return;
        }
        spray(player, stack);
        if (stack.isEmpty()) user.stopUsingItem();
    }

    /** 一次喷雾结算：消耗耐久、处理圆锥内玩家、播放音效并广播白色喷雾特效。 */
    private void spray(Player player, ItemStack stack) {
        Level world = player.level();
        if (SRE.REPLAY_MANAGER != null) {
            SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(), BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
        playSpraySound(world, player);
        if (!player.isCreative()) {
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }

        List<Player> targets = findTargets(world, player);
        ArsonistRoleData arsonist = world instanceof ServerLevel ? ArsonistRoleData.find(world) : null;
        boolean cleaned = false;
        for (Player target : targets) {
            target.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, EFFECT_DURATION, 0, false, false));
            target.addEffect(new MobEffectInstance(
                    MobEffects.BLINDNESS, EFFECT_DURATION, 0, false, false));
            target.addEffect(new MobEffectInstance(
                    SEEffects.EXTINGUISH_IMMUNITY, IMMUNITY_DURATION, 0, false, false));
            if (arsonist != null && target instanceof ServerPlayer serverTarget) {
                UUID targetId = serverTarget.getUUID();
                boolean hadGasoline = arsonist.isDoused(targetId);
                boolean hadBurning = serverTarget.hasEffect(SEEffects.BURNING);
                cleaned |= arsonist.extinguish(serverTarget, HEAT_REDUCTION);
                if (hadGasoline && !arsonist.isDoused(targetId)) {
                    ReplayEventLog.record("replay.event.extinguisher.clean_gasoline",
                            ReplayEventLog.player(player), ReplayEventLog.player(serverTarget));
                }
                if (hadBurning && !serverTarget.hasEffect(SEEffects.BURNING)) {
                    ReplayEventLog.record("replay.event.extinguisher.extinguish_flames",
                            ReplayEventLog.player(player), ReplayEventLog.player(serverTarget));
                }
            }
        }
        if (cleaned && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("item.noellesroles.extinguisher.cleaned_fuel")
                            .withStyle(ChatFormatting.GREEN), true);
        }
    }

    /** 蓄力期间持续广播白色喷雾特效。 */
    private void sendSprayParticles(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        ParticleFx.sendCustom(serverLevel, StupidExpress.id("extinguisher_spray"),
                player.getEyePosition(), 1, player.getYRot(), player.getXRot(), (float) SPRAY_RANGE);
    }

    private void playSpraySound(Level world, Player player) {
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREEZE_SLIDE, SoundSource.PLAYERS, 0.9F, 1.0F);
    }

    /** 前方5格圆锥内、且未被墙体遮挡的所有存活玩家。 */
    private List<Player> findTargets(Level world, Player player) {
        Vec3 origin = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        List<Player> result = new ArrayList<>();
        for (Player target : world.players()) {
            if (target == player || !GameUtils.isPlayerAliveAndSurvival(target)) continue;
            Vec3 toTarget = target.getBoundingBox().getCenter().subtract(origin);
            if (toTarget.length() > SPRAY_RANGE) continue;
            if (toTarget.normalize().dot(look) < CONE_DOT) continue;
            if (!player.hasLineOfSight(target)) continue;
            result.add(target);
        }
        return result;
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
        tooltip.add(Component.translatable("item.noellesroles.extinguisher.tooltip.clean")
                .withStyle(ChatFormatting.AQUA));
        super.appendHoverText(stack, context, tooltip, type);
    }
}
