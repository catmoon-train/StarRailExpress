package org.agmas.noellesroles.client.map;

import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 区域地图上可显示/筛选的任务点分类。
 *
 * <p>类型 ID 与 {@code MapScanner.scanAllTaskBlocks} 写入
 * {@code NoellesrolesClient.taskBlocks} 的方块类型 ID 一致
 * （颜色对齐 {@code TwoDimensionalTaskArrowRenderer.typeColor}）。
 * DOOR 无类型 ID，数据来自 {@code TaskBlockOverlayRenderer.RoomDoorPositions}。
 */
public enum AreaMapPointCategory {
    FOOD(0xFF55CC55, 1),
    DRINK(0xFFEA5858, 2),
    BATHE(0xFF8DEABD, 3),
    BED(0xFF00FFDC, 4),
    EXERCISE(0xFFFFF200, 5, 22),
    READ(0xFFFF7F27, 6),
    DOOR(0xFFC9A84C),
    TOILET(0xFFFFAEC9, 8),
    CHAIR(0xFF7EFFE4, 9),
    NOTE(0xFF7994FF, 10),
    SHOP(0xFFFF66AA, 11, 12, 23),
    MINIGAME(0xFFE0AD5B, 14, 15),
    SCENE(0xFFB18AE6, 16, 17, 18, 19, 20, 21),
    BELL(0xFFFFD700, 24);

    /** ARGB 显示颜色。 */
    public final int color;
    /** 归入该分类的任务点方块类型 ID（可为空，如 DOOR）。 */
    public final int[] typeIds;

    private static final Map<Integer, AreaMapPointCategory> BY_TYPE = new HashMap<>();

    static {
        for (AreaMapPointCategory cat : values()) {
            for (int id : cat.typeIds) {
                BY_TYPE.put(id, cat);
            }
        }
    }

    AreaMapPointCategory(int color, int... typeIds) {
        this.color = color;
        this.typeIds = typeIds;
    }

    /** 根据任务点方块类型 ID 找到分类，未知类型返回 null。 */
    public static AreaMapPointCategory byTypeId(int typeId) {
        return BY_TYPE.get(typeId);
    }

    public Component getName() {
        return Component.translatable("gui.noellesroles.area_map.cat." + name().toLowerCase(Locale.ROOT));
    }
}
