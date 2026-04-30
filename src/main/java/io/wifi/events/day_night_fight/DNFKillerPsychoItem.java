package io.wifi.events.day_night_fight;

import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.core.particles.ParticleTypes;

public class DNFKillerPsychoItem extends Item {
    public DNFKillerPsychoItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        if (!(user instanceof ServerPlayer player)) {
            return InteractionResultHolder.success(user.getItemInHand(hand));
        }
        if (!DNF.isDNFKiller(player)) {
            player.displayClientMessage(Component.translatable("message.dnf.item.killer_only").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(user.getItemInHand(hand));
        }
        SREPlayerPsychoComponent psycho = SREPlayerPsychoComponent.KEY.get(player);
        if (psycho.getPsychoTicks() > 0) {
            psycho.stopPsychoAndSync();
            return InteractionResultHolder.success(user.getItemInHand(hand));
        }
        psycho.startPsycho_time(Integer.MAX_VALUE / 4, 1);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 10, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 10, false, false, false));
        world.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.0f, 0.8f);
        if (world instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1, player.getZ(), 120, 2.5, 1.2, 2.5, 0.05);
            serverLevel.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1, player.getZ(), 200, 3.0, 1.5, 3.0, 0.03);
        }
        return InteractionResultHolder.success(user.getItemInHand(hand));
    }
}
