package io.wifi.starrailexpress.api;

import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModBlocks;
import org.agmas.noellesroles.init.ModItems;
import java.util.ArrayList;
import java.util.List;

public class RepairRole extends NormalRole{
    /**
     * @param identifier    the mod id and name of the role
     * @param color         the role announcement color
     * @param isInnocent    whether the gun drops when a person with this role is
     *                      shot and is considered a civilian to the win conditions
     * @param canUseKiller  can see and use the killer features
     * @param moodType      the mood type a role has
     * @param maxSprintTime the maximum sprint time in ticks
     * @param canSeeTime    if the role can see the game timer
     */
    public RepairRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller, MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
        setCanSeeTime(true);
        setCanSeeCoin(true);
        setMoodType(MoodType.FAKE);
        setCanUseInstinct(false);
        setCanAutoAddMoney(true);
        setCanBeRandomedByOtherRoles(false);
    }

    @Override
    public ResourceLocation getNormalSkin(Player player, boolean isSlim) {
        if (this.canUseKiller()) {
            return super.getPsychoSkin(player, isSlim);
        }
        return super.getNormalSkin(player, isSlim);
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        String id = getIdentifier().getPath();
        return switch (id) {
            // === 生存者阵营 ===
            case "repair_mechanic" -> mechanicShop();
            case "repair_medic" -> medicShop();
            case "repair_runner" -> runnerShop();

            // === 追捕者阵营 ===
            case "repair_warden" -> wardenShop();
            case "repair_brute" -> bruteShop();
            case "repair_tracker" -> trackerShop();

            // === 中立阵营 ===
            case "repair_archivist" -> archivistShop();
            case "repair_saboteur" -> saboteurShop();
            case "repair_collector" -> collectorShop();

            // 通用阵营（选择阶段）
            default -> factionShop(canUseKiller());
        };
    }

    // === 机械师：快速解锁机器区域和修机爆发 ===
    private List<ShopEntry> mechanicShop() {
        List<ShopEntry> entries = new ArrayList<>();
        entries.add(shopItem(ModItems.REPAIR_TOOLBOX, 120, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.SPARE_PARTS, 48, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.FLARE, 48, ShopEntry.Type.TOOL));

        entries.add(shopItem(ModItems.REPAIR_BOLT_CUTTER, 160, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.REPAIR_BATTERY, 80, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.REPAIR_VALVE_HANDLE, 72, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.SMOKE_PELLET, 60, ShopEntry.Type.TOOL));
        if (BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("handheldmoon:moonlight_lamp"))) {
            final var moonlightLampItem = BuiltInRegistries.ITEM
                    .get(ResourceLocation.parse("handheldmoon:moonlight_lamp"));
            if (moonlightLampItem != null) {
                final var defaultInstance = moonlightLampItem.getDefaultInstance();
                entries.add(new ShopEntry(defaultInstance, 120, ShopEntry.Type.TOOL));
            }
        }
        return entries;
    }


    // === 医师：搜索医疗物资和快速救援 ===
    private List<ShopEntry> medicShop() {
        List<ShopEntry> entries = new ArrayList<>();
        entries.add(shopItem(ModItems.RESCUE_FLARE, 80, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.SMOKE_PELLET, 60, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.FLARE, 48, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.REPAIR_OLD_KEY, 60, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.REPAIR_LOCKPICK, 40, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.SPARE_PARTS, 48, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.DECOY_BEACON, 80, ShopEntry.Type.TOOL));
        if (BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("handheldmoon:moonlight_lamp"))) {
            final var moonlightLampItem = BuiltInRegistries.ITEM
                    .get(ResourceLocation.parse("handheldmoon:moonlight_lamp"));
            if (moonlightLampItem != null) {
                final var defaultInstance = moonlightLampItem.getDefaultInstance();
                entries.add(new ShopEntry(defaultInstance, 120, ShopEntry.Type.TOOL));
            }
        }
        return entries;
    }

    // === 飞毛腿：真实dash位移和逃脱 ===
    private List<ShopEntry> runnerShop() {
        List<ShopEntry> entries = new ArrayList<>();
        entries.add(shopItem(ModItems.ESCAPE_GRAPPLE, 160, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.SMOKE_PELLET, 48, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.DECOY_BEACON, 88, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.FLARE, 48, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.REPAIR_LOCKPICK, 40, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.SPARE_PARTS, 56, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.REPAIR_CROWBAR, 140, ShopEntry.Type.TOOL));
        if (BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("handheldmoon:moonlight_lamp"))) {
            final var moonlightLampItem = BuiltInRegistries.ITEM
                    .get(ResourceLocation.parse("handheldmoon:moonlight_lamp"));
            if (moonlightLampItem != null) {
                final var defaultInstance = moonlightLampItem.getDefaultInstance();
                entries.add(new ShopEntry(defaultInstance, 120, ShopEntry.Type.TOOL));
            }
        }
        return entries;
    }

    // === 狱卒：强化审判/锁门压制 ===
    private List<ShopEntry> wardenShop() {
        List<ShopEntry> entries = new ArrayList<>();
        entries.add(shopItem(ModItems.HUNTER_CHAIN, 30, ShopEntry.Type.WEAPON));
        entries.add(shopItem(ModItems.HUNTER_WEAPON, 45, ShopEntry.Type.WEAPON));
        entries.add(shopItem(ModBlocks.HUNTER_SNARE.asItem(), 18, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.HUNTER_JAMMER, 35, ShopEntry.Type.WEAPON));
        entries.add(shopItem(ModItems.HUNTER_PLUGIN_SUPPRESSION, 28, ShopEntry.Type.WEAPON));
        entries.add(shopItem(ModItems.HUNTER_PLUGIN_LACERATION, 25, ShopEntry.Type.WEAPON));
        return entries;
    }

    // === 蛮徒：强化重锤/破门压力 ===
    private List<ShopEntry> bruteShop() {
        List<ShopEntry> entries = new ArrayList<>();
        entries.add(shopItem(ModItems.HUNTER_HAMMER, 50, ShopEntry.Type.WEAPON));
        entries.add(shopItem(ModItems.HUNTER_BLINK, 32, ShopEntry.Type.WEAPON));
        entries.add(shopItem(ModItems.HUNTER_PLUGIN_CONCUSSION, 28, ShopEntry.Type.WEAPON));
        entries.add(shopItem(ModItems.HUNTER_PLUGIN_LACERATION, 25, ShopEntry.Type.WEAPON));
        entries.add(shopItem(ModBlocks.HUNTER_SNARE.asItem(), 20, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.HUNTER_CHAIN, 35, ShopEntry.Type.WEAPON));
        return entries;
    }

    // === 追踪者：强化钩镰/声音定位 ===
    private List<ShopEntry> trackerShop() {
        List<ShopEntry> entries = new ArrayList<>();
        entries.add(shopItem(ModItems.HUNTER_HOOK, 45, ShopEntry.Type.WEAPON));
        entries.add(shopItem(ModItems.HUNTER_PULSE, 28, ShopEntry.Type.WEAPON));
        entries.add(shopItem(ModItems.HUNTER_PLUGIN_TRACKING, 25, ShopEntry.Type.WEAPON));
        entries.add(shopItem(ModItems.HUNTER_PLUGIN_SUPPRESSION, 30, ShopEntry.Type.WEAPON));
        entries.add(shopItem(ModBlocks.HUNTER_SNARE.asItem(), 20, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.HUNTER_CHAIN, 35, ShopEntry.Type.WEAPON));
        return entries;
    }

    // === 档案员：收集信息和记录 ===
    private List<ShopEntry> archivistShop() {
        List<ShopEntry> entries = new ArrayList<>();
        entries.add(shopItem(ModItems.SPARE_PARTS, 80, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.REPAIR_LOCKPICK, 120, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.REPAIR_OLD_KEY, 150, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.DECOY_BEACON, 180, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.SMOKE_PELLET, 140, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.REPAIR_BATTERY, 220, ShopEntry.Type.TOOL));
        return entries;
    }

    // === 破坏者：假声音和误导追捕者 ===
    private List<ShopEntry> saboteurShop() {
        List<ShopEntry> entries = new ArrayList<>();
        entries.add(shopItem(ModItems.DECOY_BEACON, 180, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.SMOKE_PELLET, 100, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.REPAIR_BOLT_CUTTER, 350, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.REPAIR_LOCKPICK, 100, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.SPARE_PARTS, 100, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.REPAIR_CROWBAR, 320, ShopEntry.Type.TOOL));
        return entries;
    }

    // === 收藏家：提高搜索收益 ===
    private List<ShopEntry> collectorShop() {
        List<ShopEntry> entries = new ArrayList<>();
        entries.add(shopItem(ModItems.REPAIR_TOOLBOX, 35, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.SPARE_PARTS, 8, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.REPAIR_AREA_KEY, 28, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.REPAIR_OLD_KEY, 15, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.REPAIR_FUSE, 22, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.REPAIR_GEAR_HANDLE, 20, ShopEntry.Type.TOOL));
        entries.add(shopItem(ModItems.REPAIR_LOCKPICK, 10, ShopEntry.Type.TOOL));
        return entries;
    }

    // === 通用阵营商店（选择阶段） ===
    private List<ShopEntry> factionShop(boolean isHunter) {
        if (isHunter) {
            List<ShopEntry> entries = new ArrayList<>();
            entries.add(shopItem(ModItems.HUNTER_WEAPON, 50, ShopEntry.Type.WEAPON));
            entries.add(shopItem(ModItems.HUNTER_CHAIN, 35, ShopEntry.Type.WEAPON));
            entries.add(shopItem(ModBlocks.HUNTER_SNARE.asItem(), 20, ShopEntry.Type.TOOL));
            return entries;
        } else {
            List<ShopEntry> entries = new ArrayList<>();
            entries.add(shopItem(ModItems.REPAIR_TOOLBOX, 40, ShopEntry.Type.TOOL));
            entries.add(shopItem(ModItems.SMOKE_PELLET, 18, ShopEntry.Type.TOOL));
            entries.add(shopItem(ModItems.SPARE_PARTS, 15, ShopEntry.Type.TOOL));
            return entries;
        }
    }

    private static ShopEntry shopItem(net.minecraft.world.item.Item item, int price, ShopEntry.Type type) {
        return new ShopEntry(new ItemStack(item), price, type);
    }
}
