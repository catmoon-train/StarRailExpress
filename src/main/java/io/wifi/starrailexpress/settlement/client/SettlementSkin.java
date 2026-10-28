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

package io.wifi.starrailexpress.settlement.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * 结算演出的「皮肤」扩展点（纯客户端）。
 *
 * <p>本体只负责与胜负、统计、网络、舞台方块、HUD 有关的中性骨架；
 * 某款 MVP 皮肤想额外叠加的布景（例如背景、天象、专属音效、镜头构图偏移）
 * 全部通过实现本接口提供，由附属模组注册。本体不再出现任何具体皮肤 ID。
 *
 * <p><b>为什么是纯客户端：</b>服务端只需要把「本局 MVP 用的是哪个皮肤 ID」
 * 写进冻结快照（见 {@code SettlementSnapshot#mvpSkin()}），
 * 皮肤的具体表现与资源都在客户端，服务端不需要认识它们。
 *
 * <p><b>实现约束：</b>
 * <ul>
 * <li>所有回调都在客户端主线程、渲染线程或客户端 tick 中调用；</li>
 * <li>回调必须可重入安全且不做阻塞 IO，异常会被调用方捕获并记录，
 * 不会中断整场结算；</li>
 * <li>布景只画在舞台坐标系内（原点 = 舞台地板中心），
 * 不要假设相机位置，也不要创建真实实体加入世界。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public interface SettlementSkin {

    /**
     * 皮肤 ID，必须与 {@code ItemSkinManager} 里注册的
     * {@code SkinTypes.MVP_ANIMATION} 皮肤 ID 完全一致。
     */
    String id();

    /**
     * 演出开场时在舞台坐标系里绘制背景布景（天象、远景等）。
     *
     * <p>调用时机在角色之前，{@code pose} 的原点已平移到舞台地板中心。
     *
     * @param pose    舞台坐标系，原点 = 舞台地板中心，单位 = 方块
     * @param buffers 顶点缓冲
     * @param time    演出已进行的 tick（含帧间插值）
     */
    default void renderBackdrop(PoseStack pose, MultiBufferSource buffers, double time) {
    }

    /**
     * 每名获胜者结算时的额外特效（光环、光束等）。
     *
     * @param order 该获胜者的站位序号，可用于错开相位
     */
    default void renderWinnerEffect(PoseStack pose, MultiBufferSource buffers,
            double x, double y, double z, double time, int order) {
    }

    /**
     * 镜头构图偏移（格）。本体把返回值叠加到默认机位的抬升量上，
     * 用于给高空布景让出画面。
     *
     * @param time 演出已进行的 tick
     * @return 向上抬升的格数，{@code 0} 表示不影响
     */
    default double cameraLift(double time) {
        return 0.0D;
    }

    /**
     * 在指定 tick 触发的专属音效。
     *
     * <p>本体只在 {@code tick == triggerTick} 时调用一次；实现自行决定播放什么。
     *
     * @return 本皮肤在此 tick 是否有专属音效
     */
    default boolean cueSound(int tick) {
        return false;
    }

    /**
     * 舞台方块覆盖：允许皮肤替换某个坐标上的方块。
     *
     * <p>返回 {@code null} 表示「不接管，用本体默认摆放」。
     * 坐标是相对舞台地板中心的偏移，与 {@code SettlementStage} 内部一致。
     *
     * @param x 相对舞台中心的 X（-16..16）
     * @param y 相对舞台地板的 Y（0..14）
     * @param z 相对舞台中心的 Z（-15..15）
     * @return 要放置的方块状态，或 {@code null} 表示用默认值
     */
    default net.minecraft.world.level.block.state.BlockState blockAt(int x, int y, int z) {
        return null;
    }
}
