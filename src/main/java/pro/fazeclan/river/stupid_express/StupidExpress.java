package pro.fazeclan.river.stupid_express;

import dev.doctor4t.ratatouille.util.registrar.SoundEventRegistrar;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.game.WTLooseEndsGameMode;
import io.wifi.starrailexpress.network.RemoveStatusBarPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.events.GameInitializeEvent;
import org.agmas.noellesroles.role.ModRoles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.fazeclan.river.stupid_express.constants.SEItems;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.modifier.lovers.LoversWinCheckEvent;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.PlayerStatsBeforeRefugee;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;
import pro.fazeclan.river.stupid_express.network.SplitBackCamera;
import pro.fazeclan.river.stupid_express.network.SplitPersonalityPackets;
import pro.fazeclan.river.stupid_express.role.initiate.InitiateUtils;

import java.util.ArrayList;
import java.util.List;

public class StupidExpress implements ModInitializer {

    public static String MOD_ID = "stupid_express";
    public static final SoundEventRegistrar SoundRegistrar = new SoundEventRegistrar(MOD_ID);
    public static final SoundEvent SOUND_REGUGEE = SoundRegistrar.create("refugee.music");

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final StupidExpressConfig CONFIG = StupidExpressConfig.getInstance();

    public static List<SRERole> getEnableRoles() {
        return getEnableRoles(true);
    }

    public static List<SRERole> getEnableRoles(boolean removeNonThisRoundRoles) {
        ArrayList<SRERole> clone = new ArrayList<>(TMMRoles.ROLES.values());

        clone.removeIf(r -> HarpyModLoaderConfig.HANDLER.instance().disabled.contains(r.getIdentifier().toString())
                || r.identifier().equals(TMMRoles.DISCOVERY_CIVILIAN.identifier())
                || r.identifier().equals(TMMRoles.LOOSE_END.identifier())
                || r.identifier().equals(ModRoles.MA_CHEN_XU.identifier())
                || r.identifier().equals(ModRoles.DIO_ID)
                || r.identifier().equals(ModRoles.WATER_GHOST_ID)
                || (removeNonThisRoundRoles && Harpymodloader.ROLE_MAX.getOrDefault(r.identifier(), 1) <= 0)
                || r.getOccupiedRoleCount() > 1);
        return clone;
    }

    public static List<SRERole> getEnableKillerRoles() {
        List<SRERole> clone = (getEnableRoles(false));
        clone.removeIf(
                r -> !r.canUseKiller());
        return clone;
    }

    @Override
    public void onInitialize() {
        SERoles.init();

        // mod stuff
        SEItems.init();
        SEModifiers.init();
        InitiateUtils.InitiateChange();

        // 初始化网络包处理
        SplitPersonalityPackets.registerPackets();
        PayloadTypeRegistry.playS2C().register(SplitBackCamera.TYPE, SplitBackCamera.CODEC);

        pro.fazeclan.river.stupid_express.network.SplitPersonalitySwitchPacket.register();

        GameInitializeEvent.EVENT.register((ServerLevel, gameWorldComponent, serverPlayers) -> {
            var refugeeC = RefugeeComponent.KEY.get(ServerLevel);
            if (refugeeC != null) {
                refugeeC.reset();
            }
        });
        StupidEventRegister.register();
        LoversWinCheckEvent.register();
        OnPlayerDeath.EVENT.register((victim, deathReason) -> {
            var gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
            if (gameWorldComponent.getGameMode() instanceof WTLooseEndsGameMode)
                return;
            var modifierComponent = WorldModifierComponent.KEY.get(victim.level());
            if (gameWorldComponent != null) {
                SRERole role = gameWorldComponent.getRole(victim);
                if (role != null) {
                    if (role.identifier().getPath().equals(TMMRoles.LOOSE_END.identifier().getPath())) {
                        var refugeeComponent = RefugeeComponent.KEY.get(victim.level());
                        refugeeComponent.onLooseEndDeath(victim);
                    }
                }
            }
            if (modifierComponent != null) {
                if (modifierComponent.isModifier(victim, SEModifiers.SPLIT_PERSONALITY)) {
                    var splc = SplitPersonalityComponent.KEY.get(victim);
                    if (splc != null && !splc.isDeath()) {
                        splc.setDeath(true);
                    }
                }
            }
        });
        GameInitializeEvent.EVENT.register((ServerLevel, gameWorldComponent, serverPlayers) -> {
            serverPlayers.forEach(serverPlayer -> {
                RemoveStatusBarPayload payload = new RemoveStatusBarPayload("loose_end");
                ServerPlayNetworking.send(serverPlayer, payload);
            });
        });

        SRE.cantSendReplay.add(
                (player -> {
                    WorldModifierComponent modifierComponent = WorldModifierComponent.KEY.get(player.level());
                    return modifierComponent.isModifier(player, SEModifiers.SPLIT_PERSONALITY);
                }));
        SRE.cantUseChatHud.add(
                (player -> {
                    WorldModifierComponent modifierComponent = WorldModifierComponent.KEY.get(player.level());
                    SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(player.level());
                    var role = gameComponent.getRole(player);
                    return role != null && !SRE.canUseChatHud.stream().anyMatch((pre) -> pre.test(role))
                            && modifierComponent.isModifier(player, SEModifiers.SPLIT_PERSONALITY);
                }));

        PlayerStatsBeforeRefugee.RegisterDeathEvent();
    }

    public static ResourceLocation id(String key) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, key);
    }

}
