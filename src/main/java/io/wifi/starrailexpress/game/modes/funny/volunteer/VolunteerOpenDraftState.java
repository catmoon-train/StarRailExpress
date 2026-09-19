/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.game.modes.funny.volunteer;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.game.modes.funny.rotation.LightningDraftState;
import io.wifi.starrailexpress.game.utils.RoleInstance;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import org.agmas.harpymodloader.Harpymodloader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.agmas.harpymodloader.modded_murder.ForceTeamInfo;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modded_murder.ForceTeamInfo.ForceTeamType;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.*;

/**
 * 志愿海选模式的服务端状态机。
 *
 * <p>
 * 与 {@link LightningDraftState}（职业轮选）的区别：玩家先填一个志愿职业（只用于提示），
 * 随后按序号分成至多五组，依次在「海选池」里挑职业——每组开始时随机揭晓与本组人数相同
 * 数量的职业，玩家也可以直接挑一个未揭晓的职业（只对自己揭晓）。
 */
public class VolunteerOpenDraftState {

    public enum Phase {
        /** 一阶段：选取志愿职业。 */
        VOLUNTEER,
        /** 二阶段：海选（分组轮流选）。 */
        OPEN,
        /** 确认阶段。 */
        CONFIRM,
        /** 已结束。 */
        END
    }

    /** 一阶段时长：8 秒。 */
    public static final int VOLUNTEER_TIME_LIMIT = 8 * 20;
    /** 海选池最多五行。 */
    public static final int MAX_GROUPS = 5;
    /** 卡牌（杀手卡 / 中立卡 / 平民卡）额外揭示的职业数量上限。 */
    public static final int CARD_EXTRA_REVEAL = 2;
    /** 每个玩家的候选职业数（与轮选模式一致，用于计算每轮时限）。 */
    private static final int PLAYER_SELECT_COUNT = 3;

    private final Random random = new Random();

    // ===== 基础 =====
    public final int totalPlayers;
    public final List<UUID> playerOrder = new ArrayList<>();
    /** 海选池（固定顺序，选中不会改变下标）。 */
    public final List<RoleInstance> pool = new ArrayList<>();
    /** 下标 -> 选中它的玩家（null 表示未选）。 */
    public final UUID[] pickedBy;
    /** 已向所有人揭晓的下标。 */
    public final Set<Integer> globallyRevealed = new LinkedHashSet<>();
    /** 玩家 -> 选中的池下标（-1 表示没有可用职业，兜底平民）。 */
    public final Map<UUID, Integer> picks = new LinkedHashMap<>();
    /** 左侧玩家列表需要显示为「随机」的玩家。 */
    public final Set<UUID> randomChoosers = new HashSet<>();
    /** 玩家 -> 仅对该玩家揭晓的池下标（来自阵营卡）。 */
    public final Map<UUID, Set<Integer>> cardReveals = new HashMap<>();
    /** 玩家 -> 被强制指定的职业在池中的下标（forcerole）。 */
    public final Map<UUID, Integer> forcedRoleReveals = new LinkedHashMap<>();
    /** 玩家 -> 志愿职业 id。 */
    public final Map<UUID, String> volunteerRoleIds = new LinkedHashMap<>();
    /** 玩家 -> 志愿职业在池中的下标（-1 表示场上没有该职业）。 */
    public final Map<UUID, Integer> volunteerPoolIndex = new LinkedHashMap<>();

    // ===== 分组 =====
    public final List<List<UUID>> groups = new ArrayList<>();
    public int groupIndex = -1;

    // ===== 阶段 =====
    public Phase phase = Phase.VOLUNTEER;
    public long phaseStartTime;
    public int phaseTimeLimit = VOLUNTEER_TIME_LIMIT;
    /**
     * 一阶段是否还在等客户端把界面打开（开局发车黑幕 / 动画播完之后才会真正开始计时）。
     * 参考职业轮选：不能让玩家在看动画的时候白白消耗选职业的时间。
     */
    public boolean waitingForClients = true;
    /** 开始等待客户端就绪的世界时间。 */
    public long holdStartTime;
    /** 已上报「界面已打开」的玩家。 */
    public final Set<UUID> uiReadyPlayers = new HashSet<>();
    /**
     * 等客户端的兜底上限（tick）：超时后即使有人没上报也照常开始计时。
     *
     * <p>
     * 要留够地图开场运镜（开场动画）的时长——客户端在运镜播完之前不会把界面顶出来。
     */
    public static final int CLIENT_READY_TIMEOUT = 20 * 20;

