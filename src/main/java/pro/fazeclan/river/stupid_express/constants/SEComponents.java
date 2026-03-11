package pro.fazeclan.river.stupid_express.constants;

import net.minecraft.world.entity.player.Player;
import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer;
import org.ladysnake.cca.api.v3.entity.RespawnCopyStrategy;
import org.ladysnake.cca.api.v3.world.WorldComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.world.WorldComponentInitializer;
import pro.fazeclan.river.stupid_express.modifier.allergist.cca.AllergistComponent;
import pro.fazeclan.river.stupid_express.modifier.cursed.cca.CursedComponent;
import pro.fazeclan.river.stupid_express.modifier.knight.cca.KnightComponent;
import pro.fazeclan.river.stupid_express.modifier.lovers.cca.LoversComponent;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;
import pro.fazeclan.river.stupid_express.modifier.secretive.cca.SecretiveComponent;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SkinSplitPersonalityComponent;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;
import pro.fazeclan.river.stupid_express.role.arsonist.cca.DousedPlayerComponent;
import pro.fazeclan.river.stupid_express.role.necromancer.cca.NecromancerComponent;

public class SEComponents implements EntityComponentInitializer, WorldComponentInitializer {

    public SEComponents() {}

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.beginRegistration(Player.class, DousedPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(DousedPlayerComponent::new);
        registry.beginRegistration(Player.class, LoversComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(LoversComponent::new);
        registry.beginRegistration(Player.class, AllergistComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(AllergistComponent::new);
        registry.beginRegistration(Player.class, CursedComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(CursedComponent::new);
        registry.beginRegistration(Player.class, SecretiveComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(SecretiveComponent::new);
        registry.beginRegistration(Player.class, KnightComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(KnightComponent::new);
        registry.beginRegistration(Player.class, SplitPersonalityComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(SplitPersonalityComponent::new);
        registry.beginRegistration(Player.class, SkinSplitPersonalityComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(SkinSplitPersonalityComponent::new);

    }

    @Override
    public void registerWorldComponentFactories(WorldComponentFactoryRegistry registry) {
        registry.register(NecromancerComponent.KEY, NecromancerComponent::new);
        registry.register(RefugeeComponent.KEY, RefugeeComponent::new);
    }
}
