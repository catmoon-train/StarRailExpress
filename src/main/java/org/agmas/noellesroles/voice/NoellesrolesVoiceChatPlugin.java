package org.agmas.noellesroles.voice;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.component.PlayerVolumeComponent;
import org.agmas.noellesroles.effects.TimeStopEffect;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.roles.commander.CommanderHandler;

public class NoellesrolesVoiceChatPlugin implements VoicechatPlugin {
  @Override
  public String getPluginId() {
    return Noellesroles.MOD_ID;
  }

  @Override
  public void initialize(VoicechatApi api) {
    VoicechatPlugin.super.initialize(api);
  }

  public void vtMode_Static(StaticSoundPacketEvent event) {
    // VoicechatServerApi api = event.getVoicechat();
    VoicechatConnection senderConnection = event.getSenderConnection();
    VoicechatConnection receiverConnection = event.getReceiverConnection();
    if (senderConnection == null || receiverConnection == null)
      return;

    if (!(senderConnection.getPlayer().getPlayer() instanceof Player senderPlayer))
      return;
    if (!(receiverConnection.getPlayer().getPlayer() instanceof Player receiverPlayer))
      return;

    var pvc = PlayerVolumeComponent.KEY.get(receiverPlayer);
    if (receiverPlayer.isSpectator() && pvc.vtMode) {
      if (senderPlayer.isSpectator()
          && SREGameWorldComponent.KEY.get(senderPlayer.level()).isRunning()) {
        event.cancel();
        return;
      }
    }
  }

  public void vtMode_Entity(EntitySoundPacketEvent event) {
    // VoicechatServerApi api = event.getVoicechat();
    VoicechatConnection senderConnection = event.getSenderConnection();
    VoicechatConnection receiverConnection = event.getReceiverConnection();
    if (senderConnection == null || receiverConnection == null)
      return;

    if (!(senderConnection.getPlayer().getPlayer() instanceof Player senderPlayer))
      return;
    if (!(receiverConnection.getPlayer().getPlayer() instanceof Player receiverPlayer))
      return;

    var pvc = PlayerVolumeComponent.KEY.get(receiverPlayer);
    if (receiverPlayer.isSpectator() && pvc.vtMode) {
      if (senderPlayer.isSpectator()
          && SREGameWorldComponent.KEY.get(senderPlayer.level()).isRunning()) {
        event.cancel();
        return;
      }
    }
  }

  public void vtMode_Locational(LocationalSoundPacketEvent event) {
    // VoicechatServerApi api = event.getVoicechat();
    VoicechatConnection senderConnection = event.getSenderConnection();
    VoicechatConnection receiverConnection = event.getReceiverConnection();
    if (senderConnection == null || receiverConnection == null)
      return;

    if (!(senderConnection.getPlayer().getPlayer() instanceof Player senderPlayer))
      return;
    if (!(receiverConnection.getPlayer().getPlayer() instanceof Player receiverPlayer))
      return;

    var pvc = PlayerVolumeComponent.KEY.get(receiverPlayer);
    if (receiverPlayer.isSpectator() && pvc.vtMode) {
      if (senderPlayer.isSpectator()
          && SREGameWorldComponent.KEY.get(senderPlayer.level()).isRunning()) {
        event.cancel();
        return;
      }
    }
  }

  public static boolean shouldBanVoice(VoicechatConnection senderConnection, VoicechatConnection receiverConnection) {
    if (senderConnection == null || receiverConnection == null)
      return false;

    if (!(senderConnection.getPlayer().getPlayer() instanceof Player senderPlayer))
      return false;
    if (!(receiverConnection.getPlayer().getPlayer() instanceof Player receiverPlayer))
      return false;
    if (senderPlayer.getEffect(ModEffects.TIME_STOP) != null) {
      if (!TimeStopEffect.canMovePlayers.contains(senderPlayer.getUUID())) {
        return true;
      }
    }
    if (receiverPlayer.getEffect(ModEffects.TIME_STOP) != null) {
      if (!TimeStopEffect.canMovePlayers.contains(receiverPlayer.getUUID())) {
        return true;
      }
    }
    var deathPenalty = ModComponents.DEATH_PENALTY.get(receiverPlayer);
    if (deathPenalty.hasPenalty()) {
      if (deathPenalty.limitCameraUUID != null) {
        return true;
      }
    }
    return false;
  }

