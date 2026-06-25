package org.agmas.noellesroles.content.block_entity;

import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModBlocks;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class VendingMachinesBlockEntity extends BlockEntity implements GoodsContainer {

   private final List<ShopEntry> items = new ArrayList<>();
   /** 已绑定的文件名（不含扩展名），null 表示未绑定。 */
   private String boundFile = null;
   /** 已加载的绑定文件最后修改时间，用于判断是否需要重新读取。 */
   private long boundFileMtime = -1L;

   public VendingMachinesBlockEntity(BlockPos pos, BlockState state) {
      super(ModBlocks.VENDING_MACHINES_BLOCK_ENTITY, pos, state);
   }

   @Override
   public List<ShopEntry> getShops() {
      this.refreshBindingIfNeeded();
      return new ArrayList<ShopEntry>(items);
   }

   public void clearItems() {
      if (!this.items.isEmpty()) {
         this.items.clear();
         this.setChanged();
         this.persistBindingIfNeeded();
      }

   }

   @Override
   public String getBoundFile() {
      return this.boundFile;
   }

   @Override
   public void setBoundFile(String sanitizedName) {
      this.boundFile = sanitizedName;
      this.boundFileMtime = -1L;
      this.setChanged();
      if (this.level != null) {
         this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(),
               Block.UPDATE_CLIENTS);
      }
      // 绑定后立刻从文件加载商品（文件为准）。
      if (sanitizedName != null) {
         this.refreshBindingIfNeeded();
      }
   }

   @Override
   public CompoundTag toBindingTag(HolderLookup.Provider provider) {
      CompoundTag tag = new CompoundTag();
      tag.put("shop", GoodsBindingStorage.saveEntries(this.items, provider));
      return tag;
   }

   /** 若已绑定且绑定文件发生变动，则从文件重新加载商品（仅服务端）。 */
   private void refreshBindingIfNeeded() {
      if (this.boundFile == null || this.level == null || this.level.isClientSide()) {
         return;
      }
      MinecraftServer server = this.level.getServer();
      if (server == null) {
         return;
      }
      long mtime = GoodsBindingStorage.lastModified(server, this.boundFile);
      if (mtime == -1L || mtime == this.boundFileMtime) {
         return;
      }
      this.boundFileMtime = mtime;
      CompoundTag tag = GoodsBindingStorage.read(server, this.boundFile);
      if (tag == null) {
         return;
      }
      this.items.clear();
      this.items.addAll(GoodsBindingStorage.loadEntries(tag, this.level.registryAccess()));
      this.setChanged();
      this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(),
            Block.UPDATE_CLIENTS);
   }

   /** 若已绑定，则把当前商品回写到绑定文件（仅服务端）。 */
   private void persistBindingIfNeeded() {
      if (this.boundFile == null || this.level == null || this.level.isClientSide()) {
         return;
      }
      MinecraftServer server = this.level.getServer();
      if (server == null) {
         return;
      }
      try {
         GoodsBindingStorage.write(server, this.boundFile, this.toBindingTag(this.level.registryAccess()));
         // 记录我们自己写入后的修改时间，避免随后把自己的写入当作外部改动再次加载。
         this.boundFileMtime = GoodsBindingStorage.lastModified(server, this.boundFile);
      } catch (IOException e) {
         Noellesroles.LOGGER.error("[VendingMachine] 回写绑定文件失败 {}: {}", this.boundFile, String.valueOf(e));
      }
   }

   @Override
   public void addItem(ShopEntry shopEntry) {
      this.items.add(shopEntry);
      this.setChanged();
      this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(),
            Block.UPDATE_CLIENTS);
      this.persistBindingIfNeeded();
      // 调试输出
      if (this.level != null && !this.level.isClientSide()) {
         Noellesroles.LOGGER.debug("[VendingMachine] 添加商品: " + shopEntry.stack().getDisplayName().getString() +
               " 价格: " + shopEntry.price() +
               " 物品为空: " + shopEntry.stack().isEmpty());
      }
   }

   @Override
   public void removeItem(ItemStack it) {
      this.items.removeIf((itt) -> {
         return itt.stack().getItem().equals(it.getItem());
      });
      this.setChanged();
      this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(),
            Block.UPDATE_CLIENTS);
      this.persistBindingIfNeeded();
   }

   @Override
   public boolean removeItemStack(int stackid) {
      if (stackid < 0)
         return false;
      if (stackid >= this.items.size())
         return false;
      this.items.remove(stackid);
      this.setChanged();
      this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(),
            Block.UPDATE_CLIENTS);
      this.persistBindingIfNeeded();
      return true;
   }

   @Override
   protected void saveAdditional(CompoundTag compoundTag, HolderLookup.Provider provider) {
      super.saveAdditional(compoundTag, provider);
      ListTag list = new ListTag();

      for (int i = 0; i < this.items.size(); ++i) {
         CompoundTag entryTag = new CompoundTag();
         ShopEntry shopEntry = this.items.get(i);
         entryTag.putInt("price", shopEntry.price());
         entryTag.putString("currency", shopEntry.currency().serializedName());
         entryTag.putInt("weight", shopEntry.weight());
         ItemStack itemStack = shopEntry.stack();

         // 仿照BeveragePlateBlockEntity的序列化方式
         if (itemStack != null && !itemStack.isEmpty()) {
            entryTag.put("item", itemStack.save(provider));
         } else {
            // 如果物品为空，跳过
            continue;
         }
         list.add(entryTag);
      }
      compoundTag.put("shop", list);
      if (this.boundFile != null) {
         compoundTag.putString("boundFile", this.boundFile);
      }
   }

   @Override
   public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
      return this.saveWithoutMetadata(registryLookup);
   }

   @Override
   public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }

   @Override
   protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
      super.loadAdditional(tag, provider);
      items.clear();
      this.boundFile = tag.contains("boundFile", Tag.TAG_STRING) ? tag.getString("boundFile") : null;
      // 重置修改时间标记，使下一次读取商品时从绑定文件重新加载。
      this.boundFileMtime = -1L;
      if (tag.contains("shop", Tag.TAG_LIST)) {
         ListTag shoptags = tag.getList("shop", Tag.TAG_COMPOUND);
         for (var s : shoptags) {
            if (s.getId() == Tag.TAG_COMPOUND) {
               var entry = (CompoundTag) (s);
               int price = 0;
               ItemStack item = ItemStack.EMPTY;
               if (entry.contains("price")) {
                  price = entry.getInt("price");
               }
               ShopEntry.Currency currency = ShopEntry.Currency.MONEY;
               if (entry.contains("currency", Tag.TAG_STRING)) {
                  currency = ShopEntry.Currency.fromSerializedName(entry.getString("currency"));
               }
               int weight = entry.contains("weight") ? Math.max(1, entry.getInt("weight")) : 1;
               if (entry.contains("item")) {
                  try {
                     CompoundTag itemTag = entry.getCompound("item");
                     // 检查是否是有效的物品标签
                     if (itemTag.contains("id") && !itemTag.getString("id").equals("minecraft:air")
                           && itemTag.getByte("count") > 0) {
                        item = ItemStack.parse(provider, entry.get("item")).orElse(ItemStack.EMPTY);
                        // 验证解析后的物品
                        if (item.isEmpty()) {
                           Noellesroles.LOGGER.warn("[VendingMachine] 警告: 物品解析失败");
                           continue;
                        }
                     } else {
                        // 空物品或无效物品
                        item = ItemStack.EMPTY;
                        Noellesroles.LOGGER.warn("[VendingMachine] 检测到空物品或无效物品");
                        continue;

                     }
                  } catch (Exception e) {
                     Noellesroles.LOGGER.error("[VendingMachine] 物品反序列化异常: " + e.getMessage());
                     item = ItemStack.EMPTY;
                     continue;

                  }
               }
               items.add(new ShopEntry(item, price, ShopEntry.Type.TOOL, currency, weight));
            }
         }
      }
   }
}
