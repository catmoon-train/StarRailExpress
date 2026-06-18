package io.wifi.starrailexpress.cca;


import io.wifi.starrailexpress.cca.gamemode.CustomRoleGameModeTeamsPlayerComponent;
import io.wifi.starrailexpress.cca.gamemode.CustomRoleGameModeWorldComponent;
import io.wifi.starrailexpress.cca.gamemode.RoleRotationPlayerComponent;
import io.wifi.starrailexpress.cca.gamemode.RoleRotationWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.content.mail.MailboxComponent;

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
        registry.register(ParticipationComponent.KEY, ParticipationComponent::new);
        registry.register(SREGameRoundEndComponent.KEY, SREGameRoundEndComponent::new);
        registry.register(MapVotingComponent.KEY, MapVotingComponent::new);
        registry.register(RoleRotationWorldComponent.KEY, RoleRotationWorldComponent::new);


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

        registry.beginRegistration(Player.class, SREPlayerAFKComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(SREPlayerAFKComponent::new);
        registry.beginRegistration(Player.class, SREPlayerSkinsComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY).end(SREPlayerSkinsComponent::new);

        registry.beginRegistration(PlayerBodyEntity.class, PlayerBodyEntityComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(PlayerBodyEntityComponent::new);
        registry.beginRegistration(Player.class, SREPlayerNunchuckComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(SREPlayerNunchuckComponent::new);
        registry.beginRegistration(Player.class, NameTagInventoryComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY).end(NameTagInventoryComponent::new);
        registry.beginRegistration(Player.class, MailboxComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY).end(MailboxComponent::new);
        registry.beginRegistration(Player.class, CustomRoleGameModeTeamsPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY)
                .end(CustomRoleGameModeTeamsPlayerComponent::new);
        registry.beginRegistration(Player.class, RoleRotationPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY)
                .end(RoleRotationPlayerComponent::new);
        registry.beginRegistration(Player.class, SREPlayerDamageTrackerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(SREPlayerDamageTrackerComponent::new);
    }
}
