package org.agmas.noellesroles.game.command_block.item;

import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.CandleShadeRoleData;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 灯油：给还亮着的烛火续 4 秒。没有烛火则不进冷却。 */
public class LampOilItem extends Item {
    public static final int COOLDOWN = 8 * 20;

    public LampOilItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer self)) {
            return InteractionResultHolder.success(stack);
        }
        if (self.isSpectator() || self.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!SREGameWorldComponent.KEY.get(level).isRole(self, ModRoles.CANDLE_SHADE)) {
            return InteractionResultHolder.fail(stack);
        }
        CandleShadeRoleData data = RoleData.getNullable(CandleShadeRoleData.class, self);
        if (data == null || !data.extend(self, level.getGameTime())) {
            self.displayClientMessage(Component.translatable("message.noellesroles.candle_shade.oil_miss")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        self.getCooldowns().addCooldown(ModItems.LAMP_OIL, COOLDOWN);
        org.agmas.noellesroles.game.command_block.CommandBlockFeedback.play(level, self,
                net.minecraft.sounds.SoundEvents.BUCKET_EMPTY, 0.6F, 1.1F);
        return InteractionResultHolder.consume(stack);
    }
}