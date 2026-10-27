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

package org.agmas.noellesroles.content.effects;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 重力颠倒。
 *
 * <p>只做<b>垂直方向</b>的重力反向：你会向天空「坠落」，撞到头顶的方块才算落地，跳跃改为向下蹬。
 *
 * <p>效果本身没有 tick 逻辑，纯标记；各层由以下 mixin 消费：
 * <ul>
 *     <li>{@code GravityFlipPhysicsMixin}：重力取反 + 撞天花板视为落地</li>
 *     <li>{@code GravityFlipJumpMixin}：跳跃方向反转</li>
 *     <li>{@code GravityFlipCameraMixin}：世界投影矩阵旋转 180°，视野整体倒转（手部不受影响）</li>
 *     <li>{@code GravityFlipEyeMixin}：眼位改从碰撞箱顶部量，相机下沉到倒立模型的头部</li>
 *     <li>{@code GravityFlipInputMixin} / {@code GravityFlipMouseMixin}：
 *     取反左右移动与鼠标两轴，抵消滚转，使操作相对画面恢复正常（世界依旧上下颠倒）</li>
 * </ul>
 * 模型倒立在 {@code LivingEntityRendererMixin#sre$isEntityUpsideDown} 里统一处理。
 *
 * <p>两条触发路径：
 * <ul>
 *     <li>药水效果：创造模式免疫；</li>
 *     <li>反重力方块：不经过药水（避免被清除），由 {@link GravityFlipManager} 在双端维护，
 *     冒险/创造模式均生效，死亡、退出冒险/创造、游戏结束/开始时自动解除。</li>
 * </ul>
 */
public class GravityFlipEffect extends SimpleMobEffect {

    public GravityFlipEffect() {
        super(MobEffectCategory.HARMFUL, 0x7B4FE0);
    }

    /**
     * 是否处于重力颠倒。
     *
     * <p>这是反重力<b>唯一的开关</b>：三个注入点全部以它为闸门，因此效果一旦结束（或玩家死亡、
     * 变旁观者、被移除）就会立刻全部失效，且不会残留任何状态。
     */
    public static boolean isFlipped(Entity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        // 死亡 / 实体已移除：立即失效，不等效果自然到时
        if (player.isRemoved() || !player.isAlive()) {
            return false;
        }
        if (player.isSpectator()) {
            return false;
        }
        // 药水效果路径：创造模式免疫
        if (player.hasEffect(ModEffects.GRAVITY_FLIP) && !player.isCreative()) {
            return true;
        }
        // 反重力方块路径：由管理器在双端维护（冒险/创造均生效，含清理规则）
        return GravityFlipManager.isFlipped(player);
    }
}
