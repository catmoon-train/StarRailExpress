package io.wifi.starrailexpress.item;

/**
 * 标记接口，表示该物品支持皮肤系统
 * 实现此接口的物品可以在皮肤管理界面中进行皮肤更换
 */
public interface ItemWithSkin {
    /**
     * 获取物品的默认皮肤名称
     * @return 默认皮肤名称
     */
    default String getDefaultSkin() {
        return "default";
    }
    
    /**
     * 获取物品支持的皮肤列表
     * @return 皮肤名称数组
     */
    default String[] getAvailableSkins() {
        return new String[]{"default"};
    }
}