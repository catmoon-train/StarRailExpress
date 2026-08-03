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
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.events.GameInitializeEvent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

/**
 * Dream（梦魇）虚拟血量组件 —— 挂在<b>所有玩家</b>身上。
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

    static {
        // 开局重置所有玩家的虚拟血量（本组件不绑定职业 componentKey，自行挂开局事件）
        GameInitializeEvent.EVENT.register((serverLevel, gameWorldComponent, players) -> {
            for (ServerPlayer p : serverLevel.getServer().getPlayerList().getPlayers()) {
                KEY.get(p).initWhenNecessary();
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

    /**
     * 按游戏时间推算当前血量：脱战 30s 后每秒恢复 1 点，直至回满。
     */
    public int getEffectiveHealth(long gameTime) {
        int max = maxHealth();
        if (baseHealth >= max || lastHurtGameTime <= 0) {
            return Math.min(baseHealth, max);
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
     * 被 Dream 的铁斧命中：扣虚拟血量，归零则判死并归属攻击者。
     *
     * @return 是否实际造成了伤害
     */
    public boolean hurt(@Nullable ServerPlayer attacker, int damage, ResourceLocation deathReason) {
        if (!(player instanceof ServerPlayer sp) || damage <= 0) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        long gameTime = sp.level().getGameTime();
        int current = getEffectiveHealth(gameTime);
        baseHealth = current - damage;
        lastHurtGameTime = gameTime;
        if (baseHealth <= 0) {
            baseHealth = 0;
            sync();
            GameUtils.killPlayer(sp, true, attacker, deathReason);
            return true;
        }
        sync();
        return true;
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
