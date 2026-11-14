package org.agmas.noellesroles.game.command_block.item;


import org.agmas.noellesroles.game.command_block.CommandBlockCombat;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.command_block.FogGuestZoneEntity;
import org.agmas.noellesroles.init.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 雾刃：只能击杀雾区内或被雾铃遮眼的玩家。落空不进冷却。 */
public class FogBladeItem extends Item {
    public static final int COOLDOWN = 5 * 20;
    public static final double RANGE = 4.0D;
    public static final net.minecraft.resources.ResourceLocation DEATH_BLADE = Noellesroles.id("fog_blade");

    public FogBladeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer self)) {
            return InteractionResultHolder.success(stack);
        }
        if (!io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(self)
                || self.hasEffect(ModEffects.USED_BANED) || self.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        ServerPlayer target = GazeRays.firstVisiblePlayer(self, RANGE, 0.35D);
        if (target == null || !(FogGuestZoneEntity.isInside(target) || target.hasEffect(ModEffects.FOG_BLINDED))) {
            self.displayClientMessage(Component.translatable("message.noellesroles.fog_guest.blade_miss")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        boolean killed = CommandBlockCombat.tryKill(self, target, DEATH_BLADE, false, null);
        if (killed) {
            self.getCooldowns().addCooldown(this, COOLDOWN);
            org.agmas.noellesroles.game.command_block.CommandBlockFeedback.play(level, self,
                    net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 0.7F);
        }
        return killed ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }
}
