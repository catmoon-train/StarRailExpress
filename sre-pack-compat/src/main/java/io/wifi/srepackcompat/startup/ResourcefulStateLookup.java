package io.wifi.srepackcompat.startup;

import com.google.common.base.Splitter;
import com.mojang.serialization.DataResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** ResourcefulLib 3.0.12 的有界查询加速；上游谓词负责校验和最终状态检查。 */
public final class ResourcefulStateLookup {
    private static final Splitter COMMA = Splitter.on(',');
    private static final Splitter EQUAL = Splitter.on('=').limit(2);
    private static final Map<StateDefinition<Block, BlockState>,
            StateValueIndex<BlockState, Property<?>, Comparable<?>>> INDEXES = new LinkedHashMap<>(8, .75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<StateDefinition<Block, BlockState>,
                StateValueIndex<BlockState, Property<?>, Comparable<?>>> eldest) {
            return size() > 8;
        }
    };

    private ResourcefulStateLookup() { }

    public static boolean eligible(StateDefinition<Block, BlockState> definition) {
        int count = definition.getPossibleStates().size();
        // 限制索引体积；小定义直接用上游扫描，避免建索引反而更慢。
        return count > 64 && count <= 32768 && definition.getProperties().size() <= 16;
    }

    /** 返回 null 代表交回上游，包含空结果的原始 DataResult 错误。 */
    public static DataResult<List<BlockState>> select(StateDefinition<Block, BlockState> definition,
            String variant, Predicate<BlockState> validatedPredicate) {
        var constraints = new HashMap<Property<?>, Comparable<?>>();
        for (String part : COMMA.split(variant)) {
            var pieces = EQUAL.split(part).iterator();
            String name = pieces.next();
            Property<?> property = definition.getProperty(name);
            // 上游已完成同样的解析校验；空属性名片段仍按上游语义忽略。
            if (property != null && pieces.hasNext()) {
                if (property.getPossibleValues().size() > 32) {
                    return null;
                }
                constraints.put(property, property.getValue(pieces.next()).orElseThrow());
            }
        }
        if (constraints.isEmpty()) {
            return null;
        }
        StateValueIndex<BlockState, Property<?>, Comparable<?>> index;
        synchronized (INDEXES) {
            index = INDEXES.computeIfAbsent(definition,
                    key -> new StateValueIndex<>(key.getPossibleStates(), BlockState::getValue));
        }
        List<BlockState> states = index.select(constraints).stream().filter(validatedPredicate).toList();
        return states.isEmpty() ? null : DataResult.success(states);
    }
}
