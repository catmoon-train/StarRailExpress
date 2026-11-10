package pro.fazeclan.river.stupid_express.role.arsonist;

import io.wifi.starrailexpress.api.NormalRole;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SEItems;

/** 纵火犯独立的职业行为，避免修改通用 SRERole。 */
public class ArsonistRole extends NormalRole {
    public ArsonistRole() {
        super(StupidExpress.id("arsonist"), 0xfc9526, false, false, MoodType.FAKE, -1, true);
    }

    @Override
    public InteractionResult onDropItem(Player player, ItemStack stack) {
        var data = ArsonistRoleData.of(player);
        return stack.is(SEItems.FLAMETHROWER) || data != null && data.arsonMoment
                ? InteractionResult.FAIL : super.onDropItem(player, stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> onItemUse(Player player, Level level, InteractionHand hand) {
        var data = ArsonistRoleData.of(player);
        if (data != null && data.arsonMoment && !player.getItemInHand(hand).is(SEItems.FLAMETHROWER)) {
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }
        return super.onItemUse(player, level, hand);
    }
    @Override
    public void onDeath(Player victim, boolean spawnBody, Player killer,
            net.minecraft.resources.ResourceLocation reason, boolean forceDeath) {
        var data = ArsonistRoleData.of(victim);
        if (data != null) data.reset();
    }


    @Override
    public boolean onGunHit(Player shooter, Player target) {
        // 即使其它模组错误地把浇油火枪加入普通枪械标签，也不能走原版左轮致死逻辑。
        return !shooter.getMainHandItem().is(SEItems.FIRE_GUN) && super.onGunHit(shooter, target);
    }
}
