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

package org.agmas.noellesroles.game.modes.fourthroom.effect;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for accumulating game action results: state changes, effects, and messages.
 * Server-side methods produce an ActionReport; the effects list is broadcast to clients.
 */
public final class ActionReport {

    private final List<EffectEvent> effects = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();
    private boolean success = true;

    private ActionReport() {}

    public static ActionReport create() {
        return new ActionReport();
    }

    public ActionReport effect(EffectEvent event) {
        effects.add(event);
        return this;
    }

    public ActionReport message(String msg) {
        messages.add(msg);
        return this;
    }

    public ActionReport fail(String msg) {
        success = false;
        messages.add(msg);
        return this;
    }

    public boolean isSuccess() {
        return success;
    }

    public List<EffectEvent> effects() {
        return effects;
    }

    public List<String> messages() {
        return messages;
    }
}
