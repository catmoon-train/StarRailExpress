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
import net.minecraft.util.RandomSource;
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
import java.util.Optional;

public class LotteryMachineBlockEntity extends BlockEntity implements GoodsContainer {
   private final List<ShopEntry> items = new ArrayList<>();
   private int drawCost = 1;
   private ShopEntry.Currency drawCurrency = ShopEntry.Currency.MONEY;
   /** 已绑定的文件名（不含扩展名），null 表示未绑定。 */
   private String boundFile = null;
   /** 已加载的绑定文件最后修改时间，用于判断是否需要重新读取。 */
   private long boundFileMtime = -1L;

   public LotteryMachineBlockEntity(BlockPos pos, BlockState state) {
      super(ModBlocks.LOTTERY_MACHINE_BLOCK_ENTITY, pos, state);
   }

   @Override
   public List<ShopEntry> getShops() {
      this.refreshBindingIfNeeded();
      return new ArrayList<>(items);
   }

   public int getDrawCost() {
      this.refreshBindingIfNeeded();
      return drawCost;
   }

   public ShopEntry.Currency getDrawCurrency() {
      this.refreshBindingIfNeeded();
      return drawCurrency;
   }

   public void setDrawCost(int drawCost, ShopEntry.Currency drawCurrency) {
      this.drawCost = Math.max(0, drawCost);
      this.drawCurrency = drawCurrency == null ? ShopEntry.Currency.MONEY : drawCurrency;
      this.setChangedAndSync();
      this.persistBindingIfNeeded();
   }

   @Override
   public String getBoundFile() {
      return this.boundFile;
   }

   @Override
   public void setBoundFile(String sanitizedName) {
      this.boundFile = sanitizedName;
      this.boundFileMtime = -1L;
      this.setChangedAndSync();
      // 绑定后立刻从文件加载奖品与抽奖费用（文件为准）。
      if (sanitizedName != null) {
         this.refreshBindingIfNeeded();
      }
   }

   @Override
   public CompoundTag toBindingTag(HolderLookup.Provider provider) {
      CompoundTag tag = new CompoundTag();
      tag.put("shop", GoodsBindingStorage.saveEntries(this.items, provider));
      tag.putInt("drawCost", this.drawCost);
      tag.putString("drawCurrency", this.drawCurrency.serializedName());
      return tag;
   }

