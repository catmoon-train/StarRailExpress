package net.exmo.mixin.client;

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

        if (screen instanceof LevelLoadingScreen levelLoadingScreen) {
            return new TrainLoadingScreen(levelLoadingScreen.progressListener,()->false  );
        }
        if (screen instanceof ReceivingLevelScreen receivingLevelScreen) {
            return new SREReceivingLevelScreen(receivingLevelScreen.levelReceived, receivingLevelScreen.reason);
        }
        if (screen instanceof TitleScreen titleScreen){
            return  new StarRailExpressTitleScreen();
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