  public void timeStopper_Static(StaticSoundPacketEvent event) {
    // VoicechatServerApi api = event.getVoicechat();
    VoicechatConnection senderConnection = event.getSenderConnection();
    VoicechatConnection receiverConnection = event.getReceiverConnection();
    if (shouldBanVoice(senderConnection, receiverConnection)) {
      event.cancel();
      return;
    }
  }

  public void timeStopper_Entity(EntitySoundPacketEvent event) {
    // VoicechatServerApi api = event.getVoicechat();
    VoicechatConnection senderConnection = event.getSenderConnection();
    VoicechatConnection receiverConnection = event.getReceiverConnection();
    if (shouldBanVoice(senderConnection, receiverConnection)) {
      event.cancel();
      return;
    }
  }

  public void timeStopper_Locational(LocationalSoundPacketEvent event) {
    // VoicechatServerApi api = event.getVoicechat();
    VoicechatConnection senderConnection = event.getSenderConnection();
    VoicechatConnection receiverConnection = event.getReceiverConnection();
    if (shouldBanVoice(senderConnection, receiverConnection)) {
      event.cancel();
      return;
    }
  }

  public void paranoidEvent(MicrophonePacketEvent event) {
    VoicechatServerApi api = event.getVoicechat();
    var connection = event.getSenderConnection();
    if (connection != null && connection.isInstalled() && connection.isConnected()) {
      var vcplayer = connection.getPlayer();
      if (vcplayer != null) {
        var vctplayer = vcplayer.getPlayer();
        if (vctplayer != null) {
          var player = (ServerPlayer) vctplayer;
          SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
          if (gameWorldComponent != null) {
            if (GameUtils.isPlayerAliveAndSurvival(player)) {
              if (gameWorldComponent.isRole(player, ModRoles.NOISEMAKER)) {
                event.cancel();
                player.level().players().forEach((p) -> {
                  if (p.getUUID() != player.getUUID()) {
                    double rangeMultiplier = 2;
                    if (player.getActiveEffectsMap().containsKey(MobEffects.LUCK)) {
                      rangeMultiplier = 8;
                    }
                    if (player.distanceTo(p) <= api.getVoiceChatDistance() * rangeMultiplier) {
                      VoicechatConnection con = api.getConnectionOf(p.getUUID());
                      if (con != null && con.isInstalled() && con.isConnected()) {
                        api.sendLocationalSoundPacketTo(con, event.getPacket()
                            .locationalSoundPacketBuilder()
                            .position(api.createPosition(p.getX(), p.getY(), p.getZ()))
                            .distance((float) api.getVoiceChatDistance())
                            .build());
                      }
                    }
                  }
                });
              } else
                CommanderHandler.vcparanoidEvent(gameWorldComponent, player, event);
            }
          }
        }
      }
    }
    // ServerPlayer players = ((ServerPlayer)
    // event.getSenderConnection().getPlayer().getPlayer());

    // if (players.interactionManager.getGameMode().equals(GameMode.SPECTATOR)) {

    // }
  }

  @Override
  public void registerEvents(EventRegistration registration) {
    registration.registerEvent(MicrophonePacketEvent.class, this::paranoidEvent);

    registration.registerEvent(LocationalSoundPacketEvent.class, this::timeStopper_Locational);
    registration.registerEvent(StaticSoundPacketEvent.class, this::timeStopper_Static);
    registration.registerEvent(EntitySoundPacketEvent.class, this::timeStopper_Entity);

    registration.registerEvent(LocationalSoundPacketEvent.class, this::vtMode_Locational);
    registration.registerEvent(StaticSoundPacketEvent.class, this::vtMode_Static);
    registration.registerEvent(EntitySoundPacketEvent.class, this::vtMode_Entity);
    VoicechatPlugin.super.registerEvents(registration);
  }
}
