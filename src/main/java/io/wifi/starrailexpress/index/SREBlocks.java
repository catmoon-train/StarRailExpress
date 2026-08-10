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

  static void initialize() {
    // SRE 方块现已合并到 ModBlocks.BLOCK_CREATIVE_GROUP，不再单独注册 starrailexpress:misc_block
    // 标签
    blockRegistrar.registerEntries();
    SREDoorBlocks.initialize();
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
