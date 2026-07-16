package io.wifi.starrailexpress.game.modes.funny.volunteer;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.utils.RoleInstance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.RoleCountManager;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentPool;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import net.exmo.sre.repair.role.RepairRole;

import java.util.*;
import java.util.stream.Collectors;

public class VolunteerDraftState {

    private final List<ServerPlayer> players;
    private final int volunteerCount;
    private final Map<UUID, List<SRERole>> candidatePool = new HashMap<>();
    private final Map<UUID, List<Integer>> submittedPreferences = new HashMap<>();
    private final Map<UUID, SRERole> finalAssignment = new LinkedHashMap<>();
    public final Set<UUID> submittedPlayers = new HashSet<>();
    private final List<SRERole> globalRolePool = new ArrayList<>();

    public Phase phase = Phase.WAITING;
    public long phaseStartTime;
    private int commitTimeLimit = 60 * 20;
    // 新增字段
    private int resultTimeLimit = 5 * 20; // 结果展示 10 秒
    private int adjustTimeLimit = 1 * 20; // 5秒调整期
    private final Random random;
    private final ServerLevel world;

    public void startCommitPhase(long gameTime) {
        this.commitTimeLimit = SREConfig.instance().volunteerModeSelectionTime * 20;
        this.phase = Phase.COMMIT;
        this.phaseStartTime = gameTime;
    }

    public VolunteerDraftState(List<ServerPlayer> players, ServerLevel world) {
        this.players = new ArrayList<>(players);
        this.world = world;
        this.random = new Random(world.getGameTime());
        this.volunteerCount = SREConfig.instance().volunteerModeVolunteerCount;
        if (volunteerCount < 2)
            throw new IllegalStateException("volunteerModeVolunteerCount must be at least 2");
        initializePools(world);
        generateCandidates();
        phaseStartTime = world.getGameTime();
    }

    private void initializePools(ServerLevel world) {
        int total = players.size();
        int killerCount = Math.max(1, RoleCountManager.getKillerCount(total));
        int vigilanteCount = Math.max(0, RoleCountManager.getVigilanteCount(total));
        int neutralsCount = Math.max(0, RoleCountManager.getNeutralCount(total));

        HarpyModLoaderConfig config = HarpyModLoaderConfig.HANDLER.instance();
        boolean enableCivilianInPool = config.enableCivilianInPool;

        RoleAssignmentPool killerPool = RoleAssignmentPool.create("Killer",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        !role.isOtherModeRole() &&
                        !(role instanceof RepairRole) &&
                        role.canUseKiller() &&
                        !role.isInnocent() &&
                        !RoleUtils.compareRole(role, ModRoles.PUPPETEER) &&
                        role != TMMRoles.CIVILIAN);
        RoleAssignmentPool vigilantePool = RoleAssignmentPool.create("Vigilante",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        role.isVigilanteTeam() &&
                        !role.isOtherModeRole() &&
                        !(role instanceof RepairRole));
        RoleAssignmentPool neutralsPool = RoleAssignmentPool.create("Neutrals",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        !role.isOtherModeRole() &&
                        !(role instanceof RepairRole) &&
                        ((!role.canUseKiller() && !role.isInnocent()) || role.isNeutrals()) &&
                        role != TMMRoles.CIVILIAN);
        RoleAssignmentPool civilianPool = RoleAssignmentPool.create("Civilian",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        !role.isOtherModeRole() &&
                        !(role instanceof RepairRole) &&
                        !role.isVigilanteTeam() &&
                        !role.canUseKiller() &&
                        !role.isNeutrals() &&
                        role.isInnocent() &&
                        (enableCivilianInPool || role != TMMRoles.CIVILIAN));

        if (enableCivilianInPool) {
            Harpymodloader.setRoleMaximum(TMMRoles.CIVILIAN.getIdentifier(), 1);
        }

        List<RoleInstance> baseRoles = SREMurderGameMode.getAllRoles(
                killerCount, vigilanteCount, neutralsCount,
                total, 0,
                killerPool, neutralsPool, vigilantePool, civilianPool, true);
        globalRolePool.clear();
        for (RoleInstance inst : baseRoles) {
            if (inst.role() != null)
                globalRolePool.add(inst.role());
        }
        while (globalRolePool.size() < total) {
            globalRolePool.add(TMMRoles.CIVILIAN);
        }
    }

