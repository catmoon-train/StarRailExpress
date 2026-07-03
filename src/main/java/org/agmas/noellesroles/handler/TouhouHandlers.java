package org.agmas.noellesroles.handler;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.role.touhou.THMiscRoles;

public class TouhouHandlers {
    public static void register() {
        // 天子
        OnPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            for (var p : player.level().players()) {
                if (gameWorldComponent.isRole(p, THMiscRoles.TENSHI)) {
                    if (p.getCooldowns().isOnCooldown(Items.BARRIER)) {
                        continue;
                    } else {
                        p.getCooldowns().addCooldown(Items.BARRIER, 30 * 20);
                        if (p instanceof ServerPlayer sp) {
                            SRENetworkMessageUtils.sendCODSubtitleToPlayerTop(sp,
                                    Component.translatable("message.tenshi.killer_killed.title")
                                            .withStyle(ChatFormatting.RED),
                                    Component.translatable("message.tenshi.killer_killed.subtitle", 30), 100);
                        }
                    }
                }
            }

        });
    }
}
