package org.agmas.noellesroles.content.item;

import org.agmas.noellesroles.role.touhou.THMiscRoles;
import org.agmas.noellesroles.role.touhou.roles.THSuikaRole;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class SuikaPillItem extends PillItem {

    public SuikaPillItem(Properties settings) {
        super(settings);
    }

    @Override
    public @NotNull ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
        if (user instanceof Player player && !world.isClientSide) {
            if (RoleUtils.isPlayerTheJob(player, THMiscRoles.IBUKI_SUIKA)) {
                THSuikaRole.restore(player);
            }
            player.getAttribute(Attributes.SCALE).removeModifiers();
            player.getAttribute(Attributes.SCALE).setBaseValue(1f);
        }
        stack.consume(1, user);
        return stack;
    }
}
