package io.wifi.starrailexpress.training;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** 修饰符选择的有界纯状态规则；完整校验后返回副本，不部分修改原配置。 */
final class TrainingModifierSelection {
    static final int MAX = 16;
    private TrainingModifierSelection() { }

    static List<String> normalize(List<String> values) {
        var result = new LinkedHashSet<String>();
        if (values != null) for (String value : values) {
            if (value == null || value.isBlank() || value.length() > 128) continue;
            var id = ResourceLocation.tryParse(value);
            if (id != null) result.add(id.toString());
            if (result.size() == MAX) break;
        }
        return new ArrayList<>(result);
    }

    static List<String> edit(List<String> current, String operation, String id,
            Predicate<String> applicable, BiPredicate<String, String> opposing) {
        var result = normalize(current);
        switch (operation) {
            case "clear" -> result.clear();
            case "remove" -> result.remove(id);
            case "add" -> {
                if (!applicable.test(id)) throw new IllegalArgumentException("training.sre.error.modifier_role");
                if (!result.contains(id)) {
                    if (result.size() >= MAX) throw new IllegalArgumentException("training.sre.error.modifier_limit");
                    if (result.stream().anyMatch(other -> opposing.test(id, other)))
                        throw new IllegalArgumentException("training.sre.error.modifier_role");
                    result.add(id);
                }
            }
            default -> throw new IllegalArgumentException("training.sre.error.modifier");
        }
        return result;
    }
}
