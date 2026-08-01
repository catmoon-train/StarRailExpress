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

package io.wifi.starrailexpress.client.util;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.concurrent.CopyOnWriteArrayList;

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
