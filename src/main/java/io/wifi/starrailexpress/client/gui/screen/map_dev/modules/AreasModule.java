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

  // 全局共用的坐标（static 保存，无需文件）
  private static BlockPos pos1 = null;
  private static BlockPos pos2 = null;

  // 输入框引用，用于 Set 按钮点击后刷新显示
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
    int sectionGap = 16; // 全局绑定区与下方区域列表的间距

    int bw = layout.columnWidth(2, gap);
    int leftX = layout.leftColumnX();
    int rightX = layout.rightColumnX(2, gap);
    int fullWidth = 2 * bw + gap;

    int y = 0;

    // ==================== 全局绑定区 ====================
    // 左列：POS1 输入框 + Set POS1
    pos1Field = new EditBox(
        Minecraft.getInstance().font,
        leftX, y, bw, inputHeight,
        Component.empty());
    pos1Field.setMaxLength(50);
    pos1Field.setValue(formatPos(pos1));
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

    // 右列：POS2 输入框 + Set POS2
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

    // 绑定区结束 Y（用于计算区域列表起点）
    int bindEndY = set1Y + btnHeight + smallGap + sectionGap;

    // ==================== 区域 Apply 按钮 ====================
    y = bindEndY;
    for (String cmd : AREA_KEYS) {
      Component areaName = Component.translatable("sre.area." + cmd);

      ModernButton applyBtn = ModernButton.builder(
          Component.translatable("sre.map_helper.area.apply", areaName),
          b -> {
            if (pos1 != null && pos2 != null) {
              ctx.sendAndClose(String.format(
                  "sre:area_manager set %s min %d %d %d max %d %d %d",
                  cmd,
                  pos1.getX(), pos1.getY(), pos1.getZ(),
                  pos2.getX(), pos2.getY(), pos2.getZ()));
            }
          })
          .bounds(leftX, y, fullWidth, btnHeight)
          .accentBar(AccentSide.LEFT)
          .build();
      placements.add(new WidgetPlacement(applyBtn, y));

      y += btnHeight + smallGap;
    }
  }

  @Override
  public int getContentHeight() {
    // 全局绑定区高度：输入框(20) + 小间距(2) + 按钮(22) + 小间距(2) + 区域间距(16) = 62
    // 每个 Apply 按钮高度：按钮(22) + 小间距(2) = 24
    return 62 + AREA_KEYS.length * 24;
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
}