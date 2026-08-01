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

package io.wifi.starrailexpress.mixin.server;

import io.wifi.starrailexpress.util.CustomMotdManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.network.protocol.status.ServerStatus.Version;
import net.minecraft.server.MinecraftServer;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public abstract class MotdMixin {
    @Shadow
    private ServerStatus.Players buildPlayerStatus() {
        return null;
    }

    @Shadow
    @Nullable
    private ServerStatus.Favicon statusIcon;

    @SuppressWarnings("resource")
    @Inject(method = "buildServerStatus", at = @At("HEAD"), cancellable = true)
    private void buildServerStatus(CallbackInfoReturnable<ServerStatus> cir) {
        ServerStatus.Players players = this.buildPlayerStatus();
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (players == null)
            return;// 出错啦
        Component motd = CustomMotdManager.getMotd();
        cir.setReturnValue(
                new ServerStatus(motd, Optional.of(players), Optional.of(Version.current()),
                        Optional.ofNullable(this.statusIcon), server.enforceSecureProfile()));
    }
}
