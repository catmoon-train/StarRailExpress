package pro.fazeclan.river.stupid_express;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import org.agmas.harpymodloader.component.WorldModifierComponent;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;

public class StupidExpressVoiceChatPlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return StupidExpress.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        VoicechatPlugin.super.initialize(api);
    }

    public void paranoidEvent(MicrophonePacketEvent event) {
        VoicechatServerApi api = event.getVoicechat();
        VoicechatConnection connection_s = event.getSenderConnection();
        if (connection_s == null || connection_s.getPlayer() == null) {
            return;
        }

        ServerPlayer sender = ((ServerPlayer) connection_s.getPlayer().getPlayer());
        WorldModifierComponent modifierComponent = WorldModifierComponent.KEY.get(sender.serverLevel());
        final var senderSpc = SplitPersonalityComponent.KEY.get(sender);

        if (senderSpc.getMainPersonality() != null
                && senderSpc.getSecondPersonality() != null) {
            if (modifierComponent.isModifier(sender, SEModifiers.SPLIT_PERSONALITY)) {
                if (!senderSpc.isDeath() && sender.isSpectator()) {
                    // sender 旁观，给双重人格发语音
                    sender.level().players().forEach((p) -> {
                        if (p != sender) {
                            if (modifierComponent.isModifier(p, SEModifiers.SPLIT_PERSONALITY)) {
                                {
                                    VoicechatConnection con = api.getConnectionOf(p.getUUID());
                                    api.sendLocationalSoundPacketTo(con,
                                            event.getPacket().locationalSoundPacketBuilder()
                                                    .position(api.createPosition(p.getX(), p.getY(), p.getZ()))
                                                    .distance((float) api.getVoiceChatDistance())
                                                    .build());
                                }
                            }
                        }
                    });
                    return;
                }
                // sender 非旁观，该咋处理咋处理
            }
        }

        // if (players.interactionManager.getGameMode().equals(GameMode.SPECTATOR)) {

        // if (gameWorldComponent.isRole(p,
        // Noellesroles.THE_INSANE_DAMNED_PARANOID_KILLER_OF_DOOM_DEATH_DESTRUCTION_AND_WAFFLES)
        // && GameFunctions.isPlayerAliveAndSurvival(p)) {
        // if (players.distanceTo(p) <= api.getVoiceChatDistance()) {
        // VoicechatConnection con = api.getConnectionOf(p.getUuid());
        // api.sendLocationalSoundPacketTo(con,
        // event.getPacket().locationalSoundPacketBuilder()
        // .position(api.createPosition(p.getX(), p.getY(), p.getZ()))
        // .distance((float)api.getVoiceChatDistance())
        // .build());
        // }
        // }
        // });
        // }
    }

    public void preventSplitSound_Entity(EntitySoundPacketEvent event) {
        VoicechatConnection senderConnection = event.getSenderConnection();
        VoicechatConnection receiverConnection = event.getReceiverConnection();

        if (senderConnection == null || receiverConnection == null)
            return;

        if (!(senderConnection.getPlayer().getPlayer() instanceof Player senderPlayer))
            return;
        if (!(receiverConnection.getPlayer().getPlayer() instanceof Player receiverPlayer))
            return;

        WorldModifierComponent modifierComponent = WorldModifierComponent.KEY.get(senderPlayer.level());

        if (modifierComponent.isModifier(receiverPlayer, SEModifiers.SPLIT_PERSONALITY)) {
            final var receiverSpc = SplitPersonalityComponent.KEY.get(receiverPlayer);
            if (receiverSpc.getMainPersonality() != null
                    && receiverSpc.getSecondPersonality() != null) {
                // 如果接收的是双重人格
                if (receiverPlayer.isSpectator()) {
                    // 如果此人还没死，且在旁观（等待切换）
                    if (!receiverSpc.isDeath() && senderPlayer.isSpectator()) {
                        // 拒绝来自旁观 sender 的语音
                        event.cancel();
                        return;
                    }
                }
            }
        }
    }

    public void preventSplitSound_Static(StaticSoundPacketEvent event) {
        VoicechatConnection senderConnection = event.getSenderConnection();
        VoicechatConnection receiverConnection = event.getReceiverConnection();

        if (senderConnection == null || receiverConnection == null)
            return;

        if (!(senderConnection.getPlayer().getPlayer() instanceof Player senderPlayer))
            return;
        if (!(receiverConnection.getPlayer().getPlayer() instanceof Player receiverPlayer))
            return;

        WorldModifierComponent modifierComponent = WorldModifierComponent.KEY.get(senderPlayer.level());

        if (modifierComponent.isModifier(receiverPlayer, SEModifiers.SPLIT_PERSONALITY)) {
            final var receiverSpc = SplitPersonalityComponent.KEY.get(receiverPlayer);
            if (receiverSpc.getMainPersonality() != null
                    && receiverSpc.getSecondPersonality() != null) {
                // 如果接收的是双重人格
                if (receiverPlayer.isSpectator()) {
                    // 如果此人还没死，且在旁观（等待切换）
                    if (!receiverSpc.isDeath() && senderPlayer.isSpectator()) {
                        // 拒绝来自旁观 sender 的语音
                        event.cancel();
                        return;
                    }
                }
            }
        }
    }

    public void preventSplitSound_Locational(LocationalSoundPacketEvent event) {
        VoicechatConnection senderConnection = event.getSenderConnection();
        VoicechatConnection receiverConnection = event.getReceiverConnection();

        if (senderConnection == null || receiverConnection == null)
            return;

        if (!(senderConnection.getPlayer().getPlayer() instanceof Player senderPlayer))
            return;
        if (!(receiverConnection.getPlayer().getPlayer() instanceof Player receiverPlayer))
            return;

        WorldModifierComponent modifierComponent = WorldModifierComponent.KEY.get(senderPlayer.level());

        if (modifierComponent.isModifier(receiverPlayer, SEModifiers.SPLIT_PERSONALITY)) {
            final var receiverSpc = SplitPersonalityComponent.KEY.get(receiverPlayer);
            if (receiverSpc.getMainPersonality() != null
                    && receiverSpc.getSecondPersonality() != null) {
                // 如果接收的是双重人格
                if (receiverPlayer.isSpectator()) {
                    // 如果此人还没死，且在旁观（等待切换）
                    if (!receiverSpc.isDeath() && senderPlayer.isSpectator()) {
                        // 拒绝来自旁观 sender 的语音
                        event.cancel();
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::paranoidEvent);
        registration.registerEvent(LocationalSoundPacketEvent.class, this::preventSplitSound_Locational);
        registration.registerEvent(StaticSoundPacketEvent.class, this::preventSplitSound_Static);
        registration.registerEvent(EntitySoundPacketEvent.class, this::preventSplitSound_Entity);

        VoicechatPlugin.super.registerEvents(registration);
    }
}
