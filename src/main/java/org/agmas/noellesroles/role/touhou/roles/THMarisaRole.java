package org.agmas.noellesroles.role.touhou.roles;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;

import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class THMarisaRole extends TouhouRole {
    public ArrayList<MobEffectInstance> playerEffects = new ArrayList<>();

    public THMarisaRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
        playerEffects.add(
                ModEffects.of(MobEffects.SLOW_FALLING, 20 * 30 + 5, 1, false, false, true));
        playerEffects.add(
                ModEffects.of(MobEffects.JUMP, 20 * 30 + 5, 0, false, false, true));
    }

    public MobEffectInstance getNewEffectInstance(MobEffectInstance instance) {
        return new MobEffectInstance(instance);
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        ArrayList<ShopEntry> SHOP = new ArrayList<>();
        SHOP.add(new ShopEntry(ModItems.MINI_BAGUALU.getDefaultInstance(), 200, ShopEntry.Type.TOOL));
        return SHOP;
    }

    @Override
    public void serverTick(ServerPlayer player) {
        if (player.level().getGameTime() % 20 == 0) {
            if (GameUtils.isPlayerAliveAndSurvival(player)) {
                for (var eff : playerEffects) {
                    if (!player.hasEffect(eff.getEffect()) || player.getEffect(eff.getEffect()).getDuration() <= 21) {
                        player.addEffect(getNewEffectInstance(eff));
                    }
                }
            }
        }
    }
}
