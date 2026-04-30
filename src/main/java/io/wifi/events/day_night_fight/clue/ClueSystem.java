package io.wifi.events.day_night_fight.clue;

import io.wifi.events.day_night_fight.cca.SREPlayerClueComponent;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;

import java.util.*;

public final class ClueSystem {
    public static final int INTERACT_TICKS = 30;
    private static final int SEARCH_RADIUS = 6;

    private ClueSystem() {}

    public static SREPlayerClueComponent getData(ServerPlayer player) { return SREPlayerClueComponent.KEY.get(player); }

    public static SREPlayerClueComponent.ClueEntry spawnClueEntity(ServerLevel level, BlockPos pos, String title, String content) {
        Display.TextDisplay display = EntityType.TEXT_DISPLAY.create(level);
        if (display == null) throw new IllegalStateException("cannot create text display");
        display.setPos(pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5);
        display.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        display.setText(Component.translatable("message.sre.clue_system.display_title", title));
        level.addFreshEntity(display);
        return new SREPlayerClueComponent.ClueEntry(display.getUUID(), title, content, System.currentTimeMillis());
    }

    public static int maxSelectable() { return Math.max(1, SREConfig.instance().clueBookMaxSelections); }

    public static boolean sendCluesAsBook(ServerPlayer player, List<UUID> clueUuids) {
        if (clueUuids.isEmpty() || clueUuids.size() > maxSelectable()) return false;
        SREPlayerClueComponent data = getData(player);
        List<SREPlayerClueComponent.ClueEntry> selected = new ArrayList<>();
        for (UUID uuid : clueUuids) {
            var found = data.clues.stream().filter(v -> v.clueEntityUuid().equals(uuid)).findFirst();
            if (found.isEmpty() || data.sentClues.contains(uuid)) return false;
            selected.add(found.get());
        }

        ServerLevel targetLevel = resolveConfiguredLevel(player.serverLevel());
        BlockPos target = new BlockPos(SREConfig.instance().clueBookshelfX, SREConfig.instance().clueBookshelfY, SREConfig.instance().clueBookshelfZ);
        ChiseledBookShelfBlockEntity shelf = findAvailableShelf(targetLevel, target);
        if (shelf == null) return false;

        ItemStack book = buildClueBook(player, selected);
        int slot = firstEmptySlot(shelf);
        if (slot < 0) return false;
        shelf.setItem(slot, book);
        shelf.setChanged();

        for (UUID uuid : clueUuids) data.sentClues.add(uuid);
        data.sync();
        return true;
    }

    private static ServerLevel resolveConfiguredLevel(ServerLevel fallback) {
        String raw = SREConfig.instance().clueBookshelfDimension;
        try {
            ResourceLocation id = ResourceLocation.parse(raw);
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
            ServerLevel level = SRE.SERVER.getLevel(key);
            return level == null ? fallback : level;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static ChiseledBookShelfBlockEntity findAvailableShelf(ServerLevel level, BlockPos origin) {
        for (int r = 0; r <= SEARCH_RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                BlockPos pos = origin.offset(dx, 0, dz);
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof ChiseledBookShelfBlockEntity shelf && firstEmptySlot(shelf) >= 0) return shelf;
            }
        }
        return null;
    }

    private static int firstEmptySlot(ChiseledBookShelfBlockEntity shelf) {
        for (int i = 0; i < 6; i++) if (shelf.getItem(i).isEmpty()) return i;
        return -1;
    }

    private static ItemStack buildClueBook(ServerPlayer sender, List<SREPlayerClueComponent.ClueEntry> clues) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        var pages = new ArrayList<Filterable<Component>>();
        Component intro = Component.translatable("message.sre.clue_system.book.sender", sender.getName());
        pages.add(new Filterable<>(intro, Optional.of(intro)));
        for (var clue : clues) {
            Component page = Component.translatable("message.sre.clue_system.book.page", clue.title(), clue.content());
            pages.add(new Filterable<>(page, Optional.of(page)));
        }
        String title = Component.translatable("message.sre.clue_system.book.title").getString();
        book.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(new Filterable<>(title, Optional.of(title)), sender.getName().getString(), 1, pages, true));
        return book;
    }
}
