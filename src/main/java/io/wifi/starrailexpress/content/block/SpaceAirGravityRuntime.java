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

package io.wifi.starrailexpress.content.block;

import io.wifi.starrailexpress.SRE;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 太空空气（{@link SpaceAirBlock}）的重力结算中心。
 *
 * <p>
 * 流程：生物 tick 时原版会调用方块的 {@code entityInside}，那里只做「记录」，把实体与档位塞进
 * {@link #PENDING}；随后每个服务端 tick 末尾（{@code END_SERVER_TICK}，此时所有维度的实体都已经
 * tick 完）由 {@link #flush()} 统一结算一次。这样每个实体每 tick 最多只会写一次属性，
 * 而且离开方块 / 方块被破坏 / 断线都能在下一 tick 自动恢复，不需要额外清理入口。
 *
 * <p>
 * 几点约定：
 * <ul>
 * <li>modifier 用独立的 id（{@code starrailexpress:space_air_gravity}），不能复用地图重力的
 * {@code SRE.id("map")}——那套用的是 {@code addOrReplacePermanentModifier}，同 id 会互相覆盖。</li>
 * <li>操作用 {@code ADD_MULTIPLIED_TOTAL}（百分比），与地图重力的 {@code ADD_VALUE} 是叠乘关系。</li>
 * <li>同一 tick 内身处多个太空空气时取<b>绝对值最大</b>的一档（效果最强者优先）。</li>
 * <li>数值为 0（无效果档位）时不留 modifier，避免白刷属性同步包。</li>
 * <li>用 transient modifier（不入存档）：死亡、跨维度、重连后天然失效，且只要还在方块里下一 tick
 * 就会重新加上，所以不需要做持久化或补挂。</li>
 * </ul>
 */
public final class SpaceAirGravityRuntime {
    /** 太空空气自身专用的 modifier id，避免与地图重力的 {@code SRE.id("map")} 撞车。 */
    private static final ResourceLocation MODIFIER_ID = SRE.id("space_air_gravity");

    /** 本 tick 检测到的「实体 → 档位」。只被 {@link #markInside} 写、被 {@link #flush()} 读并清空。 */
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    /** 当前挂着 modifier 的实体，用于离开方块后回收。 */
    private static final Map<UUID, LivingEntity> TRACKED = new HashMap<>();

    private SpaceAirGravityRuntime() {
    }

    /**
     * 记录一个"身处太空空气"的生物。由 {@link SpaceAirBlock#entityInside} 在服务端调用。
     *
     * @param entity 处于方块内的生物
     * @param value  档位对应的百分比加成，见 {@link SpaceAirBlock#modifierFor(int)}
     */
    public static void markInside(LivingEntity entity, float value) {
        Pending pending = PENDING.get(entity.getUUID());
        if (pending == null) {
            PENDING.put(entity.getUUID(), new Pending(entity, value));
        } else if (Math.abs(value) > Math.abs(pending.value)) {
            pending.value = value;
        }
    }

    /** 每服务端 tick 末尾结算一次：写入/更新本 tick 身处方块的生物的重力，回收已离开的。 */
    public static void flush() {
        try {
            for (Pending pending : PENDING.values()) {
                AttributeInstance attribute = pending.entity.getAttribute(Attributes.GRAVITY);
                if (attribute == null) {
                    continue;
                }
                if (pending.value == 0.0F) {
                    // 无效果档位：不留 modifier，也就不用跟踪
                    if (attribute.hasModifier(MODIFIER_ID)) {
                        attribute.removeModifier(MODIFIER_ID);
                    }
                    continue;
                }
                AttributeModifier current = attribute.getModifier(MODIFIER_ID);
                if (current == null) {
                    attribute.addTransientModifier(new AttributeModifier(MODIFIER_ID, pending.value,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                } else if (current.amount() != pending.value) {
                    // 数值没变就不动，免得每 tick 都把属性标脏、白发包
                    attribute.removeModifier(MODIFIER_ID);
                    attribute.addTransientModifier(new AttributeModifier(MODIFIER_ID, pending.value,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
                TRACKED.put(pending.entity.getUUID(), pending.entity);
            }
            // 本 tick 没被刷新的：已离开方块 / 方块被破坏 / 已断线，恢复原重力
            Iterator<Map.Entry<UUID, LivingEntity>> iterator = TRACKED.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, LivingEntity> entry = iterator.next();
                if (PENDING.containsKey(entry.getKey())) {
                    continue;
                }
                AttributeInstance attribute = entry.getValue().getAttribute(Attributes.GRAVITY);
                if (attribute != null) {
                    attribute.removeModifier(MODIFIER_ID);
                }
                iterator.remove();
            }
        } finally {
            // 无论结算过程中发生什么，记录都必须清空：留着上一条会让实体永久不落地
            PENDING.clear();
        }
    }

    /** 单个生物的待结算记录。{@code value} 可变，便于同一 tick 内替换成更强的一档。 */
    private static final class Pending {
        private final LivingEntity entity;
        private float value;

        private Pending(LivingEntity entity, float value) {
            this.entity = entity;
            this.value = value;
        }
    }
}
