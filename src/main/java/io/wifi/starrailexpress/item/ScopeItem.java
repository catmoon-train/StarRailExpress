package io.wifi.starrailexpress.item;

import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class ScopeItem extends Item {
    public ScopeItem(Properties settings) {
        super(settings.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, @NotNull InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        // 检查主手是否拿着狙击枪
        ItemStack mainHand = user.getMainHandItem();
        if (mainHand.is(TMMItems.SNIPER_RIFLE)) {
            return InteractionResultHolder.fail(stack); // 倍镜只能通过蹲下左键安装，不能直接使用
        }

        return super.use(world, user, hand);
    }
}
