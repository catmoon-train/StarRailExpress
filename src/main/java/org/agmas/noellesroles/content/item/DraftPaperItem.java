package org.agmas.noellesroles.content.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 稿纸 - 可以书写内容，放入邮箱后下一天会在报纸上刊登
 *
 * 注意：客户端屏幕通过反射打开，避免服务端加载 client-only 类导致崩溃。
 */
public class DraftPaperItem extends Item {

    public DraftPaperItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            openDraftScreen(stack, hand);
        }
        return InteractionResultHolder.success(stack);
    }

    /** 通过反射打开稿纸书写界面，避免在服务端尝试加载 Screen / Minecraft */
    private static void openDraftScreen(ItemStack stack, InteractionHand hand) {
        try {
            Class<?> screenClass = Class.forName(
                    "org.agmas.noellesroles.client.screen.DraftPaperScreen");
            Object screen = screenClass.getConstructor(ItemStack.class, InteractionHand.class)
                    .newInstance(stack, hand);
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Object mc = mcClass.getMethod("getInstance").invoke(null);
            // obf name "class_437" / yarn "net.minecraft.client.gui.screens.Screen"
            mcClass.getMethod("setScreen", Class.forName("net.minecraft.class_437"))
                    .invoke(mc, screen);
        } catch (Exception e) {
            // 服务端或类路径问题 —— 静默忽略
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.noellesroles.sixty_seconds_draft_paper"));
        // 读取自定义数据中的预览文本
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
