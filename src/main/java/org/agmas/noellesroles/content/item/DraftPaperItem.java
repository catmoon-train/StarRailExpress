package org.agmas.noellesroles.content.item;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.client.screen.DraftPaperScreen;

import java.util.List;

/**
 * 稿纸 - 可以书写内容，放入邮箱后下一天会在报纸上刊登。
 */
public class DraftPaperItem extends Item {

    public DraftPaperItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            Minecraft.getInstance().setScreen(new DraftPaperScreen(stack, hand));
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.noellesroles.sixty_seconds_draft_paper"));
        var tag = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (tag != null) {
            String saved = tag.copyTag().getString("DraftText");
            if (!saved.isEmpty()) {
                String preview = saved.length() > 30 ? saved.substring(0, 30) + "..." : saved;
                tooltip.add(Component.translatable("tooltip.noellesroles.sixty_seconds_draft_paper.preview", preview));
            }
        }
    }
}
