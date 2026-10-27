package org.agmas.noellesroles.game.modes.echo.map.manor;

import org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorLayout.Marker;

/** 不依赖游戏注册表的机器占地与旋转约定；与 EchoMachineStructure 的 part 编码一致。 */
public final class EchoManorMachines {
    private EchoManorMachines() { }

    public enum Model {
        BEACON(3), CORE_BEACON(3), CRYSTAL(3), BLACK_NODE(3), ALTAR(3),
        SUPPLY(2), BLACK_MARKET(2), BLACK_DROPOFF(2), CORE_CONSOLE(2), FREE_EXIT(2),
        EVACUATION_CONSOLE(2), BROKER_EXIT(2);

        private final int side;
        Model(int side) { this.side = side; }
        public int width() { return side; }
        public int height() { return 2; }
        public int depth() { return side; }
    }

    public enum Facing {
        NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);
        public final int stepX;
        public final int stepZ;
        Facing(int stepX, int stepZ) { this.stepX = stepX; this.stepZ = stepZ; }
        public int dx(int x, int z) { return -stepZ * x - stepX * z; }
        public int dz(int x, int z) { return stepX * x - stepZ * z; }
    }

    public record Machine(Marker root, Model model, Facing facing) {
        public int x(int x, int z) { return root.x() + facing.dx(x, z); }
        public int z(int x, int z) { return root.z() + facing.dz(x, z); }
    }

    /** 部件不进入 markers；part=0 才是配置所引用的根点。 */
    public record Cell(Machine machine, int part) {
        public int x() { return machine.x(part % 3, part / 3 % 3); }
        public int y() { return machine.root().y() + part / 9; }
        public int z() { return machine.z(part % 3, part / 3 % 3); }
    }
}
