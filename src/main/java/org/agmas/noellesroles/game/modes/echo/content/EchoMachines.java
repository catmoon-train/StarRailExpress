package org.agmas.noellesroles.game.modes.echo.content;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** 回声托运站机器注册表；每种机器仅注册一个方块与整机物品，PART 表示真实占格。 */
public final class EchoMachines {
    public enum Kind {
        BEACON("echo_beacon"),
        CORE_BEACON("echo_core_beacon"),
        CRYSTAL("echo_crystal_extractor"),
        ALTAR("echo_key_altar"),
        SUPPLY("echo_supply_crate"),
        BLACK_MARKET("echo_black_market"),
        BLACK_NODE("echo_black_crystal_extractor"),
        BLACK_DROPOFF("echo_black_dropoff"),
        CORE_CONSOLE("echo_core_console"),
        FREE_EXIT("echo_free_exit"),
        EVACUATION_CONSOLE("echo_evacuation_console"),
        BROKER_EXIT("echo_broker_exit");

        private final String path;

        Kind(String path) { this.path = path; }

        public ResourceLocation id() {
            return ResourceLocation.fromNamespaceAndPath("noellesroles", path);
        }

        public String nameKey() { return "block.noellesroles." + path; }
    }

    private static final Map<Block, Kind> KINDS = new IdentityHashMap<>();
    public static final EchoMachineBlock BEACON = register(Kind.BEACON);
    public static final EchoMachineBlock CORE_BEACON = register(Kind.CORE_BEACON);
    public static final EchoMachineBlock CRYSTAL = register(Kind.CRYSTAL);
    public static final EchoMachineBlock ALTAR = register(Kind.ALTAR);
    public static final EchoMachineBlock SUPPLY = register(Kind.SUPPLY);
    public static final EchoMachineBlock BLACK_MARKET = register(Kind.BLACK_MARKET);
    public static final EchoMachineBlock BLACK_NODE = register(Kind.BLACK_NODE);
    public static final EchoMachineBlock BLACK_DROPOFF = register(Kind.BLACK_DROPOFF);
    public static final EchoMachineBlock CORE_CONSOLE = register(Kind.CORE_CONSOLE);
    public static final EchoMachineBlock FREE_EXIT = register(Kind.FREE_EXIT);
    public static final EchoMachineBlock EVACUATION_CONSOLE = register(Kind.EVACUATION_CONSOLE);
    public static final EchoMachineBlock BROKER_EXIT = register(Kind.BROKER_EXIT);

    /** 顺序与 Kind 一致，返回不可变列表，供创造分页与客户端统一枚举。 */
    private static final List<Block> ALL = List.of(BEACON, CORE_BEACON, CRYSTAL, ALTAR, SUPPLY,
            BLACK_MARKET, BLACK_NODE, BLACK_DROPOFF, CORE_CONSOLE, FREE_EXIT, EVACUATION_CONSOLE, BROKER_EXIT);

    private EchoMachines() { }

    /** 调用只触发类的静态初始化；重复调用不会重复注册。 */
    public static void register() { }

    public static List<Block> all() { return ALL; }

    public static Block block(Kind kind) { return ALL.get(kind.ordinal()); }

    @Nullable
    public static Kind kind(BlockState state) { return KINDS.get(state.getBlock()); }

    private static EchoMachineBlock register(Kind kind) {
        EchoMachineBlock block = Registry.register(BuiltInRegistries.BLOCK, kind.id(),
                new EchoMachineBlock(kind, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                        .strength(3.5F, 6.0F).sound(SoundType.METAL).noOcclusion()
                        .pushReaction(PushReaction.BLOCK)));
        BlockItem item = new EchoMachineItem(block, new Item.Properties());
        item.registerBlocks(Item.BY_BLOCK, item);
        Registry.register(BuiltInRegistries.ITEM, kind.id(), item);
        KINDS.put(block, kind);
        return block;
    }
}
