package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.SixtySecondsPhase;
import net.exmo.sre.sixtyseconds.arena.SixtySecondsSearchZones;
import net.exmo.sre.sixtyseconds.component.FamilyPosition;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.content.block.DoorPurpose;
import net.exmo.sre.sixtyseconds.content.block.ShelterDoorBlock;
import net.exmo.sre.sixtyseconds.network.OpenShelterDoorS2CPacket;
import net.exmo.sre.sixtyseconds.network.OpenSixtySecondsDoorS2CPacket;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一避难所门菜单（服务端）：门是<b>通用交互中枢</b>，不再按 {@code DoorPurpose} 分工——
 * 右键任意避难所门 → 服务端按<b>该玩家</b>的上下文算出可用操作 → 客户端 {@code ShelterDoorScreen} 展示 →
 * 点选后经 {@code ShelterDoorActionC2SPacket} 回传执行。同一扇门对不同玩家给出不同选项
 * （例：搜索区里各队玩家点同一扇门各自「返回住所」；准备阶段点门「存入物资」）。
 * <p>范式：S2C 开屏 → C2S 动作 → 服务端改状态（照抄 {@code RepairRoleShopScreen} 链路）。
 */
public final class SixtySecondsDoorMenu {
    public static final int ACTION_DEPOSIT = 0;
    public static final int ACTION_EXPLORE = 1;
    public static final int ACTION_RETURN = 2;
    public static final int ACTION_EVENT = 3;
    public static final int ACTION_VISIT = 4;
    /** 目标队成员处理待定的拜访请求（点击 → 弹出同意/拒绝 GUI）。 */
    public static final int ACTION_VISIT_PROMPT = 5;
    /** 拜访对话（访客与主队成员双方都在门上打开聊天窗）。 */
    public static final int ACTION_VISIT_CHAT = 6;
    /** 做客中的访客离开：安全传回自己队的避难所。 */
    public static final int ACTION_VISIT_LEAVE = 7;
    /** 撬棍强闯（对着别队的探索区避难所门；触发报警，门锁可挡）。 */
    public static final int ACTION_BREAK_CROWBAR = 8;
    /** 撬锁器潜入（对着别队的探索区避难所门；不报警，可能触发门陷阱）。 */
    public static final int ACTION_BREAK_LOCKPICK = 9;
    /** 查看别队门情报（等级/耐久/门锁；门陷阱是暗手不透露）。 */
    public static final int ACTION_DOOR_INSPECT = 10;
    /** 闯入者离开：身处别队避难所内的破门闯入者，点门安全传回自己的避难所。 */
    public static final int ACTION_INTRUDER_LEAVE = 11;
    /** 房车专属：驾驶（上车，自动第三人称）。 */
    public static final int ACTION_RV_DRIVE = 12;
    /** 房车专属：打开配件/燃料/升级控制台。 */
    public static final int ACTION_RV_MANAGE = 13;

    /** C2S 动作合法性校验：玩家须离门 8 格以内（防远程伪造包）。 */
    private static final double MAX_USE_DISTANCE_SQR = 8 * 8;

    private SixtySecondsDoorMenu() {
    }

