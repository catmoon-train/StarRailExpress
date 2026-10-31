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
      private volatile boolean cancelled = false;

      public ScheduledTask(int delayTicks, Runnable action) {
         this.ticksLeft = delayTicks;
         this.action = action;
      }

      public boolean tick() {
         if (this.cancelled) {
            return true;
         } else if (--this.ticksLeft <= 0) {
            // 先完成再调用，回调重入或抛错都不能让同一任务再次执行。
            this.cancelled = true;
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

   /** 快照迭代允许回调新增任务；不能在 removeIf 的谓词内执行用户回调。 */
   static void tickTasks() {
      for (ScheduledTask task : TASKS) {
         if (task.tick()) TASKS.remove(task);
      }
   }

   public static void init() {
      ClientTickEvents.END_WORLD_TICK.register(client -> tickTasks());
   }
}