    // ===== 确认 =====
    public boolean confirmRequired = false;
    public int confirmCountdown = -1;
    public final Set<UUID> confirmedPlayers = new HashSet<>();

    // ===== 海选池矩形 =====
    public int poolRows = 1;
    public int poolCols = 1;

    public VolunteerOpenDraftState(List<ServerPlayer> players, ServerLevel world) {
        this.totalPlayers = players.size();

        // 复用轮选模式的池子初始化与序号分配（含卡牌 / forcerole 对序号的影响），
        // 只是不额外往池子里添加平民职业。
        LightningDraftState helper = new LightningDraftState(new ArrayList<>(players));
        helper.initializeRolePool(world, false);
        helper.assignRotationOrder();

        this.playerOrder.addAll(helper.playerOrder);
        this.pool.addAll(helper.rolePool);
        // 打乱海选池的排布：getAllRoles 是按「杀手 → 警长 → 中立 → 平民」的顺序生成的，
        // 直接铺到格子上会把阵营信息泄露出去（越靠前越是杀手）。打乱之后任意位置都可能是任意阵营。
        Collections.shuffle(this.pool, random);

        this.pickedBy = new UUID[this.pool.size()];
        computePoolLayout(this.pool.size());
        buildGroups();

        this.phase = Phase.VOLUNTEER;
        this.phaseStartTime = world.getGameTime();
        this.phaseTimeLimit = VOLUNTEER_TIME_LIMIT;
        this.holdStartTime = world.getGameTime();
        this.waitingForClients = true;

        // forcerole：被强制指定职业的玩家，在自己的海选池里直接能看到那个职业（加粗橙色）
        for (UUID id : playerOrder) {
            SRERole forced = Harpymodloader.FORCED_MODDED_ROLE.get(id);
            if (forced == null) {
                continue;
            }
            int index = indexOfRole(forced);
            if (index >= 0) {
                forcedRoleReveals.put(id, index);
            }
        }
    }

    // ==================== 一阶段计时：等客户端就绪 ====================

    /** 客户端上报「选职业界面已打开」。 */
    public void markUiReady(UUID playerUuid) {
        if (playerUuid != null) {
            uiReadyPlayers.add(playerUuid);
        }
    }

    /** 所有在线玩家是否都把界面打开了（离线玩家不阻塞）。 */
    public boolean allUiReady(ServerLevel world) {
        for (UUID id : playerOrder) {
            ServerPlayer p = world.getServer().getPlayerList().getPlayer(id);
            if (p == null || p.isRemoved()) {
                continue;
            }
            if (!uiReadyPlayers.contains(id)) {
                return false;
            }
        }
        return true;
    }

    /** 开始（或重新开始）当前阶段的计时。 */
    public void startPhaseTiming(long now) {
        waitingForClients = false;
        phaseStartTime = now;
    }

    /**
     * 当前阶段剩余时间（tick）。
     *
     * @return -1 表示仍在等客户端就绪、尚未开始计时
     */
    public int remainingTicks(long now) {
        if (phase == Phase.CONFIRM) {
            return Math.max(0, confirmCountdown);
        }
        if (waitingForClients) {
            return -1;
        }
        return (int) Math.max(0, phaseTimeLimit - (now - phaseStartTime));
    }

    // ==================== 初始化辅助 ====================

    /** 把池子排成一个「行数 ≤ 行数上限且行数 ≤ 列数」的矩形。 */
    private void computePoolLayout(int size) {
        if (size <= 0) {
            poolRows = 1;
            poolCols = 1;
            return;
        }
        int bestRows = 1;
        int bestCols = size;
        for (int r = 1; r <= Math.min(MAX_GROUPS, size); r++) {
            int c = (int) Math.ceil(size / (double) r);
            if (r > c) {
                continue;
            }
            if (bestRows == 1 || (c - r) < (bestCols - bestRows)) {
                bestRows = r;
                bestCols = c;
            }
        }
        poolRows = bestRows;
        poolCols = bestCols;
    }