    /** 右键门：按玩家上下文构建操作列表并开屏；无可用操作时给动作栏提示。 */
    public static void open(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        SixtySecondsState.TeamData team = data.teams.get(stats.teamId);

        List<OpenShelterDoorS2CPacket.Option> options = new ArrayList<>();
        // 做客中的访客：门只提供「回自己的避难所」与「与主人对话」（USED_BANED 抑制了其他交互，
        // MobEffectKeyMixin 对避难所门放行使用键，所以访客点得开这个菜单）
        if (SixtySecondsVisiting.isVisiting(player)) {
            options.add(new OpenShelterDoorS2CPacket.Option(ACTION_VISIT_LEAVE, true, 0));
            options.add(new OpenShelterDoorS2CPacket.Option(ACTION_VISIT_CHAT,
                    SixtySecondsVisitChat.hasPartner(player), 0));
            ServerPlayNetworking.send(player, new OpenShelterDoorS2CPacket(pos, false, 0, 0, 0, false, 0, options));
            return;
        }
        // 闯入者：身处别队避难所内（非做客、非本队），点是别人家的门或自己所在的门——
        // 门菜单只给「溜回自己的避难所」。闯入者是侵略行为，不能享受做客的和平对话通道。
        if (isInsideForeignShelter(player, data)) {
            options.add(new OpenShelterDoorS2CPacket.Option(ACTION_INTRUDER_LEAVE, true, 0));
            ServerPlayNetworking.send(player, new OpenShelterDoorS2CPacket(pos, false, 0, 0, 0, false, 0, options));
            return;
        }
        // 本队有待处理的拜访请求：门菜单置顶「处理拜访请求」（同意/拒绝在门上操作）
        if (data.phase == SixtySecondsPhase.DAY
                && SixtySecondsVisitSystem.pendingForTeam(level, stats.teamId) != null) {
            options.add(new OpenShelterDoorS2CPacket.Option(ACTION_VISIT_PROMPT, true, 0));
        }
        // 有进行中的拜访对话（主队一侧）：门上可打开聊天窗
        if (SixtySecondsVisitChat.hasPartner(player)) {
            options.add(new OpenShelterDoorS2CPacket.Option(ACTION_VISIT_CHAT, true, 0));
        }
        if (data.phase == SixtySecondsPhase.PREPARATION) {
            int carry = stats.familyPosition == null
                    ? FamilyPosition.MOTHER.carryLimit : stats.familyPosition.carryLimit;
            options.add(new OpenShelterDoorS2CPacket.Option(ACTION_DEPOSIT, true, carry));
        } else if (data.phase == SixtySecondsPhase.DAY) {
            if (isInOwnShelterOrResidential(player, team)) {
                // 在庇护所/住宅内 → 出门探索 / 门外事件 / 拜访：判定改为「是否身处本队庇护所/住宅」，
                // 不再依赖出门状态标记，避免乘船上岛/野外乱逛等在外但无探索记录时误显示。
                options.add(new OpenShelterDoorS2CPacket.Option(ACTION_EXPLORE, true, 0));
                options.add(new OpenShelterDoorS2CPacket.Option(ACTION_EVENT, true, 0));
                options.add(new OpenShelterDoorS2CPacket.Option(ACTION_VISIT, data.teams.size() > 1, 0));
            } else {
                // 不在庇护所内（探索区/野外/海岛等「在外」状态）→ 返回住所
                long remain = stats.exploreCooldownEndTick - level.getGameTime();
                int seconds = (int) Math.ceil(Math.max(0, remain) / 20.0D);
                // 回家只认本队出口门（未分配出口门则任意门可回）
                options.add(new OpenShelterDoorS2CPacket.Option(ACTION_RETURN,
                        remain <= 0 && isOwnReturnDoor(team, pos, player), seconds));
                // 别队的避难所门：给出闯入选项（撬棍强闯/撬锁器潜入/查看门情报），替代旧的
                // 「物品右键任意位置远程选队闯入」——必须走到目标家门口
                SixtySecondsState.TeamData target = SixtySecondsBreakIn.teamByDoor(data, player, pos);
                if (target != null) {
                    long now = level.getGameTime();
                    boolean entryOpen = !SixtySecondsBreakIn.isHomeEntryLocked(data);
                    ItemStack crowbar = SixtySecondsBreakIn.findBestTool(player, true);
                    ItemStack lockpick = SixtySecondsBreakIn.findBestTool(player, false);
                    int crowbarTier = crowbar != null && crowbar.getItem()
                            instanceof net.exmo.sre.sixtyseconds.content.item.SixtySecondsBreakInItem c
                            ? c.tier() : 0;
                    int lockpickTier = lockpick != null && lockpick.getItem()
                            instanceof net.exmo.sre.sixtyseconds.content.item.SixtySecondsBreakInItem l
                            ? l.tier() : 0;
                    options.add(new OpenShelterDoorS2CPacket.Option(ACTION_BREAK_CROWBAR,
                            entryOpen && crowbarTier >= target.doorLevel && crowbarTier > 0
                                    && !target.doorLockActive(now), target.doorLevel));
                    options.add(new OpenShelterDoorS2CPacket.Option(ACTION_BREAK_LOCKPICK,
                            entryOpen && lockpickTier >= target.doorLevel && lockpickTier > 0,
                            target.doorLevel));
                    options.add(new OpenShelterDoorS2CPacket.Option(ACTION_DOOR_INSPECT, true, 0));
                }
            }
        }
        if (options.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.door_no_action"), true);
            return;
        }

        boolean ownDoor = team != null && (pos.equals(team.doorPos)
                || (team.shelterBox != null && team.shelterBox.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5))
                || (team.residentialBox != null && team.residentialBox.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)));
        int doorHp = team == null ? 0 : team.doorHp;
        int doorMaxHp = team == null ? 0 : team.doorMaxHp;
        int doorLevel = team == null ? 0 : team.doorLevel;
        boolean doorBroken = team != null && team.doorBroken;
        int stored = team == null ? 0 : team.storedSupplies.size();
        ServerPlayNetworking.send(player, new OpenShelterDoorS2CPacket(pos, ownDoor, doorHp, doorMaxHp,
                doorLevel, doorBroken, stored, options));
    }

    /** C2S 回传：重新校验上下文后执行（客户端状态可能过期，全部条件以服务端当下为准）。 */
    public static void handleAction(ServerPlayer player, BlockPos pos, int action) {
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockState(pos).getBlock() instanceof ShelterDoorBlock)
                || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_USE_DISTANCE_SQR) {
            return;
        }
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        switch (action) {
            case ACTION_DEPOSIT -> {
                if (data.phase == SixtySecondsPhase.PREPARATION) {
                    depositSupplies(player, data);
                }
            }
            case ACTION_EXPLORE -> {
                if (data.phase == SixtySecondsPhase.DAY && !SixtySecondsSearchZones.isInSearchZone(player)) {
                    // 房车模式：从避难所门外出探索时，落点改到本队房车处（从房车旁下车），而非门外
                    SixtySecondsSearchZones.enter(player, rvExitPos(level, data, player, pos));
                }
            }
            case ACTION_RETURN -> {
                SixtySecondsState.TeamData team = data.teams
                        .get(SixtySecondsStatsComponent.KEY.get(player).teamId);
                // 判定改为「是否身处本队庇护所/住宅」：在庇护所内不能返回，在外（探索区/野外）才可回家。
                if (!isInOwnShelterOrResidential(player, team)) {
                    if (!isOwnReturnDoor(team, pos, player)) {
                        player.displayClientMessage(Component.translatable(
                                "message.noellesroles.sixty_seconds.wrong_return_door"), true);
                        return;
                    }
                    // 妹妹外出事件：只有晚上（Night）才能回家
                    if (team != null && team.sisterOutside
                            && team.sisterUUID != null
                            && team.sisterUUID.equals(player.getUUID())) {
                        var subPhase = net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.subPhase(data, level.getGameTime());
                        if (subPhase != net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.SubPhase.NIGHT) {
                            player.displayClientMessage(Component.translatable(
                                    "message.noellesroles.sixty_seconds.devent.sister_outside.cant_return_yet"), true);
                            return;
                        }
                    }
                    SixtySecondsSearchZones.returnPlayer(player);
                }
            }
            case ACTION_EVENT -> {
                if (data.phase == SixtySecondsPhase.DAY) {
                    ServerPlayNetworking.send(player,
                            new OpenSixtySecondsDoorS2CPacket(DoorPurpose.EVENT.ordinal(), pos));
                }
            }
            case ACTION_VISIT -> {
                if (data.phase == SixtySecondsPhase.DAY) {
                    SixtySecondsVisitSystem.openRequestScreen(player);
                }
            }
            case ACTION_VISIT_PROMPT -> {
                SixtySecondsVisitSystem.PendingView pending = SixtySecondsVisitSystem.pendingForTeam(
                        level, SixtySecondsStatsComponent.KEY.get(player).teamId);
                if (pending != null) {
                    ServerPlayNetworking.send(player, new net.exmo.sre.sixtyseconds.network.OpenVisitPromptS2CPacket(
                            pending.visitor(), pending.visitorName(), pending.type()));
                }
            }
            case ACTION_VISIT_CHAT -> SixtySecondsVisitChat.openScreen(player);
            case ACTION_VISIT_LEAVE -> SixtySecondsVisiting.leave(player);
            case ACTION_INTRUDER_LEAVE -> intruderLeave(player);
            case ACTION_BREAK_CROWBAR -> {
                if (data.phase == SixtySecondsPhase.DAY) {
                    SixtySecondsBreakIn.executeAtDoor(player, pos, true);
                }
            }
            case ACTION_BREAK_LOCKPICK -> {
                if (data.phase == SixtySecondsPhase.DAY) {
                    SixtySecondsBreakIn.executeAtDoor(player, pos, false);
                }
            }
            case ACTION_DOOR_INSPECT -> SixtySecondsBreakIn.inspectDoor(player, pos);
            default -> {
            }
        }
    }

    /**
     * 搜索区回家门校验：
     * <ul>
     *   <li><b>本次出门的入口落点附近的门</b>（容差 5 格）——从避难所私有门（searchDoors 绑定）出来的场景：
     *       落点就在探索区对应门旁，这扇门就是「对应的回家门」。旧逻辑只认 returnDoorPos，
     *       走私有门出来的玩家点它会被误报「不是你家的门」。</li>
     *   <li>本队出口门 {@code returnDoorPos}（容差 2 格，兼容 2 格高门体/贴脸误差）。</li>
     *   <li>两者皆未知（未分配出口门且无入口记录）则任意门可回（旧行为）。</li>
     * </ul>
     */
    private static boolean isOwnReturnDoor(SixtySecondsState.TeamData team, BlockPos pos, ServerPlayer player) {
        BlockPos entrySpawn = SixtySecondsSearchZones.entrySpawn(player);
        if (entrySpawn != null && pos.distSqr(entrySpawn) <= 5 * 5) {
            return true;
        }
        return team == null || team.returnDoorPos == null || pos.distSqr(team.returnDoorPos) <= 2 * 2;
    }

    /**
     * 闯入者点门「溜回自己的避难所」：移除闯入标记 + 清除搜索区状态 + 安全传回本队避难所。
     * 与 {@link SixtySecondsVisiting#leave} 对称：客人与闯入者都是安全的「离开」路径。
     */
    private static void intruderLeave(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // 移除闯入者标记（PvP 豁免药水效果）
        player.removeEffect(org.agmas.noellesroles.init.ModEffects.BREAK_IN_INTRUDER);
        // 清除搜索区 RETURNS 记录——闯入者在别队避难所时，RETURNS 的 confine 指向别队避难所盒，
        // 不清掉会导致后续 tick 仍把玩家往别队避难所拽
        SixtySecondsSearchZones.clearReturnEntry(player);
        SixtySecondsState.TeamData home = SixtySecondsState.get(level).teams
                .get(SixtySecondsStatsComponent.KEY.get(player).teamId);
        if (home != null && home.shelterSpawn != null) {
            BlockPos safe = SixtySecondsSearchZones.findSafeSpot(level, home.shelterSpawn);
            player.teleportTo(level, safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D,
                    player.getYRot(), player.getXRot());
            net.exmo.sre.sixtyseconds.network.SixtySecondsMapZoneS2CPacket.send(
                    player, home.shelterBox, home.shelterSpawn, true);
            // 回家时清除避难所内的怪物
            SixtySecondsDefenseSystem.clearShelterMobs(level, home.shelterBox);
        }
        player.displayClientMessage(
                Component.translatable("message.noellesroles.sixty_seconds.intruder_left"), false);
    }

    /** 准备阶段：把玩家携带槽（0..携带上限-1）里的物资转入本队库存，清空这些槽以便继续搜集。 */
    public static void depositSupplies(ServerPlayer player, SixtySecondsState.Data data) {
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        SixtySecondsState.TeamData team = data.teams.get(stats.teamId);
        if (team == null) {
            return;
        }
        int carry = stats.familyPosition == null ? FamilyPosition.MOTHER.carryLimit : stats.familyPosition.carryLimit;
        int deposited = 0;
        for (int slot = 0; slot < carry && slot <= 35; slot++) {
            ItemStack current = player.getInventory().getItem(slot);
            if (!current.isEmpty() && !current.is(Items.BARRIER)) {
                team.storedSupplies.add(current.copy());
                deposited += current.getCount();
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
        player.displayClientMessage(
                Component.translatable("message.noellesroles.sixty_seconds.supplies_recorded", deposited), true);
    }

    /**
     * 坐标判定玩家是否身处别队的避难所或住宅内（= 破门闯入别人家，非做客）。
     */
    private static boolean isInsideForeignShelter(ServerPlayer player, SixtySecondsState.Data data) {
        int myTeam = SixtySecondsStatsComponent.KEY.get(player).teamId;
        double x = player.getX(), y = player.getY(), z = player.getZ();
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            if (team.teamId != myTeam && (team.shelterBox != null && team.shelterBox.contains(x, y, z)
                    || team.residentialBox != null && team.residentialBox.contains(x, y, z))) {
                return true;
            }
        }
        return false;
    }

    /** 坐标判定玩家是否身处<b>本队</b>避难所或住宅盒内（在自己家里）。team 为空返回 false。 */
    private static boolean isInOwnShelterOrResidential(ServerPlayer player, SixtySecondsState.TeamData team) {
        if (team == null) {
            return false;
        }
        double x = player.getX(), y = player.getY(), z = player.getZ();
        return (team.shelterBox != null && team.shelterBox.contains(x, y, z))
                || (team.residentialBox != null && team.residentialBox.contains(x, y, z));
    }

    /**
     * 坐标判定玩家是否身处任意队伍的避难所或住宅内。
     * 相比 {@code SixtySecondsSearchZones#isInSearchZone} 的状态标记，
     * 坐标判定不受传送/状态不同步影响，更可靠。
     */
    private static boolean isInAnyShelterOrResidential(ServerPlayer player, SixtySecondsState.Data data) {
        double x = player.getX(), y = player.getY(), z = player.getZ();
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            if ((team.shelterBox != null && team.shelterBox.contains(x, y, z))
                    || (team.residentialBox != null && team.residentialBox.contains(x, y, z))) {
                return true;
            }
        }
        return false;
    }

    // ══ 房车作为「移动的门」：与门菜单同一套操作，按队伍（而非门坐标）判定 ══════════════

    /** 房车模式下外出探索的落点：本队房车所在处（从房车旁下车）；未开启或无房车则回退到门坐标。 */
    private static BlockPos rvExitPos(ServerLevel level, SixtySecondsState.Data data, ServerPlayer player,
            BlockPos doorPos) {
        var config = net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.current(level).orElse(null);
        if (config == null || !config.rvEnabled) {
            return doorPos;
        }
        SixtySecondsState.TeamData team = data.teams.get(SixtySecondsStatsComponent.KEY.get(player).teamId);
        var rv = SixtySecondsRvSystem.getTeamRv(level, team);
        return rv != null ? rv.blockPosition() : doorPos;
    }

    /** 右键房车：按玩家上下文（本队/别队房车）构建门菜单选项 + 驾驶/配件，走 rvEntityId 回传路径。 */
    public static void openForRv(ServerPlayer player,
            net.exmo.sre.sixtyseconds.content.entity.SixtySecondsRvEntity rv) {
        ServerLevel level = player.serverLevel();
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        SixtySecondsState.TeamData team = data.teams.get(stats.teamId);
        List<OpenShelterDoorS2CPacket.Option> options = new ArrayList<>();

        // 做客中的访客 / 别队避难所内的闯入者：与门一致，只给离开（+对话）
        if (SixtySecondsVisiting.isVisiting(player)) {
            options.add(new OpenShelterDoorS2CPacket.Option(ACTION_VISIT_LEAVE, true, 0));
            options.add(new OpenShelterDoorS2CPacket.Option(ACTION_VISIT_CHAT,
                    SixtySecondsVisitChat.hasPartner(player), 0));
            sendRv(player, rv, team, options);
            return;
        }
        if (isInsideForeignShelter(player, data)) {
            options.add(new OpenShelterDoorS2CPacket.Option(ACTION_INTRUDER_LEAVE, true, 0));
            sendRv(player, rv, team, options);
            return;
        }

        boolean own = rv.teamId() >= 0 && rv.teamId() == stats.teamId;
        if (own) {
            if (data.phase == SixtySecondsPhase.DAY
                    && SixtySecondsVisitSystem.pendingForTeam(level, stats.teamId) != null) {
                options.add(new OpenShelterDoorS2CPacket.Option(ACTION_VISIT_PROMPT, true, 0));
            }
            if (SixtySecondsVisitChat.hasPartner(player)) {
                options.add(new OpenShelterDoorS2CPacket.Option(ACTION_VISIT_CHAT, true, 0));
            }
            if (data.phase == SixtySecondsPhase.PREPARATION) {
                int carry = stats.familyPosition == null
                        ? FamilyPosition.MOTHER.carryLimit : stats.familyPosition.carryLimit;
                options.add(new OpenShelterDoorS2CPacket.Option(ACTION_DEPOSIT, true, carry));
            } else if (data.phase == SixtySecondsPhase.DAY) {
                boolean out = SixtySecondsSearchZones.isInSearchZone(player)
                        && !isInAnyShelterOrResidential(player, data);
                if (out) {
                    long remain = stats.exploreCooldownEndTick - level.getGameTime();
                    int seconds = (int) Math.ceil(Math.max(0, remain) / 20.0D);
                    options.add(new OpenShelterDoorS2CPacket.Option(ACTION_RETURN, remain <= 0, seconds));
                } else {
                    // 在家侧（房车停在住宅旁）：出门探索 = 从自己的房车旁下车进入野外
                    options.add(new OpenShelterDoorS2CPacket.Option(ACTION_EXPLORE, true, 0));
                    options.add(new OpenShelterDoorS2CPacket.Option(ACTION_EVENT, true, 0));
                    options.add(new OpenShelterDoorS2CPacket.Option(ACTION_VISIT, data.teams.size() > 1, 0));
                }
            }
            // 房车专属：驾驶（准备阶段/停机时不可）+ 配件管理
            options.add(new OpenShelterDoorS2CPacket.Option(ACTION_RV_DRIVE,
                    !rv.isDisabled() && data.phase != SixtySecondsPhase.PREPARATION, 0));
            options.add(new OpenShelterDoorS2CPacket.Option(ACTION_RV_MANAGE, true, 0));
        } else if (data.phase == SixtySecondsPhase.DAY) {
            // 别队房车：撬棍强闯 / 撬锁潜入 / 查看情报（目标队 = 房车所属队）
            SixtySecondsState.TeamData target = data.teams.get(rv.teamId());
            if (target != null) {
                long now = level.getGameTime();
                boolean entryOpen = !SixtySecondsBreakIn.isHomeEntryLocked(data);
                ItemStack crowbar = SixtySecondsBreakIn.findBestTool(player, true);
                ItemStack lockpick = SixtySecondsBreakIn.findBestTool(player, false);
                int crowbarTier = crowbar != null && crowbar.getItem()
                        instanceof net.exmo.sre.sixtyseconds.content.item.SixtySecondsBreakInItem c ? c.tier() : 0;
                int lockpickTier = lockpick != null && lockpick.getItem()
                        instanceof net.exmo.sre.sixtyseconds.content.item.SixtySecondsBreakInItem l ? l.tier() : 0;
                options.add(new OpenShelterDoorS2CPacket.Option(ACTION_BREAK_CROWBAR,
                        entryOpen && crowbarTier >= target.doorLevel && crowbarTier > 0
                                && !target.doorLockActive(now), target.doorLevel));
                options.add(new OpenShelterDoorS2CPacket.Option(ACTION_BREAK_LOCKPICK,
                        entryOpen && lockpickTier >= target.doorLevel && lockpickTier > 0, target.doorLevel));
                options.add(new OpenShelterDoorS2CPacket.Option(ACTION_DOOR_INSPECT, true, 0));
            }
        }

        if (options.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.door_no_action"), true);
            return;
        }
        sendRv(player, rv, team, options);
    }

    private static void sendRv(ServerPlayer player,
            net.exmo.sre.sixtyseconds.content.entity.SixtySecondsRvEntity rv,
            SixtySecondsState.TeamData ownTeam, List<OpenShelterDoorS2CPacket.Option> options) {
        boolean ownDoor = ownTeam != null && rv.teamId() == ownTeam.teamId;
        int doorHp = ownTeam == null ? 0 : ownTeam.doorHp;
        int doorMaxHp = ownTeam == null ? 0 : ownTeam.doorMaxHp;
        int doorLevel = ownTeam == null ? 0 : ownTeam.doorLevel;
        boolean doorBroken = ownTeam != null && ownTeam.doorBroken;
        int stored = ownTeam == null ? 0 : ownTeam.storedSupplies.size();
        ServerPlayNetworking.send(player, new OpenShelterDoorS2CPacket(rv.blockPosition(), ownDoor,
                doorHp, doorMaxHp, doorLevel, doorBroken, stored, options, rv.getId()));
    }

    /** 房车菜单动作回传：门坐标校验换成「实体存活 + 距离 + 队伍归属」。 */
    public static void handleRvAction(ServerPlayer player,
            net.exmo.sre.sixtyseconds.content.entity.SixtySecondsRvEntity rv, int action) {
        ServerLevel level = player.serverLevel();
        if (rv.isRemoved() || player.distanceToSqr(rv) > MAX_USE_DISTANCE_SQR) {
            return;
        }
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        boolean own = rv.canUse(player);
        switch (action) {
            case ACTION_DEPOSIT -> {
                if (own && data.phase == SixtySecondsPhase.PREPARATION) {
                    depositSupplies(player, data);
                }
            }
            case ACTION_EXPLORE -> {
                if (own && data.phase == SixtySecondsPhase.DAY
                        && !SixtySecondsSearchZones.isInSearchZone(player)) {
                    SixtySecondsSearchZones.enter(player, rv.blockPosition());
                }
            }
            case ACTION_RETURN -> {
                if (own && SixtySecondsSearchZones.isInSearchZone(player)
                        && !isInAnyShelterOrResidential(player, data)) {
                    SixtySecondsState.TeamData team = data.teams
                            .get(SixtySecondsStatsComponent.KEY.get(player).teamId);
                    if (team != null && team.sisterOutside && team.sisterUUID != null
                            && team.sisterUUID.equals(player.getUUID())) {
                        var subPhase = net.exmo.sre.sixtyseconds.SixtySecondsDayCycle
                                .subPhase(data, level.getGameTime());
                        if (subPhase != net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.SubPhase.NIGHT) {
                            player.displayClientMessage(Component.translatable(
                                    "message.noellesroles.sixty_seconds.devent.sister_outside.cant_return_yet"), true);
                            return;
                        }
                    }
                    SixtySecondsSearchZones.returnPlayer(player);
                }
            }
            case ACTION_EVENT -> {
                if (own && data.phase == SixtySecondsPhase.DAY) {
                    ServerPlayNetworking.send(player,
                            new OpenSixtySecondsDoorS2CPacket(DoorPurpose.EVENT.ordinal(), rv.blockPosition()));
                }
            }
            case ACTION_VISIT -> {
                if (own && data.phase == SixtySecondsPhase.DAY) {
                    SixtySecondsVisitSystem.openRequestScreen(player);
                }
            }
            case ACTION_VISIT_PROMPT -> {
                SixtySecondsVisitSystem.PendingView pending = SixtySecondsVisitSystem.pendingForTeam(
                        level, SixtySecondsStatsComponent.KEY.get(player).teamId);
                if (pending != null) {
                    ServerPlayNetworking.send(player, new net.exmo.sre.sixtyseconds.network.OpenVisitPromptS2CPacket(
                            pending.visitor(), pending.visitorName(), pending.type()));
                }
            }
            case ACTION_VISIT_CHAT -> SixtySecondsVisitChat.openScreen(player);
            case ACTION_VISIT_LEAVE -> SixtySecondsVisiting.leave(player);
            case ACTION_INTRUDER_LEAVE -> intruderLeave(player);
            case ACTION_BREAK_CROWBAR -> {
                if (!own && data.phase == SixtySecondsPhase.DAY) {
                    SixtySecondsBreakIn.executeForTeam(player, data.teams.get(rv.teamId()), true);
                }
            }
            case ACTION_BREAK_LOCKPICK -> {
                if (!own && data.phase == SixtySecondsPhase.DAY) {
                    SixtySecondsBreakIn.executeForTeam(player, data.teams.get(rv.teamId()), false);
                }
            }
            case ACTION_DOOR_INSPECT -> {
                if (!own) {
                    SixtySecondsBreakIn.inspectTeam(player, data.teams.get(rv.teamId()));
                }
            }
            case ACTION_RV_DRIVE -> {
                if (own) {
                    boardRv(player, rv);
                }
            }
            case ACTION_RV_MANAGE -> {
                if (own) {
                    ServerPlayNetworking.send(player,
                            new net.exmo.sre.sixtyseconds.network.OpenRvConsoleS2CPacket(rv.getId()));
                }
            }
            default -> {
            }
        }
    }

    /** 上车驾驶：自动切第三人称（{@code VehicleCameraS2CPacket}），停机不可上。 */
    private static void boardRv(ServerPlayer player,
            net.exmo.sre.sixtyseconds.content.entity.SixtySecondsRvEntity rv) {
        if (rv.isDisabled()) {
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.rv_disabled").withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        if (player.startRiding(rv, true)) {
            ServerPlayNetworking.send(player,
                    new net.exmo.sre.sixtyseconds.network.VehicleCameraS2CPacket(true));
            if (rv.fuelTicks() <= 0) {
                player.displayClientMessage(Component.translatable(
                        "message.noellesroles.sixty_seconds.vehicle_no_fuel")
                        .withStyle(net.minecraft.ChatFormatting.RED), true);
            }
        }
    }
}
