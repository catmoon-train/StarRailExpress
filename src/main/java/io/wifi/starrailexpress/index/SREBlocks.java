/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.index;

import dev.doctor4t.ratatouille.util.registrar.BlockRegistrar;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.block.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import org.agmas.noellesroles.init.ModBlocks;
import org.agmas.noellesroles.init.ModSceneBlocks;

import java.util.function.Function;

public interface SREBlocks {
  public static final BlockRegistrar blockRegistrar = new BlockRegistrar(SRE.MOD_ID);
  public static final dev.doctor4t.ratatouille.util.registrar.BlockEntityTypeRegistrar customBlockEntityRegistrar = new dev.doctor4t.ratatouille.util.registrar.BlockEntityTypeRegistrar(
      SRE.MOD_ID);

  Block TRAIN_LIGHT = registerOpBlock("train_light", new TrainLightBlock(
      (Block.Properties.of().replaceable().strength(-1.0F, 3600000.8F)
          .mapColor(waterloggedMapColor(MapColor.NONE)).noLootTable().noOcclusion()
          .lightLevel(TrainLightBlock.LIGHT_EMISSION))),
      new Item.Properties().rarity(Rarity.EPIC));
  Block REMOTE_REDSTONE = registerOpBlock("remote_redstone", new RemoteRedstoneBlock(
      (Block.Properties.of().strength(-1.0F, 3600000.8F)
          .mapColor(waterloggedMapColor(MapColor.NONE)).noLootTable().noOcclusion())),
      new Item.Properties().rarity(Rarity.EPIC));

  // 列车火把
  Block TRAIN_TORCH = onlyRegisterBlock(SRE.id("train_torch"),
      new TrainTorchBlock(
          Block.Properties.of().noOcclusion().mapColor(waterloggedMapColor(MapColor.NONE)).noCollission()
              .instabreak()
              .sound(SoundType.WOOD).pushReaction(PushReaction.DESTROY)));

  Block WALL_TRAIN_TORCH = onlyRegisterBlock(SRE.id("wall_train_torch"),
      new WallTrainTorchBlock(
          Block.Properties.of().noOcclusion().noCollission().mapColor(waterloggedMapColor(MapColor.NONE))
              .instabreak()
              .sound(SoundType.WOOD).dropsLike(TRAIN_TORCH)
              .pushReaction(PushReaction.DESTROY)));
  // 列车火把拉杆
  Block TRAIN_TORCH_LEVER = registerBlock("train_torch_lever", new TrainTorchLeverBlock(
      (Block.Properties.of()
          .mapColor(waterloggedMapColor(MapColor.NONE)).strength(0.5F).sound(SoundType.STONE)
          .pushReaction(PushReaction.DESTROY).noOcclusion().noCollission())),
      new Item.Properties().rarity(Rarity.COMMON));
  // 列车原版灯笼
  Block TRAIN_VANILLA_LANTERN = registerBlock("train_lantern",
      new TrainLanternBlock(
          Block.Properties.of().mapColor(MapColor.METAL).forceSolidOn().requiresCorrectToolForDrops()
              .strength(3.5F).sound(SoundType.LANTERN)
              .lightLevel((blockStatex) -> SimpleTrainLightBlock.lightBlockSupplier(15, blockStatex))
              .noOcclusion()
              .pushReaction(PushReaction.DESTROY)));
  Block TRAIN_SOUL_LANTERN = registerBlock("train_soul_lantern",
      new TrainLanternBlock(
          Block.Properties.of().mapColor(MapColor.METAL).forceSolidOn().requiresCorrectToolForDrops()
              .strength(3.5F).sound(SoundType.LANTERN)
              .lightLevel((blockStatex) -> SimpleTrainLightBlock.lightBlockSupplier(10, blockStatex))
              .noOcclusion()
              .pushReaction(PushReaction.DESTROY)));
  Block SEA_LANTERN = registerBlock("train_sea_lantern",
      new SimpleTrainLightBlock(
          Block.Properties.of().mapColor(MapColor.QUARTZ).instrument(NoteBlockInstrument.HAT).strength(0.3F)
              .sound(SoundType.GLASS)
              .lightLevel((blockStatex) -> SimpleTrainLightBlock.lightBlockSupplier(15, blockStatex))));
  Block SHROOMLIGHT = registerBlock("train_shroomlight",
      new SimpleTrainLightBlock(
          Block.Properties.of().mapColor(MapColor.COLOR_RED).strength(1.0F).sound(SoundType.SHROOMLIGHT)
              .lightLevel((blockStatex) -> SimpleTrainLightBlock.lightBlockSupplier(15, blockStatex))));
  Block JACK_O_LANTERN = registerBlock("train_jack_o_lantern",
      new TrainCarvedPumpkinBlock(Block.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(1.0F)
          .sound(SoundType.WOOD)
          .lightLevel((blockStatex) -> SimpleTrainLightBlock.lightBlockSupplier(15, blockStatex))
          .pushReaction(PushReaction.DESTROY)));


