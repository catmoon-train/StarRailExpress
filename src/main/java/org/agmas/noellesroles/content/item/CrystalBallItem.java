package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.agmas.noellesroles.game.roles.innocence.diviner.DivinerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 晶球：占卜家专用。右键对准一具尸体进行占卜，得知死者职业与名字（60 秒冷却，已占卜的尸体不可再次占卜）。
 * 若占卜对象是亡语杀手伪装的尸体，视为其用刀刺死了自己。
 */
public class CrystalBallItem extends Item {

    public CrystalBallItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(level);
            if (gw.isRole(sp, ModRoles.DIVINER)) {
                DivinerPlayerComponent.KEY.get(sp).divine(sp);
            }
        }
        player.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
