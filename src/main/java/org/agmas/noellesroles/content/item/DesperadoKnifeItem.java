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
import io.wifi.starrailexpress.content.item.KnifeItem;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 难民词条转化亡命徒专属刀：右键蓄满后短距冲刺（不造成伤害），
 * 刺杀走普通刀的蓄力结束判定。
 */
public final class DesperadoKnifeItem extends KnifeItem implements ChargeableItem {
    public static final int CHARGE_TICKS = 4;
    /** dash 距离相较原始版本削减 40%（原始值 1.35D）。 */
    private static final double DASH_SPEED = 1.35D * 0.6D;

    public DesperadoKnifeItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getMinKnifeChargeTicks(ItemStack stack, LivingEntity user) {
        return CHARGE_TICKS;
    }

    @Override
    public int getMaxChargeTime(ItemStack stack, Player player) {
        return CHARGE_TICKS;
    }

    @Override
    public float getChargePercentage(ItemStack stack, Player player, int ticksUsingItem) {
        return Math.min((float) ticksUsingItem / getMaxChargeTime(stack, player), 1f);
    }

    @Override
    public float getMaxStamina(ItemStack stack, Player player) {
        return getMaxChargeTime(stack, player);
    }

    @Override
    public boolean hasSpecialVisualEffects(ItemStack stack, Player player) {
        return true;
    }

    @Override
    public boolean canStartKnifeCharge(Level world, Player user, InteractionHand hand, ItemStack stack) {
        return !user.getCooldowns().isOnCooldown(this);
    }

    @Override
    public boolean onKnifeChargeReleased(ItemStack stack, Level world, Player attacker, int usedTicks) {
        if (usedTicks < getMaxChargeTime(stack, attacker)) {
            return false;
        }
        if (!world.isClientSide && attacker instanceof ServerPlayer serverPlayer
                && world instanceof ServerLevel serverLevel) {
            dashForward(serverLevel, serverPlayer);
        }
        return false;
    }

    private static void dashForward(ServerLevel level, ServerPlayer attacker) {
        Vec3 forward = attacker.getViewVector(1.0F);
        Vec3 horizontal = new Vec3(forward.x, 0.0D, forward.z);
        if (horizontal.lengthSqr() > 1.0E-4D) {
            Vec3 dash = horizontal.normalize().scale(DASH_SPEED);
            attacker.setDeltaMovement(dash.x, attacker.getDeltaMovement().y, dash.z);
            attacker.hurtMarked = true;
            attacker.connection.send(new ClientboundSetEntityMotionPacket(attacker.getId(), dash.scale(0.8D)));
            attacker.fallDistance = 0.0F;
        }
        level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                TMMSounds.ITEM_KNIFE_STAB, SoundSource.PLAYERS, 0.7f, 1.15f);
        level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9f, 1.05f);
        attacker.swing(InteractionHand.MAIN_HAND, true);
    }

    @Override
    public String getItemSkinType() {
        return "knife";
    }
}
