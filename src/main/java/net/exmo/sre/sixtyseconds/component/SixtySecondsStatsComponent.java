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

    /** 队伍（家庭）编号；-1 表示未编队。 */
    public int teamId = -1;
    /** 家庭身份；null 表示未分配。 */
    public FamilyPosition familyPosition = null;
    /** 当前游戏日（0=准备阶段，1..7=游戏日），同步给客户端供 HUD 显示。 */
    public int dayNumber = 0;

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
        teamId = -1;
        familyPosition = null;
        dayNumber = 0;
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

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("Hunger", hunger);
        tag.putInt("Thirst", thirst);
        tag.putInt("Sanity", sanity);
        tag.putInt("Pollution", pollution);
        tag.putInt("Health", health);
        tag.putInt("TeamId", teamId);
        tag.putInt("Family", familyPosition == null ? -1 : familyPosition.ordinal());
        tag.putInt("Day", dayNumber);
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
        pollution = tag.getInt("Pollution");
        health = tag.getInt("Health");
        teamId = tag.getInt("TeamId");
        int family = tag.getInt("Family");
        familyPosition = family < 0 ? null : FamilyPosition.values()[family];
        dayNumber = tag.getInt("Day");
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
