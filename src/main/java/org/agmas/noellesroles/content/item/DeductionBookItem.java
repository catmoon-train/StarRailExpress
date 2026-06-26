package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 推理之书（大侦探专属）。
 *
 * <p>右键打开推理界面，展示已掌握的凶手线索。打开界面由客户端回调处理，
 * 物品本体不引用任何客户端类。
 */
public class DeductionBookItem extends Item {

    /** 静态回调，由客户端设置用于打开 GUI。 */
    public static Runnable openScreenCallback = null;

    public DeductionBookItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        // 必须存活（旁观/死亡不可用）
        if (!GameUtils.isPlayerAliveAndSurvival(user)) {
            return InteractionResultHolder.fail(stack);
        }

        if (world.isClientSide()) {
            if (openScreenCallback != null) {
                openScreenCallback.run();
            }
        }

        // 不消耗物品
        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide());
    }
}
