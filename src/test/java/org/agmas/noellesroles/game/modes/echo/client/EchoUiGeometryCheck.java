package org.agmas.noellesroles.game.modes.echo.client;

/** 不依赖游戏与JUnit的布局回归入口，可直接用javac/java执行。 */
public final class EchoUiGeometryCheck {
    @org.junit.jupiter.api.Test
    void allSupportedHudAndModalLayoutsFit() { main(new String[0]); }
    private static void check(boolean value) { if (!value) throw new AssertionError("回声UI边界/间距错误"); }
    private static void inside(EchoHudLayout.Rect r, int width, int height) {
        check(r.x() >= 0 && r.y() >= 0 && r.width() > 0 && r.height() > 0);
        check(r.right() <= width && r.bottom() <= height);
    }
    public static void main(String[] args) {
        int count = 0;
        for (int width = 320; width <= 1920; width += 7) {
            for (int height = 240; height <= 1080; height += 11) {
                var h = new EchoHudLayout(width, height);
                for (var r : new EchoHudLayout.Rect[] {h.header(), h.context(), h.minimap(), h.utility(), h.supplies(), h.health(), h.hotbar()}) inside(r, width, height);
                check(h.header().height() <= 40 && h.header().bottom() < h.context().y());
                check(h.header().bottom() + 3 < h.minimap().y());
                check(h.context().right() + 3 < h.minimap().x());
                check(h.minimap().width() >= 80 && h.minimap().width() <= 110);
                check(h.minimap().height() == h.minimap().width());
                check(h.context().bottom() < h.utility().y());
                check(h.utility().bottom() < h.supplies().y());
                check(h.supplies().bottom() < h.health().y() && h.health().bottom() < h.hotbar().y());
                var l = EchoInventoryLayout.of(width, height);
                check(l.x() >= 0 && l.y() >= 0 && l.x() + l.width() <= width && l.y() + l.height() <= height);
                check(l.slotSize() >= 24);
                for (int slot = 0; slot < 9; slot++) {
                    check(l.slotX(slot) >= l.x() + 8 && l.slotX(slot) + l.slotSize() <= l.x() + l.width() - 8);
                    if (slot > 0) check(l.slotX(slot - 1) + l.slotSize() < l.slotX(slot));
                }
                check(l.slotX(3) - l.slotX(2) - l.slotSize() >= 14);
                check(l.contentY() + 50 <= l.detailBottom() && l.detailBottom() < l.actionsY());
                check(l.actionsY() + 18 <= l.hintY() && l.hintY() + 10 <= l.labelY());
                check(l.labelY() + 10 <= l.slotY() && l.slotY() + l.slotSize() < l.y() + l.height() - 13);
                check(l.modalX() > l.x() && l.modalX() + l.modalWidth() < l.x() + l.width());
                check(l.modalY() > l.y() && l.modalY() + l.modalHeight() < l.y() + l.height());
                check(l.modalY() + 120 < l.modalButtonY() && l.modalButtonY() + 18 < l.modalY() + l.modalHeight());
                check(l.y() + 109 <= l.actionsY() - 8);
                check(l.choiceBottom() < l.hintY());
                for (int n = 0; n < 3; n++) {
                    check(l.choiceX(n) >= l.x() + 8 && l.choiceX(n) + l.choiceWidth() <= l.x() + l.width() - 8);
                    if (n > 0) check(l.choiceX(n - 1) + l.choiceWidth() < l.choiceX(n));
                }
                count++;
            }
        }
        System.out.println("Echo UI geometry passed: " + count + " logical screen sizes");
    }
}
