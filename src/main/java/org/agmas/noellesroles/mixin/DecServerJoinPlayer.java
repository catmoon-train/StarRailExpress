package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.utils.RoleUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SkinSplitPersonalityComponent;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;

@Mixin(PlayerList.class)
public class DecServerJoinPlayer {
    @Inject(method = "placeNewPlayer", at = @At("TAIL"), cancellable = true)
    public void placeNewPlayer(Connection connection, ServerPlayer serverPlayer,
            CommonListenerCookie commonListenerCookie, CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        var modifierComponent = WorldModifierComponent.KEY.get(serverPlayer.level());
        if (!modifierComponent.getModifiers(serverPlayer.getUUID()).isEmpty()) {
            var pl = modifierComponent.modifiers.get(serverPlayer.getUUID());
            if (pl != null) {
                if (pl.contains(SEModifiers.SPLIT_PERSONALITY)) {
                    SplitPersonalityComponent.KEY.get(serverPlayer).init();
                    SkinSplitPersonalityComponent.KEY.get(serverPlayer).clear();
                }
                pl.clear();
                modifierComponent.sync();
            }

        }
        serverPlayer.getInventory().clearContent();
        if (!serverPlayer.getActiveEffects().isEmpty()) {
            RoleUtils.RemoveAllEffects(serverPlayer);
        }
        RoleUtils.RemoveAllPlayerAttributes(serverPlayer);
        ConfigWorldComponent.KEY.get(serverPlayer.level()).sync();
    }

}
