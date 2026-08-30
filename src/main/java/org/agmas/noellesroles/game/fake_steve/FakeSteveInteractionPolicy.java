package org.agmas.noellesroles.game.fake_steve;

import io.wifi.starrailexpress.cca.SREPlayerTaskComponent.Task;

/** Observable interaction rules kept separate from Minecraft packet plumbing. */
public final class FakeSteveInteractionPolicy {
    private FakeSteveInteractionPolicy() {
    }

    public static double maxInteractionDistance(Task task) {
        return task == Task.CHAIR || task == Task.TOILET ? 1.4D : 2.75D;
    }

    public static boolean maintainsUseAnimation(Task task) {
        return task == Task.EAT || task == Task.DRINK;
    }

    public static boolean swingsHand(Task task) {
        return task != null;
    }

    public static boolean releasesPostureAfterCompletion(Task task) {
        return task == Task.CHAIR || task == Task.TOILET || task == Task.SLEEP
                || task == Task.RAED_BOOK;
    }
}
