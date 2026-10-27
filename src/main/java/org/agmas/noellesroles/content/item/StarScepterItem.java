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
import net.minecraft.server.level.ServerLevel;
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

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseTicks) {
        if (!(level instanceof ServerLevel sl) || !(entity instanceof ServerPlayer sp)) {
            return;
        }
        int chargeTime = getUseDuration(stack, entity) - remainingUseTicks;
        if (chargeTime < MAX_CHARGE_TICKS / 2) {
            return; // 蓄力不足时不显示，保持隐蔽
        }
        // 杖端凝聚星光：先用少量 END_ROD 提示已进入可释放状态
        Vec3 look = sp.getViewVector(1.0f).normalize();
        Vec3 tip = sp.getEyePosition().add(look.scale(0.8)).add(0, -0.2, 0);
        sl.sendParticles(ParticleTypes.END_ROD, tip.x, tip.y, tip.z, 1, 0.05, 0.05, 0.05, 0.0);
        if (chargeTime % 2 == 0) {
            sl.sendParticles(ParticleTypes.ENCHANT, tip.x, tip.y, tip.z, 1, 0.04, 0.04, 0.04, 0.0);
        }
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
