package net.exmo.sre.repair.component;

import org.agmas.noellesroles.component.ModComponents;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.exmo.sre.repair.role.RepairRoleDatabase;
import net.exmo.sre.repair.role.RepairRoleDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class RepairRolePlayerComponent implements RoleComponent {
    public final Set<String> ownedRoles = new LinkedHashSet<>();
    public final EnumMap<RepairRoleDefinition.Faction, String> selectedRoles = new EnumMap<>(
            RepairRoleDefinition.Faction.class);
    public String activeRole = "";
    public int neutralTaskProgress = 0;
    public boolean neutralTaskCompleted = false;
    public long selectionEndTick = 0L;
    public boolean downed = false;
    public UUID carriedBy = null;
    public UUID carrying = null;
    public int carryBlockedTicks = 0;
    public BlockPosTag trialStand = BlockPosTag.NONE;
    public int completedStations = 0;
    /** 本局地图上一共有几台修机台（HUD 右侧面板用）。 */
    public int totalStations = 0;
    /** 当前修机速度倍率，百分比（按存活可修机人数缩放）。 */
    public int repairSpeedPercent = 100;
    public boolean gatesPowered = false;
    public int downedAllies = 0;
    public int activeTrialPrisoners = 0;
    public int nearestTrialProgress = 0;
    public String currentEventKey = "";
    public String currentEventRewardKey = "";
    public int currentEventTicks = 0;
    public int currentEventDanger = 0;
    public long lastRepairActionTick = -100L;
    public int neutralTaskNeeded = 0;
    public int repairInjuryLevel = 0;
    public long lastHunterHitTick = -1000L;
    public long activeSkillCooldownEndTick = 0L;
    public long hunterWeaponCooldownEndTick = 0L;
    public String selectedSkillState = "";
    public int carryStruggleProgress = 0;
    public int downedStruggleProgress = 0;
    public long downedLastStruggleTick = -1000L;
    public String lastStruggleSide = "";
    public long lastStruggleTick = -1000L;
    public String activeAttackPlugin = "";
    public String forcedRole = "";
    public BlockPosTag searchTarget = BlockPosTag.NONE;
    public long searchStartTick = 0L;
    public int searchTotalTicks = 0;
    public String searchPromptKey = "";
    public String lockPromptKey = "";
    public String escapedRouteId = "";
    /** 理智值 0-100（恐鬼症系统），仅幸存者/中立有效 */
    public int sanity = 100;
    private final Player player;

    public RepairRolePlayerComponent(Player player) {
        this.player = player;
        ensureStarterRoles();
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        activeRole = "";
        neutralTaskProgress = 0;
        neutralTaskCompleted = false;
        selectionEndTick = 0L;
        downed = false;
        carriedBy = null;
        carrying = null;
        carryBlockedTicks = 0;
        trialStand = BlockPosTag.NONE;
        completedStations = 0;
        totalStations = 0;
        repairSpeedPercent = 100;
        gatesPowered = false;
        downedAllies = 0;
        activeTrialPrisoners = 0;
        nearestTrialProgress = 0;
        currentEventKey = "";
        currentEventRewardKey = "";
        currentEventTicks = 0;
        currentEventDanger = 0;
        lastRepairActionTick = -100L;
        neutralTaskNeeded = 0;
        repairInjuryLevel = 0;
        lastHunterHitTick = -1000L;
        activeSkillCooldownEndTick = 0L;
        hunterWeaponCooldownEndTick = 0L;
        selectedSkillState = "";
        carryStruggleProgress = 0;
        downedStruggleProgress = 0;
        downedLastStruggleTick = -1000L;
        lastStruggleSide = "";
        lastStruggleTick = -1000L;
        activeAttackPlugin = "";
        forcedRole = "";
        searchTarget = BlockPosTag.NONE;
        searchStartTick = 0L;
        searchTotalTicks = 0;
        searchPromptKey = "";
        lockPromptKey = "";
        escapedRouteId = "";
        sanity = 100;
        ensureStarterRoles();
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public void ensureStarterRoles() {
        ownedRoles.addAll(RepairRoleDatabase.starterRoles());
        for (RepairRoleDefinition.Faction faction : RepairRoleDefinition.Faction.values()) {
            selectedRoles.computeIfAbsent(faction, ignored -> RepairRoleDefinition.byFaction(faction).stream()
                    .filter(role -> role.starter).findFirst().map(role -> role.id).orElse(""));
        }
    }

    public boolean owns(RepairRoleDefinition role) {
        return ownedRoles.contains(role.id) || role.id.equals(forcedRole);
    }

    public RepairRoleDefinition selectedRole(RepairRoleDefinition.Faction faction) {
        ensureStarterRoles();
        String id = selectedRoles.get(faction);
        return RepairRoleDefinition.byId(id).filter(role -> role.faction == faction && owns(role))
                .orElseGet(
                        () -> RepairRoleDefinition.byFaction(faction).stream().filter(role -> role.starter).findFirst()
                                .orElse(RepairRoleDefinition.byFaction(faction).getFirst()));
    }

    public void setSelectedRole(RepairRoleDefinition role) {
        if (!owns(role)) {
            return;
        }
        selectedRoles.put(role.faction, role.id);
        sync();
    }

    public void unlock(RepairRoleDefinition role) {
        ownedRoles.add(role.id);
        sync();
        if (player instanceof ServerPlayer serverPlayer) {
            RepairRoleDatabase.saveFrom(serverPlayer);
        }
    }

    public void sync() {
        ModComponents.REPAIR_ROLES.sync(player);
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeData(tag, true);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        readData(tag, true);
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (!SREConfig.instance().enableRepairMode)
            return;
        ensureStarterRoles();
    }

    private void writeData(CompoundTag tag, boolean includeRoundOnly) {
        if (!SREConfig.instance().enableRepairMode)
            return;
        ListTag owned = new ListTag();
        ownedRoles.forEach(role -> owned.add(StringTag.valueOf(role)));
        tag.put("Owned", owned);
        CompoundTag selected = new CompoundTag();
        selectedRoles.forEach((faction, role) -> selected.putString(faction.id(), role));
        tag.put("Selected", selected);
        tag.putString("ActiveRole", activeRole);
        tag.putInt("NeutralTaskProgress", neutralTaskProgress);
        tag.putBoolean("NeutralTaskCompleted", neutralTaskCompleted);
        tag.putLong("SelectionEndTick", selectionEndTick);
        tag.putBoolean("Downed", downed);
        if (carriedBy != null)
            tag.putUUID("CarriedBy", carriedBy);
        if (carrying != null)
            tag.putUUID("Carrying", carrying);
        tag.putInt("CarryBlockedTicks", carryBlockedTicks);
        trialStand.write(tag, "TrialStand");
        tag.putInt("CompletedStations", completedStations);
        tag.putInt("TotalStations", totalStations);
        tag.putInt("RepairSpeedPercent", repairSpeedPercent);
        tag.putBoolean("GatesPowered", gatesPowered);
        tag.putInt("DownedAllies", downedAllies);
        tag.putInt("ActiveTrialPrisoners", activeTrialPrisoners);
        tag.putInt("NearestTrialProgress", nearestTrialProgress);
        tag.putString("CurrentEventKey", currentEventKey);
        tag.putString("CurrentEventRewardKey", currentEventRewardKey);
        tag.putInt("CurrentEventTicks", currentEventTicks);
        tag.putInt("CurrentEventDanger", currentEventDanger);
        tag.putLong("LastRepairActionTick", lastRepairActionTick);
        tag.putInt("NeutralTaskNeeded", neutralTaskNeeded);
        tag.putInt("RepairInjuryLevel", repairInjuryLevel);
        tag.putLong("LastHunterHitTick", lastHunterHitTick);
        tag.putLong("ActiveSkillCooldownEndTick", activeSkillCooldownEndTick);
        tag.putLong("HunterWeaponCooldownEndTick", hunterWeaponCooldownEndTick);
        tag.putString("SelectedSkillState", selectedSkillState);
        tag.putInt("CarryStruggleProgress", carryStruggleProgress);
        tag.putInt("DownedStruggleProgress", downedStruggleProgress);
        tag.putLong("DownedLastStruggleTick", downedLastStruggleTick);
        tag.putString("LastStruggleSide", lastStruggleSide);
        tag.putLong("LastStruggleTick", lastStruggleTick);
        if (includeRoundOnly) {
            tag.putString("ActiveAttackPlugin", activeAttackPlugin);
            tag.putString("ForcedRole", forcedRole);
        }
        searchTarget.write(tag, "SearchTarget");
        tag.putLong("SearchStartTick", searchStartTick);
        tag.putInt("SearchTotalTicks", searchTotalTicks);
        tag.putString("SearchPromptKey", searchPromptKey);
        tag.putString("LockPromptKey", lockPromptKey);
        tag.putString("EscapedRouteId", escapedRouteId);
        tag.putInt("Sanity", sanity);
    }

    private void readData(CompoundTag tag, boolean includeRoundOnly) {
        ownedRoles.clear();
        if (tag.contains("Owned", Tag.TAG_LIST)) {
            ListTag owned = tag.getList("Owned", Tag.TAG_STRING);
            owned.forEach(entry -> ownedRoles.add(entry.getAsString()));
        }
        selectedRoles.clear();
        if (tag.contains("Selected", Tag.TAG_COMPOUND)) {
            CompoundTag selected = tag.getCompound("Selected");
            for (RepairRoleDefinition.Faction faction : RepairRoleDefinition.Faction.values()) {
                String role = selected.getString(faction.id());
                if (!role.isEmpty()) {
                    selectedRoles.put(faction, role);
                }
            }
        }
        activeRole = tag.getString("ActiveRole");
        neutralTaskProgress = tag.getInt("NeutralTaskProgress");
        neutralTaskCompleted = tag.getBoolean("NeutralTaskCompleted");
        selectionEndTick = tag.getLong("SelectionEndTick");
        downed = tag.getBoolean("Downed");
        carriedBy = tag.hasUUID("CarriedBy") ? tag.getUUID("CarriedBy") : null;
        carrying = tag.hasUUID("Carrying") ? tag.getUUID("Carrying") : null;
        carryBlockedTicks = tag.getInt("CarryBlockedTicks");
        trialStand = BlockPosTag.read(tag, "TrialStand");
        completedStations = tag.getInt("CompletedStations");
        totalStations = tag.getInt("TotalStations");
        repairSpeedPercent = tag.contains("RepairSpeedPercent") ? tag.getInt("RepairSpeedPercent") : 100;
        gatesPowered = tag.getBoolean("GatesPowered");
        downedAllies = tag.getInt("DownedAllies");
        activeTrialPrisoners = tag.getInt("ActiveTrialPrisoners");
        nearestTrialProgress = tag.getInt("NearestTrialProgress");
        currentEventKey = tag.getString("CurrentEventKey");
        currentEventRewardKey = tag.getString("CurrentEventRewardKey");
        currentEventTicks = tag.getInt("CurrentEventTicks");
        currentEventDanger = tag.getInt("CurrentEventDanger");
        lastRepairActionTick = tag.getLong("LastRepairActionTick");
        neutralTaskNeeded = tag.getInt("NeutralTaskNeeded");
        repairInjuryLevel = tag.getInt("RepairInjuryLevel");
        lastHunterHitTick = tag.getLong("LastHunterHitTick");
        activeSkillCooldownEndTick = tag.getLong("ActiveSkillCooldownEndTick");
        hunterWeaponCooldownEndTick = tag.getLong("HunterWeaponCooldownEndTick");
        selectedSkillState = tag.getString("SelectedSkillState");
        carryStruggleProgress = tag.getInt("CarryStruggleProgress");
        downedStruggleProgress = tag.getInt("DownedStruggleProgress");
        downedLastStruggleTick = tag.getLong("DownedLastStruggleTick");
        lastStruggleSide = tag.getString("LastStruggleSide");
        lastStruggleTick = tag.getLong("LastStruggleTick");
        activeAttackPlugin = includeRoundOnly ? tag.getString("ActiveAttackPlugin") : "";
        forcedRole = includeRoundOnly ? tag.getString("ForcedRole") : "";
        searchTarget = BlockPosTag.read(tag, "SearchTarget");
        searchStartTick = tag.getLong("SearchStartTick");
        searchTotalTicks = tag.getInt("SearchTotalTicks");
        searchPromptKey = tag.getString("SearchPromptKey");
        lockPromptKey = tag.getString("LockPromptKey");
        escapedRouteId = tag.getString("EscapedRouteId");
        sanity = tag.contains("Sanity") ? tag.getInt("Sanity") : 100;
    }

    public record BlockPosTag(int x, int y, int z, boolean present) {
        public static final BlockPosTag NONE = new BlockPosTag(0, 0, 0, false);

        public static BlockPosTag of(BlockPos pos) {
            return new BlockPosTag(pos.getX(), pos.getY(), pos.getZ(), true);
        }

        public BlockPos toBlockPos() {
            return new BlockPos(x, y, z);
        }

        public void write(CompoundTag tag, String prefix) {
            tag.putBoolean(prefix + "Present", present);
            if (present) {
                tag.putInt(prefix + "X", x);
                tag.putInt(prefix + "Y", y);
                tag.putInt(prefix + "Z", z);
            }
        }

        public static BlockPosTag read(CompoundTag tag, String prefix) {
            if (!tag.getBoolean(prefix + "Present")) {
                return NONE;
            }
            return new BlockPosTag(tag.getInt(prefix + "X"), tag.getInt(prefix + "Y"), tag.getInt(prefix + "Z"), true);
        }
    }
}