   /** 若已绑定且绑定文件发生变动，则从文件重新加载奖品与抽奖费用（仅服务端）。 */
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
      if (tag.contains("drawCost")) {
         this.drawCost = Math.max(0, tag.getInt("drawCost"));
      }
      if (tag.contains("drawCurrency", Tag.TAG_STRING)) {
         this.drawCurrency = ShopEntry.Currency.fromSerializedName(tag.getString("drawCurrency"));
      }
      this.setChangedAndSync();
   }

   /** 若已绑定，则把当前奖品与抽奖费用回写到绑定文件（仅服务端）。 */
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
         Noellesroles.LOGGER.error("[LotteryMachine] 回写绑定文件失败 {}: {}", this.boundFile, String.valueOf(e));
      }
   }

   public boolean hasPrizes() {
      this.refreshBindingIfNeeded();
      return !this.items.isEmpty();
   }

   public Optional<ShopEntry> draw(RandomSource random) {
      this.refreshBindingIfNeeded();
      if (this.items.isEmpty()) {
         return Optional.empty();
      }
      int totalWeight = this.items.stream().mapToInt(ShopEntry::weight).sum();
      if (totalWeight <= 0) {
         return Optional.of(this.items.get(random.nextInt(this.items.size())));
      }
      int roll = random.nextInt(totalWeight);
      for (ShopEntry item : this.items) {
         roll -= item.weight();
         if (roll < 0) {
            return Optional.of(item);
         }
      }
      return Optional.of(this.items.getLast());
   }

   public boolean canAfford(net.minecraft.world.entity.player.Player player) {
      return this.drawCurrency.getBalance(player) >= this.drawCost;
   }

   public void spendDrawCost(net.minecraft.world.entity.player.Player player) {
      this.drawCurrency.add(player, -this.drawCost);
   }

   @Override
   public void addItem(ShopEntry shopEntry) {
      this.items.add(shopEntry);
      this.setChangedAndSync();
      this.persistBindingIfNeeded();
      if (this.level != null && !this.level.isClientSide()) {
         Noellesroles.LOGGER.debug("[LotteryMachine] 添加奖品: " + shopEntry.stack().getDisplayName().getString());
      }
   }

   @Override
   public void removeItem(ItemStack it) {
      this.items.removeIf(entry -> entry.stack().getItem().equals(it.getItem()));
      this.setChangedAndSync();
      this.persistBindingIfNeeded();
   }

   @Override
   public boolean removeItemStack(int stackid) {
      if (stackid < 0 || stackid >= this.items.size()) {
         return false;
      }
      this.items.remove(stackid);
      this.setChangedAndSync();
      this.persistBindingIfNeeded();
      return true;
   }

   private void setChangedAndSync() {
      this.setChanged();
      if (this.level != null) {
         this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(),
               Block.UPDATE_CLIENTS);
      }
   }

   @Override
   protected void saveAdditional(CompoundTag compoundTag, HolderLookup.Provider provider) {
      super.saveAdditional(compoundTag, provider);
      compoundTag.putInt("drawCost", this.drawCost);
      compoundTag.putString("drawCurrency", this.drawCurrency.serializedName());

      ListTag list = new ListTag();
      for (ShopEntry shopEntry : this.items) {
         CompoundTag entryTag = new CompoundTag();
         entryTag.putInt("price", shopEntry.price());
         entryTag.putString("currency", shopEntry.currency().serializedName());
         entryTag.putInt("weight", shopEntry.weight());
         ItemStack itemStack = shopEntry.stack();
         if (itemStack == null || itemStack.isEmpty()) {
            continue;
         }
         entryTag.put("item", itemStack.save(provider));
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
      this.items.clear();
      this.boundFile = tag.contains("boundFile", Tag.TAG_STRING) ? tag.getString("boundFile") : null;
      // 重置修改时间标记，使下一次读取时从绑定文件重新加载。
      this.boundFileMtime = -1L;
      this.drawCost = tag.contains("drawCost") ? Math.max(0, tag.getInt("drawCost")) : 1;
      this.drawCurrency = tag.contains("drawCurrency", Tag.TAG_STRING)
            ? ShopEntry.Currency.fromSerializedName(tag.getString("drawCurrency"))
            : ShopEntry.Currency.MONEY;

      if (tag.contains("shop", Tag.TAG_LIST)) {
         ListTag shoptags = tag.getList("shop", Tag.TAG_COMPOUND);
         for (var s : shoptags) {
            if (s.getId() != Tag.TAG_COMPOUND) {
               continue;
            }
            CompoundTag entry = (CompoundTag) s;
            int price = entry.contains("price") ? entry.getInt("price") : 0;
            int weight = entry.contains("weight") ? Math.max(1, entry.getInt("weight")) : 1;
            ShopEntry.Currency currency = entry.contains("currency", Tag.TAG_STRING)
                  ? ShopEntry.Currency.fromSerializedName(entry.getString("currency"))
                  : ShopEntry.Currency.MONEY;
            if (!entry.contains("item")) {
               continue;
            }
            try {
               ItemStack item = ItemStack.parse(provider, entry.get("item")).orElse(ItemStack.EMPTY);
               if (!item.isEmpty()) {
                  this.items.add(new ShopEntry(item, price, ShopEntry.Type.TOOL, currency, weight));
               }
            } catch (Exception e) {
               Noellesroles.LOGGER.error("[LotteryMachine] 奖品反序列化异常: " + e.getMessage());
            }
         }
      }
   }
}
