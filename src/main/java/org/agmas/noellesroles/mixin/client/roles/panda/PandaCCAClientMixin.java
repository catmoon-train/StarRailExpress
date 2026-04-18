package org.agmas.noellesroles.mixin.client.roles.panda;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.neutral.panda.PandaClientHandle;
import org.agmas.noellesroles.game.roles.neutral.panda.PandaComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PandaComponent.class)
public abstract class PandaCCAClientMixin  {

    @Shadow
    public boolean isPanda;
    @Shadow
    public abstract Player getPlayer();
    @Inject(method = "clientTick", at = @At("RETURN"))
    public void tick(CallbackInfo ci) {
        if (isPanda){
            PandaClientHandle.getOrCreatePanda(this.getPlayer(), Minecraft.getInstance().level);
        }else {
            PandaClientHandle.pandaMap.remove(this.getPlayer().getUUID());
        }

    }
    @Inject(method = "clear", at = @At("HEAD"))
    public void clear(CallbackInfo ci) {
        if (isPanda){
            PandaClientHandle.pandaMap.remove(this.getPlayer().getUUID());
        }

    }
}
