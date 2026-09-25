package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.game.GameUtils;
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

/**
 * 独裁之书 —— 独裁者专属一次性道具（参考阴谋家的阴谋书页）。
 *
 * <p>
 * 右键打开玩家头像界面（<b>始终为所有玩家</b>），选中玩家后再选择职业
 * （可选职业只有「非好人方中立」与「杀手」职业）。提交后由服务端校验：
 * <ul>
 * <li>猜测正确 → 目标位置劈下一道闪电，目标死亡（死因「裁断」）；</li>
 * <li>猜测正确但目标已死亡 → actionbar 提示；</li>
 * <li>无论正确与否，独裁之书都会消耗。</li>
 * </ul>
 */
public class DictatorBookItem extends Item {

    /** 纯客户端回调：由客户端在初始化时赋值（右键直接打开选择界面） */
    public static Runnable openScreenCallback = null;

    public DictatorBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide() && openScreenCallback != null) {
            openScreenCallback.run();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
