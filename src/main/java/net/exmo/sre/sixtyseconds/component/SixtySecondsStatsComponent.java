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
    /** 永久增益可达到的最高上限 */
    public static final int MAX_CAP = 120;
    /** 血量专属初始上限（x1.5） */
    public static final int HEALTH_MAX = 150;
    /** 血量专属永久增益上限（x1.5） */
    public static final int HEALTH_MAX_CAP = 180;

    public int hunger = MAX;
    public int thirst = MAX;
    public int sanity = MAX;
    public int pollution = 0;
    public int health = HEALTH_MAX;
    /** 理智上限（杀人永久 -5~9，见 SixtySecondsHealthSystem.die；恢复类回san均以此为顶）。 */
    public int sanityMax = MAX;
    /** 永久增益后的个人上限（血量 150，其他 100，最高 180/120）。 */
    public int healthMax = HEALTH_MAX;
    public int hungerMax = MAX;
    public int thirstMax = MAX;
    public int pollutionMax = MAX;

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
    /**
     * 自动复活的触发 gameTime（0=未在等待复活）。死亡时由 {@code SixtySecondsAutoRevive} 写入并同步一次，
     * 客户端 HUD 按 {@code reviveEndTick - gameTime} 自行推算倒计时——不每 tick 递减、不每 tick 同步
     * （同 {@link #phaseEndTick}/{@link #bleedOutEndTick} 的纪律，见 ai_doc.md）。
     */
    public long reviveEndTick = 0L;
    /**
     * 本局已用自动复活次数（每次自动复活 +1；局末 {@code SixtySecondsAutoRevive.reset} 清零）。
     * 与按图配置的 {@code SixtySecondsConfig.autoReviveMaxUses}（-1=无限）配合判定是否还能复活。
     */
    public int reviveCount = 0;
    /** 救起后未使用医疗包的“感染风险”状态：每 2 分钟 33% 概率生病。 */
    public boolean recovering = false;
    /** san 归零开始变怪倒计时的时间戳（gameTime）；0=未开始。san 恢复>0 则清零。 */
    public long sanZeroTick = 0L;
    /** 本局击杀数（用于报纸报道等统计）。 */
    public int playerKills = 0;
    /** 已解锁的额外背包槽位数（0-18，通过扩容模块获得，总可用槽位=基础+此值）。 */
    public int extraUnlockedSlots = 0;
    /** 绷带缓慢恢复剩余生命值（不使用后重置，无需持久化）。 */
    public transient int bandageHealRemaining = 0;

    /** 上次通过 PlayerHealthS2CPacket 广播的血量（-1=未发送过，下次 sync 必发）。 */
    private transient int lastSentHealth = -1;
    private transient int lastSentHealthMax = -1;

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
        health = HEALTH_MAX;
        sanityMax = MAX;
        healthMax = HEALTH_MAX;
        hungerMax = MAX;
        thirstMax = MAX;
        pollutionMax = MAX;
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
        reviveEndTick = 0L;
        reviveCount = 0;
        exploreCooldownEndTick = 0L;
        recovering = false;
        sanZeroTick = 0L;
        playerKills = 0;
        extraUnlockedSlots = 0;
        lastSentHealth = -1;
        lastSentHealthMax = -1;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public void sync() {
        KEY.sync(player);
        // 血量变化时通过独立网络包广播给同维度其他玩家（供 SixtySecondsCombatHud 显示他人血量）。
        // 不占用 CCA 精简变体——饥饿/口渴等变化不会触发血量包，只在 health 实际变化时才发。
        if (player instanceof net.minecraft.server.level.ServerPlayer sp
                && (health != lastSentHealth || healthMax != lastSentHealthMax)) {
            net.exmo.sre.sixtyseconds.network.PlayerHealthS2CPacket.broadcast(sp, health, healthMax);
            lastSentHealth = health;
            lastSentHealthMax = healthMax;
        }
    }

    // ── 紧凑二进制同步（省流量）────────────────────────────────────────────
    // 覆写默认的 NBT 同步：NBT 每次要写 18 个字符串键（~300+ 字节），而客户端 HUD 只需要
    // 一小部分字段。这里改为 varint/flag 位打包（~25 字节），并且【不再同步纯服务端字段】
    // （downedCountToday / downedFromInjury / recovering / sanZeroTick——客户端不用）。
    // 60s 模式下还会向其他玩家发【精简变体】（队伍/家庭身份/状态位，~5 字节），
    // 供 RoleNameRenderer 在准星处显示他人家庭关系；追踪开始时 CCA 会自动补发一次。
    // 他人血量由独立的 PlayerHealthS2CPacket 推送（见 sync()），不占用精简变体。

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
        buf.writeVarInt(healthMax);
        buf.writeVarInt(hungerMax);
        buf.writeVarInt(thirstMax);
        buf.writeVarInt(pollutionMax);
        buf.writeVarInt(teamId + 1);            // -1 → 0（避免负数 varint 膨胀到 5 字节）
        buf.writeVarInt(familyPosition == null ? 0 : familyPosition.ordinal() + 1);
        buf.writeVarInt(dayNumber);
        buf.writeVarInt(totalDays);
        buf.writeVarLong(phaseEndTick);
        buf.writeVarLong(exploreCooldownEndTick);
        buf.writeVarInt(playerKills);
        buf.writeByte((sick ? 1 : 0) | (downed ? 2 : 0) | (monster ? 4 : 0));
        buf.writeVarLong(bleedOutEndTick); // 倒地 HUD 流血倒计时（倒地/救起时才变化）
        buf.writeVarLong(reviveEndTick);   // 自动复活 HUD 倒计时（死亡/复活时才变化）
        buf.writeVarInt(reviveCount);      // 本局已用复活次数（死亡/复活时才变化，HUD 显示剩余）
        buf.writeVarInt(extraUnlockedSlots);

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
        healthMax = buf.readVarInt();
        hungerMax = buf.readVarInt();
        thirstMax = buf.readVarInt();
        pollutionMax = buf.readVarInt();
        teamId = buf.readVarInt() - 1;
        int family = buf.readVarInt();
        familyPosition = family == 0 ? null : FamilyPosition.values()[family - 1];
        dayNumber = buf.readVarInt();
        totalDays = buf.readVarInt();
        phaseEndTick = buf.readVarLong();
        exploreCooldownEndTick = buf.readVarLong();
        playerKills = buf.readVarInt();
        int flags = buf.readByte();
        sick = (flags & 1) != 0;
        downed = (flags & 2) != 0;
        monster = (flags & 4) != 0;
        bleedOutEndTick = buf.readVarLong();
        reviveEndTick = buf.readVarLong();
        reviveCount = buf.readVarInt();
        extraUnlockedSlots = buf.readVarInt();
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
        tag.putInt("HealthMax", healthMax);
        tag.putInt("HungerMax", hungerMax);
        tag.putInt("ThirstMax", thirstMax);
        tag.putInt("PollutionMax", pollutionMax);
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
        tag.putLong("ReviveEndTick", reviveEndTick);
        tag.putInt("ReviveCount", reviveCount);
        tag.putLong("ExploreCooldownEndTick", exploreCooldownEndTick);
        tag.putBoolean("Recovering", recovering);
        tag.putLong("SanZeroTick", sanZeroTick);
        tag.putInt("PlayerKills", playerKills);
        tag.putInt("ExtraUnlockedSlots", extraUnlockedSlots);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        hunger = tag.getInt("Hunger");
        thirst = tag.getInt("Thirst");
        sanity = tag.getInt("Sanity");
        sanityMax = tag.contains("SanityMax") ? tag.getInt("SanityMax") : MAX;
        pollution = tag.getInt("Pollution");
        health = tag.getInt("Health");
        healthMax = tag.contains("HealthMax") ? tag.getInt("HealthMax") : HEALTH_MAX;
        hungerMax = tag.contains("HungerMax") ? tag.getInt("HungerMax") : MAX;
        thirstMax = tag.contains("ThirstMax") ? tag.getInt("ThirstMax") : MAX;
        pollutionMax = tag.contains("PollutionMax") ? tag.getInt("PollutionMax") : MAX;
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
        reviveEndTick = tag.getLong("ReviveEndTick");
        reviveCount = tag.contains("ReviveCount") ? tag.getInt("ReviveCount") : 0;
        exploreCooldownEndTick = tag.getLong("ExploreCooldownEndTick");
        recovering = tag.getBoolean("Recovering");
        sanZeroTick = tag.getLong("SanZeroTick");
        if (tag.contains("PlayerKills")) {
            playerKills = tag.getInt("PlayerKills");
        }
        extraUnlockedSlots = tag.contains("ExtraUnlockedSlots") ? tag.getInt("ExtraUnlockedSlots") : 0;
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 局内状态，不落磁盘（仅同步）。
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
