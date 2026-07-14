package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.exmo.sre.sixtyseconds.network.OpenTeamLobbyS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 末日60秒赛前组队大厅（服务端，全服静态态；游戏未开始时可用）。
 *
 * <p>玩家通过 {@code /sre:60s team} 打开组队页面，可创建/加入/离开最多
 * {@link SixtySecondsTeamAllocator#TEAM_SIZE} 人的预组队伍。这里只是「偏好登记」：
 * 真正的分队在开局时由 {@link SixtySecondsTeamAllocator} 完成——不参与本局的成员会被
 * 剔除出分配池，未满队伍用散人补足，3 人队可能被拆散（界面上有提示）。
 *
 * <p>预组信息跨局保留（打完一局无需重新组队），成员掉线在每次快照/分配前懒清理。
 */
public final class SixtySecondsTeamLobby {
    /** partyId → 成员（保持加入顺序）。 */
    private static final Map<Integer, List<UUID>> PARTIES = new LinkedHashMap<>();
    private static int nextPartyId = 1;

    private SixtySecondsTeamLobby() {
    }

    public static final int ACTION_CREATE = 0;
    public static final int ACTION_JOIN = 1;
    public static final int ACTION_LEAVE = 2;
    /** 管理员（OP）解散指定预组队伍。 */
    public static final int ACTION_DISBAND = 3;

    /** 供开局分队使用的预组队伍快照（懒清理掉线成员后）。 */
    public static List<List<UUID>> partiesForAllocation(MinecraftServer server) {
        prune(server);
        List<List<UUID>> copy = new ArrayList<>();
        for (List<UUID> members : PARTIES.values()) {
            copy.add(new ArrayList<>(members));
        }
        return copy;
    }

    /** 打开组队页面（命令入口）。游戏进行中不可用。 */
    public static void open(ServerPlayer player) {
        if (SREGameWorldComponent.KEY.get(player.serverLevel()).isRunning()) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.team_lobby_in_game"), false);
            return;
        }
        prune(player.server);
        ServerPlayNetworking.send(player, snapshotFor(player, true));
    }

    /** 客户端组队操作入口（C2S 包处理）。 */
    public static void handleAction(ServerPlayer player, int action, int partyId) {
        if (SREGameWorldComponent.KEY.get(player.serverLevel()).isRunning()) {
            return;
        }
        prune(player.server);
        UUID uuid = player.getUUID();
        switch (action) {
            case ACTION_CREATE -> {
                // 已在某队伍时不允许再创建（否则可无限刷小队）——需先离开
                if (partyOf(uuid) >= 0) {
                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.sixty_seconds.team_already_in"), false);
                    break;
                }
                List<UUID> party = new ArrayList<>();
                party.add(uuid);
                PARTIES.put(nextPartyId++, party);
            }
            case ACTION_JOIN -> {
                List<UUID> party = PARTIES.get(partyId);
                if (party == null || party.contains(uuid)) {
                    break;
                }
                if (party.size() >= SixtySecondsTeamAllocator.TEAM_SIZE) {
                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.sixty_seconds.team_party_full"), false);
                    break;
                }
                removeMember(uuid);
                party.add(uuid);
            }
            case ACTION_LEAVE -> removeMember(uuid);
            case ACTION_DISBAND -> {
                // 客户端只对管理员显示按钮，这里再做一次服务端权限校验（防伪造包）
                if (!player.hasPermissions(2)) {
                    break;
                }
                List<UUID> party = PARTIES.remove(partyId);
                if (party == null) {
                    break;
                }
                for (UUID member : party) {
                    ServerPlayer online = player.server.getPlayerList().getPlayer(member);
                    if (online != null) {
                        online.displayClientMessage(Component.translatable(
                                "message.noellesroles.sixty_seconds.team_disbanded_notice"), false);
                    }
                }
            }
            default -> {
            }
        }
        broadcastRefresh(player.server);
    }

    /** 把最新快照推给所有在线玩家；客户端仅在组队页面已打开时刷新（forceOpen=false）。 */
    private static void broadcastRefresh(MinecraftServer server) {
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(online, snapshotFor(online, false));
        }
    }

    private static OpenTeamLobbyS2CPacket snapshotFor(ServerPlayer viewer, boolean forceOpen) {
        int myParty = -1;
        List<Integer> ids = new ArrayList<>();
        List<String> memberLists = new ArrayList<>();
        for (Map.Entry<Integer, List<UUID>> entry : PARTIES.entrySet()) {
            ids.add(entry.getKey());
            List<String> names = new ArrayList<>();
            for (UUID member : entry.getValue()) {
                ServerPlayer online = viewer.server.getPlayerList().getPlayer(member);
                names.add(online != null ? online.getGameProfile().getName() : "?");
                if (member.equals(viewer.getUUID())) {
                    myParty = entry.getKey();
                }
            }
            memberLists.add(String.join(", ", names));
        }
        int[] idArray = new int[ids.size()];
        int[] sizeArray = new int[ids.size()];
        int i = 0;
        for (Map.Entry<Integer, List<UUID>> entry : PARTIES.entrySet()) {
            idArray[i] = entry.getKey();
            sizeArray[i] = entry.getValue().size();
            i++;
        }
        return new OpenTeamLobbyS2CPacket(forceOpen, myParty, idArray, sizeArray,
                memberLists.toArray(new String[0]));
    }

    /** 返回该玩家当前所在队伍 id，未在任何队伍返回 -1。 */
    private static int partyOf(UUID uuid) {
        for (Map.Entry<Integer, List<UUID>> entry : PARTIES.entrySet()) {
            if (entry.getValue().contains(uuid)) {
                return entry.getKey();
            }
        }
        return -1;
    }

    private static void removeMember(UUID uuid) {
        for (Iterator<Map.Entry<Integer, List<UUID>>> it = PARTIES.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Integer, List<UUID>> entry = it.next();
            entry.getValue().remove(uuid);
            if (entry.getValue().isEmpty()) {
                it.remove();
            }
        }
    }

    /** 懒清理：掉线成员移出预组队伍，空队伍删除。 */
    private static void prune(MinecraftServer server) {
        for (Iterator<Map.Entry<Integer, List<UUID>>> it = PARTIES.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Integer, List<UUID>> entry = it.next();
            entry.getValue().removeIf(member -> server.getPlayerList().getPlayer(member) == null);
            if (entry.getValue().isEmpty()) {
                it.remove();
            }
        }
    }
}
