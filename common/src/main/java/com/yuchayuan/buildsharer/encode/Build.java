package com.yuchayuan.buildsharer.encode;

import com.yuchayuan.buildsharer.craft.Craft;

import java.util.List;
import java.util.Set;

public record Build(
        List<Piece> equipment,
        int[] skillPoints,
        int level,
        List<Aspect> aspects,
        List<Integer> tomes,
        AbilityTree tree,
        Set<Integer> abilities) {

    public static final int SLOTS = 9;
    public static final int WEAPON = 8;
    public static final int NO_TOME = -1;

    public Build {
        if (equipment.size() != SLOTS) {
            throw new IllegalArgumentException("equipment needs " + SLOTS + " slots");
        }
        equipment = List.copyOf(equipment);
        aspects = List.copyOf(aspects);
        tomes = List.copyOf(tomes);
        abilities = Set.copyOf(abilities);
        skillPoints = skillPoints == null ? null : skillPoints.clone();
    }

    public sealed interface Piece permits Empty, Normal, Crafted {
        List<Integer> powders();
    }

    public record Empty() implements Piece {
        public List<Integer> powders() {
            return List.of();
        }
    }

    public record Normal(int id, List<Integer> powders) implements Piece {
        public Normal {
            powders = List.copyOf(powders);
        }
    }

    public record Crafted(Craft craft, List<Integer> powders) implements Piece {
        public Crafted {
            powders = List.copyOf(powders);
        }
    }

    public record Aspect(int id, int tier) {}
}
