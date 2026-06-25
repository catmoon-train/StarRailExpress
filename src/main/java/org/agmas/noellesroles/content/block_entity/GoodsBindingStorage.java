package org.agmas.noellesroles.content.block_entity;

import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.agmas.noellesroles.Noellesroles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 售货机 / 抽奖机 商品的“绑定文件”存储器。
 *
 * <p>绑定文件以 SNBT 文本形式保存在<b>世界存档目录</b>下的 {@value #DIR_NAME} 子文件夹中，可用文本编辑器手动修改。
 * 绑定为“实时绑定”：机器读取商品时若文件发生变动会重新加载（文件为准），通过指令编辑已绑定机器的商品时会回写到绑定文件。
 *
 * <p>文件内的 NBT 结构与方块实体的 {@code saveAdditional} 保持一致：
 * {@code { shop: [ { price, currency, weight, item }, ... ], drawCost?, drawCurrency? }}。
 */
public final class GoodsBindingStorage {
   public static final String DIR_NAME = "goods_bindings";
   public static final String EXTENSION = ".snbt";

   private GoodsBindingStorage() {
   }

   /** 将用户输入的文件名归一化为安全的文件名（去除路径分隔等危险字符）；非法时返回 null。 */
   public static String sanitize(String name) {
      if (name == null) {
         return null;
      }
      String cleaned = name.trim();
      if (cleaned.toLowerCase().endsWith(EXTENSION)) {
         cleaned = cleaned.substring(0, cleaned.length() - EXTENSION.length());
      }
      // 仅保留字母、数字、下划线、连字符与点，并去掉 “..” 防止目录穿越。
      cleaned = cleaned.replaceAll("[^A-Za-z0-9_.\\-]", "").replace("..", "");
      if (cleaned.isEmpty() || cleaned.equals(".")) {
         return null;
      }
      return cleaned;
   }

   public static Path dir(MinecraftServer server) {
      return server.getWorldPath(LevelResource.ROOT).resolve(DIR_NAME);
   }

   public static Path file(MinecraftServer server, String sanitizedName) {
      return dir(server).resolve(sanitizedName + EXTENSION);
   }

   public static boolean exists(MinecraftServer server, String sanitizedName) {
      return Files.isRegularFile(file(server, sanitizedName));
   }

   /** 返回绑定文件的最后修改时间（毫秒）；文件不存在或出错返回 -1。 */
   public static long lastModified(MinecraftServer server, String sanitizedName) {
      Path p = file(server, sanitizedName);
      try {
         if (!Files.isRegularFile(p)) {
            return -1L;
         }
         return Files.getLastModifiedTime(p).toMillis();
      } catch (IOException e) {
         return -1L;
      }
   }

   /** 列出绑定目录下所有可用文件名（不含扩展名），用于指令补全。 */
   public static List<String> listNames(MinecraftServer server) {
      List<String> names = new ArrayList<>();
      Path dir = dir(server);
      if (!Files.isDirectory(dir)) {
         return names;
      }
      try (var stream = Files.list(dir)) {
         stream.filter(Files::isRegularFile)
               .map(p -> p.getFileName().toString())
               .filter(n -> n.toLowerCase().endsWith(EXTENSION))
               .map(n -> n.substring(0, n.length() - EXTENSION.length()))
               .sorted()
               .forEach(names::add);
      } catch (IOException e) {
         Noellesroles.LOGGER.error("[GoodsBinding] 无法列出绑定文件目录: {}", String.valueOf(e));
      }
      return names;
   }

   /** 写入（覆盖）绑定文件，使用临时文件 + 原子替换，避免写入过程中崩溃损坏数据。 */
   public static void write(MinecraftServer server, String sanitizedName, CompoundTag tag) throws IOException {
      Path dir = dir(server);
      Files.createDirectories(dir);
      Path target = file(server, sanitizedName);
      Path temp = target.resolveSibling(target.getFileName() + ".tmp");
      String snbt = NbtUtils.structureToSnbt(tag);
      Files.writeString(temp, snbt);
      try {
         Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (Exception atomicFailed) {
         Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
      }
   }

   /** 读取绑定文件并解析为 NBT；文件缺失或解析失败返回 null。 */
   public static CompoundTag read(MinecraftServer server, String sanitizedName) {
      Path p = file(server, sanitizedName);
      if (!Files.isRegularFile(p)) {
         return null;
      }
      try {
         return NbtUtils.snbtToStructure(Files.readString(p));
      } catch (Exception e) {
         Noellesroles.LOGGER.error("[GoodsBinding] 读取绑定文件失败 {}: {}", sanitizedName, String.valueOf(e));
         return null;
      }
   }

   // ---- ShopEntry <-> NBT（与方块实体序列化保持一致的格式）----

   public static CompoundTag saveEntry(ShopEntry entry, HolderLookup.Provider provider) {
      CompoundTag entryTag = new CompoundTag();
      entryTag.putInt("price", entry.price());
      entryTag.putString("currency", entry.currency().serializedName());
      entryTag.putInt("weight", entry.weight());
      entryTag.put("item", entry.stack().save(provider));
      return entryTag;
   }

   public static Optional<ShopEntry> loadEntry(CompoundTag entry, HolderLookup.Provider provider) {
      if (!entry.contains("item")) {
         return Optional.empty();
      }
      int price = entry.contains("price") ? entry.getInt("price") : 0;
      int weight = entry.contains("weight") ? Math.max(1, entry.getInt("weight")) : 1;
      ShopEntry.Currency currency = entry.contains("currency", Tag.TAG_STRING)
            ? ShopEntry.Currency.fromSerializedName(entry.getString("currency"))
            : ShopEntry.Currency.MONEY;
      try {
         ItemStack item = ItemStack.parse(provider, entry.get("item")).orElse(ItemStack.EMPTY);
         if (item.isEmpty()) {
            return Optional.empty();
         }
         return Optional.of(new ShopEntry(item, price, ShopEntry.Type.TOOL, currency, weight));
      } catch (Exception e) {
         Noellesroles.LOGGER.error("[GoodsBinding] 商品反序列化异常: {}", String.valueOf(e));
         return Optional.empty();
      }
   }

   public static ListTag saveEntries(List<ShopEntry> items, HolderLookup.Provider provider) {
      ListTag list = new ListTag();
      for (ShopEntry entry : items) {
         ItemStack stack = entry.stack();
         if (stack == null || stack.isEmpty()) {
            continue;
         }
         list.add(saveEntry(entry, provider));
      }
      return list;
   }

   public static List<ShopEntry> loadEntries(CompoundTag tag, HolderLookup.Provider provider) {
      List<ShopEntry> items = new ArrayList<>();
      if (!tag.contains("shop", Tag.TAG_LIST)) {
         return items;
      }
      ListTag list = tag.getList("shop", Tag.TAG_COMPOUND);
      for (Tag t : list) {
         if (t.getId() != Tag.TAG_COMPOUND) {
            continue;
         }
         loadEntry((CompoundTag) t, provider).ifPresent(items::add);
      }
      return items;
   }
}
