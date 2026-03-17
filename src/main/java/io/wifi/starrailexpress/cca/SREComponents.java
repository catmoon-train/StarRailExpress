package io.wifi.starrailexpress.cca;

import net.exmo.sre.nametag.NameTagInventoryComponent;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer;
import org.ladysnake.cca.api.v3.entity.RespawnCopyStrategy;
import org.ladysnake.cca.api.v3.scoreboard.ScoreboardComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.scoreboard.ScoreboardComponentInitializer;
import org.ladysnake.cca.api.v3.world.WorldComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.world.WorldComponentInitializer;

public class SREComponents
        implements WorldComponentInitializer, EntityComponentInitializer, ScoreboardComponentInitializer {
    @Override
    public void registerWorldComponentFactories(@NotNull WorldComponentFactoryRegistry registry) {
        registry.register(SRETrainWorldComponent.KEY, SRETrainWorldComponent::new);
        registry.register(SREGameWorldComponent.KEY, SREGameWorldComponent::new);
        registry.register(SRERoleWorldComponent.KEY, SRERoleWorldComponent::new);
        registry.register(AreasWorldComponent.KEY, AreasWorldComponent::new);
        registry.register(SREWorldBlackoutComponent.KEY, SREWorldBlackoutComponent::new);
        registry.register(SREGameTimeComponent.KEY, SREGameTimeComponent::new);
        registry.register(AutoStartComponent.KEY, AutoStartComponent::new);
        registry.register(SREGameRoundEndComponent.KEY, SREGameRoundEndComponent::new);
        registry.register(MapVotingComponent.KEY, MapVotingComponent::new);
    }

    @Override
    public void registerEntityComponentFactories(@NotNull EntityComponentFactoryRegistry registry) {
        registry.beginRegistration(Player.class, BartenderPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(BartenderPlayerComponent::new);
        registry.beginRegistration(Player.class, SREPlayerTaskComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(SREPlayerTaskComponent::new);
        registry.beginRegistration(Player.class, SREPlayerMoodComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(SREPlayerMoodComponent::new);
        registry.beginRegistration(Player.class, SREPlayerShopComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(SREPlayerShopComponent::new);
        registry.beginRegistration(Player.class, DynamicShopComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY).end(DynamicShopComponent::new);
        registry.beginRegistration(Player.class, SREPlayerPoisonComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(SREPlayerPoisonComponent::new);
        registry.beginRegistration(Player.class, SREPlayerPsychoComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(SREPlayerPsychoComponent::new);
        registry.beginRegistration(Player.class, SREPlayerNoteComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(SREPlayerNoteComponent::new);
        registry.beginRegistration(Player.class, SREPlayerStatsComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY).end(SREPlayerStatsComponent::new);
        registry.beginRegistration(Player.class, SREPlayerAFKComponent.KEY).respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(SREPlayerAFKComponent::new);
        registry.beginRegistration(Player.class, SREPlayerSkinsComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY).end(SREPlayerSkinsComponent::new);
        registry.beginRegistration(Player.class, SREPlayerNunchuckComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(SREPlayerNunchuckComponent::new);
        registry.beginRegistration(Player.class, NameTagInventoryComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY).end(NameTagInventoryComponent::new);
    }

    @Override
    public void registerScoreboardComponentFactories(@NotNull ScoreboardComponentFactoryRegistry registry) {
        // 注册新的GameScoreboardComponent
        registry.registerScoreboardComponent(SREGameScoreboardComponent.KEY, SREGameScoreboardComponent::new);
    }

}