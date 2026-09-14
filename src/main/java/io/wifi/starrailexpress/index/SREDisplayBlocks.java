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

import dev.doctor4t.ratatouille.util.registrar.BlockEntityTypeRegistrar;
import dev.doctor4t.ratatouille.util.registrar.BlockRegistrar;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.block.BlockDisplayBlock;
import io.wifi.starrailexpress.content.block.EntityDisplayBlock;
import io.wifi.starrailexpress.content.block.ItemDisplayBlock;
import io.wifi.starrailexpress.content.block.TextDisplayBlock;
import io.wifi.starrailexpress.content.block_entity.BlockDisplayBlockEntity;
import io.wifi.starrailexpress.content.block_entity.EntityDisplayBlockEntity;
import io.wifi.starrailexpress.content.block_entity.ItemDisplayBlockEntity;
import io.wifi.starrailexpress.content.block_entity.TextDisplayBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.agmas.noellesroles.init.ModSceneBlocks;

/**
 * 展示方块（文本展示方块 / 方块展示方块）的注册表。
 *
 * <p>这两个方块本体不渲染、无碰撞箱，内容由方块实体渲染器按原版展示实体的规则绘制，
 * 供地图作者摆放可以精确对齐到方块坐标的文本或方块模型。两者都放在"SRE 场景方块"创造标签栏里。
 */
public interface SREDisplayBlocks {

    BlockRegistrar blockRegistrar = new BlockRegistrar(SRE.MOD_ID);
    BlockEntityTypeRegistrar blockEntityRegistrar = new BlockEntityTypeRegistrar(SRE.MOD_ID);

    /** 文本展示方块：显示一段 JSON 聊天文本。 */
    Block TEXT_DISPLAY_BLOCK = blockRegistrar.createWithItem("text_display_block",
            new TextDisplayBlock(displayProperties()),
            ModSceneBlocks.SCENE_CREATIVE_GROUP);

    /** 方块展示方块：显示一个方块模型。 */
    Block BLOCK_DISPLAY_BLOCK = blockRegistrar.createWithItem("block_display_block",
            new BlockDisplayBlock(displayProperties()),
            ModSceneBlocks.SCENE_CREATIVE_GROUP);

    /** 物品展示方块：显示一个物品堆（含组件）。 */
    Block ITEM_DISPLAY_BLOCK = blockRegistrar.createWithItem("item_display_block",
            new ItemDisplayBlock(displayProperties()),
            ModSceneBlocks.SCENE_CREATIVE_GROUP);

    /** 实体展示方块：在该方块位置渲染任意实体。 */
    Block ENTITY_DISPLAY_BLOCK = blockRegistrar.createWithItem("entity_display_block",
            new EntityDisplayBlock(displayProperties()),
            ModSceneBlocks.SCENE_CREATIVE_GROUP);

    BlockEntityType<TextDisplayBlockEntity> TEXT_DISPLAY_BLOCK_ENTITY = blockEntityRegistrar
            .create("text_display_block",
                    BlockEntityType.Builder.of(TextDisplayBlockEntity::new, TEXT_DISPLAY_BLOCK));

    BlockEntityType<BlockDisplayBlockEntity> BLOCK_DISPLAY_BLOCK_ENTITY = blockEntityRegistrar
            .create("block_display_block",
                    BlockEntityType.Builder.of(BlockDisplayBlockEntity::new, BLOCK_DISPLAY_BLOCK));

    BlockEntityType<ItemDisplayBlockEntity> ITEM_DISPLAY_BLOCK_ENTITY = blockEntityRegistrar
            .create("item_display_block",
                    BlockEntityType.Builder.of(ItemDisplayBlockEntity::new, ITEM_DISPLAY_BLOCK));

    BlockEntityType<EntityDisplayBlockEntity> ENTITY_DISPLAY_BLOCK_ENTITY = blockEntityRegistrar
            .create("entity_display_block",
                    BlockEntityType.Builder.of(EntityDisplayBlockEntity::new, ENTITY_DISPLAY_BLOCK));

    static void initialize() {
        blockRegistrar.registerEntries();
        blockEntityRegistrar.registerEntries();
    }

    /**
     * 方块本体全透明、无碰撞箱，所以方块贴图只给物品图标用。
     * 破坏方块时掉落自身，但展示数据不随物品走（和原版展示实体一样，拆了就没了）。
     */
    static BlockBehaviour.Properties displayProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.NONE)
                .strength(0.5F)
                .sound(SoundType.STONE)
                .noCollission()
                .noOcclusion()
                .pushReaction(PushReaction.DESTROY);
    }
}
