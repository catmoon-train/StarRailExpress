package org.agmas.noellesroles.blood;

import java.util.HashSet;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

/**
 * 武器血液效果配置管理器
 * 用于存储和管理不同武器对应的血液效果参数
 * 仅在客户端环境下运行
 */
@Environment(EnvType.CLIENT)
public class BloodItems {
    /**
     * 存储所有已配置的武器血液效果参数的集合
     * 每个元素包含一个武器及其对应的血液效果配置
     */
    private static final Set<ItemBlood> itemBloods = new HashSet<>();
    
    /**
     * 存储所有已注册武器的集合
     * 用于快速检查某个武器是否已配置血液效果
     */
    private static final Set<Item> items = new HashSet<>();

    /**
     * 工具类构造函数，无需实例化
     */
    public BloodItems() {
    }

    /**
     * 获取所有已配置的武器血液效果参数
     * 
     * @return 包含所有武器血液效果参数的不可修改集合
     */
    public static Set<ItemBlood> getBloods() {
        return itemBloods;
    }

    /**
     * 获取所有已注册的武器
     * 
     * @return 包含所有已注册武器的不可修改集合
     */
    public static Set<Item> getItems() {
        return items;
    }

    /**
     * 为指定武器添加血液效果配置
     * 
     * @param item 武器物品，如左轮手枪、刀等
     * @param strength 血液喷射强度，影响粒子速度
     * @param directiveness 方向性，值越小粒子方向越集中（0-1之间）
     * @param minBlood 生成的最小血液粒子数量
     * @param maxBlood 生成的最大血液粒子数量
     * @param area 血液粒子生成的区域范围（Vec3表示x,y,z三个方向的扩展）
     */
    public static void addItem(Item item, double strength, double directiveness, int minBlood, int maxBlood,
            Vec3 area) {
        // 创建新的武器血液效果配置并添加到集合中
        itemBloods.add(new ItemBlood(item, strength, directiveness, minBlood, maxBlood, area));
        // 将武器添加到快速查找集合中
        items.add(item);
    }

    /**
     * 武器血液效果配置类
     * 存储单个武器的血液效果参数
     */
    @Environment(EnvType.CLIENT)
    public static class ItemBlood {
        /** 武器物品引用 */
        public final Item item;
        /** 血液强度(strength缩写)，影响血液粒子的喷射速度 */
        public final double st;
        /** 方向性(directiveness缩写)，控制血液粒子喷射方向的集中程度 */
        public final double dt;
        /** 每次生成的最小血液粒子数量 */
        public final int minBlood;
        /** 每次生成的最大血液粒子数量 */
        public final int maxBlood;
        /** 血液粒子生成的区域范围，x、y、z三个方向的扩展大小 */
        public final Vec3 area;

        /**
         * 构造函数
         * 
         * @param item 武器物品
         * @param strength 血液强度
         * @param directiveness 方向性
         * @param minBlood 最小粒子数量
         * @param maxBlood 最大粒子数量
         * @param area 生成区域范围
         */
        public ItemBlood(Item item, double strength, double directiveness, int minBlood, int maxBlood, Vec3 area) {
            this.item = item;
            this.st = strength;
            this.dt = directiveness;
            this.minBlood = minBlood;
            this.maxBlood = maxBlood;
            this.area = area;
        }
    }
}