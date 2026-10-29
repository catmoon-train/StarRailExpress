package org.agmas.noellesroles.game.modes.echo.content;

import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoSessions;
import org.agmas.noellesroles.game.modes.echo.state.EchoRoleData;

/** 专长信物：右键请求释放主动技能，不消耗；一切判定在服务器。 */
public final class EchoSkillItem extends Item {
    public EchoSkillItem(Properties properties) { super(properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var data = RoleData.getNullable(EchoRoleData.class, player);
        if (hand != InteractionHand.MAIN_HAND || data == null || data.matchId() == null)
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        if (!level.isClientSide && player instanceof ServerPlayer server) {
            var match = EchoSessions.get(server.serverLevel());
            if (match != null) match.useSkill(server);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override public boolean isFoil(ItemStack stack) { return true; }
}
