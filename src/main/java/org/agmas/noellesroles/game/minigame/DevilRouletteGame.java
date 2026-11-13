/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.agmas.noellesroles.game.minigame;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.minigame.roulette.DevilRouletteRound;
import org.agmas.noellesroles.game.minigame.roulette.RouletteItem;
import org.agmas.noellesroles.game.minigame.roulette.RouletteItems;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.random.RandomGenerator;

/** 赌桌与纯规则之间的薄适配；物品、动画和外层比赛结算均由赌桌统一调度。 */
public final class DevilRouletteGame {
    public static final int MAX_HEALTH = org.agmas.noellesroles.game.minigame.roulette.RouletteBalance.HEALTH;
    public enum GameMode { Lobby, Roulette }
    private final ArrayList<UUID> players;
    private final RandomSource random;
    private final Level level;
    private DevilRouletteRound round;
    private GameMode mode = GameMode.Lobby;

    public DevilRouletteGame(ArrayList<UUID> players, RandomSource random, Level level) {
        if (players == null || players.size() != 2 || players.contains(null) || players.get(0).equals(players.get(1)))
            throw new IllegalArgumentException("轮盘赌需要两位不同玩家");
        this.players = new ArrayList<>(players);
        this.random = random;
        this.level = level;
    }
    public void init() { round = null; }
    public void setGameMode(GameMode mode) { this.mode = mode; }
    public GameMode getGameMode() { return mode; }
    public boolean start() {
        return start(MAX_HEALTH, MAX_HEALTH);
    }
    public boolean start(int frontMaxHealth, int backMaxHealth) {
        if (level == null || players.stream().anyMatch(id -> level.getPlayerByUUID(id) == null)) return false;
        round = new DevilRouletteRound(players.get(0), players.get(1), new Random(random.nextLong()), frontMaxHealth, backMaxHealth);
        return true;
    }
    public DevilRouletteRound round() { return round; }
    public int getTrueBulletNumber() { return round == null ? 0 : round.loadedLive(); }
    public int getHealth(UUID id) { return round == null ? 0 : round.health(id); }
    public boolean canOperate(UUID id) { return round != null && !round.ended() && round.operator().equals(id); }
    public boolean isGameEnd() { return round == null || round.ended(); }

    /** 单桌专用对局状态：补给不落盘，回调在执行前即作废，允许外层同步重置赌桌。 */
    public static final class DemonMatch {
        private final UUID demon, opponent;
        private final Map<UUID, ArrayDeque<RouletteItem>> pending = new HashMap<>();
        private BiConsumer<UUID, UUID> settlement;
        private boolean initialSupply = true;

        public DemonMatch(UUID demon, UUID opponent, BiConsumer<UUID, UUID> settlement) {
            this.demon = Objects.requireNonNull(demon);
            this.opponent = Objects.requireNonNull(opponent);
            this.settlement = Objects.requireNonNull(settlement);
            if (demon.equals(opponent)) throw new IllegalArgumentException("Participants must differ");
        }

        public void reserve(int amount, int demonCarried, int opponentCarried, RandomGenerator random) {
            if (settlement == null) return;
            if (amount < 2 || amount > 5) throw new IllegalArgumentException("Supply must be between 2 and 5");
            int reduced = org.agmas.noellesroles.game.minigame.roulette.RouletteBalance.supply(amount);
            reserve(demon, demonCarried, reduced + (initialSupply ? 1 : 0), random);
            reserve(opponent, opponentCarried, reduced, random);
            initialSupply = false;
        }

        private void reserve(UUID who, int carried, int requested, RandomGenerator random) {
            int amount = RouletteItems.grantCount(carried, pending(who), requested);
            if (amount == 0) return;
            ArrayDeque<RouletteItem> queue = pending.computeIfAbsent(who, ignored -> new ArrayDeque<>());
            for (int i = 0; i < amount; i++) queue.addLast(RouletteItem.values()[random.nextInt(RouletteItem.values().length)]);
        }

        public int pending(UUID who) {
            ArrayDeque<RouletteItem> queue = pending.get(who);
            return queue == null ? 0 : queue.size();
        }

        public RouletteItem peek(UUID who) {
            ArrayDeque<RouletteItem> queue = pending.get(who);
            return queue == null ? null : queue.peekFirst();
        }

        public void delivered(UUID who) {
            ArrayDeque<RouletteItem> queue = pending.get(who);
            if (queue != null) {
                queue.pollFirst();
                if (queue.isEmpty()) pending.remove(who);
            }
        }

        public boolean settle(UUID winner, UUID loser) {
            if (settlement == null || !(demon.equals(winner) && opponent.equals(loser)
                    || opponent.equals(winner) && demon.equals(loser))) return false;
            BiConsumer<UUID, UUID> callback = settlement;
            reset();
            callback.accept(winner, loser);
            return true;
        }

        public void reset() {
            settlement = null;
            pending.clear();
        }
    }
}
