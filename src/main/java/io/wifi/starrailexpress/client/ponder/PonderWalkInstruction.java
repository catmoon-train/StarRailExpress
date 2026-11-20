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

package io.wifi.starrailexpress.client.ponder;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.minecraft.world.phys.Vec3;

/**
 * 走动指令：让玩家模型在 {@code totalTicks} 个 tick 内走到目标点，并像 {@code idle} 一样占住时间轴。
 *
 * <p>位移由指令自己的 tick 推进（{@link TickingInstruction} 与 {@code idle} 用的是同一套调度），
 * 不依赖元素 tick，所以只要时间轴在走，模型就一定在走。
 */
public class PonderWalkInstruction extends TickingInstruction {

    private final PonderPlayerElement element;
    private final Vec3 target;

    public PonderWalkInstruction(PonderPlayerElement element, Vec3 target, int ticks) {
        super(true, Math.max(1, ticks));
        this.element = element;
        this.target = target;
    }

    @Override
    protected void firstTick(PonderScene scene) {
        element.beginWalk(target, totalTicks);
    }

    @Override
    public void tick(PonderScene scene) {
        super.tick(scene);
        element.setWalkProgress(1.0F - remainingTicks / (float) totalTicks);
    }
}
