/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package io.wifi.starrailexpress.traindummy;

import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 假人尸体只存在 30 秒，到时丢掉，不跟普通玩家尸体一起留到局末。
 */
public final class TrainDummyCorpses {

    private static final Map<Integer, Integer> LIFE = new ConcurrentHashMap<>();
    private static boolean registered;

    private TrainDummyCorpses() {
    }

    public static void init() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (LIFE.isEmpty()) {
                return;
            }
            Iterator<Map.Entry<Integer, Integer>> iterator = LIFE.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Integer, Integer> entry = iterator.next();
                int left = entry.getValue() - 1;
                var entity = server.overworld().getEntity(entry.getKey());
                if (entity == null) {
                    for (var level : server.getAllLevels()) {
                        entity = level.getEntity(entry.getKey());
                        if (entity != null) {
                            break;
                        }
                    }
                }
                if (!(entity instanceof PlayerBodyEntity) || left <= 0) {
                    if (entity != null) {
                        entity.discard();
                    }
                    iterator.remove();
                } else {
                    entry.setValue(left);
                }
            }
        });
    }

    public static void track(PlayerBodyEntity body, int ticks) {
        if (body == null) {
            return;
        }
        init();
        LIFE.put(body.getId(), ticks);
    }
}
