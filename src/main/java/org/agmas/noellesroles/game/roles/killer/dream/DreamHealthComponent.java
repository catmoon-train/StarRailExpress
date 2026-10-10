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

package org.agmas.noellesroles.game.roles.killer.dream;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREWeakArmorPlayerComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import io.wifi.starrailexpress.event.OnGameEnd;
import org.agmas.harpymodloader.events.GameInitializeEvent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dream（Dream）虚拟血量组件 —— 挂在<b>所有玩家</b>身上。
 *
 * <p>
 * 在所有人眼中每名玩家默认都有 {@code dreamMaxHealth}（默认 20）滴血；
 * 该血量<b>不使用原版血量</b>，只会被 Dream 的铁斧攻击扣除，归零时按
 * {@code dream_axe} 死因判死并归属 Dream。
 *
 * <p>
 * 回血采用<b>懒计算</b>（见 ai_doc：用触发时间代替每秒同步）：只存
 * {@code baseHealth} 与 {@code lastHurtGameTime}，脱战
 * {@code dreamHealthRegenDelaySeconds}（默认 30s）后按每秒 1 点匀速恢复，
 * 服务端与客户端都用 {@link #getEffectiveHealth(long)} 由游戏时间推算当前值，
 * 因此<b>只在受伤瞬间同步一次</b>，无需每 tick/每秒发包。
 *
 * <p>
 * 血量条 HUD（受伤后才显示，头顶浮动条）见
 * {@code org.agmas.noellesroles.game.roles.killer.dream.client.DreamHealthBarRenderer}。
 */
public class DreamHealthComponent implements RoleComponent {
    public static final ComponentKey<DreamHealthComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "dream_health"),
            DreamHealthComponent.class);

    /**
     * 因虚拟血量归零而被判死的玩家：uuid -> 判死时的游戏时间。
     * 供护士尸体透视判定（见 {@code NurseRole.onBodySpawn}），
     * 这样不必逐个枚举武器死因，后续新增虚拟血量武器也能自动覆盖。
     */
    private static final Map<UUID, Long> VIRTUAL_HEALTH_DEATH_MARKS = new ConcurrentHashMap<>();

    /**
     * 一次性消费「该玩家是否刚刚因虚拟血量归零而死」的标记。
     *
     * @param uuid     玩家 UUID
     * @param gameTime 当前游戏时间（服务端）
     * @return 该玩家是否刚因虚拟血量归零而死
     */
    public static boolean consumeVirtualHealthDeath(UUID uuid, long gameTime) {
        if (uuid == null) {
            return false;
        }
        Long marked = VIRTUAL_HEALTH_DEATH_MARKS.remove(uuid);
        if (marked == null) {
            return false;
        }
        long delta = gameTime - marked;
        // 只认「刚刚」打标的死亡，避免陈旧标记影响该玩家之后的其它死法
        return delta >= 0 && delta <= 40;
    }

    static {
        // 开局重置所有玩家的虚拟血量（本组件不绑定职业 componentKey，自行挂开局事件）
        GameInitializeEvent.EVENT.register((serverLevel, gameWorldComponent, players) -> {
            for (ServerPlayer p : serverLevel.getServer().getPlayerList().getPlayers()) {
                KEY.get(p).initWhenNecessary();
            }
        });
        // 游戏结束时重置所有玩家的虚拟血量，避免残留到下一局
        OnGameEnd.EVENT.register((serverLevel, gameWorldComponent) -> {
            for (ServerPlayer p : serverLevel.getServer().getPlayerList().getPlayers()) {
                KEY.get(p).init();
            }
        });
    }

    private final Player player;
    /** 最后一次受伤时刻的剩余血量（受伤结算后的基准值）。 */
    public int baseHealth;
    /** 最后一次被 Dream 打伤的游戏时间；0 = 本局尚未受伤。 */
    public long lastHurtGameTime;

    public DreamHealthComponent(Player player) {
        this.player = player;
        this.baseHealth = maxHealth();
    }

    public void initWhenNecessary() {
        if (this.baseHealth == maxHealth() && lastHurtGameTime == 0) {
            return;
        }
        init();
    }

    public static int maxHealth() {
        return Math.max(1, NoellesRolesConfig.HANDLER.instance().dreamMaxHealth);
    }

    private static int regenDelayTicks() {
        return NoellesRolesConfig.HANDLER.instance().dreamHealthRegenDelaySeconds * 20;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        // 血量条对所有人可见（"在所有人眼中玩家默认都有20滴血"）
        return true;
    }

    public void sync() {
        KEY.sync(player);
    }

    public void init(boolean sync) {
        // 本组件挂在所有玩家身上且对全员同步，开局对每人无脑 sync 是 N² 个包；
        // 绝大多数玩家本来就处于默认满血态，只有状态真正变化时才广播。
        int max = maxHealth();
        // if (baseHealth == max && lastHurtGameTime == 0) {
        //     return;
        // }
        baseHealth = max;
        lastHurtGameTime = 0;
        if (sync)
            sync();
    }

    @Override
    public void init() {
        init(true);
    }

    @Override
    public void clear() {
        init(true);
    }

    /** 是否启用「脱战自动回血」（配置项 {@code dreamHealthRegenEnabled}，默认关闭）。 */
    public static boolean regenEnabled() {
        return NoellesRolesConfig.HANDLER.instance().dreamHealthRegenEnabled;
    }

    /**
     * 按游戏时间推算当前血量。
     *
     * <p>脱战回血开关（{@link #regenEnabled()}）开启时：脱战
     * {@code dreamHealthRegenDelaySeconds} 秒后每秒恢复 1 点，直至回满；
     * 关闭时：保持受伤后的血量，不再随时间自动回升（仍可用康复药丸 / 康复试剂等手段恢复）。
     */
    public int getEffectiveHealth(long gameTime) {
        int max = maxHealth();
        if (baseHealth >= max || lastHurtGameTime <= 0) {
            return Math.min(baseHealth, max);
        }
        if (!regenEnabled()) {
            return Mth.clamp(baseHealth, 0, max);
        }
        long regenStart = lastHurtGameTime + regenDelayTicks();
        if (gameTime <= regenStart) {
            return Math.max(0, baseHealth);
        }
        long regained = (gameTime - regenStart) / 20; // 每秒 1 点
        return (int) Mth.clamp(baseHealth + regained, 0, max);
    }

    /** 受伤后才显示血量条。 */
    public boolean shouldShowBar(long gameTime) {
        return getEffectiveHealth(gameTime) < maxHealth();
    }

    /**
     * 被 Dream 的铁斧命中：<b>先扣虚拟护盾</b>，剩余伤害才扣虚拟血量；归零则判死并归属攻击者。
     *
     * @return 是否实际造成了伤害（护盾全部吸收时也算造成了伤害，只是不掉血）
     */
    public boolean hurt(@Nullable ServerPlayer attacker, int damage, ResourceLocation deathReason) {
        return hurt(attacker, damage, deathReason, false);
    }

    /**
     * 被 Dream 的铁斧命中：<b>先扣虚拟护盾</b>，剩余伤害才扣虚拟血量；归零则判死并归属攻击者。
     *
     * <p>
     * {@code ignoreShield} 为 true 时本次伤害<b>无视全部护盾</b>（用于 Dream 重锤）：
     * 虚拟护盾不参与吸收、点数保持不变；若这一击足以致死，普通护盾（含限时护盾）
     * 与弱效护盾也会在判死前被清空，不会替玩家挡下这次死亡
     * （参考狙击枪 50 格外的穿透处理）。
     *
     * @param ignoreShield true 时本次伤害<b>无视护盾</b>，直接打在虚拟血量上
     * @return 是否实际造成了伤害（护盾全部吸收时也算造成了伤害，只是不掉血）
     */
    public boolean hurt(@Nullable ServerPlayer attacker, int damage, ResourceLocation deathReason,
            boolean ignoreShield) {
        if (!(player instanceof ServerPlayer sp) || damage <= 0) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        // 虚拟护盾只抵挡虚拟血量伤害：先在这里吸收，掉多少血由吸收后的剩余伤害决定
        int remaining = ignoreShield ? damage : VirtualShieldComponent.KEY.get(sp).absorbVirtualDamage(damage);
        if (remaining <= 0) {
            // 护盾把这次伤害全吃掉了：不掉血、不致死，但伤害确实生效（护盾可能被打空）
            return true;
        }
        long gameTime = sp.level().getGameTime();
        int current = getEffectiveHealth(gameTime);
        baseHealth = current - remaining;
        lastHurtGameTime = gameTime;
        if (baseHealth <= 0) {
            baseHealth = 0;
            sync();
            // 打标：本条命是「虚拟血量归零」判死的（护士尸体透视据此判定，不依赖具体死因）
            VIRTUAL_HEALTH_DEATH_MARKS.put(sp.getUUID(), gameTime);
            if (ignoreShield) {
                clearAllShields(sp);
            }
            GameUtils.killPlayer(sp, true, attacker, deathReason);
            return true;
        }
        sync();
        return true;
    }

    /**
     * 清空玩家身上的普通护盾（含限时护盾）与弱效护盾。
     *
     * <p>仅供「无视护盾」的伤害在判死前调用：护盾既不会替玩家挡下这次死亡，
     * 也不会在死亡结算里被白白消耗一层（与狙击枪 50 格外穿透的处理一致）。
     */
    private static void clearAllShields(ServerPlayer player) {
        SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(player);
        if (armor.getArmor() > 0) {
            // clearTimedArmor=true：常驻护盾与限时护盾一并清空
            armor.setArmor(0, true);
        }
        SREWeakArmorPlayerComponent weakArmor = SREWeakArmorPlayerComponent.KEY.get(player);
        if (weakArmor.getWeakArmor() > 0) {
            weakArmor.clear();
        }
    }

    /**
     * 扣除虚拟血量但始终保留 1 点，不通过 death reason 使玩家死亡。
     * 用于烟花弩的范围溅射伤害；精确命中目标的击杀由烟花弩自身单独处理。
     * 与 {@link #hurt} 一样<b>先扣虚拟护盾</b>。
     */
    public boolean hurtWithoutKilling(@Nullable ServerPlayer attacker, int damage) {
        if (!(player instanceof ServerPlayer sp) || damage <= 0) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        int remaining = VirtualShieldComponent.KEY.get(sp).absorbVirtualDamage(damage);
        if (remaining <= 0) {
            return true;
        }
        long gameTime = sp.level().getGameTime();
        int current = getEffectiveHealth(gameTime);
        int appliedDamage = Math.min(remaining, Math.max(0, current - 1));
        if (appliedDamage <= 0) {
            return false;
        }
        baseHealth = current - appliedDamage;
        lastHurtGameTime = gameTime;
        sync();
        return true;
    }

    /**
     * 恢复虚拟血量（护士体系：康复药丸 / 虚拟血量恢复药水效果）。
     *
     * <p>回血后把 {@code lastHurtGameTime} 重置为当前时刻：懒回血基线从「现在」重新起算，
     * 避免旧基线与新基准值叠加导致多算；回满时清零进入「默认满血态」省同步。
     *
     * @param amount 恢复量，至少 1 点
     * @return 是否实际恢复了血量（已满或非服务端玩家时为 false）
     */
    public boolean restore(int amount) {
        if (!(player instanceof ServerPlayer sp) || amount <= 0) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        long gameTime = sp.level().getGameTime();
        int current = getEffectiveHealth(gameTime);
        int max = maxHealth();
        if (current >= max) {
            return false;
        }
        baseHealth = Math.min(max, current + amount);
        if (baseHealth >= max) {
            lastHurtGameTime = 0;
        } else {
            lastHurtGameTime = gameTime;
        }
        sync();
        return true;
    }

    /**
     * 读取当前虚拟血量（按游戏时间推算后的实际值）。
     */
    public int currentHealth() {
        if (player == null) {
            return maxHealth();
        }
        return getEffectiveHealth(player.level().getGameTime());
    }

    /**
     * 直接增加 / 减少虚拟血量，结果夹在 {@code [0, 上限]}，<b>不触发死亡判定</b>。
     *
     * @param delta 变化量，正数为增加、负数为减少
     * @return 变化后的血量；未生效（非服务端玩家 / 玩家不存活）时返回 -1
     */
    public int addHealth(int delta) {
        if (!(player instanceof ServerPlayer sp) || !GameUtils.isPlayerAliveAndSurvival(sp)) {
            return -1;
        }
        long gameTime = sp.level().getGameTime();
        return applyHealth(getEffectiveHealth(gameTime) + delta, gameTime);
    }

    /**
     * 直接设置虚拟血量，结果夹在 {@code [0, 上限]}，<b>不触发死亡判定</b>。
     *
     * @param value 目标血量
     * @return 设置后的血量；未生效（非服务端玩家 / 玩家不存活）时返回 -1
     */
    public int setHealth(int value) {
        if (!(player instanceof ServerPlayer sp) || !GameUtils.isPlayerAliveAndSurvival(sp)) {
            return -1;
        }
        return applyHealth(value, sp.level().getGameTime());
    }

    /** 写入血量并重置懒回血基线（与 {@link #restore(int)} 保持一致的处理）。 */
    private int applyHealth(int value, long gameTime) {
        int max = maxHealth();
        int next = Mth.clamp(value, 0, max);
        baseHealth = next;
        if (next >= max) {
            lastHurtGameTime = 0; // 回满：进入默认满血态
        } else {
            lastHurtGameTime = gameTime;
        }
        sync();
        return next;
    }

    // ── NBT 同步 ───────────────────────────────────────────────

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        // 默认满血态写空包：CCA 开始追踪实体时会对全部组件自动同步，
        // 本组件挂在所有玩家身上，省掉默认字段能把这类包压到近乎空载。
        if (baseHealth != maxHealth()) {
            tag.putInt("baseHealth", baseHealth);
        }
        if (lastHurtGameTime != 0) {
            tag.putLong("lastHurt", lastHurtGameTime);
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        baseHealth = RoleComponent.getIntTagOrDefault(tag, "baseHealth", maxHealth());
        lastHurtGameTime = RoleComponent.getLongTagOrDefault(tag, "lastHurt", 0);
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider lookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider lookup) {
    }
}
