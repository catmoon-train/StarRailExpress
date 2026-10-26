package org.agmas.noellesroles.game.modes.echo.client;

/** HUD 的逻辑像素边界；原版生命/饥饿条与九格快捷栏之间不放文字。 */
public record EchoHudLayout(int width, int height) {
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
    }
    private int stripWidth() { return Math.min(400, width - 16); }
    private int stripX() { return (width - stripWidth()) / 2; }
    public Rect header() { return new Rect(stripX(), 4, stripWidth(), 34); }
    public Rect utility() { return new Rect(stripX(), height - 68, stripWidth(), 12); }
    public Rect supplies() { return new Rect(stripX(), height - 54, stripWidth(), 12); }
    public Rect health() { return new Rect(width / 2 - 91, height - 39, 27, 9); }
    public Rect hotbar() { return new Rect((width - 202) / 2 - 2, height - 28, 206, 24); }
    /** 右侧小地图在低逻辑分辨率下缩至86px，为狩猎状态与底栏留空间。 */
    public Rect minimap() {
        int size = Math.max(80, Math.min(110, height - 154));
        return new Rect(width - size - 8, 44, size, size);
    }
    public Rect context() { return new Rect(8, 44, minimap().x() - 16, Math.max(0, height - 118)); }
}
