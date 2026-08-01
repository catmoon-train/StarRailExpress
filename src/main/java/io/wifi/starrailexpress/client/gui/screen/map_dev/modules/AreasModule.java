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

package io.wifi.starrailexpress.client.gui.screen.map_dev.modules;

import io.wifi.starrailexpress.client.gui.screen.map_dev.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton.AccentSide;

import java.util.List;

public class AreasModule implements TabModule {
  private static final String[] AREA_KEYS = {
      "readyArea", "playArea", "sceneArea", "resetTemplateArea", "resetPasteArea"
  };

  // 静态存储（全局共用，关闭屏幕不丢失）
  private static BlockPos pos1 = null;
  private static BlockPos pos2 = null;

  // 输入框引用
  private EditBox pos1Field;
  private EditBox pos2Field;

  @Override
  public Component getTabTitle() {
    return Component.translatable("sre.map_helper.tab.areas");
  }

  @Override
  public void init(LayoutContext layout, ModuleContext ctx, List<WidgetPlacement> placements) {
    int gap = 10;
    int inputHeight = 20;
    int btnHeight = 22;
    int smallGap = 2;
    int sectionGap = 16;

    int bw = layout.columnWidth(2, gap);
    int leftX = layout.leftColumnX();
    int rightX = layout.rightColumnX(2, gap);
    int fullWidth = 2 * bw + gap;

    int y = 0;

    // ==================== 全局绑定区 ====================
    pos1Field = new EditBox(
        Minecraft.getInstance().font,
        leftX, y, bw, inputHeight,
        Component.empty());
    pos1Field.setMaxLength(50);
    pos1Field.setValue(formatPos(pos1)); // 显示已保存的坐标
    placements.add(new WidgetPlacement(pos1Field, y));

    int set1Y = y + inputHeight + smallGap;
    ModernButton setPos1Btn = ModernButton.builder(
        Component.translatable("sre.map_helper.area.set_pos1"),
        b -> {
          pos1 = blockPosFromContext(ctx);
          pos1Field.setValue(formatPos(pos1));
        })
        .bounds(leftX, set1Y, bw, btnHeight)
        .accentBar(AccentSide.LEFT)
        .build();
    placements.add(new WidgetPlacement(setPos1Btn, set1Y));

    pos2Field = new EditBox(
        Minecraft.getInstance().font,
        rightX, y, bw, inputHeight,
        Component.empty());
    pos2Field.setMaxLength(50);
    pos2Field.setValue(formatPos(pos2));
    placements.add(new WidgetPlacement(pos2Field, y));

    int set2Y = y + inputHeight + smallGap;
    ModernButton setPos2Btn = ModernButton.builder(
        Component.translatable("sre.map_helper.area.set_pos2"),
        b -> {
          pos2 = blockPosFromContext(ctx);
          pos2Field.setValue(formatPos(pos2));
        })
        .bounds(rightX, set2Y, bw, btnHeight)
        .accentBar(AccentSide.RIGHT)
        .build();
    placements.add(new WidgetPlacement(setPos2Btn, set2Y));

    int bindEndY = set1Y + btnHeight + smallGap + sectionGap;

    // ==================== 区域 Apply 按钮 ====================
    y = bindEndY;
    for (String cmd : AREA_KEYS) {
      Component areaName = Component.translatable("sre.area." + cmd);

      ModernButton applyBtn = ModernButton.builder(
          Component.translatable("sre.map_helper.area.apply", areaName),
          b -> applyArea(cmd, ctx)) // 提取为独立方法
          .bounds(leftX, y, fullWidth, btnHeight)
          .accentBar(AccentSide.LEFT)
          .build();
      placements.add(new WidgetPlacement(applyBtn, y));

      y += btnHeight + smallGap;
    }
    ctx.registerCloseHook(this::saveData);
  }

  private void saveData() {
    BlockPos p1 = parseBlockPos(pos1Field.getValue());
    BlockPos p2 = parseBlockPos(pos2Field.getValue());

    if (p1 != null)
      pos1 = p1; // 回退到静态存储
    if (p2 != null)
      pos2 = p2;
  }

  /**
   * 解析输入框并发送区域设置命令
   */
  private void applyArea(String cmd, ModuleContext ctx) {
    // 优先尝试从输入框解析坐标，失败则使用静态存储
    BlockPos p1 = parseBlockPos(pos1Field.getValue());
    BlockPos p2 = parseBlockPos(pos2Field.getValue());

    if (p1 == null)
      p1 = pos1; // 回退到静态存储
    if (p2 == null)
      p2 = pos2;

    if (p1 != null && p2 != null) {
      // 若输入框有效，将解析结果同步回静态存储
      pos1 = p1;
      pos2 = p2;
      ctx.sendAndClose(String.format(
          "sre:area_manager set %s min %d %d %d max %d %d %d",
          cmd,
          p1.getX(), p1.getY(), p1.getZ(),
          p2.getX(), p2.getY(), p2.getZ()));
    }
  }

  @Override
  public int getContentHeight() {
    return 62 + AREA_KEYS.length * 24; // 与之前相同
  }

  // ========== 工具方法 ==========

  private BlockPos blockPosFromContext(ModuleContext ctx) {
    return new BlockPos(
        (int) Math.floor(ctx.ax()),
        (int) Math.floor(ctx.ay()),
        (int) Math.floor(ctx.az()));
  }

  private String formatPos(BlockPos pos) {
    return pos == null ? "" : pos.getX() + " " + pos.getY() + " " + pos.getZ();
  }

  /**
   * 将 "x y z" 字符串解析为 BlockPos，非法格式返回 null
   */
  private BlockPos parseBlockPos(String input) {
    if (input == null || input.isBlank())
      return null;
    String[] parts = input.trim().split("\\s+");
    if (parts.length != 3)
      return null;
    try {
      int x = Integer.parseInt(parts[0]);
      int y = Integer.parseInt(parts[1]);
      int z = Integer.parseInt(parts[2]);
      return new BlockPos(x, y, z);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}