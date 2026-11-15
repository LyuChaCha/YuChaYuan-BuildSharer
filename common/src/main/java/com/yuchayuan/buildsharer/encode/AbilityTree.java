package com.yuchayuan.buildsharer.encode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class AbilityTree {

    public record Node(int id, String name, List<Integer> parents) {
        public Node {
            parents = List.copyOf(parents);
        }
    }

    private static final String[] ROMAN = {"i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x"};

    private final Map<Integer, List<Integer>> children = new LinkedHashMap<>();
    private final Map<String, Integer> byName = new LinkedHashMap<>();
    private final int root;

    public AbilityTree(List<Node> nodes) {
        Integer head = null;
        for (Node node : nodes) {
            children.put(node.id(), new ArrayList<>());
            byName.putIfAbsent(normalise(node.name()), node.id());
            if (node.parents().isEmpty() && head == null) {
                head = node.id();
            }
        }
        if (head == null) {
            throw new IllegalArgumentException("ability tree has no root");
        }
        for (Node node : nodes) {
            for (int parent : node.parents()) {
                List<Integer> list = children.get(parent);
                if (list != null) {
                    list.add(node.id());
                }
            }
        }
        root = head;
    }

    public Optional<Integer> find(String name) {
        String key = normalise(name);
        for (String candidate : List.of(key, arabic(key), key + " i", key + " 1")) {
            Integer id = byName.get(candidate);
            if (id != null) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }

    public Bits encode(Set<Integer> active) {
        Bits out = new Bits();
        Set<Integer> visited = new HashSet<>();
        walk(root, active, visited, out);
        return out;
    }

    private void walk(int head, Set<Integer> active, Set<Integer> visited, Bits out) {
        for (int child : children.getOrDefault(head, List.of())) {
            if (!visited.add(child)) {
                continue;
            }
            if (active.contains(child)) {
                out.append(1, 1);
                walk(child, active, visited, out);
            } else {
                out.append(0, 1);
            }
        }
    }

    static String normalise(String name) {
        return name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String arabic(String key) {
        int space = key.lastIndexOf(' ');
        if (space < 0) {
            return key;
        }
        String tail = key.substring(space + 1);
        for (int i = 0; i < ROMAN.length; i++) {
            if (ROMAN[i].equals(tail)) {
                return key.substring(0, space + 1) + (i + 1);
            }
        }
        return key;
    }
}
