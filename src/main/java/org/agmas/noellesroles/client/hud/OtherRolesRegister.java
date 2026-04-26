package org.agmas.noellesroles.client.hud;

import org.agmas.noellesroles.client.hud.modifiers.LoversHud;
import org.agmas.noellesroles.client.hud.modifiers.RefugeeHud;
import org.agmas.noellesroles.client.hud.roles.*;

import io.wifi.events.day_night_fight.client.DNFHud;

public class OtherRolesRegister {

    public static void registerSons() {
        DNFHud.register();
        CustomPendingHud.register();
        AdmirerHud.register();
        AvengerHud.register();
        BomberHud.register();
        BoxerHud.register();
        DetectiveHud.register();
        DetectivePassiveHud.register();
        DIOHud.register();
        ExecutionerHud.register();
        GamblerHud.register();
        InsaneHud.register();
        NecromancerHud.register();
        MagicianHud.register();
        MonitorHud.register();
        MorphlingHud.register();
        NianShouHud.register();
        PhantomHud.register();
        PsychologistHud.register();
        PuppeteerHud.register();
        RecallerHud.register();
        SeaKingHud.register();
        SingerHud.register();
        SuperStarHud.register();
        TrapperHud.register();
        VultureHud.register();
        WaterGhostHud.register();
        RefugeeHud.register();
        LoversHud.register();
        BroadcasterHud.register();
        ImitatorHud.register();
        FoolHud.register();
        SuperLooseEndHud.register();
        PartyKillerHud.register();
    }
}