  @SuppressWarnings("unchecked")
  public static <T extends Block> T registerBlock(String id, T block) {
    return blockRegistrar.createWithItem(id, block, ModBlocks.BLOCK_CREATIVE_GROUP);
  }


  @SuppressWarnings("unchecked")
  public static <T extends Block> T registerBlock(String id, T block, Item.Properties settings) {
    return blockRegistrar.createWithItem(id, block, settings, ModBlocks.BLOCK_CREATIVE_GROUP);
  }


  @SuppressWarnings("unchecked")
  public static <T extends Block> T registerOpBlock(String id, T block, Item.Properties settings) {
    return blockRegistrar.createWithItem(id, block, settings,
        CreativeModeTabs.OP_BLOCKS, ModSceneBlocks.SCENE_CREATIVE_GROUP);
  }

  /**
   * 自定义方块：全模组唯一的那个方块，具体是哪个自定义方块由方块实体里记录的 id 决定。
   * 立即注册（不能延后），因为方块物品要按方块的注册名注册到同一个 id 上。
   *
   * <p>
   * 放在所有既有字段之后声明：接口字段按声明顺序初始化，而物品注册会读取 SREItems
   * （它的静态字段又会读本接口前面的 TRAIN_TORCH 等），提前声明会让那些字段还没赋值就被读到。
   */
  Block CUSTOM_BLOCK = onlyRegisterBlock(SRE.id("custom_block"),
      new io.wifi.starrailexpress.customblock.CustomBlock(
          io.wifi.starrailexpress.customblock.CustomBlock.defaultProperties()));

  net.minecraft.world.level.block.entity.BlockEntityType<io.wifi.starrailexpress.customblock.CustomBlockEntity> CUSTOM_BLOCK_ENTITY = customBlockEntityRegistrar
      .create("custom_block", net.minecraft.world.level.block.entity.BlockEntityType.Builder
          .of(io.wifi.starrailexpress.customblock.CustomBlockEntity::new, CUSTOM_BLOCK));

  static void initialize() {
    // SRE 方块现已合并到 ModBlocks.BLOCK_CREATIVE_GROUP，不再单独注册 starrailexpress:misc_block
    // 标签
    blockRegistrar.registerEntries();
    customBlockEntityRegistrar.registerEntries();
    registerCustomBlockItem();
    SREDoorBlocks.initialize();
  }

  /**
   * 注册自定义方块物品。
   *
   * <p>
   * 用自定义的 {@link io.wifi.starrailexpress.customblock.CustomBlockItem}，所以不能用
   * {@code blockRegistrar.createWithItem}；和 {@link SREItems#registerItem} 一样补上
   * {@code Item.BY_BLOCK}（让 {@code Block.asItem()} 拿得到物品），并放进 OP 方块标签栏。
   */
  private static void registerCustomBlockItem() {
    if (BuiltInRegistries.ITEM.containsKey(SRE.id("custom_block"))) {
      return;
    }
    io.wifi.starrailexpress.customblock.CustomBlockItem item = new io.wifi.starrailexpress.customblock.CustomBlockItem(
        CUSTOM_BLOCK, new Item.Properties().stacksTo(64));
    item.registerBlocks(Item.BY_BLOCK, item);
    Item registered = Registry.register(BuiltInRegistries.ITEM, SRE.id("custom_block"), item);
    net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.OP_BLOCKS)
        .register(entries -> entries.accept(registered));
  }

  private static Function<BlockState, MapColor> waterloggedMapColor(MapColor mapColor) {
    return (blockState) -> (Boolean) blockState.getValue(BlockStateProperties.WATERLOGGED) ? MapColor.WATER
        : mapColor;
  }

  public static Block onlyRegisterBlock(ResourceLocation res, Block block) {
    return Registry.register(BuiltInRegistries.BLOCK, res, block);
  }

  public static Block onlyRegisterBlock(ResourceKey<Block> resourceKey, Block block) {
    return Registry.register(BuiltInRegistries.BLOCK, resourceKey, block);
  }
}
