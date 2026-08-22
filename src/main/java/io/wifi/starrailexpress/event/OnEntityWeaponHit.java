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

package io.wifi.starrailexpress.event;

import io.wifi.starrailexpress.api.hit.HitType;
import io.wifi.starrailexpress.api.hit.SREHitManager;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 玩家用枪 / 刀 / 狙击命中非玩家实体时触发。
 *
 * <p>所有监听器都会被调用。任意监听器返回 {@code true} 即视为已处理
 * （与实体自身的 {@link io.wifi.starrailexpress.api.hit.IsTargetObject#onWeaponHit}
 * 效果叠加）。用于无法改实体类、或要在命中时追加额外效果的场合。
 *
 * <p>瞄准拾取请用 {@link SREHitManager#getTarget} / {@link SREHitManager#registerTargetFilter}，
 * 本事件只负责服务端结算。
 */
public interface OnEntityWeaponHit {

    Event<OnEntityWeaponHit> EVENT = createArrayBacked(OnEntityWeaponHit.class,
            listeners -> (attacker, target, type) -> {
                boolean handled = false;
                for (OnEntityWeaponHit listener : listeners) {
                    if (listener.onHit(attacker, target, type)) {
                        handled = true;
                    }
                }
                return handled;
            });

    /**
     * @param attacker 攻击者
     * @param target   被命中的非玩家实体
     * @param type     武器类型
     * @return {@code true} 表示已处理该次命中
     */
    boolean onHit(Player attacker, Entity target, HitType type);
}
