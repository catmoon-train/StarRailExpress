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
