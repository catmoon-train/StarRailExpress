package net.exmo.sre.sixtyseconds.logic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * 末日60秒开局分队算法（纯逻辑，不依赖 MC 运行态，便于独立验证）。
 *
 * <p>规则（见 docs/末日60秒生存模式.md §1 编队 + 组队系统需求）：
 * <ul>
 *   <li>队伍数量无上限（按 人数/4 向上取整），每队最多 {@link #TEAM_SIZE} 人。</li>
 *   <li>只有「参与本局」的玩家进入分配池——调用方传入的 participants 即参与者名单，
 *       预组队伍中的不参与成员会被剔除。</li>
 *   <li>预组的整队尽量不拆：优先把整队放进同一支队伍；放不下（队伍数超限等）才拆散，
 *       其中 3 人队最容易被拆（4 人满队总能独占一队，除非队伍数不够）。</li>
 *   <li>未满的队伍用散人自动补足到 4 人。</li>
 *   <li>收尾再平衡：避免出现 1 人孤队（从最多人的队伍挪一个「可挪」成员过去；
 *       整队成员不可挪，散人/已拆散成员可挪）。</li>
 * </ul>
 */
public final class SixtySecondsTeamAllocator {
    /** @deprecated 队伍数已无上限，仅作旧引用兼容保留。 */
    @Deprecated
    public static final int MAX_TEAMS = Integer.MAX_VALUE;
    public static final int TEAM_SIZE = 4;

    private SixtySecondsTeamAllocator() {
    }

    /** 分配结果：teams 按 index 即 teamId；splitPlayers 为「预组队伍被拆散」的成员。 */
    public record Result(List<List<UUID>> teams, Set<UUID> splitPlayers) {
    }

    /**
     * @param participants 参与本局的玩家（顺序无关，内部会打乱散人保证公平）
     * @param parties      预组队伍（成员可能包含不参与/重复玩家，内部会清洗）
     * @param random       随机源（散人洗牌用）
     */
    public static Result allocate(List<UUID> participants, Collection<List<UUID>> parties, Random random) {
        Set<UUID> pool = new LinkedHashSet<>(participants);
        int n = pool.size();
        if (n == 0) {
            return new Result(List.of(), Set.of());
        }
        // 队伍数量不设上限：网格克隆随队数沿 X 延伸，克隆前有净空工作项挖开自然地形（见 SixtySecondsArena）。
        int teamCount = Math.max(1, (n + TEAM_SIZE - 1) / TEAM_SIZE);
        int cap = Math.max(TEAM_SIZE, (n + teamCount - 1) / teamCount);

        // 清洗预组队伍：剔除不参与者与重复出现的玩家；只剩 1 人的队按散人处理。
        List<List<UUID>> cleanParties = new ArrayList<>();
        Set<UUID> inParty = new HashSet<>();
        for (List<UUID> party : parties) {
            List<UUID> clean = new ArrayList<>();
            for (UUID member : party) {
                if (pool.contains(member) && inParty.add(member)) {
                    clean.add(member);
                }
            }
            if (clean.size() >= 2) {
                cleanParties.add(clean);
            } else {
                inParty.removeAll(clean);
            }
        }
        // 大队优先落位，减少后续拆散。
        cleanParties.sort((a, b) -> Integer.compare(b.size(), a.size()));

        List<List<UUID>> teams = new ArrayList<>(teamCount);
        for (int i = 0; i < teamCount; i++) {
            teams.add(new ArrayList<>());
        }
        Set<UUID> splitPlayers = new HashSet<>();
        Set<UUID> movable = new HashSet<>();

        for (List<UUID> party : cleanParties) {
            int target = -1;
            // 整队落位：选能完整容纳的最空队伍（并列取序号小的），让整队各占一队。
            for (int i = 0; i < teamCount; i++) {
                if (teams.get(i).size() + party.size() <= cap
                        && (target < 0 || teams.get(i).size() < teams.get(target).size())) {
                    target = i;
                }
            }
            if (target >= 0) {
                teams.get(target).addAll(party);
            } else {
                // 放不下整队（典型：3 人队太多超出队伍数）→ 拆散，逐人塞进最空的队伍。
                for (UUID member : party) {
                    emptiestWithRoom(teams, cap).add(member);
                }
                splitPlayers.addAll(party);
                movable.addAll(party);
            }
        }

        // 散人洗牌后补队：优先补「人最多但未满」的队伍，把家庭凑满 4 人。
        List<UUID> solos = new ArrayList<>();
        for (UUID player : pool) {
            if (!inParty.contains(player)) {
                solos.add(player);
            }
        }
        java.util.Collections.shuffle(solos, random);
        for (UUID solo : solos) {
            // 先把队伍补满到 4 人（人多但未满的优先，凑整家庭）。
            List<UUID> target = null;
            for (List<UUID> team : teams) {
                if (team.size() < TEAM_SIZE && (target == null || team.size() > target.size())) {
                    target = team;
                }
            }
            if (target == null) {
                // 全员满 4（人数 >60 的溢出）：塞进人数最少且未到 cap 的队伍，保持均衡。
                for (List<UUID> team : teams) {
                    if (team.size() < cap && (target == null || team.size() < target.size())) {
                        target = team;
                    }
                }
            }
            if (target == null) {
                target = emptiestWithRoom(teams, Integer.MAX_VALUE);
            }
            target.add(solo);
            movable.add(solo);
        }

        // 再平衡：消除 1 人孤队——从人数最多且含「可挪」成员的队伍挪一人过来。
        boolean moved = true;
        while (moved) {
            moved = false;
            List<UUID> lonely = null;
            for (List<UUID> team : teams) {
                if (team.size() == 1) {
                    lonely = team;
                    break;
                }
            }
            if (lonely == null) {
                break;
            }
            List<UUID> donor = null;
            UUID candidate = null;
            for (List<UUID> team : teams) {
                if (team == lonely || team.size() < 3 || (donor != null && team.size() <= donor.size())) {
                    continue;
                }
                for (UUID member : team) {
                    if (movable.contains(member)) {
                        donor = team;
                        candidate = member;
                        break;
                    }
                }
            }
            if (donor != null) {
                donor.remove(candidate);
                lonely.add(candidate);
                moved = true;
            }
        }

        teams.removeIf(List::isEmpty);
        return new Result(teams, splitPlayers);
    }

    private static List<UUID> emptiestWithRoom(List<List<UUID>> teams, int cap) {
        List<UUID> best = null;
        for (List<UUID> team : teams) {
            if (team.size() < cap && (best == null || team.size() < best.size())) {
                best = team;
            }
        }
        return best != null ? best : teams.get(0);
    }
}
