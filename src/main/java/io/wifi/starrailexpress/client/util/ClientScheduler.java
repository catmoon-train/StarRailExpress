package io.wifi.starrailexpress.client.util;

import java.util.concurrent.CopyOnWriteArrayList;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class ClientScheduler {
   private static final CopyOnWriteArrayList<ScheduledTask> TASKS = new CopyOnWriteArrayList<ScheduledTask>();

   public static ScheduledTask schedule(Runnable action, int delayTicks) {
      ScheduledTask task = new ScheduledTask(delayTicks, action);
      TASKS.add(task);
      return task;
   }

   public static class ScheduledTask {
      private int ticksLeft;
      private final Runnable action;
      private boolean cancelled = false;

      public ScheduledTask(int delayTicks, Runnable action) {
         this.ticksLeft = delayTicks;
         this.action = action;
      }

      public boolean tick() {
         if (this.cancelled) {
            return true;
         } else if (--this.ticksLeft <= 0) {
            this.action.run();
            return true;
         } else {
            return false;
         }
      }

      public void cancel() {
         this.cancelled = true;
      }
   }

   public static void init() {
      ClientTickEvents.END_WORLD_TICK
            .register((client) -> TASKS.removeIf(ScheduledTask::tick));
   }
}
