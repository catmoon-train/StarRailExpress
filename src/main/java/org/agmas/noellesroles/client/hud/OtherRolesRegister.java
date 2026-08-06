/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.client.hud;

import io.wifi.starrailexpress.customrole.CustomRoleHud;
import org.agmas.noellesroles.client.hud.modifiers.LoversHud;
import org.agmas.noellesroles.client.hud.modifiers.RefugeeHud;
import org.agmas.noellesroles.client.hud.modifiers.ShitSplitHud;
import org.agmas.noellesroles.client.hud.roles.*;

public class OtherRolesRegister {
    public static int warningOffset = 0;
    public static void registerSons() {
        BannedBlockWarningHud.register();
        CuckooHud.register();
        ShitSplitHud.register();
        VoteHud.register();
        GreatDetectiveHud.register();
        CustomPendingHud.register();
        AdmirerHud.register();
        AvengerHud.register();
        BomberHud.register();
        BoxerHud.register();
        AgentHud.register();
        DetectivePassiveHud.register();
        DIOHud.register();
        ExecutionerHud.register();
        GamblerHud.register();
        InsaneHud.register();
        NecromancerHud.register();
        MagicianHud.register();
        MonitorHud.register();
        MorphlingHud.register();
        GhostEyeHud.register();
        SilencerHud.register();
        NostalgistHud.register();
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
        MeatballHud.register();
        MorticianHud.register();
        BuilderHud.register();
        PelicanHud.register();
        GodfatherHud.register();
        WarlockHud.register();
        WizardHud.register();
        RavenHud.register();
        DoomedSinnerHud.register();
        WraithAssassinHud.register();
        org.agmas.noellesroles.client.hud.roles.AmonHud.register();
        AdventurerHud.register();
        ReasonerHud.register();
        VoiceChangerHud.register();
        EmbalmerHud.register();
        SkincrawlerHud.register();
        SwapperHud.register();
        PhantomMusicianHud.register();
        UndeadLordHud.register();
        VeteranHud.register();
        CakeMakerHud.register();
        LeonHud.register();
        HunterHud.register();
        THReimuHud.register();
        LeaderHud.register();
        // 自定义职业HUD
        CustomRoleHud.register();
    }
}
