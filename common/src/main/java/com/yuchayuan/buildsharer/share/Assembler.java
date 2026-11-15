package com.yuchayuan.buildsharer.share;

import com.yuchayuan.buildsharer.craft.Craft;
import com.yuchayuan.buildsharer.craft.CraftSolver;
import com.yuchayuan.buildsharer.data.WynnBuilderData;
import com.yuchayuan.buildsharer.encode.AbilityTree;
import com.yuchayuan.buildsharer.encode.Build;
import com.yuchayuan.buildsharer.encode.BuildEncoder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class Assembler {

    public static final int POWDER_TIER = 6;
    private static final long SOLVER_BUDGET = 4_000_000;
    private static final long SOLVER_MILLIS = 4_000;

    public record Result(String url, List<String> unknownItems, List<String> unknownAbilities,
                         List<String> unknownStats, List<String> approximateCrafts) {}

    private final WynnBuilderData data;
    private final List<String> unknownItems = new ArrayList<>();
    private final List<String> unknownAbilities = new ArrayList<>();
    private final List<String> unknownStats = new ArrayList<>();
    private final List<String> approximate = new ArrayList<>();

    public static final class MissingWeaponException extends IllegalStateException {
        MissingWeaponException() {
            super("weapon");
        }
    }

    public Assembler(WynnBuilderData data) {
        this.data = data;
    }

    public Result assemble(Snapshot snapshot) {
        List<Build.Piece> pieces = new ArrayList<>();
        for (Snapshot.Slot slot : snapshot.equipment()) {
            pieces.add(piece(slot));
        }
        if (pieces.get(Build.WEAPON) instanceof Build.Empty) {
            throw new MissingWeaponException();
        }
        AbilityTree tree = data.tree(snapshot.className())
                .orElseThrow(() -> new IllegalStateException("no ability tree for " + snapshot.className()));
        Set<Integer> abilities = new HashSet<>();
        for (String name : snapshot.abilities()) {
            Optional<Integer> id = tree.find(name);
            if (id.isPresent()) {
                abilities.add(id.get());
            } else {
                unknownAbilities.add(name);
            }
        }
        List<Build.Aspect> aspects = new ArrayList<>();
        for (Snapshot.NamedAspect aspect : snapshot.aspects()) {
            data.aspect(snapshot.className(), aspect.name()).ifPresent(info ->
                    aspects.add(new Build.Aspect(info.id(), Math.max(1, Math.min(info.tiers(), aspect.tier())))));
        }
        Build build = new Build(pieces, snapshot.skillPoints(), snapshot.level(), aspects, tree, abilities);
        return new Result(data.encoder().url(build), List.copyOf(unknownItems), List.copyOf(unknownAbilities),
                List.copyOf(unknownStats), List.copyOf(approximate));
    }

    private Build.Piece piece(Snapshot.Slot slot) {
        return switch (slot) {
            case Snapshot.Nothing nothing -> new Build.Empty();
            case Snapshot.Gear gear -> data.item(gear.name())
                    .<Build.Piece>map(id -> new Build.Normal(id, powders(gear.powders())))
                    .orElseGet(() -> {
                        unknownItems.add(gear.name());
                        return new Build.Empty();
                    });
            case Snapshot.Crafted crafted -> crafted(crafted);
        };
    }

    private Build.Piece crafted(Snapshot.Crafted crafted) {
        Map<String, Integer> ids = new HashMap<>();
        for (Map.Entry<String, Integer> stat : crafted.apiIds().entrySet()) {
            Optional<String> key = data.stat(stat.getKey());
            if (key.isPresent()) {
                ids.merge(key.get(), stat.getValue(), Integer::sum);
            } else {
                unknownStats.add(stat.getKey());
            }
        }
        CraftSolver.CraftedStats stats = new CraftSolver.CraftedStats(crafted.type(), crafted.level(),
                crafted.attackSpeed(), crafted.durability(), crafted.health(), crafted.damageLow(),
                crafted.damageHigh(), crafted.reqs(), ids);
        Optional<Craft> craft = new CraftSolver(data.crafting(), SOLVER_BUDGET, SOLVER_MILLIS).solve(stats);
        if (craft.isEmpty()) {
            unknownItems.add(crafted.name());
            return new Build.Empty();
        }
        if (!craft.get().exact()) {
            approximate.add(crafted.name());
        }
        return new Build.Crafted(craft.get(), powders(crafted.powders()));
    }

    private List<Integer> powders(List<Integer> elements) {
        List<Integer> ids = new ArrayList<>(elements.size());
        for (int element : elements) {
            ids.add(BuildEncoder.powderId(element, POWDER_TIER, data.constants()));
        }
        return ids;
    }
}
