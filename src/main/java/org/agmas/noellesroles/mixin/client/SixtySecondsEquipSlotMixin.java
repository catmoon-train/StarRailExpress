package org.agmas.noellesroles.mixin.client;

import com.mojang.datafixers.util.Pair;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedHandledScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 末日60秒模式：给 E 键受限背包（{@link LimitedInventoryScreen}）补上两排额外槽——
 * <b>背包第二排</b>（游戏日放行的容器槽 9..17，见 {@code SixtySecondsInventoryLimit.DAY_ALLOWED_SLOTS}）
 * 和一排「装备槽」（头/胸/腿/脚护甲 + 副手），让玩家能在此界面存取第二排物品并直接穿脱装备。
 *
 * <p>受限背包默认只绘制/命中快捷栏槽（见 {@link LimitedHandledScreen} 里的 {@code isHotbarSlot}），
 * 其余槽既不画也点不到。这里只在 60s 模式下：
 * <ul>
 *   <li>在快捷栏条下方额外画出背包第二排（9 格）与装备槽（5 格，空槽显示原版占位轮廓）；</li>
 *   <li>往 {@code getSlotAt} 注入——鼠标落在这些槽上时返回对应槽，
 *       于是父类既有的拾取/放置状态机自动生效，无需另写点击逻辑。</li>
 * </ul>
 * 服务端侧的容器点击在 60s 模式已由 {@code AbstractContainerMenuMixin} 放行，故仅需补客户端表现。
 * 装备槽自带 {@code mayPlace} 校验（护甲槽只收对应护甲），不会被塞入非法物品。
 */
@Mixin(LimitedHandledScreen.class)
public abstract class SixtySecondsEquipSlotMixin {
    /** 装备槽在 {@code InventoryMenu} 里的菜单槽序号：头(5)/胸(6)/腿(7)/脚(8) + 副手(45)。 */
    @Unique
    private static final int[] sixtyseconds$EQUIP_SLOTS = { 5, 6, 7, 8, 45 };
    /** 背包第二排在 {@code InventoryMenu} 里的菜单槽序号（=容器槽 9..17，游戏日放行的两排中的上排）。 */
    @Unique
    private static final int[] sixtyseconds$BACKPACK_SLOTS = { 9, 10, 11, 12, 13, 14, 15, 16, 17 };
    @Unique
    private static final int sixtyseconds$SLOT_STEP = 18;
    /** 两排附加槽的行高（16 槽 + 双描边 3+3 + 2 间距）。 */
    @Unique
    private static final int sixtyseconds$ROW_STEP = 24;

    @Shadow
    protected int x;
    @Shadow
    protected int y;
    @Shadow
    protected int backgroundWidth;
    @Shadow
    protected int backgroundHeight;
    @Shadow
    @Final
    protected AbstractContainerMenu handler;

    /** 仅在正处于 60s 模式（且已开局）的受限背包里启用装备槽。条件与 SixtySecondsHud 一致。 */
    @Unique
    private boolean sixtyseconds$equipEnabled() {
        if (!(((Object) this) instanceof LimitedInventoryScreen)) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        var game = SREClient.gameComponent;
        return game != null && game.isRunning()
                && SixtySecondsMod.MODE != null && game.getGameMode() == SixtySecondsMod.MODE;
    }

    /** 一排附加槽的左起 X（在快捷栏条下方居中）。 */
    @Unique
    private int sixtyseconds$rowStartX(int slotCount) {
        return this.x + (this.backgroundWidth - slotCount * sixtyseconds$SLOT_STEP) / 2 + 1;
    }

    /** 背包第二排的顶部 Y（快捷栏条正下方留出描边 3px + 间距 3px）。 */
    @Unique
    private int sixtyseconds$backpackRowY() {
        return this.y + this.backgroundHeight + 6;
    }

    /** 装备槽这一排的顶部 Y（背包第二排之下）。 */
    @Unique
    private int sixtyseconds$rowY() {
        return sixtyseconds$backpackRowY() + sixtyseconds$ROW_STEP;
    }

    // ── 「兑换实体币」按钮（装备槽下方；打开 TokenExchangeScreen 把余额兑成实体币）──
    @Unique
    private static final int sixtyseconds$COIN_BTN_W = 90;
    @Unique
    private static final int sixtyseconds$COIN_BTN_H = 14;

