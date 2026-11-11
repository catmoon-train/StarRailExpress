package pro.fazeclan.river.stupid_express.role.arsonist;

import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.packet.MechanicalBirdControlC2SPacket;

/** 大油罐的镜头相对输入与速度响应；纯计算，不访问玩家本体朝向或游戏注册表。 */
public final class OilCanFlightControl {
    public static final double MAX_SPEED = 0.4D;
    private static final double RESPONSE = 0.55D;
    public static final int INPUT_MASK = MechanicalBirdControlC2SPacket.BIT_FORWARD
            | MechanicalBirdControlC2SPacket.BIT_BACK | MechanicalBirdControlC2SPacket.BIT_LEFT
            | MechanicalBirdControlC2SPacket.BIT_RIGHT | MechanicalBirdControlC2SPacket.BIT_JUMP
            | MechanicalBirdControlC2SPacket.BIT_SNEAK;

    private OilCanFlightControl() {}

    /** 前后沿当前视线，左右沿镜头水平右向量，空格/潜行沿世界竖直方向；斜向不加速。 */
    public static Vec3 input(float yaw, float pitch, int bits) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) return Vec3.ZERO;
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(Math.clamp(pitch, -85.0F, 85.0F));
        int forward = axis(bits, MechanicalBirdControlC2SPacket.BIT_FORWARD, MechanicalBirdControlC2SPacket.BIT_BACK);
        int strafe = axis(bits, MechanicalBirdControlC2SPacket.BIT_RIGHT, MechanicalBirdControlC2SPacket.BIT_LEFT);
        int vertical = axis(bits, MechanicalBirdControlC2SPacket.BIT_JUMP, MechanicalBirdControlC2SPacket.BIT_SNEAK);
        // MC yaw=0 面向+Z，此时屏幕右侧是-X，不能使用反号的左向量。
        return new Vec3(-Math.sin(y) * Math.cos(p) * forward - Math.cos(y) * strafe,
                -Math.sin(p) * forward + vertical,
                Math.cos(y) * Math.cos(p) * forward - Math.sin(y) * strafe).normalize();
    }

    /** 跟随目标速度而非不断叠加微弱推力，快速转向和制动，同时保留少量平滑。 */
    public static Vec3 nextVelocity(Vec3 current, Vec3 input) {
        Vec3 target = input.scale(MAX_SPEED);
        Vec3 next = current.lerp(target, RESPONSE);
        return next.lengthSqr() > MAX_SPEED * MAX_SPEED ? next.normalize().scale(MAX_SPEED) : next;
    }

    private static int axis(int bits, int positive, int negative) {
        return ((bits & positive) != 0 ? 1 : 0) - ((bits & negative) != 0 ? 1 : 0);
    }
}
