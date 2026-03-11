package pro.fazeclan.river.stupid_express.client.mixin.modifier.split_personality;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.spectator.SpectatorGui;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.wifi.starrailexpress.SRE;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

@Mixin(SpectatorGui.class)
public abstract class BanSpectatorGuiMove {
    @Inject(method = "onHotbarSelected", at = @At("HEAD"), cancellable = true)
    private void se$onHotbarSelected(int i, CallbackInfo ci) {
        if (SRE.isLobby) {
            return;
        }
        try {
            final var worldModifierComponent = WorldModifierComponent.KEY.get(Minecraft.getInstance().player.level());
            if (worldModifierComponent.isModifier(Minecraft.getInstance().player, SEModifiers.SPLIT_PERSONALITY))
                {
                    ci.cancel();
                }

        }catch (Exception ignored){

        }
    }
    @Inject(method = "onMouseMiddleClick", at = @At("HEAD"), cancellable = true)
    private void se$onMouseMiddleClick(CallbackInfo ci) {
        if (SRE.isLobby) {
            return;
        }
        try {
            final var worldModifierComponent = WorldModifierComponent.KEY.get(Minecraft.getInstance().player.level());
            if (worldModifierComponent.isModifier(Minecraft.getInstance().player, SEModifiers.SPLIT_PERSONALITY))
                {
                    ci.cancel();
                }

        }catch (Exception ignored){

        }
    }
}
