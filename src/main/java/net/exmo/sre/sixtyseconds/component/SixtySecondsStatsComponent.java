package net.exmo.sre.sixtyseconds.component;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

/**
 * 末日60秒模式的统一生存状态组件：饥饿 / 口渴 / san / 污染 / 健康 五值 + 家庭身份 + 队伍编号。
 * <p>
 * P0 骨架：字段、同步、NBT、HUD 已就位；{@code SixtySecondsStatsSystem} 目前空跑，
 * 实际扣减 / 生病 / 变怪等后果留 TODO（见 {@code docs/末日60秒生存模式.md}）。
 * <p>
 * 参照 {@code net.exmo.sre.repair.component.RepairRolePlayerComponent}。仅同步给玩家自己
 * （{@link RoleComponent#shouldSyncWith} 默认），重大更改时才 {@link #sync()}（见 ai_doc.md）。
 */
public class SixtySecondsStatsComponent implements RoleComponent {
    public static final ComponentKey<SixtySecondsStatsComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "sixty_seconds_stats"),
            SixtySecondsStatsComponent.class);

    public static final int MAX = 100;

    public int hunger = MAX;
    public int thirst = MAX;
    public int sanity = MAX;
    public int pollution = 0;
    public int health = MAX;
    /** 理智上限（杀人永久 -5~9，见 SixtySecondsHealthSystem.die；恢复类回san均以此为顶）。 */
    public int sanityMax = MAX;

    /** 队伍（家庭）编号；-1 表示未编队。 */
    public int teamId = -1;
    /** 家庭身份；null 表示未分配。 */
    public FamilyPosition familyPosition = null;
    /** 当前游戏日（0=准备阶段，1..totalDays=游戏日），同步给客户端供 HUD 显示。 */
    public int dayNumber = 0;
    /**
     * 本局总游戏日数（可按图配置，见 {@code SixtySecondsManager.totalDays}）。
     * 客户端 HUD 读不到服务端配置，故随 dayNumber 一起<b>按玩家同步</b>过来显示「第 X/N 天」。
     */
    public int totalDays = net.exmo.sre.sixtyseconds.logic.SixtySecondsManager.DEFAULT_TOTAL_DAYS;
    /** 本日相位截止时间戳（gameTime，换日/跳时间时同步一次），客户端据此推算子相位与倒计时（HUD 时钟）。 */
    public long phaseEndTick = 0L;

    public boolean sick = false;
    public boolean downed = false;
    public boolean monster = false;

    /** 本游戏日已倒地次数（第 2 次倒地直接死亡）。 */
    public int downedCountToday = 0;
    /** 当前倒地是否由受伤造成（饥渴致空的倒地直接死亡，不进此状态）。 */
    public boolean downedFromInjury = false;
    /** 倒地流血死亡的时间戳（gameTime）；0 表示未倒地。 */
    public long bleedOutEndTick = 0L;
    /** 探索归来冷却结束时间戳（gameTime）。 */
    public long exploreCooldownEndTick = 0L;
    /** 救起后未使用医疗包的“感染风险”状态：每 2 分钟 33% 概率生病。 */
    public boolean recovering = false;
    /** san 归零开始变怪倒计时的时间戳（gameTime）；0=未开始。san 恢复>0 则清零。 */
    public long sanZeroTick = 0L;

    private final Player player;

    public SixtySecondsStatsComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        hunger = MAX;
        thirst = MAX;
        sanity = MAX;
        pollution = 0;
        health = MAX;
        sanityMax = MAX;
        teamId = -1;
        familyPosition = null;
        dayNumber = 0;
        phaseEndTick = 0L;
        sick = false;
        downed = false;
        monster = false;
        downedCountToday = 0;
        downedFromInjury = false;
        bleedOutEndTick = 0L;
        exploreCooldownEndTick = 0L;
        recovering = false;
        sanZeroTick = 0L;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public void sync() {
        KEY.sync(player);
    }

    // ── 紧凑二进制同步（省流量）────────────────────────────────────────────
    // 覆写默认的 NBT 同步：NBT 每次要写 18 个字符串键（~300+ 字节），而客户端 HUD 只需要
    // 一小部分字段。这里改为 varint/flag 位打包（~25 字节），并且【不再同步纯服务端字段】
    // （downedCountToday / downedFromInjury / recovering / sanZeroTick——客户端不用）。
    // 60s 模式下还会向其他玩家发【精简变体】（队伍/家庭身份/状态位，~5 字节），
    // 供 RoleNameRenderer 在准星处显示他人家庭关系；追踪开始时 CCA 会自动补发一次。

    @Override
    public boolean shouldSyncWith(@NotNull net.minecraft.server.level.ServerPlayer recipient) {
        return recipient == player
                || net.exmo.sre.sixtyseconds.SixtySecondsMod.isActive(player.level());
    }

    @Override
    public void writeSyncPacket(@NotNull net.minecraft.network.RegistryFriendlyByteBuf buf,
            @NotNull net.minecraft.server.level.ServerPlayer recipient) {
        boolean full = recipient == player;
        buf.writeBoolean(full);
        if (!full) {
            buf.writeVarInt(teamId + 1);
            buf.writeVarInt(familyPosition == null ? 0 : familyPosition.ordinal() + 1);
            buf.writeByte((sick ? 1 : 0) | (downed ? 2 : 0) | (monster ? 4 : 0));
            return;
        }
        buf.writeVarInt(hunger);
        buf.writeVarInt(thirst);
        buf.writeVarInt(sanity);
        buf.writeVarInt(pollution);
        buf.writeVarInt(health);
        buf.writeVarInt(sanityMax);
        buf.writeVarInt(teamId + 1);            // -1 → 0（避免负数 varint 膨胀到 5 字节）
        buf.writeVarInt(familyPosition == null ? 0 : familyPosition.ordinal() + 1);
        buf.writeVarInt(dayNumber);
        buf.writeVarInt(totalDays);
        buf.writeVarLong(phaseEndTick);
        buf.writeVarLong(exploreCooldownEndTick);
        buf.writeByte((sick ? 1 : 0) | (downed ? 2 : 0) | (monster ? 4 : 0));
        buf.writeVarLong(bleedOutEndTick); // 倒地 HUD 流血倒计时（倒地/救起时才变化）

    }

    @Override
    public void applySyncPacket(@NotNull net.minecraft.network.RegistryFriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            // 精简变体（他人组件）：只更新准星显示需要的字段
            teamId = buf.readVarInt() - 1;
            int slimFamily = buf.readVarInt();
            familyPosition = slimFamily == 0 ? null : FamilyPosition.values()[slimFamily - 1];
            int slimFlags = buf.readByte();
            sick = (slimFlags & 1) != 0;
            downed = (slimFlags & 2) != 0;
            monster = (slimFlags & 4) != 0;
            return;
        }
        hunger = buf.readVarInt();
        thirst = buf.readVarInt();
        sanity = buf.readVarInt();
        pollution = buf.readVarInt();
        health = buf.readVarInt();
        sanityMax = buf.readVarInt();
        teamId = buf.readVarInt() - 1;
        int family = buf.readVarInt();
        familyPosition = family == 0 ? null : FamilyPosition.values()[family - 1];
        dayNumber = buf.readVarInt();
        totalDays = buf.readVarInt();
        phaseEndTick = buf.readVarLong();
        exploreCooldownEndTick = buf.readVarLong();
        int flags = buf.readByte();
        sick = (flags & 1) != 0;
        downed = (flags & 2) != 0;
        monster = (flags & 4) != 0;
        bleedOutEndTick = buf.readVarLong();
    }

    /** 已被上面的紧凑二进制同步取代，仅保留以满足接口（不再被调用）。 */
    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("Hunger", hunger);
        tag.putInt("Thirst", thirst);
        tag.putInt("Sanity", sanity);
        tag.putInt("SanityMax", sanityMax);
        tag.putInt("Pollution", pollution);
        tag.putInt("Health", health);
        tag.putInt("TeamId", teamId);
        tag.putInt("Family", familyPosition == null ? -1 : familyPosition.ordinal());
        tag.putInt("Day", dayNumber);
        tag.putInt("TotalDays", totalDays);
        tag.putLong("PhaseEndTick", phaseEndTick);
        tag.putBoolean("Sick", sick);
        tag.putBoolean("Downed", downed);
        tag.putBoolean("Monster", monster);
        tag.putInt("DownedCountToday", downedCountToday);
        tag.putBoolean("DownedFromInjury", downedFromInjury);
        tag.putLong("BleedOutEndTick", bleedOutEndTick);
        tag.putLong("ExploreCooldownEndTick", exploreCooldownEndTick);
        tag.putBoolean("Recovering", recovering);
        tag.putLong("SanZeroTick", sanZeroTick);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        hunger = tag.getInt("Hunger");
        thirst = tag.getInt("Thirst");
        sanity = tag.getInt("Sanity");
        sanityMax = tag.contains("SanityMax") ? tag.getInt("SanityMax") : MAX;
        pollution = tag.getInt("Pollution");
        health = tag.getInt("Health");
        teamId = tag.getInt("TeamId");
        int family = tag.getInt("Family");
        familyPosition = family < 0 ? null : FamilyPosition.values()[family];
        dayNumber = tag.getInt("Day");
        if (tag.contains("TotalDays")) {
            totalDays = tag.getInt("TotalDays");
        }
        phaseEndTick = tag.getLong("PhaseEndTick");
        sick = tag.getBoolean("Sick");
        downed = tag.getBoolean("Downed");
        monster = tag.getBoolean("Monster");
        downedCountToday = tag.getInt("DownedCountToday");
        downedFromInjury = tag.getBoolean("DownedFromInjury");
        bleedOutEndTick = tag.getLong("BleedOutEndTick");
        exploreCooldownEndTick = tag.getLong("ExploreCooldownEndTick");
        recovering = tag.getBoolean("Recovering");
        sanZeroTick = tag.getLong("SanZeroTick");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 局内状态，不落磁盘（仅同步）。
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
