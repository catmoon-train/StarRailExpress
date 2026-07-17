package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.config.SixtySecondsConfig;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理员搭图工具：登记<b>避难所锚点门</b>，并当场预演「避难所锚定到探索区出口门」的落位
 * （开关见 {@code /sre:60s shelter_at_door}）。取代手敲 {@code /sre:60s_area anchor <x y z>}——
 * 指哪打哪，不用先去抄坐标。
 * <ul>
 *   <li><b>右键避难所模板内的门</b>：登记为 {@link SixtySecondsConfig#shelterAnchorDoor}（立刻落盘）。</li>
 *   <li><b>右键探索区里的出口门</b>：预演——报出这扇门是第几队的、平移量、避难所会落到哪个盒，
 *       并检查该落位是否与别队重叠。不写配置，纯查看。</li>
 *   <li><b>潜行右键任意门</b>：清除锚点（回退网格克隆）。</li>
 *   <li><b>右键非门方块</b>：打印当前锚点/开关状态。</li>
 * </ul>
 * 与 {@link SixtySecondsAreaWandItem} 分成两把而不是并进一把：那把绑的是「门→探索区」，
 * 这把定的是「避难所整体落位」，两者右键的都是门但语义完全不同，混在一起没法区分意图。
 */
public class SixtySecondsAnchorWandItem extends Item {

    private static final String LANG = "message.noellesroles.sixty_seconds.anchor_wand.";

    public SixtySecondsAnchorWandItem(Properties properties) {
        super(properties);
    }

    private static boolean isAdmin(Player player) {
        return player.hasPermissions(2) || player.isCreative();
    }

    /**
     * 由 {@code ShelterDoorBlock} 在「手持本工具右键门」时调用——门方块的 useItemOn 先于物品 useOn 执行
     * 并吞掉交互，不在门那边短路的话本工具对着门永远不会触发。
     */
    public static void selectDoor(ServerPlayer player, BlockPos doorPos) {
        if (!isAdmin(player)) {
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.wand.no_permission")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        SixtySecondsConfig config = SixtySecondsConfigStore.current(level).orElseGet(SixtySecondsConfig::new);
        if (player.isShiftKeyDown()) {
            config.shelterAnchorDoor = null;
            SixtySecondsConfigStore.save(level, config);
            player.displayClientMessage(Component.translatable(LANG + "cleared")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        if (config.shelterTemplate == null) {
            player.displayClientMessage(Component.translatable(LANG + "no_template")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        if (config.shelterTemplate.toBox().isInside(doorPos)) {
            setAnchor(level, player, config, doorPos);
        } else {
            preview(level, player, config, doorPos);
        }
    }

    /** 门在避难所模板盒内 = 登记为锚点门。 */
    private static void setAnchor(ServerLevel level, ServerPlayer player, SixtySecondsConfig config,
            BlockPos doorPos) {
        config.shelterAnchorDoor = new SixtySecondsConfig.Vec(doorPos.getX(), doorPos.getY(), doorPos.getZ());
        SixtySecondsConfigStore.save(level, config);
        player.displayClientMessage(Component.translatable(LANG + "anchor_set",
                doorPos.getX(), doorPos.getY(), doorPos.getZ()).withStyle(ChatFormatting.GREEN), false);
        if (!config.shelterAtSearchDoorEnabled) {
            player.displayClientMessage(Component.translatable(LANG + "switch_off")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        int exits = exitDoors(config).size();
        player.displayClientMessage(Component.translatable(LANG + "anchor_hint", exits)
                .withStyle(ChatFormatting.GRAY), false);
    }

    /**
     * 门在模板外 = 探索区出口门：预演这扇门对应第几队、平移量多少、避难所落到哪，并查重叠。
     * 纯查看，不写配置——搭图者常要先看一眼「这门放这儿房子会不会撞上隔壁」再决定。
     */
    private static void preview(ServerLevel level, ServerPlayer player, SixtySecondsConfig config,
            BlockPos doorPos) {
        if (config.shelterAnchorDoor == null) {
            player.displayClientMessage(Component.translatable(LANG + "need_anchor")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        List<SixtySecondsConfig.DoorBinding> exits = exitDoors(config);
        int index = -1;
        for (int i = 0; i < exits.size(); i++) {
            if (exits.get(i).door.toBlockPos().equals(doorPos)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            player.displayClientMessage(Component.translatable(LANG + "not_bound")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        BlockPos anchor = config.shelterAnchorDoor.toBlockPos();
        BlockPos offset = doorPos.subtract(anchor);
        BoundingBox template = config.shelterTemplate.toBox();
        BlockPos min = new BlockPos(template.minX(), template.minY(), template.minZ()).offset(offset);
        BlockPos max = new BlockPos(template.maxX(), template.maxY(), template.maxZ()).offset(offset);
        player.displayClientMessage(Component.translatable(LANG + "preview_team", index + 1)
                .withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(Component.translatable(LANG + "preview_offset",
                offset.getX(), offset.getY(), offset.getZ()).withStyle(ChatFormatting.AQUA), false);
        player.displayClientMessage(Component.translatable(LANG + "preview_box",
                min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ())
                .withStyle(ChatFormatting.AQUA), false);

        // 重叠检查：两队出口门挨得比避难所模板还近的话，后建的会盖掉先建的（建图只会在日志里告警，
        // 搭图时当场看到才来得及挪门）
        for (int i = 0; i < exits.size(); i++) {
            if (i == index) {
                continue;
            }
            BlockPos otherOffset = exits.get(i).door.toBlockPos().subtract(anchor);
            if (boxesOverlap(template, offset, otherOffset)) {
                player.displayClientMessage(Component.translatable(LANG + "preview_overlap", i + 1)
                        .withStyle(ChatFormatting.RED), false);
                return;
            }
        }
        player.displayClientMessage(Component.translatable(LANG + "preview_ok")
                .withStyle(ChatFormatting.GREEN), false);
    }

    /** 两份按不同偏移落位的同一模板盒（含 2 格净空环带）是否相交。 */
    private static boolean boxesOverlap(BoundingBox template, BlockPos a, BlockPos b) {
        int margin = 2;
        return overlap1D(template.minX() + a.getX() - margin, template.maxX() + a.getX() + margin,
                        template.minX() + b.getX() - margin, template.maxX() + b.getX() + margin)
                && overlap1D(template.minY() + a.getY(), template.maxY() + a.getY(),
                        template.minY() + b.getY(), template.maxY() + b.getY())
                && overlap1D(template.minZ() + a.getZ() - margin, template.maxZ() + a.getZ() + margin,
                        template.minZ() + b.getZ() - margin, template.maxZ() + b.getZ() + margin);
    }

    private static boolean overlap1D(int minA, int maxA, int minB, int maxB) {
        return minA <= maxB && minB <= maxA;
    }

    /** 探索区里的出口门（= 门绑定中落在住宅/避难所模板<b>外</b>的那些，与建图同一套判定与顺序）。 */
    private static List<SixtySecondsConfig.DoorBinding> exitDoors(SixtySecondsConfig config) {
        List<SixtySecondsConfig.DoorBinding> exits = new ArrayList<>();
        if (config.searchDoorBindings == null) {
            return exits;
        }
        for (SixtySecondsConfig.DoorBinding binding : config.searchDoorBindings) {
            if (binding.door == null || binding.boxMin == null || binding.boxMax == null
                    || binding.spawn == null) {
                continue;
            }
            BlockPos door = binding.door.toBlockPos();
            boolean inTemplate = (config.residentialTemplate != null
                            && config.residentialTemplate.toBox().isInside(door))
                    || (config.shelterTemplate != null && config.shelterTemplate.toBox().isInside(door));
            if (!inTemplate) {
                exits.add(binding);
            }
        }
        return exits;
    }

    /** 右键非门方块：打印当前锚点与开关状态。 */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player raw = context.getPlayer();
        if (level.isClientSide || !(raw instanceof ServerPlayer player)
                || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!isAdmin(player)) {
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.wand.no_permission")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        // 门由 ShelterDoorBlock 短路到 selectDoor，这里只会收到非门方块
        SixtySecondsConfig config = SixtySecondsConfigStore.current(serverLevel)
                .orElseGet(SixtySecondsConfig::new);
        Component anchor = config.shelterAnchorDoor == null
                ? Component.translatable(LANG + "status_unset").withStyle(ChatFormatting.RED)
                : Component.literal(config.shelterAnchorDoor.x + ", " + config.shelterAnchorDoor.y + ", "
                        + config.shelterAnchorDoor.z).withStyle(ChatFormatting.AQUA);
        player.displayClientMessage(Component.translatable(LANG + "status", anchor,
                config.shelterAtSearchDoorEnabled
                        ? Component.translatable(LANG + "status_on").withStyle(ChatFormatting.GREEN)
                        : Component.translatable(LANG + "status_off").withStyle(ChatFormatting.RED),
                exitDoors(config).size()), false);
        return InteractionResult.SUCCESS;
    }
}
