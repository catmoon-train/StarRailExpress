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
        registry.register(TrainWorldComponent.KEY, TrainWorldComponent::new);
        registry.register(GameWorldComponent.KEY, GameWorldComponent::new);
        registry.register(RoleWorldComponent.KEY, RoleWorldComponent::new);
        registry.register(AreasWorldComponent.KEY, AreasWorldComponent::new);
        registry.register(WorldBlackoutComponent.KEY, WorldBlackoutComponent::new);
        registry.register(GameTimeComponent.KEY, GameTimeComponent::new);
        registry.register(AutoStartComponent.KEY, AutoStartComponent::new);
        registry.register(GameRoundEndComponent.KEY, GameRoundEndComponent::new);
        registry.register(MapVotingComponent.KEY, MapVotingComponent::new);
    }

    @Override
    public void registerEntityComponentFactories(@NotNull EntityComponentFactoryRegistry registry) {
        registry.beginRegistration(Player.class, BartenderPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(BartenderPlayerComponent::new);
        registry.beginRegistration(Player.class, AbilityPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(AbilityPlayerComponent::new);
        registry.beginRegistration(Player.class, PlayerMoodComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(PlayerMoodComponent::new);
        registry.beginRegistration(Player.class, PlayerShopComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(PlayerShopComponent::new);
        registry.beginRegistration(Player.class, DynamicCoinComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY).end(DynamicCoinComponent::new);
        registry.beginRegistration(Player.class, PlayerPoisonComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(PlayerPoisonComponent::new);
        registry.beginRegistration(Player.class, PlayerPsychoComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(PlayerPsychoComponent::new);
        registry.beginRegistration(Player.class, PlayerNoteComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(PlayerNoteComponent::new);
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
        registry.registerScoreboardComponent(ScoreboardRoleSelectorComponent.KEY, ScoreboardRoleSelectorComponent::new);
        // 注册新的GameScoreboardComponent
        registry.registerScoreboardComponent(GameScoreboardComponent.KEY, GameScoreboardComponent::new);
    }

}