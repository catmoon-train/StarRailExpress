package org.agmas.noellesroles.game.modes.echo.cards;

import net.minecraft.nbt.*;
import java.util.*;
import static org.agmas.noellesroles.game.modes.echo.cards.EchoSceneState.*;

/** 仅服务端到客户端的展示编码，不作为资源或场景创建入口。 */
public final class EchoSceneNbt {
    private EchoSceneNbt() { }
    public static ListTag write(List<Scene> scenes) {
        ListTag tags=new ListTag();
        for(var s:scenes.stream().limit(64).toList()) {
            CompoundTag t=new CompoundTag(); t.putUUID("id",s.id()); t.putUUID("owner",s.owner()); t.putString("kind",s.kind().name());
            t.putDouble("x",s.point().x()); t.putDouble("y",s.point().y()); t.putDouble("z",s.point().z()); t.putFloat("yaw",s.yaw());
            t.putLong("start",s.start()); t.putLong("end",s.end()); tags.add(t);
        }
        return tags;
    }
    public static List<Scene> read(ListTag tags) {
        List<Scene> scenes=new ArrayList<>();
        for(int n=0;n<Math.min(64,tags.size());n++) {
            var t=tags.getCompound(n);
            try {
                if(!t.hasUUID("id") || !t.hasUUID("owner")) continue;
                scenes.add(new Scene(t.getUUID("id"),t.getUUID("owner"),Kind.valueOf(t.getString("kind")),new Point(t.getDouble("x"),t.getDouble("y"),t.getDouble("z")),t.getFloat("yaw"),t.getLong("start"),t.getLong("end"),-1));
            } catch(IllegalArgumentException ignored) { /* 无效展示条目忽略，不影响任何权威状态。 */ }
        }
        return List.copyOf(scenes);
    }
}
