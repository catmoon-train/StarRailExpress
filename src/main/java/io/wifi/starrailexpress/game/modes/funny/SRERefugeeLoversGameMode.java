package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModifierAssigned;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SRERefugeeLoversGameMode extends SREMurderGameMode {
    public SRERefugeeLoversGameMode(ResourceLocation identifier) {
        super(identifier);
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        super.initializeGame(serverWorld, gameWorldComponent, players);
        int refugeeCount = Math.round((float) players.size() * SREConfig.instance().refugeeModeRefugeePercent);
        int t = 0;
        List<ServerPlayer> unassignedPlayers = new ArrayList<>(players);
        WorldModifierComponent wmcca = WorldModifierComponent.KEY.get(serverWorld);
        for (ServerPlayer p : unassignedPlayers) {
            if (wmcca.isModifier(p, SEModifiers.REFUGEE)) {
                refugeeCount--;
            }
        }
        unassignedPlayers.removeIf((p) -> wmcca.isModifier(p, SEModifiers.REFUGEE));
        Collections.shuffle(unassignedPlayers);
        for (ServerPlayer p : unassignedPlayers) {
            if (t >= refugeeCount)
                break;
            wmcca.addModifier(p.getUUID(), SEModifiers.REFUGEE, false);
            ModifierAssigned.EVENT.invoker().assignModifier(p, SEModifiers.REFUGEE);
            t++;
        }
        
        boolean noLimitLover = SREConfig.instance().enableNoLimitLoversInLoverMode;// 允许N角恋
        int loverCount = Math.round((float) players.size() * SREConfig.instance().loverModeLoversPercent);
        t = 0;
        unassignedPlayers = new ArrayList<>(players);
        for (ServerPlayer p : unassignedPlayers) {
            if (wmcca.isModifier(p, SEModifiers.LOVERS)) {
                loverCount--;
            }
        }
        if (!noLimitLover)
            loverCount /= 2;
        unassignedPlayers.removeIf((p) -> wmcca.isModifier(p, SEModifiers.LOVERS));
        Collections.shuffle(unassignedPlayers);
        for (ServerPlayer p : unassignedPlayers) {
            if (t >= loverCount)
                break;
            if (!noLimitLover) {
                if (wmcca.isModifier(p.getUUID(), SEModifiers.LOVERS))
                    continue;
            }
            wmcca.addModifier(p.getUUID(), SEModifiers.LOVERS, false);
            ModifierAssigned.EVENT.invoker().assignModifier(p, SEModifiers.LOVERS);
            t++;
        }
        wmcca.sync();
    }
}
