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

package io.wifi.starrailexpress.customblock;

import io.wifi.starrailexpress.api.RoleTeam;
import io.wifi.starrailexpress.client.gui.SREPanelStyle;
import io.wifi.starrailexpress.client.gui.screen.CustomEditorScreen;
import io.wifi.starrailexpress.client.render.block.CustomBlockAppearance;
import io.wifi.starrailexpress.client.render.item.CustomBlockItemRenderer;
import io.wifi.starrailexpress.customblock.CustomBlockData.BlockEvent;
import io.wifi.starrailexpress.customblock.CustomBlockData.BlockEventType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 自定义方块编辑界面（四个页签：基础数据 / 外观 / 属性 / 事件）。
 *
 * <p>
 * 布局、页签、滚动、渲染与鼠标键盘全部交给 {@link CustomEditorScreen}，这里只描述字段本身：
 * 每一行用 {@code field / number / toggle / section / note / cluster} 声明，
 * 窄屏由基类自动重排（标签换到字段上方、行内控件折行），不会再出现戳出面板的写死偏移。
 *
 * <p>
 * 所有文案均走翻译键（{@code sre.custom_block.*}），代码中不出现硬编码文案。
 */
@Environment(EnvType.CLIENT)
public class CustomBlockScreen extends CustomEditorScreen {

    private static final String PREFIX = "sre.custom_block";
    private static final String[] TABS = { "basic", "appearance", "properties", "events" };
    private static final int PREVIEW_SIZE = 56;

    private static final BlockEventType[] EVENT_TYPES = BlockEventType.values();
    private static final RoleTeam[] TEAMS = RoleTeam.values();

    private CustomBlockData data = new CustomBlockData();
    private String originalId = "";

    public CustomBlockScreen() {
        super(Component.translatable(PREFIX + ".title"));
    }

    public CustomBlockScreen(CustomBlockData source) {
        super(Component.translatable(PREFIX + ".title"));
        if (source != null) {
            this.data = source;
            this.originalId = source.id == null ? "" : source.id;
        }
    }

    @Override
    protected String translationPrefix() {
        return PREFIX;
    }

    @Override
    protected String[] tabKeys() {
        return TABS;
    }

