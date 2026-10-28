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

import io.wifi.starrailexpress.api.ChargeableItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role_data.innocence.StarGathererRoleData;

/**
 * 星之权杖：摘星者开局自带的蓄力物品（用法与巫师法杖一致）。
 * <p>
 * 右键蓄力 → 松开时按视线锁定 35 格内最近的玩家，星星从高空砸下（死因「星星」）。
 * 蓄力不足一半不会释放；冷却统一走原版物品冷却。
 */
public class StarScepterItem extends Item implements ChargeableItem {

    /** 需要蓄力的 tick 数。不足一半不释放。 */
    private static final int MAX_CHARGE_TICKS = 20;

    public StarScepterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 原版物品冷却中 → 直接拒绝，避免依赖原版 use 被冷却拦截的隐含前提
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingUseTicks) {
        if (level.isClientSide || !(entity instanceof ServerPlayer sp)) {
            return;
        }
        int chargeTime = getUseDuration(stack, entity) - remainingUseTicks;
        if (chargeTime < MAX_CHARGE_TICKS / 2) {
            return; // 蓄力不足
        }
        // useScepter 自己会再做一次就绪检查；成功后立刻进入原版物品冷却
        if (StarGathererRoleData.useScepter(sp)) {
            sp.getCooldowns().addCooldown(this, StarGathererRoleData.SCEPTER_HIT_COOLDOWN_TICKS);
        }
    }

    /**
     * 蓄力中的杖端粒子。
     * <p>
     * <b>只发给使用者本人</b>：{@code ServerLevel#sendParticles} 是向周围 32 格内
     * 所有玩家广播，别人也能看到摘星者在蓄力，等于暴露位置。这里直接构造
     * {@link ClientboundLevelParticlesPacket} 只塞给使用者自己的连接。
     * <p>
     * 另外先校验手里拿的确实是星之权杖，避免粒子挂到别的物品上。
     */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseTicks) {
        if (!(entity instanceof ServerPlayer sp)) {
            return;
        }
        // 只在「正拿着星之权杖」时生效
        if (!stack.is(ModItems.STAR_SCEPTER)) {
            return;
        }
        int chargeTime = getUseDuration(stack, entity) - remainingUseTicks;
        if (chargeTime < MAX_CHARGE_TICKS / 2) {
            return; // 蓄力不足时不显示，保持隐蔽
        }

        Vec3 look = sp.getViewVector(1.0f).normalize();
        Vec3 tip = sp.getEyePosition().add(look.scale(0.8)).add(0, -0.2, 0);
        // 仅发送给使用者自己
        sendParticleToSelf(sp, ParticleTypes.END_ROD, tip);
        if (chargeTime % 2 == 0) {
            sendParticleToSelf(sp, ParticleTypes.ENCHANT, tip);
        }
    }

    /** 把单颗粒子只发给这一个玩家（不进网络广播）。 */
    private static void sendParticleToSelf(ServerPlayer sp, net.minecraft.core.particles.SimpleParticleType type,
            Vec3 pos) {
        sp.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket(
                type, false, pos.x, pos.y, pos.z, 0.05F, 0.05F, 0.05F, 0.0F, 1));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    // ==================== ChargeableItem（蓄力视觉） ====================

    @Override
    public int getMaxChargeTime(ItemStack stack, Player player) {
        return MAX_CHARGE_TICKS;
    }

    @Override
    public float getChargePercentage(ItemStack stack, Player player, int ticksUsingItem) {
        return Math.max(0f, Math.min(1f, (float) ticksUsingItem / MAX_CHARGE_TICKS));
    }

    @Override
    public boolean hasSpecialVisualEffects(ItemStack stack, Player player) {
        return true;
    }
}