    /** 按序号平均分至多五组（人数不足时组数相应减少），多出来的人优先放在前面的组。 */
    private void buildGroups() {
        int n = playerOrder.size();
        if (n <= 0) {
            return;
        }
        int groupCount = Math.min(MAX_GROUPS, n);
        int base = n / groupCount;
        int remainder = n % groupCount;
        int index = 0;
        for (int g = 0; g < groupCount; g++) {
            int size = base + (g < remainder ? 1 : 0);
            List<UUID> group = new ArrayList<>(size);
            for (int i = 0; i < size && index < n; i++) {
                group.add(playerOrder.get(index++));
            }
            groups.add(group);
        }
    }

    // ==================== 一阶段：志愿职业 ====================

    /**
     * 提交 / 修改志愿职业（一阶段内一直可以改选）。
     *
     * @return 志愿是否真的发生了变化（没变化就不用广播）
     */
    public boolean submitVolunteer(ServerLevel world, ServerPlayer player, String roleId) {
        if (phase != Phase.VOLUNTEER) {
            return false;
        }
        UUID id = player.getUUID();
        SRERole role = resolveRole(roleId);
        if (role == null) {
            return false;
        }
        String newId = role.identifier().toString();
        if (newId.equals(volunteerRoleIds.get(id)) && volunteerPoolIndex.containsKey(id)) {
            return false;
        }
        volunteerRoleIds.put(id, newId);
        volunteerPoolIndex.put(id, indexOfRole(role));
        player.displayClientMessage(
                Component.translatable("gui.sre.volunteer_open.selected",
                        RoleUtils.getRoleName(role).withColor(role.getColor()))
                        .withStyle(ChatFormatting.GREEN),
                true);
        RoleUtils.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 1.0f, 1.2f);
        return true;
    }

    /** 是否所有在线玩家都已提交志愿。 */
    public boolean allVolunteersSubmitted(ServerLevel world) {
        for (UUID id : playerOrder) {
            ServerPlayer p = world.getServer().getPlayerList().getPlayer(id);
            if (p == null || p.isRemoved()) {
                continue;
            }
            if (!volunteerRoleIds.containsKey(id)) {
                return false;
            }
        }
        return true;
    }

    // ==================== 二阶段：海选 ====================

    public void startOpenPhase(ServerLevel world) {
        phase = Phase.OPEN;
        groupIndex = -1;
        advanceGroup(world);
    }

    public void advanceGroup(ServerLevel world) {
        groupIndex++;
        if (groupIndex >= groups.size()) {
            startConfirmPhase();
            return;
        }
        // 与职业轮选模式进入下一轮时使用同一声铃声；第一组没有“下一轮”提示音。
        if (groupIndex > 0) {
            for (ServerPlayer player : world.players()) {
                RoleUtils.playSound(player, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 1.0f, 1.5f);
            }
        }
        List<UUID> group = groups.get(groupIndex);
        revealForGroup(group);
        grantCardReveals(world, group);
        phaseStartTime = world.getGameTime();
        phaseTimeLimit = computeGroupTimeLimit();
        waitingForClients = false;
    }

    /**
     * 每组的选择时长与轮选模式中一轮的选择时长一致（3 个候选 × 每职业时间），
     * 第一组额外给一点缓冲。
     */
    private int computeGroupTimeLimit() {
        int base = (int) (PLAYER_SELECT_COUNT * SREConfig.instance().roleRotationPerPlayerPerRoleTime * 20);
        if (groupIndex == 0) {
            base += 60;
        }
        return Math.max(20, base);
    }

    /** 从「未揭晓且未被任何人选择」的职业中随机揭晓与本组人数相同的数量。 */
    private void revealForGroup(List<UUID> group) {
        List<Integer> eligible = new ArrayList<>();
        for (int i = 0; i < pool.size(); i++) {
            if (globallyRevealed.contains(i) || pickedBy[i] != null) {
                continue;
            }
            eligible.add(i);
        }
        Collections.shuffle(eligible, random);
        int count = Math.min(group.size(), eligible.size());
        for (int i = 0; i < count; i++) {
            globallyRevealed.add(eligible.get(i));
        }
    }

    /**
     * 轮到持有阵营卡（杀手卡 / 中立卡 / 平民卡）的玩家时，仅对其额外揭示至多
     * {@link #CARD_EXTRA_REVEAL} 个该阵营的职业。
     *
     * <p>
     * 与轮选模式一致：若场上根本没有该阵营的职业，则退还这张卡并提示。
     */
    private void grantCardReveals(ServerLevel world, List<UUID> group) {
        for (UUID id : group) {
            ForceTeamInfo info = PlayerRoleWeightManager.ForcePlayerTeam.get(id);
            if (info == null || info.type() != ForceTeamType.CARD) {
                continue;
            }
            int rawType = info.roleType();
            // 直接通过职业类型枚举匹配，保证普通中立（2）与杀手方中立（3）不会混淆。
            FactionCardType cardType = FactionCardType.fromRoleType(rawType);
            Set<Integer> already = cardReveals.getOrDefault(id, Set.of());
            Integer forcedIndex = forcedRoleReveals.get(id);
            List<Integer> candidates = new ArrayList<>();
            boolean factionInPool = false;
            for (int i = 0; i < pool.size(); i++) {
                SRERole role = pool.get(i).role();
                if (role == null || FactionCardType.fromRoleType(role.getRoleType()) != cardType) {
                    continue;
                }
                factionInPool = true;
                if (pickedBy[i] != null || globallyRevealed.contains(i) || already.contains(i)
                        || (forcedIndex != null && forcedIndex == i)) {
                    // 已经能看到的（含 forcerole 那个）不再占用卡牌的额外揭示名额
                    continue;
                }
                candidates.add(i);
            }

            if (!factionInPool) {
                // 场上没有该阵营的职业：退还卡牌（与轮选模式的「无法提供匹配职业」一致）
                PlayerRoleWeightManager.ForcePlayerTeam.remove(id);
                ServerPlayer sp = world.getServer().getPlayerList().getPlayer(id);
                if (sp != null) {
                    if (cardType != FactionCardType.NONE) {
                        ProgressionDataManager.addFactionCard(sp, cardType, 1);
                    }
                    sp.displayClientMessage(
                            Component.translatable("message.sre.role_rotation.card_limit")
                                    .withStyle(ChatFormatting.RED),
                            true);
                }
                continue;
            }
            if (candidates.isEmpty()) {
                continue;
            }
            Collections.shuffle(candidates, random);
            Set<Integer> set = cardReveals.computeIfAbsent(id, k -> new LinkedHashSet<>());
            for (int i = 0; i < Math.min(CARD_EXTRA_REVEAL, candidates.size()); i++) {
                set.add(candidates.get(i));
            }
        }
    }

    public boolean processPick(ServerLevel world, ServerPlayer player, int index) {
        if (phase != Phase.OPEN || groupIndex < 0 || groupIndex >= groups.size()) {
            return false;
        }
        UUID id = player.getUUID();
        if (!groups.get(groupIndex).contains(id)) {
            return false;
        }
        if (picks.containsKey(id)) {
            return false;
        }
        if (index < 0 || index >= pool.size() || pickedBy[index] != null) {
            return false;
        }
        applyPick(id, index, false);
        SRERole role = pool.get(index).role();
        if (role != null) {
            player.displayClientMessage(
                    Component.translatable("gui.sre.volunteer_open.selected",
                            RoleUtils.getRoleName(role).withColor(role.getColor()))
                            .withStyle(ChatFormatting.GREEN),
                    true);
        }
        RoleUtils.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 1.0f, 1.2f);
        return true;
    }

    private void applyPick(UUID id, int index, boolean randomMarker) {
        pickedBy[index] = id;
        picks.put(id, index);
        SRERole role = pool.get(index).role();
        boolean hidden = role != null && role.isHiddenForRoleRotation();
        if (randomMarker || hidden || !globallyRevealed.contains(index)) {
            randomChoosers.add(id);
        }
    }

    /** 当前正在挑选的组内玩家。 */
    public Set<UUID> currentGroupMembers() {
        if (phase != Phase.OPEN || groupIndex < 0 || groupIndex >= groups.size()) {
            return Set.of();
        }
        return new LinkedHashSet<>(groups.get(groupIndex));
    }

    /** 当前组是否所有人都已选择。 */
    public boolean isCurrentGroupDone() {
        if (phase != Phase.OPEN || groupIndex < 0 || groupIndex >= groups.size()) {
            return false;
        }
        for (UUID id : groups.get(groupIndex)) {
            if (!picks.containsKey(id)) {
                return false;
            }
        }
        return true;
    }

    /** 当前组超时：未选择的玩家自动随机。 */
    public void timeoutCurrentGroup(ServerLevel world) {
        if (phase != Phase.OPEN || groupIndex < 0 || groupIndex >= groups.size()) {
            return;
        }
        for (UUID id : new ArrayList<>(groups.get(groupIndex))) {
            if (picks.containsKey(id)) {
                continue;
            }
            int index = randomFreeIndex();
            if (index < 0) {
                picks.put(id, -1);
                randomChoosers.add(id);
            } else {
                applyPick(id, index, true);
            }
            ServerPlayer p = world.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                p.displayClientMessage(
                        Component.translatable("gui.sre.volunteer_open.selection_timeout")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
        }
    }

    /** 总超时兜底：所有还没选的玩家一律随机补齐。 */
    public void forceFillRemaining() {
        for (UUID id : playerOrder) {
            if (picks.containsKey(id)) {
                continue;
            }
            int index = randomFreeIndex();
            if (index < 0) {
                picks.put(id, -1);
                randomChoosers.add(id);
            } else {
                applyPick(id, index, true);
            }
        }
        if (phase == Phase.VOLUNTEER || phase == Phase.OPEN) {
            phase = Phase.CONFIRM;
            waitingForClients = false;
        }
    }

    private int randomFreeIndex() {
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < pickedBy.length; i++) {
            if (pickedBy[i] == null) {
                free.add(i);
            }
        }
        if (free.isEmpty()) {
            return -1;
        }
        return free.get(random.nextInt(free.size()));
    }

    // ==================== 确认阶段 ====================

    public void startConfirmPhase() {
        phase = Phase.CONFIRM;
        waitingForClients = false;
        groupIndex = groups.size();
        confirmRequired = SREConfig.instance().roleRotationPreparingConfirmRequire;
        confirmedPlayers.clear();
        confirmCountdown = confirmRequired
                ? Math.max(1, SREConfig.instance().roleRotationPreparingConfirmTimeout) * 20
                : 6 * 20;
    }

    public boolean confirm(UUID playerUuid) {
        if (!confirmRequired) {
            return false;
        }
        return confirmedPlayers.add(playerUuid);
    }

    /** 所有（在线且已选职业）的玩家是否都已确认。 */
    public boolean allConfirmed(ServerLevel world) {
        for (UUID id : playerOrder) {
            ServerPlayer p = world.getServer().getPlayerList().getPlayer(id);
            if (p == null || p.isRemoved()) {
                continue;
            }
            if (!confirmedPlayers.contains(id)) {
                return false;
            }
        }
        return true;
    }

    // ==================== 离线处理 ====================

    /** @return 状态是否发生变化 */
    public boolean handleOfflinePlayers(ServerLevel world) {
        if (phase == Phase.END) {
            return false;
        }
        boolean changed = false;
        for (UUID id : playerOrder) {
            ServerPlayer p = world.getServer().getPlayerList().getPlayer(id);
            if (p != null && !p.isRemoved()) {
                continue;
            }
            switch (phase) {
                case VOLUNTEER -> {
                    if (!volunteerRoleIds.containsKey(id)) {
                        volunteerRoleIds.put(id, "");
                        volunteerPoolIndex.put(id, -1);
                        changed = true;
                    }
                }
                case OPEN -> {
                    if (groupIndex >= 0 && groupIndex < groups.size()
                            && groups.get(groupIndex).contains(id) && !picks.containsKey(id)) {
                        int index = randomFreeIndex();
                        if (index < 0) {
                            picks.put(id, -1);
                            randomChoosers.add(id);
                        } else {
                            applyPick(id, index, true);
                        }
                        changed = true;
                    }
                }
                case CONFIRM -> {
                    if (confirmedPlayers.add(id)) {
                        changed = true;
                    }
                }
                default -> {
                }
            }
        }
        return changed;
    }

    // ==================== 结果 ====================

    public Map<UUID, SRERole> getFinalRoles() {
        Map<UUID, SRERole> result = new HashMap<>();
        for (UUID id : playerOrder) {
            Integer index = picks.get(id);
            SRERole role = (index != null && index >= 0 && index < pool.size()) ? pool.get(index).role() : null;
            result.put(id, role == null ? TMMRoles.CIVILIAN : role);
        }
        return result;
    }

    // ==================== 客户端数据 ====================

    public int pickIndexOf(UUID id) {
        Integer index = picks.get(id);
        return index == null ? -1 : index;
    }

    public Set<Integer> chosenIndices() {
        return new LinkedHashSet<>(picks.values());
    }

    /** 该玩家能看到的池下标 -> 职业 id（全局揭晓 + 卡牌/强制职业揭示 + 自己选的）。 */
    public Map<Integer, String> visibleRolesFor(UUID id) {
        Map<Integer, String> map = new LinkedHashMap<>();
        for (Integer index : globallyRevealed) {
            map.put(index, roleId(index));
        }
        Set<Integer> personal = cardReveals.get(id);
        if (personal != null) {
            for (Integer index : personal) {
                map.put(index, roleId(index));
            }
        }
        Integer forced = forcedRoleReveals.get(id);
        if (forced != null) {
            map.put(forced, roleId(forced));
        }
        int myPick = pickIndexOf(id);
        if (myPick >= 0) {
            map.put(myPick, roleId(myPick));
        }
        return map;
    }

    /**
     * 看到的「隐藏职业」下标。
     *
     * <p>
     * 自己选中的那个、以及 forcerole 指定给自己的那个都可以看真名（与职业轮选里
     * 强制职业会以候选卡片的形式明文出现一致），其余揭晓出来的隐藏职业只显示「隐藏职业」。
     */
    public Set<Integer> hiddenVisibleFor(UUID id) {
        Set<Integer> hidden = new LinkedHashSet<>();
        int myPick = pickIndexOf(id);
        Integer forced = forcedRoleReveals.get(id);
        for (Integer index : visibleRolesFor(id).keySet()) {
            if (index == myPick || (forced != null && forced.equals(index))) {
                continue;
            }
            SRERole role = pool.get(index).role();
            if (role != null && role.isHiddenForRoleRotation()) {
                hidden.add(index);
            }
        }
        return hidden;
    }

    /**
     * 需要「加粗 + 橙色」显示的额外揭示：阵营卡揭示的，以及 forcerole 指定的那个职业。
     */
    public Set<Integer> highlightedRevealsFor(UUID id) {
        Set<Integer> set = new LinkedHashSet<>();
        Integer forced = forcedRoleReveals.get(id);
        if (forced != null) {
            set.add(forced);
        }
        Set<Integer> cards = cardReveals.get(id);
        if (cards != null) {
            set.addAll(cards);
        }
        return set;
    }

    /** 左侧玩家列表用：玩家 -> 职业 id（空串表示显示为「随机」，缺失表示还没选）。 */
    public Map<UUID, String> publicPickedRoles() {
        Map<UUID, String> map = new LinkedHashMap<>();
        for (Map.Entry<UUID, Integer> entry : picks.entrySet()) {
            Integer index = entry.getValue();
            if (randomChoosers.contains(entry.getKey()) || index == null || index < 0) {
                map.put(entry.getKey(), "");
            } else {
                map.put(entry.getKey(), roleId(index));
            }
        }
        return map;
    }

    public boolean canPlayerSelect(UUID id) {
        if (phase != Phase.OPEN || groupIndex < 0 || groupIndex >= groups.size()) {
            return false;
        }
        return groups.get(groupIndex).contains(id) && !picks.containsKey(id);
    }

    private String roleId(int index) {
        SRERole role = pool.get(index).role();
        return role == null ? "" : role.identifier().toString();
    }

    private int indexOfRole(SRERole role) {
        for (int i = 0; i < pool.size(); i++) {
            SRERole r = pool.get(i).role();
            if (r != null && r.identifier().equals(role.identifier())) {
                return i;
            }
        }
        return -1;
    }

    private static SRERole resolveRole(String roleId) {
        if (roleId == null || roleId.isBlank()) {
            return null;
        }
        ResourceLocation location = ResourceLocation.tryParse(roleId);
        if (location == null) {
            return null;
        }
        SRERole role = TMMRoles.ROLES.get(location);
        if (role != null) {
            return role;
        }
        for (Map.Entry<ResourceLocation, SRERole> entry : TMMRoles.ROLES.entrySet()) {
            if (entry.getKey().getPath().equals(roleId)) {
                return entry.getValue();
            }
        }
        return null;
    }

}