    private void generateCandidates() {
        for (ServerPlayer player : players) {
            List<SRERole> factionPool = getFactionPool(player);
            if (factionPool.size() < volunteerCount) {
                factionPool = new ArrayList<>(globalRolePool);
            }
            Collections.shuffle(factionPool, random);
            candidatePool.put(player.getUUID(), new ArrayList<>(factionPool.subList(0, volunteerCount)));
        }
    }

    private List<SRERole> getFactionPool(ServerPlayer player) {
        Integer forced = PlayerRoleWeightManager.ForcePlayerTeam.get(player.getUUID());
        if (forced != null) {
            int type = normalizeCardType(forced);
            return globalRolePool.stream()
                    .filter(r -> PlayerRoleWeightManager.getRoleType(r) == type)
                    .collect(Collectors.toList());
        }
        return globalRolePool.stream()
                .filter(r -> r.isInnocent() && !r.canUseKiller())
                .collect(Collectors.toList());
    }

    private int normalizeCardType(int raw) {
        return switch (raw) {
            case 5 -> 1;
            case 3 -> 2;
            default -> raw;
        };
    }

    public boolean submitPreference(UUID playerId, List<Integer> orderedPreferences) {
        if (submittedPlayers.contains(playerId))
            return false;
        List<SRERole> candidates = candidatePool.get(playerId);
        if (candidates == null)
            return false;
        for (int pref : orderedPreferences) {
            if (pref != -1 && (pref < 0 || pref >= candidates.size()))
                return false;
        }
        submittedPreferences.put(playerId, new ArrayList<>(orderedPreferences));
        submittedPlayers.add(playerId);

        if (submittedPlayers.size() >= players.size()) {
            phase = Phase.ADJUST;
            phaseStartTime = world.getGameTime();
            return true;
        }
        return false;
    }

    public void runAssignment() {
        List<UUID> remaining = new ArrayList<>(submittedPlayers);
        Map<String, Integer> capacity = new HashMap<>(); // 改用 String ID
        for (SRERole role : globalRolePool) {
            capacity.merge(role.identifier().toString(), 1, Integer::sum);
        }

        int maxRounds = submittedPreferences.values().stream()
                .mapToInt(List::size).max().orElse(0);

        for (int round = 0; round < maxRounds; round++) {
            if (remaining.isEmpty())
                break;

            // 本轮玩家及其志愿
            Map<UUID, Integer> roundChoices = new HashMap<>();
            for (UUID pid : new ArrayList<>(remaining)) {
                List<Integer> prefs = submittedPreferences.get(pid);
                if (prefs != null && round < prefs.size()) {
                    roundChoices.put(pid, prefs.get(round));
                }
            }

            // 分离具体志愿与随机志愿
            Map<String, List<UUID>> byRole = new LinkedHashMap<>(); // key: role ID
            List<UUID> randomPickPlayers = new ArrayList<>();
            for (UUID pid : roundChoices.keySet()) {
                int choice = roundChoices.get(pid);
                if (choice == -1) {
                    randomPickPlayers.add(pid);
                } else {
                    SRERole target = candidatePool.get(pid).get(choice);
                    String rid = target.identifier().toString();
                    byRole.computeIfAbsent(rid, k -> new ArrayList<>()).add(pid);
                }
            }

            // 先分配具体志愿
            for (Map.Entry<String, List<UUID>> entry : byRole.entrySet()) {
                String rid = entry.getKey();
                List<UUID> applicants = entry.getValue();
                // 按权重排序
                applicants.sort((a, b) -> {
                    SRERole roleA = candidatePool.get(a).stream()
                            .filter(r -> r.identifier().toString().equals(rid)).findFirst().orElse(null);
                    SRERole roleB = candidatePool.get(b).stream()
                            .filter(r -> r.identifier().toString().equals(rid)).findFirst().orElse(null);
                    int w1 = roleA != null ? getWeight(a, roleA) : 0;
                    int w2 = roleB != null ? getWeight(b, roleB) : 0;
                    if (w1 != w2)
                        return Integer.compare(w2, w1);
                    return Integer.compare(Objects.hash(a, random.nextInt()), Objects.hash(b, random.nextInt()));
                });

                int slots = capacity.getOrDefault(rid, 0);
                Iterator<UUID> iter = applicants.iterator();
                while (slots > 0 && iter.hasNext()) {
                    UUID winner = iter.next();
                    SRERole winnerRole = candidatePool.get(winner).stream()
                            .filter(r -> r.identifier().toString().equals(rid)).findFirst().orElse(null);
                    if (winnerRole != null) {
                        finalAssignment.put(winner, winnerRole);
                        capacity.merge(rid, -1, Integer::sum);
                        remaining.remove(winner);
                        slots--;
                    }
                }
            }

            // 再处理随机志愿
            for (UUID pid : randomPickPlayers) {
                if (finalAssignment.containsKey(pid))
                    continue;
                List<SRERole> available = getAvailableRolesForPlayer(pid, capacity);
                if (!available.isEmpty()) {
                    SRERole picked = available.get(random.nextInt(available.size()));
                    finalAssignment.put(pid, picked);
                    capacity.merge(picked.identifier().toString(), -1, Integer::sum);
                    remaining.remove(pid);
                }
            }
        }

        // 最终兜底
        for (UUID pid : new ArrayList<>(remaining)) {
            List<SRERole> available = getAvailableRolesForPlayer(pid, capacity);
            if (!available.isEmpty()) {
                available.sort(Comparator.comparingInt(r -> -getWeight(pid, r)));
                SRERole chosen = available.get(0);
                finalAssignment.put(pid, chosen);
                capacity.merge(chosen.identifier().toString(), -1, Integer::sum);
            } else {
                finalAssignment.put(pid, TMMRoles.CIVILIAN);
            }
        }

        phase = Phase.RESULT;
        phaseStartTime = world.getGameTime();
    }

