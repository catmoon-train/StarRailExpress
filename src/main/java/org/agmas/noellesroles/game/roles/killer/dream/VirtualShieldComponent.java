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
import io.wifi.starrailexpress.event.OnGameEnd;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.events.GameInitializeEvent;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

/**
 * 虚拟护盾组件 —— 挂在<b>所有玩家</b>身上（与 {@link DreamHealthComponent} 一致）。
 *
 * <p>
 * 虚拟护盾是挂在<b>虚拟血量</b>前面的第二层血条：来源为「虚拟护盾试剂」
 * （{@code starrailexpress:virtual_defense_vial}，可放入食物盘 / 饮料盘，
 * 也可被亡命徒等职业直接饮用）。它<b>只抵挡虚拟血量伤害</b>
 * （即所有走 {@link DreamHealthComponent#hurt} / {@link #hurtWithoutKilling} 的伤害），
 * <b>完全不影响正常死亡</b>（原版血量归零、环境伤害、摔死等一律照旧）。
 *
 * <p>
 * 结算顺序：受到虚拟血量伤害时<b>优先扣虚拟护盾</b>，护盾清空后剩余伤害才落到虚拟血量上
 * （见 {@link DreamHealthComponent#hurt}）。
 *
 * <p>
 * 与虚拟血量一样，本组件<b>不写入玩家存档</b>，只在会话内通过 CCA 同步；
 * 开局 / 结束时自动重置为 0。
 */
public class VirtualShieldComponent implements RoleComponent {
    public static final ComponentKey<VirtualShieldComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "virtual_shield"),
            VirtualShieldComponent.class);

    /** 虚拟护盾试剂提供的默认护盾值。 */
    public static final int DEFAULT_SHIELD = 20;

    static {
        GameInitializeEvent.EVENT.register((serverLevel, gameWorldComponent, players) -> {
            for (ServerPlayer p : serverLevel.getServer().getPlayerList().getPlayers()) {
                KEY.get(p).init();
            }
        });
        OnGameEnd.EVENT.register((serverLevel, gameWorldComponent) -> {
            for (ServerPlayer p : serverLevel.getServer().getPlayerList().getPlayers()) {
                KEY.get(p).init();
            }
        });
    }

    private final Player player;
    /** 当前剩余虚拟护盾值；0 = 没有护盾（此时 HUD 不渲染护盾条）。 */
    public int shield;

    public VirtualShieldComponent(Player player) {
        this.player = player;
        this.shield = 0;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        // 护盾条与虚拟血量条共用同一套可见性门禁（护士 / canUseSpVanillaWeapon），
        // 但数据本身仍需对全员同步：可见性判定在客户端做。
        return true;
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void init() {
        if (shield == 0) {
            return;
        }
        shield = 0;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    /** 当前护盾值（夹在 [0, 上限]）。 */
    public int currentShield() {
        return Mth.clamp(shield, 0, Integer.MAX_VALUE);
    }

    /**
     * 增加虚拟护盾（虚拟护盾试剂 / 指令）。
     *
     * @param amount 增加量，至少 1 点
     * @return 增加后的护盾值；未生效（非服务端玩家 / 玩家不存活）时返回 -1
     */
    public int addShield(int amount) {
        if (!(player instanceof ServerPlayer sp) || amount <= 0) {
            return -1;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return -1;
        }
        return applyShield(currentShield() + amount);
    }

    /** 直接设置虚拟护盾（不生效于不存活 / 非服务端玩家）。 */
    public int setShield(int value) {
        if (!(player instanceof ServerPlayer sp) || !GameUtils.isPlayerAliveAndSurvival(sp)) {
            return -1;
        }
        return applyShield(value);
    }

    /** 清空虚拟护盾（0 时 HUD 不渲染护盾条）。 */
    public int clearShield() {
        return setShield(0);
    }

    private int applyShield(int value) {
        int next = Math.max(0, value);
        if (next == shield) {
            return shield;
        }
        shield = next;
        sync();
        return shield;
    }

    /**
     * 结算虚拟血量伤害时<b>优先</b>扣虚拟护盾，返回真正落到虚拟血量上的伤害。
     *
     * @param damage 本次虚拟血量伤害
     * @return 扣完护盾后剩余的伤害（护盾足够时为 0）
     */
    public int absorbVirtualDamage(int damage) {
        if (damage <= 0 || !(player instanceof ServerPlayer)) {
            return damage;
        }
        int current = currentShield();
        if (current <= 0) {
            return damage;
        }
        int absorbed = Math.min(current, damage);
        shield = current - absorbed;
        sync();
        return damage - absorbed;
    }

    // ── NBT 同步 ───────────────────────────────────────────────

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        // 默认无盾态写空包：CCA 开始追踪实体时会对全部组件自动同步，省掉默认字段能把这类包压到近乎空载。
        if (shield != 0) {
            tag.putInt("shield", shield);
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        shield = RoleComponent.getIntTagOrDefault(tag, "shield", 0);
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider lookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider lookup) {
    }
}