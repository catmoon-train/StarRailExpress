package net.exmo.mixin;

import net.exmo.sre.loading.StarRailLoadingOverlay;
import net.exmo.sre.loading.TrainLoadingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Minecraft.class)
public class LoadingScreenMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private Screen setScreen(Screen screen) {

        if (screen instanceof LevelLoadingScreen levelLoadingScreen) {
            return new TrainLoadingScreen(levelLoadingScreen.progressListener);
        }
        return screen;
    }
    @ModifyVariable(method = "setOverlay", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private Overlay setOverlay(Overlay overlay) {

        if (overlay instanceof LoadingOverlay loadingOverlay) {
            StarRailLoadingOverlay.registerTextures(loadingOverlay.minecraft);
            return new StarRailLoadingOverlay(loadingOverlay.minecraft, loadingOverlay.reload, loadingOverlay.onFinish, loadingOverlay.fadeIn);
        }
        return overlay;
    }

}
