package org.agmas.noellesroles.game.command_block.item;

import org.agmas.noellesroles.game.command_block.LegendFx;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.init.ModEffects;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 雾铃：半径四格内的玩家被雾遮眼四秒。没人则不进冷却。 */
public class FogBellItem extends Item {
    public static final int COOLDOWN = 25 * 20;
    public static final double RANGE = 4.0D;

    public FogBellItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer self)) {
            return InteractionResultHolder.success(stack);
        }
        if (!GameUtils.isPlayerAliveAndSurvival(self) || self.hasEffect(ModEffects.USED_BANED)
                || self.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        int found = 0;
        for (ServerPlayer other : self.serverLevel().players()) {
            if (other == self || !GameUtils.isPlayerAliveAndSurvival(other)) {
                continue;
            }
            if (other.distanceToSqr(self) > RANGE * RANGE) {
                continue;
            }
            other.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 4 * 20, 0));
            other.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 4 * 20, 0));
            other.addEffect(new MobEffectInstance(ModEffects.FOG_BLINDED, 4 * 20, 0, false, false, false));
            found++;
        }
        if (found == 0) {
            self.displayClientMessage(Component.translatable("message.noellesroles.fog_guest.bell_miss")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        self.level().playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.BELL_BLOCK,
                SoundSource.PLAYERS, 0.8F, 1.6F);
        LegendFx.ring(self.serverLevel(), self.position(), 2.2F, LegendFx.FOG);
        self.getCooldowns().addCooldown(ModItems.FOG_BELL, COOLDOWN);
        return InteractionResultHolder.consume(stack);
    }
}
