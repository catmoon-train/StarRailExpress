package org.agmas.noellesroles.mixin.client.roles.morphling;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;

@Mixin(SplitPersonalityComponent.class)
public class SPClientTick {
    @Shadow @Final private Player player;

//    @Inject(method = "clientDo", at = @At("HEAD"), cancellable = true)
//    private void clientDo(CallbackInfo ci) {
//        SplitPersonalityComponent comp = (SplitPersonalityComponent) (Object) this;
//        if (comp.getMainPersonality() ==null || comp.getSecondPersonality()==null)return;
//        if (comp.getTemporaryRevivalStartTick()>0 )return;
//        if (!comp.isDeath()){
//            if (!comp.isCurrentlyActive()){
//                player.level().players().forEach(p -> {
//                    if (p.getUUID().equals(comp.getCurrentActivePerson())){
//                        Minecraft.getInstance().setCameraEntity( p);
//                    }
//                });
//            }
//        }
//    }
}
