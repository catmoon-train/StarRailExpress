package org.agmas.noellesroles.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SkinSplitPersonalityComponent;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;

import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.component.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.utils.RoleUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.wifi.starrailexpress.cca.PlayerMoodComponent;
import io.wifi.starrailexpress.cca.PlayerNoteComponent;
import io.wifi.starrailexpress.cca.PlayerPoisonComponent;
import io.wifi.starrailexpress.cca.PlayerPsychoComponent;
import io.wifi.starrailexpress.cca.PlayerShopComponent;
import io.wifi.starrailexpress.SRE;

@Mixin(PlayerList.class)
public class DecServerJoinPlayer {
    @Inject(method = "placeNewPlayer", at = @At("TAIL"), cancellable = true)
    public void placeNewPlayer(Connection connection, ServerPlayer serverPlayer,
            CommonListenerCookie commonListenerCookie, CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        var modifierComponent = WorldModifierComponent.KEY.get(serverPlayer.level());
        var pl = modifierComponent.modifiers.get(serverPlayer.getUUID());
        if (pl != null) {
            pl.clear();
            modifierComponent.sync();
        }
        SplitPersonalityComponent.KEY.get(serverPlayer).reset();
        SkinSplitPersonalityComponent.KEY.get(serverPlayer).clear();
        serverPlayer.getInventory().clearContent();
        RoleUtils.RemoveAllEffects(serverPlayer);
        RoleUtils.RemoveAllPlayerAttributes(serverPlayer);
        (InsaneKillerPlayerComponent.KEY.get(serverPlayer)).reset();
        ((PlayerMoodComponent) PlayerMoodComponent.KEY.get(serverPlayer)).reset();
        ((PlayerShopComponent) PlayerShopComponent.KEY.get(serverPlayer)).reset();
        (SplitPersonalityComponent.KEY.get(serverPlayer)).clear();
        ((PlayerPoisonComponent) PlayerPoisonComponent.KEY.get(serverPlayer)).reset();
        ((PlayerPsychoComponent) PlayerPsychoComponent.KEY.get(serverPlayer)).reset();
        ((PlayerNoteComponent) PlayerNoteComponent.KEY.get(serverPlayer)).reset();
        ConfigWorldComponent.KEY.get(serverPlayer.level()).sync();
    }

}
