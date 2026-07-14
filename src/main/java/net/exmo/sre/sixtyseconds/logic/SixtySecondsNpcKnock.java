package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家 NPC 敲门喊话：<b>创造模式的非参与玩家</b>（扮演 NPC 的演员）右键任意避难所门 =
 * 敲门——门内家庭听到敲门声与提示；随后 NPC 用 {@code /sre:60s ask <文字>} 向门内喊话，
 * 门内成员在聊天栏收到「门外的声音」，并经 {@link SRENetworkMessageUtils#sendBroadcast} 弹广播。
 * 敲门记录（NPC → 目标队）保留到下次敲门/游戏重置，期间可连续喊话。
 */
public final class SixtySecondsNpcKnock {
    private static final String LANG = "message.noellesroles.sixty_seconds.knock.";

    /** NPC uuid → 最近敲的队 id（跨维度极少见，60s 单世界运行，直接记 teamId）。 */
    private static final Map<UUID, Integer> LAST_KNOCKED = new ConcurrentHashMap<>();

    private SixtySecondsNpcKnock() {
    }

    public static void reset() {
        LAST_KNOCKED.clear();
    }

    /**
     * 门交互拦截：创造 + 未编队（NPC 演员）时接管为敲门，返回 true（不再开门菜单）。
     * 参与中的玩家（含创造管理员）不受影响，仍走正常门菜单。
     */
    public static boolean tryKnock(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!player.isCreative() || SixtySecondsStatsComponent.KEY.get(player).teamId != -1) {
            return false;
        }
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        if (data.teams.isEmpty()) {
            return false; // 模式没在跑（无队伍）：不接管
        }
        SixtySecondsState.TeamData team = findOwnerTeam(data, pos);
        if (team == null) {
            player.displayClientMessage(Component.translatable(LANG + "no_owner")
                    .withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        LAST_KNOCKED.put(player.getUUID(), team.teamId);
        // 敲门声：门口就地播放（门内外都听得到）
        level.playSound(null, pos, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.BLOCKS, 0.6F, 1.3F);
        Component heard = Component.translatable(LANG + "heard").withStyle(ChatFormatting.GOLD);
        for (ServerPlayer member : members(level, team)) {
            member.displayClientMessage(heard, false);
        }
        // 提示 NPC：点击补全 /sre:60s ask 输入喊话内容
        player.displayClientMessage(Component.translatable(LANG + "knocked", team.teamId + 1)
                .withStyle(ChatFormatting.GRAY), false);
        player.displayClientMessage(Component.literal("【")
                .append(Component.translatable(LANG + "prompt"))
                .append(Component.literal("】"))
                .withStyle(style -> style.withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/sre:60s ask "))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable(LANG + "prompt_hint")))), false);
        return true;
    }

    /** {@code /sre:60s ask <text>}：向最近敲过的队喊话；未敲过门或队伍已不存在时提示。 */
    public static void ask(ServerPlayer npc, String text) {
        if (!npc.isCreative()) {
            npc.displayClientMessage(Component.translatable(LANG + "creative_only")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        ServerLevel level = npc.serverLevel();
        Integer teamId = LAST_KNOCKED.get(npc.getUUID());
        SixtySecondsState.TeamData team = teamId == null ? null
                : SixtySecondsState.get(level).teams.get(teamId);
        if (team == null) {
            npc.displayClientMessage(Component.translatable(LANG + "no_knock")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        Component line = Component.translatable(LANG + "voice", text)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC);
        List<ServerPlayer> members = members(level, team);
        for (ServerPlayer member : members) {
            member.displayClientMessage(line, false);
            SRENetworkMessageUtils.sendBroadcast(member, Component.translatable(LANG + "voice", text));
            member.playNotifySound(SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.AMBIENT, 0.4F, 1.5F);
        }
        npc.displayClientMessage(Component.translatable(LANG + "delivered", members.size())
                .withStyle(ChatFormatting.GREEN), false);
    }

    /** 找门归属的家庭：doorPos 一致，或落在住宅/避难所范围盒内。 */
    private static SixtySecondsState.TeamData findOwnerTeam(SixtySecondsState.Data data, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            if (pos.equals(team.doorPos)
                    || (team.shelterBox != null && team.shelterBox.contains(x, y, z))
                    || (team.residentialBox != null && team.residentialBox.contains(x, y, z))) {
                return team;
            }
        }
        return null;
    }

    private static List<ServerPlayer> members(ServerLevel level, SixtySecondsState.TeamData team) {
        List<ServerPlayer> out = new java.util.ArrayList<>();
        for (UUID uuid : team.members) {
            if (level.getPlayerByUUID(uuid) instanceof ServerPlayer member
                    && !io.wifi.starrailexpress.game.GameUtils.isPlayerEliminated(member)) {
                out.add(member);
            }
        }
        return out;
    }
}
