package com.yuchayuan.buildsharer.share;

import java.util.List;
import java.util.Map;

public record Snapshot(List<Slot> equipment, int[] skillPoints, int level, String className,
                       List<NamedAspect> aspects, List<String> abilities, List<String> tomes) {

    public Snapshot {
        equipment = List.copyOf(equipment);
        aspects = List.copyOf(aspects);
        abilities = List.copyOf(abilities);
        tomes = List.copyOf(tomes);
        skillPoints = skillPoints == null ? null : skillPoints.clone();
    }

    public sealed interface Slot permits Nothing, Gear, Crafted {}

    public record Nothing() implements Slot {}

    public record Gear(String name, List<Integer> powders) implements Slot {
        public Gear {
            powders = List.copyOf(powders);
        }
    }

    public record Crafted(String name, String type, int level, String attackSpeed, int durability,
                          int health, int damageLow, int damageHigh, int[] reqs,
                          Map<String, Integer> apiIds, List<Integer> powders) implements Slot {
        public Crafted {
            reqs = reqs.clone();
            apiIds = Map.copyOf(apiIds);
            powders = List.copyOf(powders);
        }
    }

    public record NamedAspect(String name, int tier) {}
}
