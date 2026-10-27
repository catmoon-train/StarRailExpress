package org.agmas.noellesroles.game.modes.echo.content;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.modes.echo.state.EchoRoleData;
import io.wifi.starrailexpress.api.data.RoleData;

/** 无资源数据的输入代理；客户端处理器仅在客户端初始化时常量接入。 */
public final class EchoCardProxyItem extends Item {
    public static Runnable clientUse=() -> { };
    public EchoCardProxyItem(Properties properties) { super(properties); }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        var data=RoleData.getNullable(EchoRoleData.class,player);
        if(hand!=InteractionHand.MAIN_HAND || data==null || data.matchId()==null) return InteractionResultHolder.fail(player.getItemInHand(hand));
        if(level.isClientSide) clientUse.run();
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
}
