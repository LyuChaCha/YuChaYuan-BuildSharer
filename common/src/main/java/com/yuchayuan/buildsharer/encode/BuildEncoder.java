package com.yuchayuan.buildsharer.encode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BuildEncoder {

    public static final String BUILDER_URL = "https://wynnbuilder.github.io/builder/#";

    private static final int BINARY_FLAG = 0xC;
    private static final int VERSION_WIDTH = 10;
    private static final Set<Integer> POWDERABLE = Set.of(0, 1, 2, 3, Build.WEAPON);

    private final JsonObject constants;
    private final int version;

    public BuildEncoder(JsonObject constants, int version) {
        this.constants = constants;
        this.version = version;
    }

    public String url(Build build) {
        return BUILDER_URL + encode(build).toBase64();
    }

    public Bits encode(Build build) {
        Bits out = new Bits();
        out.append(BINARY_FLAG, 6);
        out.append(version, VERSION_WIDTH);
        equipment(build, out);
        tomes(build.tomes(), out);
        skillPoints(build.skillPoints(), out);
        level(build.level(), out);
        aspects(build.aspects(), out);
        out.append(build.tree().encode(build.abilities()));
        return out;
    }

    private void equipment(Build build, Bits out) {
        JsonObject kinds = constants.getAsJsonObject("EQUIPMENT_KIND");
        int kindWidth = kinds.get("BITLEN").getAsInt();
        for (int slot = 0; slot < Build.SLOTS; slot++) {
            Build.Piece piece = build.equipment().get(slot);
            switch (piece) {
                case Build.Empty empty -> {
                    out.append(kinds.get("NORMAL").getAsInt(), kindWidth);
                    out.append(0, number("ITEM_ID_BITLEN"));
                }
                case Build.Normal normal -> {
                    out.append(kinds.get("NORMAL").getAsInt(), kindWidth);
                    out.append(normal.id() + 1L, number("ITEM_ID_BITLEN"));
                }
                case Build.Crafted crafted -> {
                    out.append(kinds.get("CRAFTED").getAsInt(), kindWidth);
                    out.append(crafted.craft().encode());
                }
            }
            if (POWDERABLE.contains(slot)) {
                powders(piece.powders(), out);
            }
        }
    }

    private void powders(List<Integer> powders, Bits out) {
        if (powders.isEmpty()) {
            flag(out, "EQUIPMENT_POWDERS_FLAG", "NO_POWDERS");
            return;
        }
        flag(out, "EQUIPMENT_POWDERS_FLAG", "HAS_POWDERS");
        int tiers = number("POWDER_TIERS");
        int elements = constants.getAsJsonArray("POWDER_ELEMENTS").size();
        int previous = -1;
        for (List<Integer> chunk : grouped(powders, tiers)) {
            int i = 0;
            while (i < chunk.size()) {
                int powder = chunk.get(i);
                if (previous >= 0) {
                    flag(out, "POWDER_REPEAT_OP", "NO_REPEAT");
                    if (powder % tiers == previous % tiers) {
                        flag(out, "POWDER_REPEAT_TIER_OP", "REPEAT_TIER");
                        int wrap = Math.floorMod((powder - previous) / tiers, elements) - 1;
                        out.append(wrap, number("POWDER_WRAPPER_BITLEN"));
                    } else {
                        flag(out, "POWDER_REPEAT_TIER_OP", "CHANGE_POWDER");
                        flag(out, "POWDER_CHANGE_OP", "NEW_POWDER");
                        out.append(powder, number("POWDER_ID_BITLEN"));
                    }
                } else {
                    out.append(powder, number("POWDER_ID_BITLEN"));
                }
                while (++i < chunk.size() && chunk.get(i) == powder) {
                    flag(out, "POWDER_REPEAT_OP", "REPEAT");
                }
                previous = powder;
            }
        }
        flag(out, "POWDER_REPEAT_OP", "NO_REPEAT");
        flag(out, "POWDER_REPEAT_TIER_OP", "CHANGE_POWDER");
        flag(out, "POWDER_CHANGE_OP", "NEW_ITEM");
    }

    static List<List<Integer>> grouped(List<Integer> powders, int tiers) {
        Map<Integer, List<Integer>> byElement = new LinkedHashMap<>();
        for (int powder : powders) {
            byElement.computeIfAbsent(powder / tiers, e -> new ArrayList<>()).add(powder);
        }
        return new ArrayList<>(byElement.values());
    }

    private void tomes(List<Integer> tomes, Bits out) {
        if (tomes.stream().allMatch(id -> id == Build.NO_TOME)) {
            flag(out, "TOMES_FLAG", "NO_TOMES");
            return;
        }
        flag(out, "TOMES_FLAG", "HAS_TOMES");
        int slots = number("TOME_NUM");
        for (int i = 0; i < slots; i++) {
            int id = i < tomes.size() ? tomes.get(i) : Build.NO_TOME;
            if (id == Build.NO_TOME) {
                flag(out, "TOME_SLOT_FLAG", "UNUSED");
            } else {
                flag(out, "TOME_SLOT_FLAG", "USED");
                out.append(id, number("TOME_ID_BITLEN"));
            }
        }
    }

    private void skillPoints(int[] points, Bits out) {
        if (points == null) {
            flag(out, "SP_FLAG", "AUTOMATIC");
            return;
        }
        flag(out, "SP_FLAG", "ASSIGNED");
        int width = number("MAX_SP_BITLEN");
        for (int value : points) {
            flag(out, "SP_ELEMENT_FLAG", "ELEMENT_ASSIGNED");
            out.appendSigned(value, width);
        }
    }

    private void level(int level, Bits out) {
        if (level >= number("MAX_LEVEL")) {
            flag(out, "LEVEL_FLAG", "MAX");
        } else {
            flag(out, "LEVEL_FLAG", "OTHER");
            out.append(Math.max(1, level), number("LEVEL_BITLEN"));
        }
    }

    private void aspects(List<Build.Aspect> aspects, Bits out) {
        if (aspects.isEmpty()) {
            flag(out, "ASPECTS_FLAG", "NO_ASPECTS");
            return;
        }
        flag(out, "ASPECTS_FLAG", "HAS_ASPECTS");
        int slots = number("NUM_ASPECTS");
        for (int i = 0; i < slots; i++) {
            if (i >= aspects.size()) {
                flag(out, "ASPECT_SLOT_FLAG", "UNUSED");
                continue;
            }
            Build.Aspect aspect = aspects.get(i);
            flag(out, "ASPECT_SLOT_FLAG", "USED");
            out.append(aspect.id(), number("ASPECT_ID_BITLEN"));
            out.append(aspect.tier() - 1, number("ASPECT_TIER_BITLEN"));
        }
    }

    private void flag(Bits out, String field, String value) {
        JsonObject flags = constants.getAsJsonObject(field);
        out.append(flags.get(value).getAsInt(), flags.get("BITLEN").getAsInt());
    }

    private int number(String name) {
        JsonElement value = constants.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalStateException("missing encoding constant " + name);
        }
        return value.getAsInt();
    }

    public static int powderId(int element, int tier, JsonObject constants) {
        int tiers = constants.get("POWDER_TIERS").getAsInt();
        JsonArray names = constants.getAsJsonArray("POWDER_ELEMENTS");
        if (element < 0 || element >= names.size() || tier < 1 || tier > tiers) {
            throw new IllegalArgumentException("powder " + element + "/" + tier);
        }
        return element * tiers + tier - 1;
    }
}
