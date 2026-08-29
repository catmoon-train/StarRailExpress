package org.agmas.noellesroles.role.bouns.roles;

import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role_data.innocence.BarbarianRoleData;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.EggRole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

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
    public boolean onUseKnifeHit(Player player, Player target) {
        if (!player.getMainHandItem().is(ModItems.BARBARIAN_KNIFE)) {
            return false;
        }
        player.getCooldowns().addCooldown(ModItems.BARBARIAN_KNIFE, 4 * 20);
        return true;
    }

    @Override
    public void onPsychoOver(Player player, SREPlayerPsychoComponent psychoComponent) {
        var data = RoleData.getNullable(BarbarianRoleData.class, player);
        if (data != null) {
            data.removeBarbarianKnives();
        }
        player.displayClientMessage(Component.translatable("message.noellesroles.barbarian.transform_end")
                .withStyle(ChatFormatting.DARK_RED), true);
        GameUtils.forceKillPlayer(player, true, null, GameConstants.DeathReasons.TIMEOUT);
    }

    @Override
    public Item getPsychoItem() {
        return ModItems.BARBARIAN_KNIFE;
    };

    @Override
    public ResourceLocation getPsychoSkin(Player player, boolean isSlim) {
        return SRE.id("textures/entity/custom_psycho/barbarian.png");
    }

    @Override
    public boolean haveInstinctNightVision(Player player) {
        if (SREPlayerPsychoComponent.KEY.get(player).havePsycho())
            return true;
        return haveInstinctNightVision();
    }

    @Override
    public boolean canUseInstinct(Player player) {
        if (SREPlayerPsychoComponent.KEY.get(player).havePsycho())
            return true;
        return canUseInstinct();
    }
}
