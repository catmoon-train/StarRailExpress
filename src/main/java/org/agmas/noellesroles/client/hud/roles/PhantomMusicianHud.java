package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.neutral.phantom_musician.PhantomMusicianPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 幻音师 HUD
 *
 * 显示：
 * - 传送技能冷却 / 已就绪
 * - 商店音效冷却状态
 */
public class PhantomMusicianHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.PHANTOM_MUSICIAN_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator())
                return;

            PhantomMusicianPlayerComponent comp = PhantomMusicianPlayerComponent.KEY.get(client.player);
            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(client.player);

            int screenRight = context.guiWidth();
            int drawY = context.guiHeight();
            var font = client.font;

            Component line;
            int color = ModRoles.PHANTOM_MUSICIAN.color();

            // 传送技能冷却显示（优先显示，最下方）
            if (comp.teleportCooldown > 0) {
                int seconds = (comp.teleportCooldown + 19) / 20;
                line = Component.translatable("hud.noellesroles.phantom_musician.teleport_cooldown", seconds)
                        .withStyle(ChatFormatting.RED);
            } else if (shop.balance >= PhantomMusicianPlayerComponent.TELEPORT_COST) {
                line = Component.translatable("hud.noellesroles.phantom_musician.teleport_ready")
                        .withStyle(ChatFormatting.GREEN);
            } else {
                line = Component.translatable("hud.noellesroles.phantom_musician.teleport_no_coin")
                        .withStyle(ChatFormatting.GOLD);
            }

            drawY -= font.wordWrapHeight(line, 999999);
            context.drawString(font, line,
                    screenRight - font.width(line), drawY, color);
        });
    }
}