    @Override
    protected void buildTab(int tab) {
        switch (tab) {
            case 0 -> buildBasicTab();
            case 1 -> buildAppearanceTab();
            case 2 -> buildPropertiesTab();
            default -> buildEventsTab();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 0：基础数据
    // ══════════════════════════════════════════════════════════════════
    private void buildBasicTab() {
        int r = 0;
        r = field(r, PREFIX + ".label.id", data.id, LIMIT_ID,
                Component.translatable(PREFIX + ".hint.id"), value -> data.id = value.toLowerCase());
        r = field(r, PREFIX + ".label.display_name", data.displayName, LIMIT_NAME,
                Component.translatable(PREFIX + ".hint.display_name"), value -> data.displayName = value);

        r = section(r, PREFIX + ".hint.tooltip_title");
        r = lines(r, PREFIX + ".label.tooltip", data.tooltip, LIMIT_TEXT, PREFIX + ".hint.tooltip",
                PREFIX + ".hint.text_line", PREFIX + ".add_line", PREFIX + ".remove", Integer.MAX_VALUE);

        r = section(r, PREFIX + ".hint.fixed_behaviour");
        r = yesNoRow(r, PREFIX + ".label.rotate", data.rotate, value -> data.rotate = value);
        r = note(r, PREFIX + ".hint.waterlogged", SREPanelStyle.MUTED);
        r = note(r, PREFIX + ".hint.drop_self", SREPanelStyle.MUTED);
    }

    /**
     * 「标签 + 是/否」开关行：标签在标签列、小方块左对齐在字段区（这类行不参与自动并排，一行一个更清楚）。
     */
    private int yesNoRow(int r, String labelKey, boolean current, Consumer<Boolean> setter) {
        return cluster(r, labelKey, yesNoSwitchCell(current,
                on -> Component.translatable(Boolean.TRUE.equals(on) ? PREFIX + ".value.yes" : PREFIX + ".value.no"),
                setter, false));
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 1：外观
    // ══════════════════════════════════════════════════════════════════
    private void buildAppearanceTab() {
        int r = 0;
        r = section(r, PREFIX + ".hint.appearance_priority");
        r = field(r, PREFIX + ".label.pack_texture", data.packTexturePath, LIMIT_PATH,
                Component.translatable(PREFIX + ".hint.pack_texture"), value -> data.packTexturePath = value);
        r = field(r, PREFIX + ".label.inherit_block", data.inheritBlock, LIMIT_PATH,
                Component.translatable(PREFIX + ".hint.inherit_block"),
                value -> data.inheritBlock = value.trim().toLowerCase());
        r = note(r, PREFIX + ".hint.inherit_block_note", SREPanelStyle.MUTED);

        r = number(r, PREFIX + ".label.light_level", String.valueOf(data.lightLevel), PREFIX + ".unit.level",
                value -> data.lightLevel = parseInt(value, data.lightLevel));
        r = yesNoRow(r, PREFIX + ".label.light_blackout", data.lightAffectedByBlackout,
                value -> data.lightAffectedByBlackout = value);
        r = note(r, PREFIX + ".hint.light_blackout", SREPanelStyle.MUTED);

        // 预览：宽屏贴在右侧专属列，窄屏落回这里占一行
        r = previewRow(r, PREFIX + ".label.preview");
        r = note(r, previewLine(PREFIX + ".preview.inherit"), SREPanelStyle.MUTED);
        r = note(r, previewLine(PREFIX + ".preview.texture"), SREPanelStyle.MUTED);
        r = note(r, previewLine(PREFIX + ".preview.light"), SREPanelStyle.MUTED);
        r = note(r, previewLine(PREFIX + ".preview.sound"), SREPanelStyle.MUTED);
    }

    private Component previewLine(String key) {
        String inherit = data.inheritBlock == null || data.inheritBlock.isBlank()
                ? Component.translatable(PREFIX + ".value.any").getString()
                : data.inheritBlock;
        String texture = data.packTexturePath == null || data.packTexturePath.isBlank()
                ? Component.translatable(PREFIX + ".value.any").getString()
                : data.packTexturePath;
        return Component.translatable(key, inherit, texture, data.lightLevel);
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 2：属性
    // ══════════════════════════════════════════════════════════════════
    private void buildPropertiesTab() {
        int r = 0;
        r = yesNoRow(r, PREFIX + ".label.no_collision", data.noCollision, value -> data.noCollision = value);
        r = note(r, PREFIX + ".hint.no_collision", SREPanelStyle.MUTED);
        r = yesNoRow(r, PREFIX + ".label.blocks_skylight", data.blocksSkylight, value -> data.blocksSkylight = value);
        r = note(r, PREFIX + ".hint.blocks_skylight", SREPanelStyle.MUTED);
        r = note(r, PREFIX + ".hint.hardness_fixed", SREPanelStyle.MUTED);
        r = note(r, PREFIX + ".hint.shape_from_inherit", SREPanelStyle.MUTED);
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 3：事件
    // ══════════════════════════════════════════════════════════════════
    private void buildEventsTab() {
        int r = 0;
        r = section(r, PREFIX + ".hint.events");
        if (data.events == null) {
            data.events = new ArrayList<>();
        }
        if (data.events.isEmpty()) {
            r = note(r, PREFIX + ".events.empty", SREPanelStyle.MUTED);
        }
        for (int i = 0; i < data.events.size(); i++) {
            r = eventBlock(r, data.events.get(i), i);
        }
        r = cluster(r, null, fixedButton(Component.translatable(PREFIX + ".event.add"), 200, () -> {
            if (data.events.size() >= CustomBlockData.MAX_EVENTS) {
                showMessage(Component.translatable(PREFIX + ".error.too_many_events",
                        CustomBlockData.MAX_EVENTS));
                return;
            }
            data.events.add(new BlockEvent());
            requestRebuild();
        }));
    }

    /** 单个事件的编辑块（类型 / 指令 / 冷却 / 条件，字段按类型显示）。 */
    private int eventBlock(int r, BlockEvent event, int index) {
        BlockEventType type = event.type();

        // 一个事件一张卡片：标题「事件 N」+ 类型徽标，卡片头右侧可直接删掉这一块
        r = cardBegin(r, "block_event_" + index,
                Component.translatable(PREFIX + ".event.title", index + 1),
                Component.translatable(PREFIX + ".event_type." + type.name().toLowerCase()),
                () -> data.events.remove(index));
        r = cluster(r, null,
                stateButtonCell(() -> Component.translatable(
                        PREFIX + ".event_type." + event.type().name().toLowerCase()), () -> {
                            event.setType(EVENT_TYPES[(event.type().ordinal() + 1) % EVENT_TYPES.length]);
                        }, true));

        r = lines(r, PREFIX + ".label.commands", event.commands, LIMIT_COMMAND, PREFIX + ".hint.commands",
                PREFIX + ".hint.text_line", PREFIX + ".add_command", PREFIX + ".remove", Integer.MAX_VALUE);

        r = number(r, PREFIX + ".label.cooldown", String.valueOf(event.cooldownTicks), PREFIX + ".unit.tick",
                value -> event.cooldownTicks = parseInt(value, event.cooldownTicks));
        if (type.usesSneakOnly()) {
            r = yesNoRow(r, PREFIX + ".label.sneak_only", event.sneakOnly, value -> event.sneakOnly = value);
        }
        if (type.usesConsume()) {
            r = yesNoRow(r, PREFIX + ".label.consume_block", event.consumeBlock,
                    value -> event.consumeBlock = value);
        }
        if (type.usesRadius()) {
            r = number(r, PREFIX + ".label.radius", num(event.radius), PREFIX + ".unit.blocks",
                    value -> event.radius = parseDouble(value, event.radius));
        }
        if (type.usesOncePerPlayer()) {
            r = yesNoRow(r, PREFIX + ".label.once_per_player", event.oncePerPlayer,
                    value -> event.oncePerPlayer = value);
        }

        r = section(r, PREFIX + ".label.conditions");
        r = yesNoRow(r, PREFIX + ".label.game_running_only", event.gameRunningOnly,
                value -> event.gameRunningOnly = value);
        r = lines(r, PREFIX + ".label.required_roles", event.requiredRoles, LIMIT_TEXT,
                PREFIX + ".hint.required_roles", PREFIX + ".hint.text_line", PREFIX + ".add_line",
                PREFIX + ".remove", Integer.MAX_VALUE);
        r = teamRow(r, PREFIX + ".label.required_team", event.requiredTeams);
        r = note(r, PREFIX + ".hint.conditions_note", SREPanelStyle.MUTED);
        return gap(cardEnd(gap(r)));
    }

    /** 「阵营轮回」按钮：空 = 任意阵营，点一下切到下一个阵营。 */
    private int teamRow(int r, String labelKey, List<String> list) {
        Supplier<Component> text = () -> teamName(list.isEmpty() ? "" : list.get(0));
        return stateButton(r, labelKey, text, () -> {
            String current = list.isEmpty() ? "" : list.get(0);
            int index = -1;
            for (int i = 0; i < TEAMS.length; i++) {
                if (TEAMS[i].name().equals(current)) {
                    index = i;
                    break;
                }
            }
            list.clear();
            int next = index + 1;
            if (next < TEAMS.length) {
                list.add(TEAMS[next].name());
            }
        });
    }

    private static Component teamName(String value) {
        if (value == null || value.isBlank()) {
            return Component.translatable(PREFIX + ".value.any");
        }
        return Component.translatable(PREFIX + ".team." + value.toLowerCase());
    }

    // ══════════════════════════════════════════════════════════════════
    // 预览
    // ══════════════════════════════════════════════════════════════════

    @Override
    protected int previewSize() {
        return PREVIEW_SIZE;
    }

    @Override
    protected boolean showPreview(int tab) {
        return tab == 1;
    }

    /** 外观预览（资源包贴图 / 继承方块主贴图 / 未配置占位）。 */
    @Override
    protected void renderPreviewContent(GuiGraphics g, int x, int y, int size) {
        ResourceLocation packTexture = CustomBlockAppearance.resolvePackTexture(data.packTexturePath);
        if (packTexture != null) {
            g.blit(packTexture, x, y, size, size, 0.0F, 0.0F, 16, 16, 16, 16);
            return;
        }
        TextureAtlasSprite sprite = resolveInheritedSprite(data.inheritBlock);
        if (sprite != null) {
            g.blit(x, y, 0, size, size, sprite);
            return;
        }
        g.blit(CustomBlockAppearance.PLACEHOLDER_TEXTURE, x, y, size, size, 0.0F, 0.0F, 16, 16, 16, 16);
    }

    /** 取继承方块模型的主贴图（用于 2D 预览）。 */
    private static TextureAtlasSprite resolveInheritedSprite(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return null;
        }
        ResourceLocation location = ResourceLocation.tryParse(blockId.trim());
        if (location == null) {
            location = ResourceLocation.tryBuild("minecraft", blockId.trim().toLowerCase());
        }
        if (location == null) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.get(location);
        if (block == null || block == Blocks.AIR) {
            return null;
        }
        try {
            var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(block.defaultBlockState());
            return model == null ? null : model.getParticleIcon();
        } catch (Exception e) {
            return null;
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 保存 / 管理
    // ══════════════════════════════════════════════════════════════════

    @Override
    protected void onSave() {
        if (data.id == null || data.id.isBlank()) {
            showMessage(Component.translatable(PREFIX + ".error.empty_id"));
            return;
        }
        data.sanitize();
        CustomBlockConfig config = CustomBlockConfig.getInstance();
        if (config.isIdTaken(data.id, originalId)) {
            showMessage(Component.translatable(PREFIX + ".error.duplicate_id", data.id));
            return;
        }
        if (!originalId.isBlank()) {
            config.removeBlock(originalId);
        }
        config.removeBlock(data.id);
        config.addBlock(data);
        config.savePreferWorldPath(minecraft.getSingleplayerServer());

        try {
            config.saveToDefaultPath();
            CustomBlockLoader.reloadClient();
            CustomBlockAppearance.clearCache();
            CustomBlockItemRenderer.clearCache();
        } catch (Exception ignored) {
        }
        var server = minecraft.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                try {
                    CustomBlockReloadCommand.reload(server);
                } catch (Exception ignored) {
                }
            });
        }
        originalId = data.id;
        showMessage(Component.translatable(PREFIX + ".saved", data.id));
        onClose();
    }

    @Override
    protected void onOpenManage() {
        CustomBlockConfig config = CustomBlockConfig.getInstance();
        config.savePreferWorldPath(minecraft.getSingleplayerServer());
        minecraft.setScreen(new CustomBlockManageScreen(() -> new CustomBlockScreen()));
    }

    private void showMessage(Component message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(message, false);
        }
    }
}
