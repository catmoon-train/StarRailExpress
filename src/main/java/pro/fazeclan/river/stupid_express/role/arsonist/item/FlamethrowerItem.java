package pro.fazeclan.river.stupid_express.role.arsonist.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import pro.fazeclan.river.stupid_express.role.arsonist.ArsonistRoleData;

/** 持续使用物品负责记录按住右键，喷火与命中只在服务端 RoleData 中结算。 */
public class FlamethrowerItem extends Item {
    public FlamethrowerItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ArsonistRoleData data = ArsonistRoleData.of(player);
        if (data == null || !data.canSpray() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }
}
