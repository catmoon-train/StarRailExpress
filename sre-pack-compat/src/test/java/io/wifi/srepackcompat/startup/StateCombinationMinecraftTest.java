package io.wifi.srepackcompat.startup;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StateCombinationMinecraftTest {
    private static final class Owner {
        private StateDefinition<Owner, TestState> definition;
        TestState any() { return definition.any(); }
    }
    private static final class TestState extends StateHolder<Owner, TestState> {
        TestState(Owner owner, it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap<Property<?>, Comparable<?>> values,
                MapCodec<TestState> codec) {
            super(owner, values, codec);
        }
        @SuppressWarnings("unchecked")
        void rebuild(Map<Map<Property<?>, Comparable<?>>, TestState> map) throws Exception {
            var field = StateHolder.class.getDeclaredField("neighbours");
            field.setAccessible(true);
            field.set(this, null);
            populateNeighbours(map);
            var table = (Table<Property<?>, Comparable<?>, TestState>) field.get(this);
            var source = HashBasedTable.<Property<?>, Comparable<?>, TestState>create();
            for (var cell : table.cellSet()) {
                if (cell.getValue() != null) source.put(cell.getRowKey(), cell.getColumnKey(), cell.getValue());
            }
            field.set(this, NeighbourTableFactory.create(source));
        }
    }

    @Test
    void actualMinecraftNeighbourTablesRemainIdenticalForEveryStateAndValue() throws Exception {
        var owner = new Owner();
        var properties = List.of(IntegerProperty.create("a", 0, 3), IntegerProperty.create("b", 0, 3),
                IntegerProperty.create("c", 0, 3), IntegerProperty.create("d", 0, 3));
        var builder = new StateDefinition.Builder<Owner, TestState>(owner);
        properties.forEach(builder::add);
        owner.definition = builder.create(Owner::any, TestState::new);
        var original = new LinkedHashMap<Map<Property<?>, Comparable<?>>, TestState>();
        for (TestState state : owner.definition.getPossibleStates()) original.put(state.getValues(), state);
        Map<Map<Property<?>, Comparable<?>>, TestState> indexed = StateCombinationMap.create(original,
                new ArrayList<Property<?>>(properties), Property::getPossibleValues, StateHolder::getValues);
        assertNotSame(original, indexed);
        assertEquals(256, original.size());
        var targets = new ArrayList<TestState>();
        for (TestState state : owner.definition.getPossibleStates()) {
            for (var property : properties) {
                for (int value : property.getPossibleValues()) targets.add(state.setValue(property, value));
            }
        }
        for (TestState state : owner.definition.getPossibleStates()) state.rebuild(indexed);
        int check = 0;
        for (TestState state : owner.definition.getPossibleStates()) {
            for (var property : properties) {
                for (int value : property.getPossibleValues()) assertSame(targets.get(check++), state.setValue(property, value));
            }
            assertThrows(IllegalArgumentException.class, () -> state.setValue(properties.getFirst(), 9));
        }
        assertEquals(4096, check);
    }
}
