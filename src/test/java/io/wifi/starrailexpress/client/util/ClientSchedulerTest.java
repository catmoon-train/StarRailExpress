package io.wifi.starrailexpress.client.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ClientSchedulerTest {
    private final List<ClientScheduler.ScheduledTask> tasks = new ArrayList<>();

    private ClientScheduler.ScheduledTask schedule(Runnable action, int delay) {
        var task = ClientScheduler.schedule(action, delay);
        tasks.add(task);
        return task;
    }

    @AfterEach void cleanup() {
        tasks.forEach(ClientScheduler.ScheduledTask::cancel);
        ClientScheduler.tickTasks();
    }

    @Test void callbackCanScheduleAnotherTaskWithoutConcurrentModification() {
        List<String> events = new ArrayList<>();
        schedule(() -> {
            events.add("parent");
            schedule(() -> events.add("child"), 0);
        }, 1);
        assertDoesNotThrow(ClientScheduler::tickTasks);
        assertEquals(List.of("parent"), events);
        ClientScheduler.tickTasks();
        assertEquals(List.of("parent", "child"), events);
        ClientScheduler.tickTasks();
        assertEquals(2, events.size());
    }

    @Test void callbackCanCancelLaterTaskInSameSnapshot() {
        AtomicInteger runs = new AtomicInteger();
        var later = new ClientScheduler.ScheduledTask[1];
        schedule(() -> later[0].cancel(), 1);
        later[0] = schedule(runs::incrementAndGet, 1);
        ClientScheduler.tickTasks();
        ClientScheduler.tickTasks();
        assertEquals(0, runs.get());
    }

    @Test void delaysAndIndependentTasksKeepTheirOriginalTickTiming() {
        List<Integer> events = new ArrayList<>();
        schedule(() -> events.add(3), 3);
        schedule(() -> events.add(1), 1);
        ClientScheduler.tickTasks();
        assertEquals(List.of(1), events);
        ClientScheduler.tickTasks();
        assertEquals(List.of(1), events);
        ClientScheduler.tickTasks();
        assertEquals(List.of(1, 3), events);
    }

    @Test void reentrantTaskCannotRunItselfTwice() {
        AtomicInteger runs = new AtomicInteger();
        var task = new ClientScheduler.ScheduledTask[1];
        task[0] = schedule(() -> {
            runs.incrementAndGet();
            assertTrue(task[0].tick());
        }, 1);
        ClientScheduler.tickTasks();
        assertEquals(1, runs.get());
    }

    @Test void throwingCallbackDoesNotRepeatOnTheNextTick() {
        AtomicInteger runs = new AtomicInteger();
        schedule(() -> {
            runs.incrementAndGet();
            throw new IllegalStateException("test callback");
        }, 1);
        assertThrows(IllegalStateException.class, ClientScheduler::tickTasks);
        assertDoesNotThrow(ClientScheduler::tickTasks);
        assertEquals(1, runs.get());
    }
}
