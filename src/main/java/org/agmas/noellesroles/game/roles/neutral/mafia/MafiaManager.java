package org.agmas.noellesroles.game.roles.neutral.mafia;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.packet.MafiaActionC2SPacket;
import org.agmas.noellesroles.packet.MafiaStateS2CPacket;
import org.agmas.noellesroles.packet.MafiaAmmoS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.*;

public final class MafiaManager {
    private static final Map<UUID, UUID> godfatherByMember = new HashMap<>();
    private static final Map<UUID, SRERole> previousRoleByMember = new HashMap<>();
    private static int syncTick;

    public static void register() {
        PayloadTypeRegistry.playC2S().register(MafiaActionC2SPacket.ID, MafiaActionC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(MafiaStateS2CPacket.ID, MafiaStateS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(MafiaAmmoS2CPacket.ID, MafiaAmmoS2CPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(MafiaActionC2SPacket.ID,
            (payload, context) -> context.server().execute(() -> handleAction(context.player(), payload.action(), payload.target())));
        ServerTickEvents.END_WORLD_TICK.register(MafiaManager::tick);
    }

    private static void tick(ServerLevel world) {
        if (++syncTick % 20 != 0) return;
        for (ServerPlayer p : world.players())
            if (isGodfather(p)) syncFamily(p);
    }

    private static void handleAction(ServerPlayer player, int action, UUID target) {
        if (!isGodfather(player)) return;
        if (target == null) return;
        ServerPlayer tgt = player.server.getPlayerList().getPlayer(target);
        if (tgt == null || !GameUtils.isPlayerAliveAndSurvival(tgt)) return;
        var comp = GodfatherComponent.KEY.get(player);
        long now = player.level().getGameTime();

        if (action == MafiaActionC2SPacket.RECRUIT_MAFIOSO || action == MafiaActionC2SPacket.RECRUIT_JANITOR) {
            if (now < comp.recruitCooldownUntil) return;
            if (comp.familyMembers.size() >= comp.recruitLimit) return;
            if (!isRecruitable(tgt)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.noellesroles.godfather.cannot_recruit"), true);
                return;
            }
            SRERole prevRole = SREGameWorldComponent.KEY.get(tgt.level()).getRole(tgt);
            SRERole newRole = action == MafiaActionC2SPacket.RECRUIT_MAFIOSO ? ModRoles.MAFIOSO : ModRoles.JANITOR;
            previousRoleByMember.put(target, prevRole);
            godfatherByMember.put(target, player.getUUID());
            comp.familyMembers.add(target);
            RoleUtils.changeRole(tgt, newRole);
            comp.recruitCooldownUntil = now + comp.recruitCooldownSeconds * 20L;
            comp.sync();
            syncFamily(player);
        }
    }

    public static boolean isRecruitable(ServerPlayer p) {
        var role = SREGameWorldComponent.KEY.get(p.level()).getRole(p);
        return role != null && role.canBeRandomed();
    }
    public static boolean isGodfather(ServerPlayer p) {
        return SREGameWorldComponent.KEY.get(p.level()).isRole(p, ModRoles.GODFATHER);
    }
    public static boolean isMafiaMember(ServerPlayer p) {
        var gw = SREGameWorldComponent.KEY.get(p.level());
        return gw.isRole(p, ModRoles.GODFATHER) || gw.isRole(p, ModRoles.MAFIOSO) || gw.isRole(p, ModRoles.JANITOR);
    }

    public static void onGodfatherDeath(ServerPlayer godfather) {
        UUID gfId = godfather.getUUID();
        for (UUID memberId : new ArrayList<>(godfatherByMember.keySet())) {
            if (gfId.equals(godfatherByMember.get(memberId))) {
                ServerPlayer member = godfather.server.getPlayerList().getPlayer(memberId);
                if (member != null && previousRoleByMember.containsKey(memberId)) {
                    RoleUtils.changeRole(member, previousRoleByMember.get(memberId));
                }
                godfatherByMember.remove(memberId);
                previousRoleByMember.remove(memberId);
            }
        }
    }

    // Bullet system
    public static int getLoadedBullets(ServerPlayer godfather) {
        return GodfatherComponent.KEY.get(godfather).loadedBullets;
    }
    public static int getMaxLoadedBullets(ServerPlayer godfather) {
        return GodfatherComponent.KEY.get(godfather).maxLoadedBullets;
    }
    public static void consumeBullet(ServerPlayer godfather) {
        var comp = GodfatherComponent.KEY.get(godfather);
        if (comp.loadedBullets > 0) { comp.loadedBullets--; comp.sync(); syncAmmo(godfather); }
    }
    public static boolean tryLoadBullet(ServerPlayer godfather) {
        var comp = GodfatherComponent.KEY.get(godfather);
        if (comp.loadedBullets >= comp.maxLoadedBullets) return false;
        comp.loadedBullets++; comp.sync(); syncAmmo(godfather); return true;
    }

    public static void syncFamily(ServerPlayer godfather) {
        var comp = GodfatherComponent.KEY.get(godfather);
        List<UUID> members = new ArrayList<>(comp.familyMembers);
        for (ServerPlayer p : godfather.serverLevel().players())
            ServerPlayNetworking.send(p, new MafiaStateS2CPacket(members, ModRoles.GODFATHER.color()));
    }
    public static void syncAmmo(ServerPlayer godfather) {
        var comp = GodfatherComponent.KEY.get(godfather);
        ServerPlayNetworking.send(godfather, new MafiaAmmoS2CPacket(comp.loadedBullets, comp.maxLoadedBullets));
    }

    public static void playSpawnSound(ServerPlayer godfather) {
        for (var p : godfather.serverLevel().players()) {
            if (p != null) {
                p.playNotifySound(NRSounds.MAFIA, SoundSource.MASTER, 1.0F, 1.0F);
            }
        }
    }

    public static boolean checkMafiaVictory(ServerLevel level) {
        int alive = 0, mafiaAlive = 0;
        for (ServerPlayer p : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(p)) continue;
            alive++;
            if (isMafiaMember(p)) mafiaAlive++;
        }
        if (mafiaAlive > 0 && alive == mafiaAlive) {
            for (ServerPlayer p : level.players())
                if (isMafiaMember(p) && GameUtils.isPlayerAliveAndSurvival(p))
                    RoleUtils.customWinnerWin(level, WinStatus.CUSTOM, "godfather", java.util.OptionalInt.of(ModRoles.GODFATHER.color()));
            return true;
        }
        return false;
    }

    public static boolean shouldPreventGameEnd(ServerLevel level) {
        for (ServerPlayer p : level.players())
            if (GameUtils.isPlayerAliveAndSurvival(p) && isGodfather(p)) return true;
        return false;
    }

    public static void clearAll() {
        godfatherByMember.clear();
        previousRoleByMember.clear();
        syncTick = 0;
    }
}
