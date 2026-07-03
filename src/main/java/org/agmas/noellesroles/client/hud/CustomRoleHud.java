package org.agmas.noellesroles.client.hud;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.client.OnRenderRoleName;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;

public class CustomRoleHud {
    public static void registerEvents() {
        // 乘务员
        OnRenderRoleName.RENDER_PLAYER_EXTRA.register((player, target, context, delta, font) -> {
            if (SREClient.gameComponent.isRole(Minecraft.getInstance().player.getUUID(), ModRoles.ATTENDANT)) {
                String room_name_str = "No Room";

                if (GameUtils.roomToPlayer.containsKey(target.getUUID())) {
                    int room_number = GameUtils.roomToPlayer.get(target.getUUID());
                    room_name_str = "Room " + room_number;
                }
                var room_name = Component.translatable("message.noellesroles.attendant.room_show",
                        Component.literal(room_name_str).withStyle(ChatFormatting.GOLD));
                // NoellesrolesClient.hudTarget
                var _color = java.awt.Color.MAGENTA.getRGB();

                context.drawString(font, room_name, -font.width(room_name) / 2, -20,
                        _color | (int) (1 * 255.0F) << 24);
            }
        });
        // 通用
        OnRenderRoleName.RENDER_PLAYER_EXTRA.register((player, target, context, delta, font) -> {
            if (!SREClient.isPlayerSpectatingOrCreative())
                return;
            SRERole targetRole = SREClient.gameComponent.getRole(target);
            if (targetRole == null) {
                targetRole = TMMRoles.DISCOVERY_CIVILIAN;
            }
            MutableComponent name = RoleUtils.getRoleName(targetRole);
            int di_color = targetRole.color();
            if (SREClient.modifierComponent != null) {
                if (SREClient.modifierComponent.isModifier(player, SEModifiers.SPLIT_PERSONALITY)) {
                    var splitComponent = SplitPersonalityComponent.KEY.get(player);
                    if (splitComponent != null && !splitComponent.isDeath()) {
                        return;
                    }
                }
            }
            // 不用考虑死亡惩罚，这已经在最开始被处理了。
            context.drawString(font, name, -font.width(name) / 2, 0,
                    di_color | (int) (1 * 255.0F) << 24);
        });
    }
}
