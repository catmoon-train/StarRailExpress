package io.wifi.starrailexpress.content.vote.client;

import io.wifi.starrailexpress.content.vote.VoteOption;
import io.wifi.starrailexpress.content.vote.network.VoteSyncS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.*;

public class ClientVoteCache {
    private static boolean active;
    private static Component title = Component.empty();
    private static List<VoteOption> options = new ArrayList<>();
    private static long endTick;
    private static boolean showResults;
    private static Map<Integer, Integer> results = Map.of();
    private static int totalVotes;
    private static boolean allowReVote;
    private static int maxSelectCount = 1;

    public static void updateFromPacket(VoteSyncS2CPacket packet) {
        active = packet.active();
        if (packet.active()) {
            title = packet.title();
            if (packet.hasOptions()) {
                options = new ArrayList<>(packet.options());
            }
            endTick = packet.endTick();
            showResults = packet.showResults();
            results = packet.results();
            totalVotes = packet.totalVotes();
            allowReVote = packet.allowReVote();
            maxSelectCount = packet.maxSelectCount();
        } else {
            active = false;
        }

        if (!packet.active()) {
            // 投票会话结束，重置本地状态
            hasVoted = false;
            selectedIndices.clear();
        }
    }

    public static void clientTick() {
        if (active) {
            if (Minecraft.getInstance().level == null) {
                active = false;
                return;
            }
            if (endTick == -1) {
                active = false;
                return;
            }
            long currentTick = Minecraft.getInstance().level.getGameTime();
            if (currentTick > endTick + 30) { // 延迟1.5s
                clear();
            }
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean canReOpen() {
        return active && allowReVote;
    }

    public static Component getTitle() {
        return title;
    }

    public static List<VoteOption> getOptions() {
        return options;
    }

    public static boolean isShowResults() {
        return showResults;
    }

    public static Map<Integer, Integer> getResults() {
        return results;
    }

    public static int getTotalVotes() {
        return totalVotes;
    }

    public static boolean isAllowReVote() {
        return allowReVote;
    }

    public static int getMaxSelectCount() {
        return maxSelectCount;
    }

    public static int getRemainingSeconds() {
        if (endTick == -1)
            return -1;
        if (Minecraft.getInstance().level == null)
            return 0;
        long currentTick = Minecraft.getInstance().level.getGameTime();
        long remainingTicks = endTick - (int) currentTick;
        long seconds = remainingTicks / 20;
        return (int) Math.max(0, seconds);
    }

    // 新增：客户端本地投票状态
    private static boolean hasVoted = false;
    private static final Set<Integer> selectedIndices = new LinkedHashSet<>();

    // 新增 getter
    public static boolean hasVoted() {
        return hasVoted;
    }

    public static Set<Integer> getSelectedIndices() {
        return Collections.unmodifiableSet(selectedIndices);
    }

    // 新增 setter（仅供 VoteScreen 调用）
    public static void onVoteSubmitted(Collection<Integer> indices) {
        hasVoted = true;
        selectedIndices.clear();
        selectedIndices.addAll(indices);
    }

    public static void clear() {
        active = false;
        selectedIndices.clear();
    }
}