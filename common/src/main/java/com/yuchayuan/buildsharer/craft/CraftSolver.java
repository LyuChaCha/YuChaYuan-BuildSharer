package com.yuchayuan.buildsharer.craft;

import com.yuchayuan.buildsharer.craft.CraftData.Ingredient;
import com.yuchayuan.buildsharer.craft.CraftData.Recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CraftSolver {

    public record CraftedStats(String type, int level, String attackSpeed, int durability,
                               int health, int damageLow, int damageHigh, int[] reqs, Map<String, Integer> ids) {}

    private static final Set<String> WEAPONS = Set.of("SPEAR", "WAND", "DAGGER", "BOW", "RELIK");
    private static final Set<String> ARMOUR = Set.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");
    private static final double[] TIER_MULTIPLIER = {0, 1, 1.25, 1.4};
    private static final int SLOTS = 6;

    private final CraftData data;
    private final long budget;
    private final long deadlineMillis;

    private CraftedStats stats;
    private Recipe recipe;
    private long evaluations;
    private long deadline;
    private Best best;
    private final Set<String> seen = new HashSet<>();

    private record Best(int violations, double deviation, Ingredient[] order, int[] tiers) {}

    public CraftSolver(CraftData data, long budget, long deadlineMillis) {
        this.data = data;
        this.budget = budget;
        this.deadlineMillis = deadlineMillis;
    }

    public Optional<Craft> solve(CraftedStats stats) {
        this.stats = stats;
        this.best = null;
        this.evaluations = 0;
        this.deadline = System.currentTimeMillis() + deadlineMillis;
        Recipe chosenRecipe = null;
        for (Recipe candidate : recipes(stats)) {
            this.recipe = candidate;
            this.seen.clear();
            Best before = best;
            search();
            if (best != before) {
                chosenRecipe = candidate;
            }
        }
        if (best == null || chosenRecipe == null) {
            return Optional.empty();
        }
        List<Integer> ids = new ArrayList<>(SLOTS);
        for (Ingredient ingredient : best.order()) {
            ids.add(ingredient.id());
        }
        return Optional.of(new Craft(chosenRecipe.id(), WEAPONS.contains(stats.type()), best.tiers(), ids,
                attackSpeed(stats.attackSpeed()), best.violations() == 0));
    }

    private List<Recipe> recipes(CraftedStats stats) {
        List<Recipe> exact = new ArrayList<>();
        List<Recipe> containing = new ArrayList<>();
        for (Recipe candidate : data.recipes) {
            if (!candidate.type().equalsIgnoreCase(stats.type())) {
                continue;
            }
            if (candidate.lvlMax() == stats.level()) {
                exact.add(candidate);
            } else if (candidate.lvlMin() <= stats.level() && stats.level() <= candidate.lvlMax()) {
                containing.add(candidate);
            }
        }
        return exact.isEmpty() ? containing : exact;
    }

    private void search() {
        List<Ingredient> candidates = new ArrayList<>();
        List<Ingredient> boosters = new ArrayList<>();
        for (Ingredient ingredient : data.ingredients) {
            if (!ingredient.skills().contains(recipe.skill()) || ingredient.lvl() > recipe.lvlMax()) {
                continue;
            }
            if (!idsPossible(ingredient)) {
                continue;
            }
            if (!reqsPossible(ingredient)) {
                continue;
            }
            if (ingredient.ids().isEmpty()) {
                if (ingredient.positional()) {
                    boosters.add(ingredient);
                }
            } else {
                candidates.add(ingredient);
            }
        }
        Set<String> required = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> id : stats.ids().entrySet()) {
            if (id.getValue() != 0) {
                required.add(id.getKey());
            }
        }
        cover(new ArrayList<>(), required, candidates, boosters);
    }

    private boolean idsPossible(Ingredient ingredient) {
        for (Map.Entry<String, int[]> id : ingredient.ids().entrySet()) {
            boolean alwaysShown = id.getValue()[0] > 0 || id.getValue()[1] < 0;
            if (alwaysShown && !stats.ids().containsKey(id.getKey())) {
                return false;
            }
        }
        return true;
    }

    private boolean reqsPossible(Ingredient ingredient) {
        for (int i = 0; i < 5; i++) {
            if (ingredient.reqs()[i] > 0 && stats.reqs()[i] == 0) {
                return false;
            }
        }
        return true;
    }

    private boolean exhausted() {
        return evaluations >= budget || System.currentTimeMillis() > deadline;
    }

    private void cover(List<Ingredient> chosen, Set<String> uncovered, List<Ingredient> candidates,
                       List<Ingredient> boosters) {
        if (exhausted() || chosen.size() > SLOTS) {
            return;
        }
        if (uncovered.isEmpty()) {
            fill(chosen, boosters);
            return;
        }
        if (chosen.size() == SLOTS) {
            return;
        }
        String hardest = null;
        List<Ingredient> options = null;
        for (String id : uncovered) {
            List<Ingredient> having = new ArrayList<>();
            for (Ingredient candidate : candidates) {
                if (candidate.ids().containsKey(id)) {
                    having.add(candidate);
                }
            }
            if (options == null || having.size() < options.size()) {
                hardest = id;
                options = having;
            }
        }
        if (options == null || options.isEmpty()) {
            return;
        }
        for (Ingredient option : options) {
            List<Ingredient> next = new ArrayList<>(chosen);
            next.add(option);
            if (!seen.add(key(next))) {
                continue;
            }
            Set<String> remaining = new LinkedHashSet<>(uncovered);
            remaining.removeAll(option.ids().keySet());
            remaining.remove(hardest);
            cover(next, remaining, candidates, boosters);
        }
    }

    private void fill(List<Ingredient> chosen, List<Ingredient> boosters) {
        Set<Ingredient> pool = new LinkedHashSet<>(chosen);
        pool.add(CraftData.NONE);
        pool.addAll(boosters);
        List<Ingredient> options = new ArrayList<>(pool);
        options.sort(Comparator.comparingInt(Ingredient::id));
        fillFrom(new ArrayList<>(chosen), options, 0);
    }

    private void fillFrom(List<Ingredient> picked, List<Ingredient> options, int start) {
        if (exhausted()) {
            return;
        }
        if (picked.size() == SLOTS) {
            if (seen.add("full:" + key(picked))) {
                arrangements(picked);
            }
            return;
        }
        for (int i = start; i < options.size(); i++) {
            picked.add(options.get(i));
            fillFrom(picked, options, i);
            picked.remove(picked.size() - 1);
        }
    }

    private void arrangements(List<Ingredient> multiset) {
        boolean positional = false;
        for (Ingredient ingredient : multiset) {
            positional |= ingredient.positional();
        }
        Ingredient[] order = multiset.toArray(new Ingredient[0]);
        Arrays.sort(order, Comparator.comparingInt(Ingredient::id));
        if (!positional) {
            evaluate(order);
            return;
        }
        do {
            evaluate(order);
        } while (!exhausted() && nextPermutation(order));
    }

    private static boolean nextPermutation(Ingredient[] a) {
        int i = a.length - 2;
        while (i >= 0 && a[i].id() >= a[i + 1].id()) {
            i--;
        }
        if (i < 0) {
            return false;
        }
        int j = a.length - 1;
        while (a[j].id() <= a[i].id()) {
            j--;
        }
        Ingredient t = a[i];
        a[i] = a[j];
        a[j] = t;
        for (int l = i + 1, r = a.length - 1; l < r; l++, r--) {
            t = a[l];
            a[l] = a[r];
            a[r] = t;
        }
        return true;
    }

    private void evaluate(Ingredient[] order) {
        evaluations++;
        double[] mult = effectiveness(order);
        int violations = 0;
        double deviation = 0;
        for (int s = 0; s < 5; s++) {
            int total = 0;
            for (int n = 0; n < SLOTS; n++) {
                total += (int) Math.round(order[n].reqs()[s] * mult[n] + 1e-9);
            }
            if (total != stats.reqs()[s]) {
                violations++;
                deviation += Math.abs(total - stats.reqs()[s]);
            }
        }
        Map<String, int[]> ranges = new HashMap<>();
        int dura = 0;
        for (int n = 0; n < SLOTS; n++) {
            dura += order[n].dura();
            for (Map.Entry<String, int[]> id : order[n].ids().entrySet()) {
                int a = (int) Math.floor(id.getValue()[0] * mult[n]);
                int b = (int) Math.floor(id.getValue()[1] * mult[n]);
                int[] range = ranges.computeIfAbsent(id.getKey(), k -> new int[2]);
                range[0] += Math.min(a, b);
                range[1] += Math.max(a, b);
            }
        }
        for (Map.Entry<String, Integer> id : stats.ids().entrySet()) {
            int value = id.getValue();
            int[] range = ranges.getOrDefault(id.getKey(), new int[2]);
            if (value < range[0] || value > range[1]) {
                violations++;
                deviation += Math.min(Math.abs(value - range[0]), Math.abs(value - range[1])) + 1;
            } else {
                deviation += Math.abs(value - (range[0] + range[1]) / 2.0) / (range[1] - range[0] + 1);
            }
        }
        for (Map.Entry<String, int[]> range : ranges.entrySet()) {
            if (!stats.ids().containsKey(range.getKey()) && (range.getValue()[0] > 0 || range.getValue()[1] < 0)) {
                violations++;
                deviation += 1;
            }
        }
        int bestTierViolations = Integer.MAX_VALUE;
        double bestTierDeviation = 0;
        int[] tiers = null;
        for (int t1 = 1; t1 <= 3; t1++) {
            for (int t2 = 1; t2 <= 3; t2++) {
                double m = (TIER_MULTIPLIER[t1] * recipe.amount1() + TIER_MULTIPLIER[t2] * recipe.amount2())
                        / (recipe.amount1() + recipe.amount2());
                int tierViolations = 0;
                double tierDeviation = 0;
                if (stats.durability() >= 0) {
                    int low = Math.max(0, (int) Math.round(recipe.lowDura() * m) + dura);
                    int high = Math.max(0, (int) Math.round(recipe.highDura() * m) + dura);
                    if (stats.durability() < low || stats.durability() > high) {
                        tierViolations++;
                        tierDeviation += Math.min(Math.abs(stats.durability() - low), Math.abs(stats.durability() - high));
                    }
                }
                double base = baseDeviation(m);
                if (base > 0) {
                    tierViolations++;
                    tierDeviation += base;
                }
                if (tierViolations < bestTierViolations
                        || tierViolations == bestTierViolations && tierDeviation < bestTierDeviation) {
                    bestTierViolations = tierViolations;
                    bestTierDeviation = tierDeviation;
                    tiers = new int[] {t1, t2};
                }
            }
        }
        violations += bestTierViolations;
        deviation += bestTierDeviation;
        if (best == null || violations < best.violations()
                || violations == best.violations() && deviation < best.deviation()) {
            best = new Best(violations, deviation, order.clone(), tiers);
        }
    }

    private double baseDeviation(double m) {
        int low = (int) Math.floor(recipe.lowHod() * m);
        int high = (int) Math.floor(recipe.highHod() * m);
        if (ARMOUR.contains(stats.type()) && stats.health() > 0) {
            return outside(stats.health(), low, high);
        }
        if (WEAPONS.contains(stats.type()) && stats.damageHigh() > 0) {
            double ratio = switch (attackSpeed(stats.attackSpeed())) {
                case "SLOW" -> 2.05 / 1.5;
                case "FAST" -> 2.05 / 2.5;
                default -> 1;
            };
            int baseLow = (int) Math.floor(low * ratio);
            int baseHigh = (int) Math.floor(high * ratio);
            return outside(stats.damageLow(), (int) Math.floor(baseLow * 0.9) - 1, (int) Math.floor(baseLow * 1.1) + 1)
                    + outside(stats.damageHigh(), (int) Math.floor(baseHigh * 0.9) - 1, (int) Math.floor(baseHigh * 1.1) + 1);
        }
        return 0;
    }

    private static double outside(int value, int low, int high) {
        if (value < low) {
            return low - value;
        }
        if (value > high) {
            return value - high;
        }
        return 0;
    }

    static double[] effectiveness(Ingredient[] order) {
        int[][] eff = {{100, 100}, {100, 100}, {100, 100}};
        for (int n = 0; n < SLOTS; n++) {
            int i = n / 2;
            int j = n % 2;
            int[] pos = order[n].positions();
            if (pos[0] != 0 && j == 1) {
                eff[i][0] += pos[0];
            }
            if (pos[1] != 0 && j == 0) {
                eff[i][1] += pos[1];
            }
            for (int k = i - 1; k >= 0 && pos[2] != 0; k--) {
                eff[k][j] += pos[2];
            }
            for (int k = i + 1; k < 3 && pos[3] != 0; k++) {
                eff[k][j] += pos[3];
            }
            for (int k = 0; k < 3; k++) {
                for (int l = 0; l < 2; l++) {
                    int dk = Math.abs(k - i);
                    int dl = Math.abs(l - j);
                    if (pos[4] != 0 && (dk == 1 && dl == 0 || dk == 0 && dl == 1)) {
                        eff[k][l] += pos[4];
                    }
                    if (pos[5] != 0 && (dk > 1 || dk == 1 && dl == 1)) {
                        eff[k][l] += pos[5];
                    }
                }
            }
        }
        double[] mult = new double[SLOTS];
        for (int n = 0; n < SLOTS; n++) {
            mult[n] = eff[n / 2][n % 2] / 100.0;
        }
        return mult;
    }

    private static String attackSpeed(String speed) {
        if (speed == null) {
            return "NORMAL";
        }
        return switch (speed) {
            case "SUPER_SLOW", "VERY_SLOW", "SLOW" -> "SLOW";
            case "FAST", "VERY_FAST", "SUPER_FAST" -> "FAST";
            default -> "NORMAL";
        };
    }

    private static String key(List<Ingredient> list) {
        int[] ids = new int[list.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = list.get(i).id();
        }
        Arrays.sort(ids);
        return Arrays.toString(ids);
    }
}
