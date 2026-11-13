package org.agmas.noellesroles.game.minigame.roulette;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import java.util.List;

/** 只允许在赌桌操作区生效，绝不继承普通手铐、药剂或武器的场外行为。 */
public final class RouletteToolItem extends Item {
    private final RouletteItem kind;
    public RouletteToolItem(RouletteItem kind) { super(new Properties().stacksTo(1)); this.kind = kind; }
    public RouletteItem kind() { return kind; }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(kind.descriptionKey()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("roulette.hint.use_center").withStyle(ChatFormatting.GOLD));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) player.displayClientMessage(Component.translatable("roulette.hint.use_center"), true);
        return InteractionResultHolder.fail(player.getItemInHand(hand));
    }
}
