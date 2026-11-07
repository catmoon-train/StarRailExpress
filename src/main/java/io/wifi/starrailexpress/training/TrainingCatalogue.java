package io.wifi.starrailexpress.training;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.customitem.CustomItemLoader;
import io.wifi.starrailexpress.game.ShopContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** 服务端持有实际物品栈，客户端不能提交任意 NBT 或命令。 */
public final class TrainingCatalogue {
    private record Entry(ItemStack stack, String category) { }
    private final Map<String, Entry> items = new LinkedHashMap<>();
    private final Map<String, String> fingerprints = new HashMap<>();

    public void rebuild(ServerPlayer owner) {
        items.clear(); fingerprints.clear();
        BuiltInRegistries.ITEM.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().location().toString())).forEach(e -> {
            String namespace = e.getKey().location().getNamespace();
            if (Set.of("starrailexpress", "trainmurdermystery", "noellesroles", "stupid_express", "wathe").contains(namespace))
                add(e.getValue().getDefaultInstance(), null, owner);
        });
        TMMRoles.ROLES.values().stream().sorted(Comparator.comparing(r -> r.identifier().toString())).forEach(role -> {
            try {
                role.getDefaultItems().forEach(stack -> add(stack, "role", owner));
                ShopContent.getShopEntries(role, owner).forEach(entry -> add(entry.stack(), "role", owner));
            } catch (RuntimeException exception) {
                SRE.LOGGER.warn("训练目录跳过不可用职业商品 {}", role.identifier(), exception);
            }
        });
        CustomItemLoader.getAllData().forEach(data -> add(CustomItemLoader.buildStack(data, 1), null, owner));
    }

    private void add(ItemStack stack, String category, ServerPlayer owner) {
        if (stack.isEmpty() || stack.getItem() instanceof BlockItem) return;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (id.contains("tool") || id.contains("editor") || id.contains("debug") || id.contains("spawn_egg") || id.contains("desc_")) return;
        var copy = stack.copyWithCount(1);
        String fingerprint = copy.save(owner.registryAccess()).toString();
        if (fingerprints.containsKey(fingerprint)) {
            if ("role".equals(category) && category(copy).equals("functional")) {
                String key = fingerprints.get(fingerprint);
                items.put(key, new Entry(copy, "role"));
            }
            return;
        }
        if (items.size() >= 2500) return;
        String key = Integer.toString(items.size());
        items.put(key, new Entry(copy, category != null && category(copy).equals("functional") ? category : category(copy)));
        fingerprints.put(fingerprint, key);
    }

    private static String category(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (id.matches(".*(bullet|ammo|cartridge|shell).*$")) return "ammo";
        if (id.matches(".*(knife|revolver|gun|sword|axe|bat|spear|trident|bow|grenade|bomb|nunchuck|katana).*$")) return "weapons";
        if (stack.has(DataComponents.FOOD) || id.matches(".*(vial|pill|medic|bandage|drink|potion|syringe).*$")) return "consumables";
        return "functional";
    }

    public ItemStack stack(String key, int count) {
        Entry entry = items.get(key);
        return entry == null ? ItemStack.EMPTY : entry.stack.copyWithCount(Math.clamp(count, 1, entry.stack.getMaxStackSize()));
    }

    public ListTag items(ServerPlayer viewer) {
        ListTag result = new ListTag();
        int bytes = 0;
        for (var e : items.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("key", e.getKey()); tag.putString("category", e.getValue().category);
            tag.put("stack", e.getValue().stack.save(viewer.registryAccess()));
            bytes += tag.toString().length() * 3;
            if (bytes > 700_000) break;
            result.add(tag);
        }
        return result;
    }

    public ListTag roles(ServerPlayer viewer) {
        ListTag result = new ListTag();
        var areas = AreasWorldComponent.KEY.get(viewer.level());
        TMMRoles.ROLES.values().stream().sorted(Comparator.comparing(r -> r.identifier().toString())).forEach(role -> {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", role.identifier().toString());
            tag.putString("name", role.getName().getString());
            var description = role.getDescription();
            tag.putString("description", description.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey() : description.getString());
            tag.putString("team", role.isVigilanteTeam() ? "sheriff" : role.isNeutrals() ? "neutral" : role.canUseKiller() ? "killer" : "innocent");
            tag.putBoolean("restricted", !role.canSpawnInMap(areas.mapName, areas.areasSettings));
            result.add(tag);
        });
        return result;
    }
}
