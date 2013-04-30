/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.plugins.memory;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;
import static com.google.common.base.Predicates.notNull;
import static com.google.common.collect.Collections2.filter;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Maps.filterValues;
import static com.google.common.collect.Maps.newHashMap;
import static java.util.Collections.emptyList;
import static org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState.MISSING_NODE;
import static org.apache.jackrabbit.oak.plugins.memory.MemoryChildNodeEntry.iterable;

import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.spi.state.AbstractNodeState;
import org.apache.jackrabbit.oak.spi.state.ChildNodeEntry;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.jackrabbit.oak.spi.state.NodeStateDiff;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Maps;

/**
 * Immutable snapshot of a mutable node state.
 */
public class ModifiedNodeState extends AbstractNodeState {

    static long getPropertyCount(
            NodeState base, Map<String, PropertyState> properties) {
        long count = 0;
        if (base.exists()) {
            count = base.getPropertyCount();
            for (Entry<String, PropertyState> entry : properties.entrySet()) {
                if (base.hasProperty(entry.getKey())) {
                    count--;
                }
                if (entry.getValue() != null) {
                    count++;
                }
            }
        }
        return count;
    }

    static boolean hasProperty(
            NodeState base, Map<String, PropertyState> properties,
            String name) {
        if (properties.containsKey(name)) {
            return properties.get(name) != null;
        } else {
            return base.hasProperty(name);
        }
    }

    static PropertyState getProperty(
            NodeState base, Map<String, PropertyState> properties,
            String name) {
        PropertyState property = properties.get(name);
        if (property == null && !properties.containsKey(name)) {
            property = base.getProperty(name);
        }
        return property;
    }

    static Iterable<? extends PropertyState> getProperties(
            NodeState base, Map<String, PropertyState> properties,
            boolean copy) {
        if (!base.exists()) {
            return emptyList();
        } else if (properties.isEmpty()) {
            return base.getProperties(); // shortcut
        } else {
            if (copy) {
                properties = newHashMap(properties);
            }
            Predicate<PropertyState> predicate = Predicates.compose(
                    not(in(properties.keySet())), PropertyState.GET_NAME);
            return concat(
                    filter(base.getProperties(), predicate),
                    filter(properties.values(), notNull()));
        }
    }

    static long getChildNodeCount(
            NodeState base, Map<String, ? extends NodeState> nodes) {
        long count = 0;
        if (base.exists()) {
            count = base.getChildNodeCount();
            for (Map.Entry<String, ? extends NodeState> entry
                    : nodes.entrySet()) {
                if (base.getChildNode(entry.getKey()).exists()) {
                    count--;
                }
                if (entry.getValue() != null && entry.getValue().exists()) {
                    count++;
                }
            }
        }
        return count;
    }

    static Iterable<String> getChildNodeNames(
            NodeState base, Map<String, ? extends NodeState> nodes,
            boolean copy) {
        if (!base.exists()) {
            return emptyList();
        } else if (nodes.isEmpty()) {
            return base.getChildNodeNames(); // shortcut
        } else {
            if (copy) {
                nodes = newHashMap(nodes);
            }
            return concat(
                    filter(base.getChildNodeNames(), not(in(nodes.keySet()))),
                    filterValues(nodes, notNull()).keySet());
        }
    }
    static NodeState with(
            NodeState base,
            Map<String, PropertyState> properties,
            Map<String, ? extends NodeState> nodes) {
        if (properties.isEmpty() && nodes.isEmpty()) {
            return base;
        } else {
            // TODO: Do we need collapse() here? See OAK-778
            return collapse(new ModifiedNodeState(base, properties, nodes));
        }
    }

    public static ModifiedNodeState collapse(ModifiedNodeState state) {
        NodeState base = state.getBaseState();
        if (base instanceof ModifiedNodeState) {
            ModifiedNodeState mbase = collapse((ModifiedNodeState) base);

            Map<String, PropertyState> properties = Maps.newHashMap(mbase.properties);
            properties.putAll(state.properties);

            Map<String, NodeState> nodes = Maps.newHashMap(mbase.nodes);
            nodes.putAll(state.nodes);

            return new ModifiedNodeState(mbase.getBaseState(), properties, nodes);
        } else {
            return state;
        }
    }

    /**
     * The base state.
     */
    private final NodeState base;

    /**
     * Set of added, modified or removed ({@code null} value)
     * property states.
     */
    private final Map<String, PropertyState> properties;

    /**
     * Set of added, modified or removed ({@code null} value)
     * child nodes.
     */
    private final Map<String, ? extends NodeState> nodes;

    private final Predicate<ChildNodeEntry> unmodifiedNodes = new Predicate<ChildNodeEntry>() {
        @Override
        public boolean apply(ChildNodeEntry input) {
            return !nodes.containsKey(input.getName());
        }
    };

    private final Predicate<NodeState> existingNodes = new Predicate<NodeState>() {
        @Override
        public boolean apply(@Nullable NodeState node) {
            return node != null && node.exists();
        }
    };

    private ModifiedNodeState(
            @Nonnull NodeState base,
            @Nonnull Map<String, PropertyState> properties,
            @Nonnull Map<String, ? extends NodeState> nodes) {
        this.base = checkNotNull(base);
        this.properties = checkNotNull(properties);
        this.nodes = checkNotNull(nodes);
    }

    @Nonnull
    public NodeState getBaseState() {
        return base;
    }

