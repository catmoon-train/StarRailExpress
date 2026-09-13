package org.agmas.noellesroles.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 复现原版 LivingEntity.removeAllEffects 的迭代方式：先 onEffectRemoved，再
 * iterator.remove()。失心症结束回调会在此时再删其它效果，HashMap 直接 CME。
 */
class RemoveAllEffectsIterationTest {

    @Test
    void vanillaIteratorRemoveThrowsWhenEndedCallbackMutatesMap() {
        Map<String, Integer> activeEffects = new HashMap<>();
        activeEffects.put("aphrenia", 1);
        activeEffects.put("move_baned", 1);
        activeEffects.put("used_baned", 1);

        Iterator<Map.Entry<String, Integer>> iterator = activeEffects.entrySet().iterator();
        assertThrows(ConcurrentModificationException.class, () -> {
            while (iterator.hasNext()) {
                Map.Entry<String, Integer> entry = iterator.next();
                onAphreniaEnded(activeEffects, entry.getKey());
                iterator.remove();
            }
        });
    }

    @Test
    void snapshotThenRemoveSurvivesEndedCallbackMutatingMap() {
        Map<String, Integer> activeEffects = new HashMap<>();
        activeEffects.put("aphrenia", 1);
        activeEffects.put("move_baned", 1);
        activeEffects.put("used_baned", 1);

        List<String> snapshot = new ArrayList<>(activeEffects.keySet());
        for (String key : snapshot) {
            if (!activeEffects.containsKey(key)) {
                continue;
            }
            onAphreniaEnded(activeEffects, key);
            activeEffects.remove(key);
        }
        assertTrue(activeEffects.isEmpty());
    }

    private static void onAphreniaEnded(Map<String, Integer> map, String key) {
        if ("aphrenia".equals(key)) {
            map.remove("move_baned");
            map.remove("used_baned");
        }
    }
}
