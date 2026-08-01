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

import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.client.gui.screen.WithParentScreenPauseScreen;
import net.exmo.sre.loading.SREReceivingLevelScreen;
import net.exmo.sre.loading.StarRailExpressTitleScreen;
import net.exmo.sre.loading.StarRailLoadingOverlay;
import net.exmo.sre.loading.TrainLoadingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Minecraft.class)
public class LoadingScreenMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private Screen setScreen(Screen screen) {
        if (!SREClientConfig.instance().disableCustomLoadingScreen) {
            if (screen instanceof LevelLoadingScreen levelLoadingScreen) {
                return new TrainLoadingScreen(levelLoadingScreen.progressListener, () -> false);
            }
            if (screen instanceof ReceivingLevelScreen receivingLevelScreen) {
                return new SREReceivingLevelScreen(receivingLevelScreen.levelReceived, receivingLevelScreen.reason);
            }
        }
        if (!SREClientConfig.instance().disableCustomTitleScreen) {
            if (screen instanceof TitleScreen) {
                return new StarRailExpressTitleScreen();
            } else if (screen instanceof PauseScreen ps && !(screen instanceof WithParentScreenPauseScreen)) {
                return new WithParentScreenPauseScreen(ps.showPauseMenu);
            }
        }
        return screen;
    }

    @ModifyVariable(method = "setOverlay", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private Overlay setOverlay(Overlay overlay) {
        if (SREClientConfig.instance().disableCustomLoadingScreen) {
            return overlay;
        }
        if (overlay instanceof LoadingOverlay loadingOverlay) {
            StarRailLoadingOverlay.registerTextures(loadingOverlay.minecraft);
            return new StarRailLoadingOverlay(loadingOverlay.minecraft, loadingOverlay.reload, loadingOverlay.onFinish,
                    loadingOverlay.fadeIn);
        }
        return overlay;
    }

}
