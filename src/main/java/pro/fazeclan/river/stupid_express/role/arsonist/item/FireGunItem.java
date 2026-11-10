package pro.fazeclan.river.stupid_express.role.arsonist.item;

import io.wifi.starrailexpress.content.item.RevolverItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import pro.fazeclan.river.stupid_express.role.arsonist.ArsonistRoleData;

/** 继承左轮持枪语义，但完全覆盖开火流程，不发送原版致死枪击包。 */
public class FireGunItem extends RevolverItem {
    public FireGunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ArsonistRoleData data = ArsonistRoleData.of(player);
        if (data == null || data.arsonMoment) return InteractionResultHolder.fail(stack);
        if (!level.isClientSide && !data.shootFireGun(hand)) return InteractionResultHolder.fail(stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
