package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.cca.gamemode.CustomRoleGameModeTeamsPlayerComponent;
import io.wifi.starrailexpress.cca.gamemode.CustomRoleGameModeWorldComponent;
import io.wifi.starrailexpress.content.mail.MailboxComponent;
import io.wifi.events.day_night_fight.DNFDailyTaskComponent;
import io.wifi.events.day_night_fight.DNFKillerStatsComponent;
import io.wifi.events.day_night_fight.DNFPlayerComponent;
import net.exmo.sre.nametag.NameTagInventoryComponent;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer;
import org.ladysnake.cca.api.v3.entity.RespawnCopyStrategy;
import org.ladysnake.cca.api.v3.world.WorldComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.world.WorldComponentInitializer;

public class SREComponents
        implements WorldComponentInitializer, EntityComponentInitializer {
    @Override
    public void registerWorldComponentFactories(@NotNull WorldComponentFactoryRegistry registry) {
        registry.register(SRETrainWorldComponent.KEY, SRETrainWorldComponent::new);
        registry.register(CustomRoleGameModeWorldComponent.KEY, CustomRoleGameModeWorldComponent::new);
        registry.register(SREGameWorldComponent.KEY, SREGameWorldComponent::new);
        registry.register(SRERoleWorldComponent.KEY, SRERoleWorldComponent::new);
        registry.register(AreasWorldComponent.KEY, AreasWorldComponent::new);
        registry.register(SREWorldBlackoutComponent.KEY, SREWorldBlackoutComponent::new);
        registry.register(SREMonitorWorldComponent.KEY, SREMonitorWorldComponent::new);
        registry.register(SREGameTimeComponent.KEY, SREGameTimeComponent::new);
        registry.register(AutoStartComponent.KEY, AutoStartComponent::new);
        registry.register(SREGameRoundEndComponent.KEY, SREGameRoundEndComponent::new);
        registry.register(MapVotingComponent.KEY, MapVotingComponent::new);
    }

    @Override
    public void registerEntityComponentFactories(@NotNull EntityComponentFactoryRegistry registry) {
        registry.beginRegistration(Player.class, SREArmorPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(SREArmorPlayerComponent::new);
        registry.beginRegistration(Player.class, SREPlayerTaskComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(SREPlayerTaskComponent::new);
        registry.beginRegistration(Player.class, ExtraSlotComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(ExtraSlotComponent::new);
        registry.beginRegistration(Player.class, SREPlayerMoodComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(SREPlayerMoodComponent::new);
        registry.beginRegistration(Player.class, SREPlayerShopComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(SREPlayerShopComponent::new);
        registry.beginRegistration(Player.class, DynamicShopComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(DynamicShopComponent::new);
        registry.beginRegistration(Player.class, SREPlayerPoisonComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(SREPlayerPoisonComponent::new);
        registry.beginRegistration(Player.class, SREPlayerPsychoComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(SREPlayerPsychoComponent::new);
        registry.beginRegistration(Player.class, SREPlayerNoteComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(SREPlayerNoteComponent::new);
        registry.beginRegistration(Player.class, SREPlayerClueComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY).end(SREPlayerClueComponent::new);
        registry.beginRegistration(Player.class, SREPlayerStatsComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY).end(SREPlayerStatsComponent::new);
        registry.beginRegistration(Player.class, SREPlayerAFKComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(SREPlayerAFKComponent::new);
        registry.beginRegistration(Player.class, SREPlayerSkinsComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY).end(SREPlayerSkinsComponent::new);
        registry.beginRegistration(Player.class, SREPlayerProgressionComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY).end(SREPlayerProgressionComponent::new);
        registry.beginRegistration(Player.class, SREPlayerNunchuckComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(SREPlayerNunchuckComponent::new);
        registry.beginRegistration(Player.class, NameTagInventoryComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY).end(NameTagInventoryComponent::new);
        registry.beginRegistration(Player.class, MailboxComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY).end(MailboxComponent::new);
        registry.beginRegistration(Player.class, CustomRoleGameModeTeamsPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY).end(CustomRoleGameModeTeamsPlayerComponent::new);
        registry.beginRegistration(Player.class, DNFKillerStatsComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(DNFKillerStatsComponent::new);
        registry.beginRegistration(Player.class, DNFDailyTaskComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(DNFDailyTaskComponent::new);
        registry.beginRegistration(Player.class, DNFPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(DNFPlayerComponent::new);
    }
}
