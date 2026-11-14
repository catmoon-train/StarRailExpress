package org.agmas.noellesroles.game.command_block.item;

import org.agmas.noellesroles.game.command_block.StringerRole;
import org.agmas.noellesroles.game.command_block.entity.ThreadBallEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.role_data.killer.StringerRoleData;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.content.item.GrenadeItem;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** 丝线团：与普通手榴弹一致的蓄力、抛速、共享冷却和碰撞引爆。 */
public class ThreadBallItem extends Item {
    public ThreadBallItem(Properties properties) {
        super(properties);
    }

    private boolean canThrow(ServerPlayer player) {
        return StringerRole.canAct(player) && !player.hasEffect(ModEffects.USED_BANED)
                && !player.getCooldowns().isOnCooldown(this) && !GrenadeItem.isAnyGrenadeOnCooldown(player);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer self && !canThrow(self)) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int remainingUseTicks) {
        if (level.isClientSide || !(user instanceof ServerPlayer self) || !canThrow(self)) return;
        StringerRoleData data = RoleData.getNullable(StringerRoleData.class, self);
        if (data == null) return;
        int charge = Math.max(0, Math.min(getUseDuration(stack, user) - remainingUseTicks, GrenadeItem.MAX_CHARGE_TIME));
        float speed = 0.4F + 0.75F * charge / GrenadeItem.MAX_CHARGE_TIME;
        ThreadBallEntity ball = new ThreadBallEntity(ModEntities.THREAD_BALL, level);
        ball.setOwner(self);
        ball.setSource(data);
        ball.setItem(stack.copyWithCount(1));
        ball.setPos(self.getX(), self.getEyeY() - 0.1D, self.getZ());
        ball.shootFromRotation(self, self.getXRot(), self.getYRot(), 0, speed, 1);
        if (!level.addFreshEntity(ball)) return;
        level.playSound(null, self.getX(), self.getY(), self.getZ(), TMMSounds.ITEM_GRENADE_THROW,
                SoundSource.NEUTRAL, 0.5F, 1F + (level.random.nextFloat() - 0.5F) / 10F);
        if (!self.isCreative()) {
            GrenadeItem.addGrenadeCooldown(self);
            self.getCooldowns().addCooldown(this, SREConfig.instance().grenadeCooldown);
        }
        stack.consume(1, self);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }
}
