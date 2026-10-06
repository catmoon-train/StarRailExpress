package org.agmas.noellesroles.content.block_entity;

import io.wifi.starrailexpress.content.block_entity.BeveragePlateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.init.ModBlocks;

/**
 * 厨师「客户端」食物盘 / 饮料盘专用的方块实体。
 *
 * <p>盘子只画在客户端世界里，但它仍然需要一个方块实体来承载「盘里装着什么」，
 * 并交给渲染器画出物品。之所以要单独一个类型而不能直接用原版的
 * {@code trainmurdermystery:beverage_plate}，是因为 {@code BlockEntity} 构造函数会做
 * {@code type.isValid(state)} 校验，而原版那个类型只接受 {@code food_platter} / {@code drink_tray}，
 * 拿厨师的方块状态去 new 会直接抛 {@code IllegalStateException}。
 *
 * <p>盘内物品的渲染参数与原版食物盘 / 饮料盘完全一致（见 {@code ChefPlateRenderer}），
 * 唯一的区别是物品居中摆放而不是沿圆周排开。
 */
public class ChefPlateBlockEntity extends BeveragePlateBlockEntity {

    public ChefPlateBlockEntity(BlockPos pos, BlockState state) {
        // 这里读取 ModBlocks.CHEF_TRAY_BLOCK_ENTITY 是安全的：
        // BlockEntityType.Builder.of(...) 传进来的是 lambda，构造只会在运行时（BE 真正被创建时）
        // 才被调用，那时该字段早已赋值完成。
        super(ModBlocks.CHEF_TRAY_BLOCK_ENTITY, pos, state);
    }
}
