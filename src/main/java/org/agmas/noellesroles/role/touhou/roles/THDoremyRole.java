package org.agmas.noellesroles.role.touhou.roles;

import org.agmas.noellesroles.init.FunnyItems;
import org.agmas.noellesroles.role_data.killer.DoremyRoleData;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.agmas.noellesroles.utils.MoneyUtils;

import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class THDoremyRole extends TouhouRole {

    public static final int SKILL_DREAM_COST = 180;
    public static final int SKILL_MAKE_GHOST_COST = 60;
    public static final int COOLDOWN_FOR_DREAM = 180 * 20;

    public THDoremyRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public InteractionResult onDropItem(Player player, ItemStack item) {
        if (item.is(FunnyItems.DOREMY_GHOST)) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override

    public void onKill(Player victim, boolean spawnBody, Player killer, ResourceLocation deathReason) {
        if (killer == null || victim == null)
            return;
        if (!(killer instanceof ServerPlayer player)) {
            return;
        }
        var cca = RoleData.getNullable(DoremyRoleData.class, player);
        if (cca == null || cca.cooldownForDoremyGhost > 0) {
            return;
        }
        if (!MoneyUtils.hasBalance(player, SKILL_MAKE_GHOST_COST)) {
            return;
        }
        var item = FunnyItems.DOREMY_GHOST.getDefaultInstance();
        item.set(DataComponents.ITEM_NAME,
                Component.translatable("item.noellesroles.doremy_ghost.name", victim.getScoreboardName()));
        if (!MCItemsUtils.insertStackInFreeSlot(player, item)) {
            player.drop(item, false);
        }
        MoneyUtils.addToBalance(player, -SKILL_MAKE_GHOST_COST);
        cca.cooldownForDoremyGhost = 120 * 20;
        cca.sync();
        return;
    }

}
