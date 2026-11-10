package pro.fazeclan.river.stupid_express.role.arsonist.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import pro.fazeclan.river.stupid_express.role.arsonist.ArsonistRoleData;

/** 打火机没有冷却；条件不足时安全失败，达标才点燃并进入纵火时刻。 */
public class LighterItem extends Item {
    public LighterItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        var data = ArsonistRoleData.of(player);
        if (data == null || data.arsonMoment) return InteractionResultHolder.fail(stack);
        if (!level.isClientSide && !data.ignite()) return InteractionResultHolder.fail(stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
