package org.agmas.noellesroles.game.modes.echo.content;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.modes.echo.editor.EchoEditorActionPayload;
import org.agmas.noellesroles.game.modes.echo.editor.EchoEditorService;

import java.util.List;

/**
 * 回响地图编辑工具。
 *
 * <p>普通右键对着空气打开编辑界面。潜行右键对着方块时，服务端按当前准星识别回声机器并标记点位；
 * 不潜行对着方块仍打开界面，避免盖住原入口。机器类型和坐标不由客户端传入。
 *
 * <p>仅 OP2、仅非对局、每人一份草稿。手持时 HUD 常驻显示会话状态、草稿 revision 与地图模式声明。
 * 识别只改内存草稿，不会自动保存。物品本体不引用客户端类，界面由静态回调注入。
 */
public final class EchoEditorItem extends Item {
    /** 客户端设置：右键时打开编辑界面。 */
    public static Runnable openScreenCallback;

    public EchoEditorItem(Properties properties) { super(properties); }

    @Override public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer server) {
            // 不信点击包里的方块：服务端按当前视线重新识别。
            EchoEditorService.handle(server, EchoEditorActionPayload.of(EchoEditorActionPayload.Op.IDENTIFY));
        }
        return InteractionResult.CONSUME;
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide && openScreenCallback != null) openScreenCallback.run();
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.echo_editor.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.noellesroles.echo_editor.desc2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("echo.editor.machine.tooltip").withStyle(ChatFormatting.DARK_AQUA));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override public boolean isFoil(ItemStack stack) { return true; }
}