    @Unique
    private int sixtyseconds$coinButtonX() {
        return this.x + (this.backgroundWidth - sixtyseconds$COIN_BTN_W) / 2;
    }

    @Unique
    private int sixtyseconds$coinButtonY() {
        return sixtyseconds$rowY() + sixtyseconds$ROW_STEP;
    }

    @Unique
    private boolean sixtyseconds$inCoinButton(double mouseX, double mouseY) {
        int bx = sixtyseconds$coinButtonX();
        int by = sixtyseconds$coinButtonY();
        return mouseX >= bx && mouseX < bx + sixtyseconds$COIN_BTN_W
                && mouseY >= by && mouseY < by + sixtyseconds$COIN_BTN_H;
    }

    /** 点击「兑换实体币」→ 打开兑换界面（余额读本人 CCA，无需 S2C）。 */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void sixtyseconds$clickCoinButton(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (button == 0 && sixtyseconds$equipEnabled() && sixtyseconds$inCoinButton(mouseX, mouseY)) {
            Minecraft mc = Minecraft.getInstance();
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            mc.setScreen(new net.exmo.sre.sixtyseconds.client.screen.TokenExchangeScreen());
            cir.setReturnValue(true);
        }
    }

    /** 一排槽的命中测试：鼠标落在某槽内则返回对应 Slot，否则 null。 */
    @Unique
    private Slot sixtyseconds$rowSlotAt(int[] menuSlots, int startX, int rowY, double mouseX, double mouseY) {
        for (int i = 0; i < menuSlots.length; i++) {
            int sx = startX + i * sixtyseconds$SLOT_STEP;
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= rowY && mouseY < rowY + 16) {
                return this.handler.slots.get(menuSlots[i]);
            }
        }
        return null;
    }

    /** 命中测试：背包第二排 + 装备槽。 */
    @Unique
    private Slot sixtyseconds$equipSlotAt(double mouseX, double mouseY) {
        if (!sixtyseconds$equipEnabled()) {
            return null;
        }
        Slot backpack = sixtyseconds$rowSlotAt(sixtyseconds$BACKPACK_SLOTS,
                sixtyseconds$rowStartX(sixtyseconds$BACKPACK_SLOTS.length), sixtyseconds$backpackRowY(),
                mouseX, mouseY);
        if (backpack != null) {
            return backpack;
        }
        return sixtyseconds$rowSlotAt(sixtyseconds$EQUIP_SLOTS,
                sixtyseconds$rowStartX(sixtyseconds$EQUIP_SLOTS.length), sixtyseconds$rowY(), mouseX, mouseY);
    }

    /** 让 getSlotAt 也认得装备槽——父类的拾取/放置/拖拽状态机由此自动覆盖装备槽。 */
    @Inject(method = "getSlotAt", at = @At("HEAD"), cancellable = true)
    private void sixtyseconds$includeEquipSlots(double mouseX, double mouseY, CallbackInfoReturnable<Slot> cir) {
        Slot slot = sixtyseconds$equipSlotAt(mouseX, mouseY);
        if (slot != null) {
            cir.setReturnValue(slot);
        }
    }

    /** 在快捷栏条下方补画背包第二排 + 装备槽（含空槽占位轮廓、hover 高亮与物品 tooltip）。 */
    @Inject(method = "render", at = @At("TAIL"))
    private void sixtyseconds$renderEquipSlots(GuiGraphics context, int mouseX, int mouseY, float delta,
            CallbackInfo ci) {
        if (!sixtyseconds$equipEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ItemStack hovered = sixtyseconds$renderRow(context, mc, sixtyseconds$BACKPACK_SLOTS,
                sixtyseconds$rowStartX(sixtyseconds$BACKPACK_SLOTS.length), sixtyseconds$backpackRowY(),
                mouseX, mouseY);
        ItemStack hoveredEquip = sixtyseconds$renderRow(context, mc, sixtyseconds$EQUIP_SLOTS,
                sixtyseconds$rowStartX(sixtyseconds$EQUIP_SLOTS.length), sixtyseconds$rowY(), mouseX, mouseY);
        if (hovered.isEmpty()) {
            hovered = hoveredEquip;
        }
        // 「兑换实体币」按钮（ui_style 配色：深棕底 + 棕褐描边，hover 金边）
        int bx = sixtyseconds$coinButtonX();
        int by = sixtyseconds$coinButtonY();
        boolean hoveredBtn = sixtyseconds$inCoinButton(mouseX, mouseY);
        context.fillGradient(bx, by, bx + sixtyseconds$COIN_BTN_W, by + sixtyseconds$COIN_BTN_H,
                0xD81A1008, 0xD820140A);
        context.renderOutline(bx, by, sixtyseconds$COIN_BTN_W, sixtyseconds$COIN_BTN_H,
                hoveredBtn ? 0xFFD4AF37 : 0xFF8B6914);
        context.drawCenteredString(mc.font,
                net.minecraft.network.chat.Component.translatable(
                        "message.noellesroles.sixty_seconds.coin_exchange_button"),
                bx + sixtyseconds$COIN_BTN_W / 2, by + (sixtyseconds$COIN_BTN_H - 8) / 2 + 1,
                hoveredBtn ? 0xFFFFF4DC : 0xFFC8B898);
        // 仅在手上没抓着物品时显示 tooltip，避免遮挡拖拽
        if (!hovered.isEmpty() && this.handler.getCarried().isEmpty()) {
            context.renderTooltip(mc.font, hovered, mouseX, mouseY);
        }
    }

    /**
     * 限定背包背景贴图（{@code limited_inventory.png}）里槽位框的起点与尺寸：
     * 快捷栏槽内容绘制在背景 (8,8) 起、步进 18，故含 1px 描边的槽位框在贴图 (7,7) 处、每格 18×18。
     * 附加槽直接从这条槽位框带上按格数横向切割，与快捷栏观感完全一致。
     */
    @Unique
    private static final int sixtyseconds$SLOT_TEX_U = 7;
    @Unique
    private static final int sixtyseconds$SLOT_TEX_V = 7;

    /** 画一排附加槽，返回当前 hover 的物品（无则 EMPTY）。 */
    @Unique
    private ItemStack sixtyseconds$renderRow(GuiGraphics context, Minecraft mc, int[] menuSlots,
            int startX, int rowY, int mouseX, int mouseY) {
        ItemStack hovered = ItemStack.EMPTY;
        int w = menuSlots.length * sixtyseconds$SLOT_STEP;
        // 外深内金双描边：复刻背景面板自身的边框配色（贴图外缘 0x381406 + 金线 0xC5A244），
        // 否则附加行只有近黑的槽格条，贴在深色界面上看不出边界
        context.renderOutline(startX - 3, rowY - 3, w + 4, 22, 0xFF381406);
        context.renderOutline(startX - 2, rowY - 2, w + 2, 20, 0xFFC5A244);
        // 槽位底：从背景贴图的快捷栏槽位框整条切割（按槽数取宽），替代此前的纯色 fill
        context.blit(LimitedInventoryScreen.BACKGROUND_TEXTURE, startX - 1, rowY - 1,
                sixtyseconds$SLOT_TEX_U, sixtyseconds$SLOT_TEX_V, w, 18);
        for (int i = 0; i < menuSlots.length; i++) {
            Slot slot = this.handler.slots.get(menuSlots[i]);
            int sx = startX + i * sixtyseconds$SLOT_STEP;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                Pair<ResourceLocation, ResourceLocation> icon = slot.getNoItemIcon();
                if (icon != null) {
                    // 占位剪影是纯 #555555，直接画在近黑（#160902）槽底上不可见；
                    // 先垫一层浅褐底（ui_style MUTED），对比度即恢复到原版观感
                    context.fill(sx, rowY, sx + 16, rowY + 16, 0xFF9E8B6E);
                    TextureAtlasSprite sprite = mc.getTextureAtlas(icon.getFirst()).apply(icon.getSecond());
                    context.blit(sx, rowY, 0, 16, 16, sprite);
                }
            } else {
                context.renderItem(stack, sx, rowY);
                context.renderItemDecorations(mc.font, stack, sx, rowY);
            }
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= rowY && mouseY < rowY + 16) {
                LimitedHandledScreen.drawSlotHighlight(context, sx, rowY, 0);
                if (!stack.isEmpty()) {
                    hovered = stack;
                }
            }
        }
        return hovered;
    }
}
