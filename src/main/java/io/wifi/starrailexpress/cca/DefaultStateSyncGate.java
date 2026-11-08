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

package io.wifi.starrailexpress.cca;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 「默认态不发包」的同步门控。
 *
 * <p>
 * CCA 在两种时机对<b>每一个</b>组件无条件调用
 * {@link org.ladysnake.cca.api.v3.component.ComponentKey#syncWith}：
 * 实体开始被某位玩家追踪（{@code TrackingStartCallback}）与玩家自身登录 / 重生
 * （{@code PlayerSyncCallback}）。{@code shouldSyncWith} 恒为 {@code true} 的组件
 * 因此会发出大量「负载等于默认值」的空包（数量级接近 玩家数 × 观察者数）。
 *
 * <p>
 * 本类把「默认态不发给观察者」这件事收敛成一份可复用的记录表：
 * <ul>
 * <li>本人（组件持有者自己）<b>恒同步</b>，绝不门控 —— 自己的 HUD 永远拿到完整数据；</li>
 * <li>其它观察者在状态<b>非默认</b>时照常同步，并记入 {@link #syncedViewers}；</li>
 * <li>状态回落默认时，只向记录里的观察者补发<b>一次</b>空包（让他们把客户端数据复位），
 * 随后把该观察者移出记录，之后不再发包。</li>
 * </ul>
 *
 * <p>
 * 使用约定（与 {@code SREArmorPlayerComponent} 的写法一致）：
 * <ol>
 * <li>{@link #shouldSyncWith} 在组件的 {@code shouldSyncWith} 里调用；</li>
 * <li>{@link #recordSent} 在写包方法（{@code writeSyncPacket} /
 * {@code writeToSyncNbtWithPlayer}）里调用，因为只有那里能拿到 recipient；</li>
 * <li>把状态复位成默认值并 {@code sync()} 之后调用 {@link #clear()}，清掉过期记录。
 * <b>顺序不能反</b>：先 {@code sync()} 补发空包，再 {@code clear()}，
 * 否则观察者会永久保留旧数据。</li>
 * </ol>
 *
 * <p>
 * 仅在服务端有意义；客户端上记录的集合始终为空，不影响任何逻辑。
 */
public final class DefaultStateSyncGate {
    /** 组件持有者本人（恒同步，不参与门控）。 */
    private final Player holder;
    /**
     * 已经收到过「非默认态」数据的观察者。
     *
     * <p>
     * 只有集合里的观察者才会在状态回落默认时额外收到一次复位包；
     * 从未见过非默认数据的观察者（例如刚登录 / 刚进入追踪范围）客户端本来就是默认值，
     * 直接跳过即可。
     */
    private final Set<UUID> syncedViewers = new HashSet<>();

    /**
     * @param holder 组件持有者，用于识别「本人」这一特例
     */
    public DefaultStateSyncGate(@NotNull Player holder) {
        this.holder = holder;
    }

    /**
     * 是否应该向该观察者同步。
     *
     * @param recipient    观察者
     * @param defaultState 组件当前是否处于默认态（无任何需要广播的数据）
     * @return 本人恒为 {@code true}；非默认态恒为 {@code true}；
     *         默认态下只有拿到过非默认数据的观察者才为 {@code true}
     */
    public boolean shouldSyncWith(@NotNull ServerPlayer recipient, boolean defaultState) {
        if (isHolder(recipient)) {
            return true;
        }
        return !defaultState || this.syncedViewers.contains(recipient.getUUID());
    }

    /**
     * 记录「本次已经向该观察者发了包」，必须在真正写包之后调用。
     *
     * @param recipient    本次的接收者
     * @param defaultState 本次写入的是否为默认态数据（即空负载 / 复位包）
     */
    public void recordSent(@NotNull ServerPlayer recipient, boolean defaultState) {
        if (isHolder(recipient)) {
            // 本人永远同步，不需要记录
            return;
        }
        if (defaultState) {
            // 复位包已送达：该观察者客户端回到默认值，以后不用再发
            this.syncedViewers.remove(recipient.getUUID());
        } else {
            this.syncedViewers.add(recipient.getUUID());
        }
    }

    /** 当前是否有观察者手里拿着非默认数据（复位时必须给他们补发一次包）。 */
    public boolean hasSyncedViewers() {
        return !this.syncedViewers.isEmpty();
    }

    /** 丢掉全部记录（状态已经复位并补发过空包之后调用）。 */
    public void clear() {
        this.syncedViewers.clear();
    }

    /**
     * 该接收者是否就是组件持有者本人。
     *
     * <p>
     * 除了引用比较，再按 UUID 兜底：玩家跨维度切换 / 重生后实体实例会换，
     * 但 UUID 不变，本人依旧要拿到完整数据。
     */
    private boolean isHolder(@NotNull ServerPlayer recipient) {
        return recipient == this.holder
                || (this.holder != null && recipient.getUUID().equals(this.holder.getUUID()));
    }
}
