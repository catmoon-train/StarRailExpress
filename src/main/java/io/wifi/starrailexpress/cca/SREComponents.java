package io.wifi.starrailexpress.cca;

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
        registry.register(StarTrainWorldComponent.KEY, StarTrainWorldComponent::new);
        registry.register(StarGameWorldComponent.KEY, StarGameWorldComponent::new);
        registry.register(RoleWorldComponent.KEY, RoleWorldComponent::new);
        registry.register(AreasWorldComponent.KEY, AreasWorldComponent::new);
        registry.register(StarWorldBlackoutComponent.KEY, StarWorldBlackoutComponent::new);
        registry.register(StarGameTimeComponent.KEY, StarGameTimeComponent::new);
        registry.register(AutoStartComponent.KEY, AutoStartComponent::new);
        registry.register(StarGameRoundEndComponent.KEY, StarGameRoundEndComponent::new);
        registry.register(MapVotingComponent.KEY, MapVotingComponent::new);
    }

    @Override
    public void registerEntityComponentFactories(@NotNull EntityComponentFactoryRegistry registry) {
        registry.beginRegistration(Player.class, BartenderPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(BartenderPlayerComponent::new);
        registry.beginRegistration(Player.class, StarPlayerMoodComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(StarPlayerMoodComponent::new);
        registry.beginRegistration(Player.class, StarPlayerShopComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(StarPlayerShopComponent::new);
        registry.beginRegistration(Player.class, DynamicCoinComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY).end(DynamicCoinComponent::new);
        registry.beginRegistration(Player.class, StarPlayerPoisonComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(StarPlayerPoisonComponent::new);
        registry.beginRegistration(Player.class, StarPlayerPsychoComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(StarPlayerPsychoComponent::new);
        registry.beginRegistration(Player.class, StarPlayerNoteComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(StarPlayerNoteComponent::new);
        registry.beginRegistration(Player.class, PlayerStatsComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY).end(PlayerStatsComponent::new);
        registry.beginRegistration(Player.class, PlayerAFKComponent.KEY).respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(PlayerAFKComponent::new);
        registry.beginRegistration(Player.class, PlayerSkinsComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY).end(PlayerSkinsComponent::new);
        registry.beginRegistration(Player.class, PlayerNunchuckComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(PlayerNunchuckComponent::new);
    }

    @Override
    public void registerScoreboardComponentFactories(@NotNull ScoreboardComponentFactoryRegistry registry) {
        // 注册新的GameScoreboardComponent
        registry.registerScoreboardComponent(GameScoreboardComponent.KEY, GameScoreboardComponent::new);
    }

}