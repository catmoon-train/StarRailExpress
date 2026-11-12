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

package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机模式：本身不参与对局，发车时从候选模式池里等概率抽一个真实模式，
 * 由 {@code GameLifecycle.startGame} 把本模式整体替换为抽中的模式，
 * 后续的准备、初始化、结算、存档与 HUD 都直接使用抽中的模式，
 * 因此候选模式特有的网络包、事件与 UI 逻辑都能正常工作。
 *
 * <p>目前有两个实例：
 * <ul>
 * <li>{@code haiman:three_random} 三项随机模式：谋杀、职业轮选、志愿海选；</li>
 * <li>{@code haiman:five_random} 五项随机模式：在三项的基础上再加志愿、阳光自选。</li>
 * </ul>
 *
 * <p>本类继承谋杀模式只是兜底：正常流程不会用它的 {@code initializeGame}，
 * 但若外部代码绕过发车入口直接把它设为本局模式，也不会因为缺少实现而失效。
 */
public class SRERandomGameMode extends SREMurderGameMode {

    private final List<GameMode> candidates;

    /**
     * @param identifier 随机模式自身的 ID（haiman 命名空间）
     * @param candidates 候选的真实模式，必须已在 {@link SREGameModes} 中注册
     */
    public SRERandomGameMode(ResourceLocation identifier, GameMode... candidates) {
        super(identifier, 10,
                Arrays.stream(candidates).mapToInt(mode -> mode.minPlayerCount).min().orElse(3));
        this.candidates = List.of(candidates);
    }

    /** 候选模式池，不可修改。 */
    public List<GameMode> candidates() {
        return candidates;
    }

    /**
     * 按地图模式白名单过滤候选池。白名单为空表示不限制；
     * 返回空列表表示该地图不支持池内任何模式。
     */
    public List<GameMode> candidatesFor(@Nullable List<String> allowedModePaths) {
        if (allowedModePaths == null || allowedModePaths.isEmpty()) {
            return candidates;
        }
        return candidates.stream()
                .filter(mode -> allowedModePaths.contains(mode.identifier.getPath()))
                .toList();
    }

    /**
     * 抽取本局实际进行的模式。优先排除地图不支持、以及当前人数不足的候选；
     * 如果过滤后一个都不剩，则退回完整候选池，由原有的人数校验给出提示。
     */
    public GameMode pick(@Nullable List<String> allowedModePaths, int playerCount) {
        List<GameMode> pool = candidatesFor(allowedModePaths);
        if (playerCount > 0) {
            List<GameMode> playable = pool.stream()
                    .filter(mode -> playerCount >= mode.minPlayerCount)
                    .toList();
            if (!playable.isEmpty()) {
                pool = playable;
            }
        }
        if (pool.isEmpty()) {
            pool = candidates;
        }
        // 候选池为空属于注册错误，退回谋杀模式保证发车流程不中断。
        return pool.isEmpty()
                ? SREGameModes.MURDER
                : pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    /**
     * 发车入口的统一解析：普通模式原样返回，随机模式返回抽中的真实模式。
     *
     * @param allowedModePaths 当前地图声明支持的模式 path 列表，可为空
     * @param playerCount       本局准备参与的玩家数
     */
    public static GameMode resolve(GameMode gameMode, @Nullable List<String> allowedModePaths, int playerCount) {
        return gameMode instanceof SRERandomGameMode randomMode
                ? randomMode.pick(allowedModePaths, playerCount)
                : gameMode;
    }
}