    //---------------------------------------------------------< NodeState >--

    @Override
    public NodeBuilder builder() {
        return new MemoryNodeBuilder(this);
    }

    @Override
    public boolean exists() {
        return base.exists();
    }

    @Override
    public long getPropertyCount() {
        return getPropertyCount(base, properties);
    }

    @Override
    public boolean hasProperty(String name) {
        return hasProperty(base, properties, name);
    }

    @Override
    public PropertyState getProperty(String name) {
        return getProperty(base, properties, name);
    }

    @Override
    public Iterable<? extends PropertyState> getProperties() {
        return getProperties(base, properties, false);
    }

    @Override
    public long getChildNodeCount() {
        return getChildNodeCount(base, nodes);
    }

    @Override
    public NodeState getChildNode(String name) {
        // checkArgument(!checkNotNull(name).isEmpty());  // TODO: should be caught earlier
        NodeState child = nodes.get(name);
        if (child != null) {
            return child;
        } else if (nodes.containsKey(name)) {
            return MISSING_NODE;
        } else {
            return base.getChildNode(name);
        }
    }

    @Override
    public Iterable<String> getChildNodeNames() {
        return getChildNodeNames(base, nodes, false);
    }

    @Override
    public Iterable<? extends ChildNodeEntry> getChildNodeEntries() {
        if (!exists()) {
            return emptyList();
        }
        if (nodes.isEmpty()) {
            return base.getChildNodeEntries(); // shortcut
        }
        return concat(
                filter(base.getChildNodeEntries(), unmodifiedNodes),
                iterable(filterValues(nodes, existingNodes).entrySet()));
    }

    /**
     * Since we keep track of an explicit base node state for a
     * {@link ModifiedNodeState} instance, we can do this in two steps:
     * first compare the base states to each other (often a fast operation),
     * ignoring all changed properties and child nodes for which we have
     * further modifications, and then compare all the modified properties
     * and child nodes to those in the given base state.
     */
    @Override
    public boolean compareAgainstBaseState(
            NodeState base, final NodeStateDiff diff) {
        if (!this.base.compareAgainstBaseState(base, new NodeStateDiff() {
            @Override
            public boolean propertyAdded(PropertyState after) {
                return properties.containsKey(after.getName())
                        || diff.propertyAdded(after);
            }
            @Override
            public boolean propertyChanged(
                    PropertyState before, PropertyState after) {
                return properties.containsKey(before.getName())
                        || diff.propertyChanged(before, after);
            }
            @Override
            public boolean propertyDeleted(PropertyState before) {
                return properties.containsKey(before.getName())
                        || diff.propertyDeleted(before);
            }
            @Override
            public boolean childNodeAdded(String name, NodeState after) {
                return nodes.containsKey(name)
                        || diff.childNodeAdded(name, after);
            }
            @Override
            public boolean childNodeChanged(String name, NodeState before, NodeState after) {
                return nodes.containsKey(name)
                        || diff.childNodeChanged(name, before, after);
            }
            @Override
            public boolean childNodeDeleted(String name, NodeState before) {
                return nodes.containsKey(name)
                        || diff.childNodeDeleted(name, before);
            }
        })) {
            return false;
        }

        for (Map.Entry<String, ? extends PropertyState> entry : properties.entrySet()) {
            PropertyState before = base.getProperty(entry.getKey());
            PropertyState after = entry.getValue();
            if (before == null && after == null) {
                // do nothing
            } else if (after == null) {
                if (!diff.propertyDeleted(before)) {
                    return false; 
                }
            } else if (before == null) {
                if (!diff.propertyAdded(after)) {
                    return false;
                }
            } else if (!before.equals(after)) {
                if (!diff.propertyChanged(before, after)) {
                    return false;
                }
            }
        }

        for (Map.Entry<String, ? extends NodeState> entry : nodes.entrySet()) {
            String name = entry.getKey();
            NodeState before = base.getChildNode(name);
            NodeState after = entry.getValue();
            if (after == null) {
                if (before.exists()) {
                    if (!diff.childNodeDeleted(name, before)) {
                        return false;
                    }
                }
            } else if (!before.exists()) {
                if (!diff.childNodeAdded(name, after)) {
                    return false;
                }
            } else if (!before.equals(after)) {
                if (!diff.childNodeChanged(name, before, after)) {
                    return false;
                }
            }
        }

        return true;
    }

    public void compareAgainstBaseState(NodeStateDiff diff) {
        for (Map.Entry<String, ? extends PropertyState> entry
                : properties.entrySet()) {
            PropertyState before = base.getProperty(entry.getKey());
            PropertyState after = entry.getValue();
            if (after == null) {
                diff.propertyDeleted(before);
            } else if (before == null) {
                diff.propertyAdded(after);
            } else if (!before.equals(after)) { // TODO: can we assume this?
                diff.propertyChanged(before, after);
            }
        }

        for (Map.Entry<String, ? extends NodeState> entry : nodes.entrySet()) {
            String name = entry.getKey();
            NodeState before = base.getChildNode(name);
            NodeState after = entry.getValue();
            if (after == null) {
                if (before.exists()) { // TODO: can we assume this?
                    diff.childNodeDeleted(name, before);
                }
            } else if (!before.exists()) {
                diff.childNodeAdded(name, after);
            } else if (!before.equals(after)) { // TODO: can we assume this?
                diff.childNodeChanged(name, before, after);
            }
        }
    }

}
