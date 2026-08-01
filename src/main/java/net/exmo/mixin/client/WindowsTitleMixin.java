/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.exmo.mixin.client;

import io.wifi.starrailexpress.SRE;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.IntegratedServer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import static net.minecraft.client.Minecraft.checkModStatus;

@Mixin(Minecraft.class)
public abstract class WindowsTitleMixin {
    @Shadow
    @Nullable
    public abstract ClientPacketListener getConnection();

    @Shadow
    @Nullable
    public abstract ServerData getCurrentServer();

    @Shadow
    @Nullable
    private IntegratedServer singleplayerServer;
    /**
     * @author canyuesama
     * @reason  title
     */
    @Overwrite
    private String createTitle() {
        StringBuilder stringBuilder = new StringBuilder("Minecraft");
        if (checkModStatus().shouldReportAsModified()) {
            stringBuilder.append("*");
        }
        stringBuilder.append(" ");
        stringBuilder.append(SharedConstants.getCurrentVersion().getName());
        stringBuilder.append(" - StarRailExpress ");
        stringBuilder.append(SRE.modPacketVersion);
        ClientPacketListener clientPacketListener = this.getConnection();
        if (clientPacketListener != null && clientPacketListener.getConnection().isConnected()) {
            stringBuilder.append(" - ");
            ServerData serverData = this.getCurrentServer();
            if (this.singleplayerServer != null && !this.singleplayerServer.isPublished()) {
                stringBuilder.append(I18n.get("title.singleplayer", new Object[0]));
            } else if (serverData != null && serverData.isRealm()) {
                stringBuilder.append(I18n.get("title.multiplayer.realms", new Object[0]));
            } else if (this.singleplayerServer == null && (serverData == null || !serverData.isLan())) {
                stringBuilder.append(I18n.get("title.multiplayer.other", new Object[0]));
            } else {
                stringBuilder.append(I18n.get("title.multiplayer.lan", new Object[0]));
            }
        }

        return stringBuilder.toString();
    }
}
