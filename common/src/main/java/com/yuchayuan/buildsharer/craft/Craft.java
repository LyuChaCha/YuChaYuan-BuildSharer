package com.yuchayuan.buildsharer.craft;

import com.yuchayuan.buildsharer.encode.Bits;

import java.util.List;

public record Craft(int recipeId, boolean weapon, int[] materialTiers, List<Integer> ingredients,
                    String attackSpeed, boolean exact) {

    static final List<String> ATTACK_SPEEDS = List.of("SLOW", "NORMAL", "FAST");

    public Craft {
        materialTiers = materialTiers.clone();
        ingredients = List.copyOf(ingredients);
    }

    public Bits encode() {
        Bits out = new Bits();
        out.append(0, 1);
        out.append(2, 7);
        for (int id : ingredients) {
            out.append(id, 12);
        }
        out.append(recipeId, 12);
        for (int tier : materialTiers) {
            out.append(tier - 1, 3);
        }
        if (weapon) {
            out.append(Math.max(0, ATTACK_SPEEDS.indexOf(attackSpeed)), 4);
        }
        out.append(0, 6 - out.length() % 6);
        return out;
    }

    public String crafterUrl() {
        return "https://wynnbuilder.github.io/crafter/#" + encode().toBase64();
    }
}
