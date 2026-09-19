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
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.Scheduler;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.modifier.refugee.RefugeeDesperadoFx;
import org.agmas.noellesroles.init.ModItems;

/**
 * 难民词条转化亡命徒专属刀：右键 0.2s 蓄力，0.1s 前摇后向前挥出剑气并短距冲刺。
 */
public final class DesperadoKnifeItem extends KnifeItem implements ChargeableItem {
    public static final int CHARGE_TICKS = 4;
    public static final int WINDUP_TICKS = 2;
    private static final double SLASH_LENGTH = 3.45D;
    private static final double SLASH_WIDTH = 0.95D;
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
        if (usedTicks < CHARGE_TICKS) {
            return false;
        }
        if (world.isClientSide) {
            return true;
        }
        if (!(attacker instanceof ServerPlayer serverPlayer) || !(world instanceof ServerLevel serverLevel)) {
            return true;
        }
        Vec3 look = serverPlayer.getViewVector(1.0F);
        RefugeeDesperadoFx.sendCustomNearby(serverLevel, RefugeeDesperadoFx.SLASH_ID,
                serverPlayer.getEyePosition(), WINDUP_TICKS,
                serverPlayer.getYRot(), serverPlayer.getXRot(), (float) SLASH_LENGTH, 0.35F);
        Scheduler.schedule(() -> {
            if (!serverPlayer.isAlive() || serverPlayer.isRemoved()) {
                return;
            }
            if (!serverPlayer.getMainHandItem().is(ModItems.DESPERADO_KNIFE)) {
                return;
            }
            releaseSlash(serverLevel, serverPlayer, look);
        }, WINDUP_TICKS);
        return true;
    }

    private static void releaseSlash(ServerLevel level, ServerPlayer attacker, Vec3 look) {
        Vec3 eye = attacker.getEyePosition();
        Vec3 forward = look.normalize();
        Vec3 end = eye.add(forward.scale(SLASH_LENGTH));
        AABB slashBox = new AABB(eye, end).inflate(SLASH_WIDTH, 0.7D, SLASH_WIDTH);
        for (Player target : level.getEntitiesOfClass(Player.class, slashBox,
                player -> RefugeeDesperadoFx.isLivingTarget(attacker, player))) {
            if (isBlockedByWall(level, attacker, target)) {
                continue;
            }
            GameUtils.killPlayer(target, true, attacker, GameConstants.DeathReasons.KNIFE);
        }

        RefugeeDesperadoFx.sendCustomNearby(level, RefugeeDesperadoFx.SLASH_ID, eye, 6,
                attacker.getYRot(), attacker.getXRot(), (float) SLASH_LENGTH, 1.0F);

        Vec3 horizontal = new Vec3(forward.x, 0.0D, forward.z);
        if (horizontal.lengthSqr() > 1.0E-4D) {
            Vec3 dash = horizontal.normalize().scale(DASH_SPEED);
            attacker.setDeltaMovement(dash.x, attacker.getDeltaMovement().y, dash.z);
            attacker.hurtMarked = true;
            attacker.connection.send(new ClientboundSetEntityMotionPacket(attacker.getId(), dash.scale(0.8D)));
            attacker.fallDistance = 0.0F;
        }

        level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                TMMSounds.ITEM_KNIFE_STAB, SoundSource.PLAYERS, 1.25f, 0.7f);
        level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.4f, 0.55f);
        level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.7f, 0.85f);
        attacker.swing(InteractionHand.MAIN_HAND, true);
    }

    private static boolean isBlockedByWall(ServerLevel level, ServerPlayer attacker, Player target) {
        Vec3 from = attacker.getEyePosition();
        Vec3 to = target.getEyePosition();
        var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, attacker));
        return hit.getType() == HitResult.Type.BLOCK && hit.getLocation().distanceToSqr(from) + 0.04D < from.distanceToSqr(to);
    }

    @Override
    public String getItemSkinType() {
        return "knife";
    }
}
