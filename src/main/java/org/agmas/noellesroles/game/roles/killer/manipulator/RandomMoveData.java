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

package org.agmas.noellesroles.game.roles.killer.manipulator;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class RandomMoveData {
    // 随机移动配置
    public static final int MIN_MOVE_TICKS = 40; // 最短移动时间
    public static final int MAX_MOVE_TICKS = 120; // 最长移动时间
    public static final float MOVE_SPEED = 0.1f; // 移动速度
    public static final float TURN_CHANCE = 0.02f; // 每tick转向概率
    // 存储玩家的随机移动状态
    public static final Map<ServerPlayer, RandomMoveData> randomMoveData = new HashMap<>();
    public static final Random random = new Random();
    public int moveTicksRemaining; // 剩余移动时间
    public Vec3 moveDirection; // 移动方向
    public int currentMoveDuration; // 当前移动持续时间

    public RandomMoveData() {
        startNewMove();
    }

    public void startNewMove() {
        // 随机选择移动时间
        currentMoveDuration = MIN_MOVE_TICKS + random.nextInt(MAX_MOVE_TICKS - MIN_MOVE_TICKS);
        moveTicksRemaining = currentMoveDuration;

        // 随机选择方向
        float angle = random.nextFloat() * (float) Math.PI * 2;
        moveDirection = new Vec3(
                Math.sin(angle) * MOVE_SPEED,
                0,
                Math.cos(angle) * MOVE_SPEED);
    }

    public void tick() {
        moveTicksRemaining--;
        if (moveTicksRemaining <= 0) {
            startNewMove();
        }
    }

    public boolean shouldTurn() {
        return random.nextFloat() < TURN_CHANCE;
    }
}