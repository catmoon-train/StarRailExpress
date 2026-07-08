package net.exmo.sre.repair.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.component.ModComponents;
import net.exmo.sre.repair.state.RepairModeState;

import java.util.List;

public class HunterAttackPluginItem extends Item {
    private final String pluginId;

    public HunterAttackPluginItem(String pluginId, Properties properties) {
        super(properties);
        this.pluginId = pluginId;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player,
            InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.consume(stack);
        }
        if (!RepairModeState.canUseHunterUtility(serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }
        var component = ModComponents.REPAIR_ROLES.get(serverPlayer);
        component.activeAttackPlugin = pluginId;
        component.sync();
        level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS,
                0.75F, 1.15F);
        serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.repair.attack_plugin_ready",
                Component.translatable("item.noellesroles.hunter_plugin_" + pluginId)).withStyle(ChatFormatting.RED), true);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.hunter_plugin_" + pluginId + ".tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
