package org.agmas.noellesroles.client.hud;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.client.OnRenderRoleName;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.role.ModRoles;

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
                context.pose().translate(0, 12, 0);
                context.drawString(font, room_name, -font.width(room_name) / 2, 0,
                        _color | (int) (1 * 255.0F) << 24);
            }
        });
    }
}
