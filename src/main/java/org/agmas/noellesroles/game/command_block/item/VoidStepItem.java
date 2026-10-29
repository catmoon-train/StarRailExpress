package org.agmas.noellesroles.game.command_block.item;

import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** 向前错位 7 格。落地后短暂失明，并且 3 秒内不能使用物品。 */
public class VoidStepItem extends Item {
    public static final int COOLDOWN = 16 * 20;
    private static final double DISTANCE = 7.0D;

    public VoidStepItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer self) || !(level instanceof ServerLevel server)) {
            return InteractionResultHolder.success(stack);
        }
        if (self.isSpectator() || self.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!SREGameWorldComponent.KEY.get(level).isRole(self, ModRoles.ENTITY_NULL)) {
            return InteractionResultHolder.fail(stack);
        }
        Vec3 look = self.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 1.0E-4) {
            flat = new Vec3(0.0D, 0.0D, 1.0D);
        }
        flat = flat.normalize();
        Vec3 dest = self.position().add(flat.scale(DISTANCE));
        BlockPos spot = findStand(server, BlockPos.containing(dest));
        if (spot == null) {
            return InteractionResultHolder.fail(stack);
        }
        self.teleportTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D);
        self.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.BLINDNESS, 2 * 20, 0, false, false, true));
        self.addEffect(ModEffects.of(ModEffects.USED_BANED, 3 * 20, 0, false, false, true));
        server.sendParticles(ParticleTypes.PORTAL, self.getX(), self.getY() + 1.0D, self.getZ(),
                24, 0.3D, 0.5D, 0.3D, 0.2D);
        server.playSound(null, self.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 0.4F);
        self.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.consume(stack);
    }

    private static BlockPos findStand(ServerLevel level, BlockPos origin) {
        for (int dy = 1; dy >= -2; dy--) {
            BlockPos feet = origin.offset(0, dy, 0);
            if (canStand(level, feet)) {
                return feet;
            }
        }
        if (level.getBlockState(origin).isAir() && level.getBlockState(origin.above()).isAir()) {
            return origin;
        }
        return null;
    }

    private static boolean canStand(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet).isAir()
                && level.getBlockState(feet.above()).isAir()
                && !level.getBlockState(feet.below()).isAir();
    }
}
