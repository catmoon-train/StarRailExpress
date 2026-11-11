package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.roles.killer.ma_chen_xu.GhostRelicService;
import java.util.List;

/** 同一件可传承法器承载净雨与破境；冷却保存在物品中，不与职业数据绑定。 */
public final class GhostWardRelicItem extends Item {
    public GhostWardRelicItem() { super(new Properties().stacksTo(1)); }

    @Override public boolean canFitInsideContainerItems() { return false; }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.sidedSuccess(stack, true);
        return player instanceof ServerPlayer sp && GhostRelicService.use(sp, stack, player.isShiftKeyDown())
                ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }

    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        return use(player.level(), player, hand).getResult();
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.noellesroles.ghost_ward_relic.transfer").withStyle(ChatFormatting.DARK_GREEN));
    }
}
