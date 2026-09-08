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

package org.agmas.noellesroles.game.fake_steve;

/** Pure observation state machine shared by client rendering and server tests. */
public final class FakeSteveApparitionLifecycle {
    public static final int OBSERVE_TICKS = 5;
    public static final int LOOK_AWAY_TICKS = 3;
    public static final int TIMEOUT_TICKS = 30 * 20;

    public enum Stage {
        UNSEEN,
        OBSERVED,
        LOOKED_AWAY,
        TIMED_OUT
    }

    private Stage stage = Stage.UNSEEN;
    private int ageTicks;
    private int visibleTicks;
    private int lostTicks;

    public Stage tick(boolean visible, int elapsedTicks) {
        if (terminal()) {
            return stage;
        }
        int elapsed = Math.max(0, elapsedTicks);
        ageTicks += elapsed;
        if (ageTicks >= TIMEOUT_TICKS) {
            stage = Stage.TIMED_OUT;
            return stage;
        }
        if (stage == Stage.UNSEEN) {
            visibleTicks = visible ? visibleTicks + elapsed : 0;
            if (visibleTicks >= OBSERVE_TICKS) {
                stage = Stage.OBSERVED;
            }
            return stage;
        }
        lostTicks = visible ? 0 : lostTicks + elapsed;
        if (lostTicks >= LOOK_AWAY_TICKS) {
            stage = Stage.LOOKED_AWAY;
        }
        return stage;
    }

    public Stage stage() {
        return stage;
    }

    public boolean shouldReplace() {
        return stage == Stage.LOOKED_AWAY;
    }

    public boolean terminal() {
        return stage == Stage.LOOKED_AWAY || stage == Stage.TIMED_OUT;
    }
}
