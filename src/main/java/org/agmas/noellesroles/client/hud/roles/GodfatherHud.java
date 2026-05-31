package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.neutral.mafia.GodfatherComponent;
import org.agmas.noellesroles.role.ModRoles;

public class GodfatherHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.GODFATHER_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || SREClient.isPlayerSpectator()) return;
            GodfatherComponent comp = GodfatherComponent.KEY.maybeGet(client.player).orElse(null);
            if (comp == null) return;
            int y = context.guiHeight() - 30;
            // Cooldown display
            if (comp.recruitCooldownUntil > 0 && client.player.level() != null) {
                long now = client.player.level().getGameTime();
                if (now < comp.recruitCooldownUntil) {
                    long seconds = (comp.recruitCooldownUntil - now) / 20 + 1;
                    Component cd = Component.translatable("hud.noellesroles.godfather.cooldown", seconds);
                    int x = context.guiWidth() - client.font.width(cd) - 10;
                    context.drawString(client.font, cd, x, y, ModRoles.GODFATHER.color(), true);
                    y -= client.font.lineHeight + 2;
                }
            }
            // Ammo display
            Component ammo = Component.translatable("hud.noellesroles.godfather.ammo", comp.loadedBullets, comp.maxLoadedBullets);
            int x = context.guiWidth() - client.font.width(ammo) - 10;
            context.drawString(client.font, ammo, x, y, ModRoles.GODFATHER.color(), true);
            y -= client.font.lineHeight + 2;
            // Family count
            int recruited = comp.familyMembers.size();
            Component fam = Component.translatable("hud.noellesroles.godfather.family", recruited, comp.recruitLimit);
            x = context.guiWidth() - client.font.width(fam) - 10;
            context.drawString(client.font, fam, x, y, ModRoles.GODFATHER.color(), true);
        });
    }
}
