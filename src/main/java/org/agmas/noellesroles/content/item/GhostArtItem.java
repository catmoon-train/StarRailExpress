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
import org.agmas.noellesroles.game.roles.killer.ma_chen_xu.GhostArt;
import org.agmas.noellesroles.role_data.killer.MaChenXuRoleData;
import java.util.List;

/** 一次性诡术物品；归属、目标、容量和消耗均交给服务端职业数据校验。 */
public final class GhostArtItem extends Item {
    public final GhostArt art;

    public GhostArtItem(GhostArt art) {
        super(new Properties().stacksTo(1));
        this.art = art;
    }

    @Override public boolean canFitInsideContainerItems() { return false; }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.sidedSuccess(stack, true);
        var data = org.agmas.noellesroles.game.roles.killer.ma_chen_xu.SackGhostItems.data(player);
        return player instanceof ServerPlayer && data != null && data.useArt(stack, art)
                ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }

    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        return use(player.level(), player, hand).getResult();
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.noellesroles.ghost_art.bound").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
