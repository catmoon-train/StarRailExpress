package io.wifi.starrailexpress.training;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 训练专用修饰符适配；添加、移除始终经过正常事件及同步 API。 */
final class TrainingModifiers {
    private TrainingModifiers() { }

    static SREModifier resolve(String id) {
        return id == null ? null : HMLModifiers.getModifier(ResourceLocation.tryParse(id));
    }

    static List<String> compatible(List<String> values, SRERole role) {
        var result = new ArrayList<String>();
        for (String id : TrainingModifierSelection.normalize(values)) {
            var modifier = resolve(id);
            if (RoleUtils.isModifierApplicable(modifier, role)
                    && result.stream().noneMatch(other -> opposing(id, other))) result.add(id);
        }
        return result;
    }

    static boolean opposing(String first, String second) {
        var a = resolve(first); var b = resolve(second);
        return a != null && b != null && (a.isOpposingModifier(b) || b.isOpposingModifier(a));
    }

    static List<String> edit(List<String> values, String operation, String id, SRERole role) {
        return TrainingModifierSelection.edit(values, operation, id,
                key -> RoleUtils.isModifierApplicable(resolve(key), role), TrainingModifiers::opposing);
    }

    static List<String> actual(ServerPlayer player) {
        return WorldModifierComponent.KEY.get(player.level()).getModifiers(player).stream()
                .filter(m -> m != null && m.identifier() != null).map(m -> m.identifier().toString()).sorted().toList();
    }

    /** 职业初始化后只补配置项，保留职业本身授予的修饰符，不重复触发已有项。 */
    static void addConfigured(ServerPlayer player, List<String> values) {
        var role = SREGameWorldComponent.KEY.get(player.level()).getRole(player);
        for (String id : compatible(values, role)) {
            var modifier = resolve(id);
            var current = WorldModifierComponent.KEY.get(player.level()).getModifiers(player);
            if (!current.contains(modifier) && current.stream().noneMatch(other -> opposing(id, other.identifier().toString())))
                RoleUtils.addModifier(player, modifier, false, false);
        }
    }

    /** 显式编辑只改修饰符，不重置生命、金币、职业数据或背包。 */
    static void replace(ServerPlayer player, List<String> values) {
        var desired = compatible(values, SREGameWorldComponent.KEY.get(player.level()).getRole(player));
        var current = new ArrayList<>(WorldModifierComponent.KEY.get(player.level()).getModifiers(player));
        current.sort(Comparator.comparing(m -> m.identifier().toString()));
        for (var modifier : current) if (!desired.contains(modifier.identifier().toString()))
            RoleUtils.removeModifier(player, modifier, false, false);
        addConfigured(player, desired);
    }

    static ListTag tag(List<String> values) {
        var tag = new ListTag();
        for (String value : values) tag.add(StringTag.valueOf(value));
        return tag;
    }
}
