package io.wifi.starrailexpress.mixin.gui;

import io.wifi.starrailexpress.client.gui.screen.ingame.DrawableGet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;

@Mixin(Screen.class)
public class ScreenDrawableGetMixin implements DrawableGet {

    @Shadow @Final private List<Renderable> renderables;

    @Override
    public List<Renderable> getDrawable() {
        return this.renderables;
    }
}
