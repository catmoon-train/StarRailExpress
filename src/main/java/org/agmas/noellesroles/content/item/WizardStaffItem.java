package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.api.ChargeableItem;
import io.wifi.starrailexpress.content.item.api.SREItemProperties;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
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
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.game.roles.killer.wizard.WizardPlayerComponent;

/**
 * 巫师法杖：右键蓄力释放魔法火焰箭（或就绪时的九环火球术），左键击退目标。
 */
public class WizardStaffItem extends Item implements ChargeableItem, SREItemProperties.LeftClickHurtable {

    private static final int MAX_CHARGE_TICKS = 14;

    public WizardStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
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
        WizardPlayerComponent.KEY.get(sp).castStaff(sp);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseTicks) {
        if (!(level instanceof ServerLevel sl) || !(entity instanceof ServerPlayer sp)) {
            return;
        }
        int chargeTime = getUseDuration(stack, entity) - remainingUseTicks;
        if (chargeTime < MAX_CHARGE_TICKS / 2) {
            return; // 蓄力不足时不显形，保持隐蔽
        }
        // 杖端凝聚稀疏的灵魂火与黑烟：低调、阴冷，暗示一击毙命而非招摇
        Vec3 look = sp.getViewVector(1.0f).normalize();
        Vec3 tip = sp.getEyePosition().add(look.scale(0.8)).add(0, -0.2, 0);
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, tip.x, tip.y, tip.z, 1, 0.05, 0.05, 0.05, 0.0);
        if (chargeTime % 2 == 0) {
            sl.sendParticles(ParticleTypes.SMOKE, tip.x, tip.y, tip.z, 1, 0.04, 0.04, 0.04, 0.0);
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

    // ==================== 左键击退 ====================

    @Override
    public boolean onServerAttack(ServerPlayer attacker, ServerPlayer target, ItemStack mainhandItem) {
        double strength = NoellesRolesConfig.HANDLER.instance().wizardStaffKnockback;
        double dx = attacker.getX() - target.getX();
        double dz = attacker.getZ() - target.getZ();
        target.knockback(strength, dx, dz);
        target.hurtMarked = true;
        target.connection.send(new ClientboundSetEntityMotionPacket(target.getId(), target.getDeltaMovement()));
        target.level().playSound(null, target.blockPosition(),
                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.2f);
        return false;
    }
}
