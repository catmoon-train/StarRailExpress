package org.agmas.noellesroles.role.bouns.roles;

import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role_data.innocence.BarbarianRoleData;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.EggRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class BarbarianRole extends EggRole {

    public BarbarianRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public void onPsychoStart(Player player, SREPlayerPsychoComponent psychoComponent) {
        super.onPsychoStart(player, psychoComponent);
        player.addEffect(ModEffects.of(MobEffects.MOVEMENT_SPEED, 400, 4, false, false, true));
    }

    @Override
    public boolean onPsychoGiveItem(Player player, SREPlayerPsychoComponent psychoComponent) {
        ItemStack knife = new ItemStack(ModItems.BARBARIAN_KNIFE);
        if (RoleUtils.insertStackInFreeSlot(player, knife)) {
            return true;
        }
        player.getInventory().setItem(0, knife);
        return true;
    }

    @Override
    public boolean onUseKnifeHit(Player player, Player target) {
        if (!player.getMainHandItem().is(ModItems.BARBARIAN_KNIFE)) {
            return false;
        }
        BarbarianRoleData data = RoleData.getNullable(BarbarianRoleData.class, player);
        if (data == null || !data.isBerserk()) {
            return false;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        SRERole targetRole = game.getRole(target);
        if (targetRole == null || !(game.isKillerTeam(target) || targetRole.isNeutrals())) {
            if (player instanceof ServerPlayer attacker) {
                attacker.displayClientMessage(Component.translatable("message.noellesroles.barbarian.knife_wrong_target")
                        .withStyle(ChatFormatting.RED), true);
            }
            return false;
        }
        player.getCooldowns().addCooldown(ModItems.BARBARIAN_KNIFE, 6 * 20);
        return true;
    }

    @Override
    public void onPsychoOver(Player player, SREPlayerPsychoComponent psychoComponent) {
        if (player instanceof ServerPlayer serverPlayer) {
            BarbarianRoleData data = RoleData.getNullable(BarbarianRoleData.class, serverPlayer);
            if (data != null) {
                data.finishBerserk(serverPlayer);
            }
        }
    }

    @Override
    public Item getPsychoItem() {
        return ModItems.BARBARIAN_KNIFE;
    }

    @Override
    public ResourceLocation getPsychoSkin(Player player, boolean isSlim) {
        return SRE.id("textures/entity/custom_psycho/barbarian.png");
    }

    @Override
    public boolean haveInstinctNightVision(Player player) {
        BarbarianRoleData data = RoleData.getNullable(BarbarianRoleData.class, player);
        if ((data != null && data.isBerserk()) || SREPlayerPsychoComponent.KEY.get(player).havePsycho()) {
            return true;
        }
        return haveInstinctNightVision();
    }

    @Override
    public boolean canUseInstinct(Player player) {
        BarbarianRoleData data = RoleData.getNullable(BarbarianRoleData.class, player);
        if ((data != null && data.isBerserk()) || SREPlayerPsychoComponent.KEY.get(player).havePsycho()) {
            return true;
        }
        return canUseInstinct();
    }
}
