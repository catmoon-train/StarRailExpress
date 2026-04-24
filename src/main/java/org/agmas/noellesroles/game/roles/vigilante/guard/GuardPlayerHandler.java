package org.agmas.noellesroles.game.roles.vigilante.guard;

import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

public class GuardPlayerHandler {

    public static void register() {
        RoleSkill.register(ModRoles.GUARD, GuardPlayerHandler::useSkill);
    }

    public static void useSkill(RoleSkillContext context) {
        ServerPlayer player = context.player();
        if (player.level().isClientSide) return;

        ItemStack off = player.getOffhandItem();
        if (off != null && off.is(ModItems.RIOT_SHIELD)) {
            ItemStack copy = off.copy();
            boolean inserted = RoleUtils.insertStackInFreeSlot(player, copy);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            if (!inserted) {
                // 无法插入则直接丢出世界
                ItemEntity ent = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), copy);
                player.level().addFreshEntity(ent);
            }
            return;
        }

        // 主手优先
        ItemStack main = player.getMainHandItem();
        if (main != null && main.is(ModItems.RIOT_SHIELD)) {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, main);
            return;
        }

        // 从背包中寻找一个防暴盾并放到副手
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s != null && s.is(ModItems.RIOT_SHIELD)) {
                ItemStack one = s.copy();
                one.setCount(1);
                s.shrink(1);
                if (s.isEmpty()) player.getInventory().setItem(i, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND, one);
                return;
            }
        }
    }
}
