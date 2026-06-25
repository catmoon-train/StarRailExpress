package org.agmas.noellesroles.content.block_entity;

import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface GoodsContainer {
   List<ShopEntry> getShops();

   void addItem(ShopEntry shopEntry);

   void removeItem(ItemStack itemStack);

   boolean removeItemStack(int stackid);

   /** 当前绑定的文件名（不含扩展名）；未绑定返回 null。 */
   String getBoundFile();

   /**
    * 绑定到指定的绑定文件（名字需已通过 {@link GoodsBindingStorage#sanitize} 归一化），传 null 解绑。
    * 绑定后会立刻从该文件加载商品（文件为准）。
    */
   void setBoundFile(String sanitizedName);

   /** 构建用于导出 / 回写绑定文件的 NBT（售货机仅含商品；抽奖机另含抽奖费用）。 */
   CompoundTag toBindingTag(HolderLookup.Provider provider);
}