    public int getResultTimeLimit() {
        return resultTimeLimit;
    }

    private List<SRERole> getAvailableRolesForPlayer(UUID playerId, Map<String, Integer> capacity) {
        ServerPlayer player = players.stream().filter(p -> p.getUUID().equals(playerId)).findFirst().orElse(null);
        if (player == null)
            return List.of();
        List<SRERole> factionPool = getFactionPool(player);
        List<SRERole> available = new ArrayList<>();
        for (SRERole role : factionPool) {
            if (capacity.getOrDefault(role.identifier().toString(), 0) > 0) {
                available.add(role);
            }
        }
        if (available.isEmpty()) {
            // 回退到全池剩余
            for (SRERole role : globalRolePool) {
                if (capacity.getOrDefault(role.identifier().toString(), 0) > 0) {
                    available.add(role);
                }
            }
        }
        return available;
    }

    private int getWeight(UUID playerId, SRERole role) {
        int type = PlayerRoleWeightManager.getRoleType(role);
        return (int) (PlayerRoleWeightManager.getRoleWeightPercent(playerId, type) * 100);
    }

    // Getters
    public Phase getPhase() {
        return phase;
    }

    public long getPhaseStartTime() {
        return phaseStartTime;
    }

    public int getCommitTimeLimit() {
        return commitTimeLimit;
    }

    public int getVolunteerCount() {
        return volunteerCount;
    }

    public List<String> getMyCandidateIds(UUID uuid) {
        return candidatePool.getOrDefault(uuid, List.of()).stream()
                .map(r -> r.identifier().toString()).toList();
    }

    public Map<UUID, String> getFinalRolesAsStrings() {
        return finalAssignment.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().identifier().toString()));
    }

    public String getMyFinalRoleId(UUID uuid) {
        SRERole role = finalAssignment.get(uuid);
        return role != null ? role.identifier().toString() : "";
    }

    public Map<UUID, SRERole> getFinalAssignment() {
        return finalAssignment;
    }

    public enum Phase {
        WAITING, COMMIT, ADJUST, RESULT
    }

    // 提供 getter
    public int getAdjustTimeLimit() {
        return adjustTimeLimit;
    }

    // 玩家退出处理：移除该玩家所有数据，若剩余玩家均已提交则直接进入 ADJUST
    public boolean removePlayer(UUID playerId) {
        candidatePool.remove(playerId);
        submittedPreferences.remove(playerId);
        submittedPlayers.remove(playerId);
        players.removeIf(p -> p.getUUID().equals(playerId));
        if (phase == Phase.COMMIT && !players.isEmpty() && submittedPlayers.size() == players.size()) {
            phase = Phase.ADJUST;
            phaseStartTime = world.getGameTime();
            return true;
        }
        return false;
    }

    public void setPhase(Phase adjust) {
        this.phase = adjust;
    }

    public void setPhaseStartTime(long gameTime) {
        this.phaseStartTime = gameTime;
    }
}