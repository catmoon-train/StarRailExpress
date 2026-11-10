package pro.fazeclan.river.stupid_express.role.arsonist.item;

import io.wifi.starrailexpress.content.item.RevolverItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.roles.neutral.leader.LeaderFollowerEffects;
import pro.fazeclan.river.stupid_express.role.arsonist.ArsonistRoleData;

/** 继承左轮持枪语义，但完全覆盖开火流程，不发送原版致死枪击包。
 *  领袖也能使用：其浇上的汽油会记到纵火犯追随者名下。 */
public class FireGunItem extends RevolverItem {
    public FireGunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(stack, true);
        if (!(player instanceof ServerPlayer shooter)) return InteractionResultHolder.fail(stack);
        ArsonistRoleData data = resolveData(shooter);
        if (data == null || data.arsonMoment || !data.shootFireGunFor(shooter, hand)) {
            return InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    /** 纵火犯用自己的数据；领袖则借用其纵火犯追随者的数据。 */
    private static ArsonistRoleData resolveData(ServerPlayer shooter) {
        ServerPlayer follower = LeaderFollowerEffects.getArsonistFollowerOf(shooter);
        if (follower != null) return ArsonistRoleData.of(follower);
        return ArsonistRoleData.of(shooter);
    }
}
