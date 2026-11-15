package com.yuchayuan.buildsharer.share;

import com.yuchayuan.buildsharer.craft.Craft;
import com.yuchayuan.buildsharer.craft.CraftSolver;
import com.yuchayuan.buildsharer.data.WynnBuilderData;
import com.yuchayuan.buildsharer.encode.AbilityTree;
import com.yuchayuan.buildsharer.encode.Build;
import com.yuchayuan.buildsharer.encode.BuildEncoder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class Assembler {

    private static final List<String> TOME_SLOTS = List.of("weaponTome", "weaponTome",
            "armorTome", "armorTome", "armorTome", "armorTome", "guildTome", "lootrunTome",
            "gatherXpTome", "gatherXpTome", "dungeonXpTome", "dungeonXpTome", "mobXpTome", "mobXpTome");
    private static final long SOLVER_BUDGET = 4_000_000;
    private static final long SOLVER_MILLIS = 4_000;

    public record Result(String url, List<String> unknownItems, List<String> unknownAbilities,
                         List<String> unknownStats, List<String> approximateCrafts) {}

    private static final Set<String> WEAPON_TYPES = Set.of("wand", "spear", "bow", "dagger", "relik");

    private final WynnBuilderData data;
    private final List<String> unknownItems = new ArrayList<>();
    private final List<String> unknownAbilities = new ArrayList<>();
    private final List<String> unknownStats = new ArrayList<>();
    private final List<String> approximate = new ArrayList<>();

    public static final class MissingWeaponException extends IllegalStateException {

        private final String name;

        MissingWeaponException(String name) {
            super(name == null ? "weapon" : name);
            this.name = name;
        }

        public String name() {
            return name;
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
        Build.Piece weapon = pieces.get(Build.WEAPON);
        boolean wrongType = weapon instanceof Build.Normal normal
                && !WEAPON_TYPES.contains(data.itemType(normal.id()).orElse(""));
        if (weapon instanceof Build.Empty || wrongType) {
            throw new MissingWeaponException(snapshot.equipment().get(Build.WEAPON) instanceof Snapshot.Gear gear
                    ? gear.name() : null);
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
        Build build = new Build(pieces, snapshot.skillPoints(), snapshot.level(), aspects,
                tomes(snapshot.tomes()), tree, abilities);
        return new Result(data.encoder().url(build), List.copyOf(unknownItems), List.copyOf(unknownAbilities),
                List.copyOf(unknownStats), List.copyOf(approximate));
    }

    private List<Integer> tomes(List<String> names) {
        List<Integer> slots = new ArrayList<>(Collections.nCopies(TOME_SLOTS.size(), Build.NO_TOME));
        for (String name : names) {
            Optional<WynnBuilderData.TomeInfo> tome = data.tome(name);
            if (tome.isEmpty()) {
                unknownItems.add(name);
                continue;
            }
            for (int i = 0; i < TOME_SLOTS.size(); i++) {
                if (TOME_SLOTS.get(i).equals(tome.get().type()) && slots.get(i) == Build.NO_TOME) {
                    slots.set(i, tome.get().id());
                    break;
                }
            }
        }
        return slots;
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

    private List<Integer> powders(List<Snapshot.Powder> powders) {
        List<Integer> ids = new ArrayList<>(powders.size());
        for (Snapshot.Powder powder : powders) {
            ids.add(BuildEncoder.powderId(powder.element(), powder.tier(), data.constants()));
        }
        return ids;
    }
}
