package com.yuchayuan.buildsharer.craft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CraftData {

    public static final String[] SKILLS = {"str", "dex", "int", "def", "agi"};
    static final String[] POSITIONS = {"left", "right", "above", "under", "touching", "notTouching"};
    static final int NO_INGREDIENT = 4000;

    public record Recipe(int id, String type, String skill, int lvlMin, int lvlMax,
                         int lowHod, int highHod, int lowDura, int highDura, int amount1, int amount2) {}

    public record Ingredient(int id, String name, int lvl, Set<String> skills, Map<String, int[]> ids,
                             int[] reqs, int dura, int[] positions) {

        boolean positional() {
            for (int value : positions) {
                if (value != 0) {
                    return true;
                }
            }
            return false;
        }
    }

    static final Ingredient NONE = new Ingredient(NO_INGREDIENT, "No Ingredient", 0, Set.of(),
            Map.of(), new int[5], 0, new int[6]);

    final List<Recipe> recipes = new ArrayList<>();
    final List<Ingredient> ingredients = new ArrayList<>();

    public CraftData(JsonObject recipes, JsonElement ingredients) {
        for (JsonElement element : recipes.getAsJsonArray("recipes")) {
            JsonObject r = element.getAsJsonObject();
            JsonArray materials = r.getAsJsonArray("materials");
            this.recipes.add(new Recipe(r.get("id").getAsInt(), r.get("type").getAsString(),
                    r.get("skill").getAsString(),
                    min(r, "lvl"), max(r, "lvl"), min(r, "healthOrDamage"), max(r, "healthOrDamage"),
                    min(r, "durability"), max(r, "durability"),
                    materials.get(0).getAsJsonObject().get("amount").getAsInt(),
                    materials.get(1).getAsJsonObject().get("amount").getAsInt()));
        }
        JsonArray list = ingredients.isJsonArray() ? ingredients.getAsJsonArray()
                : ingredients.getAsJsonObject().getAsJsonArray("ingredients");
        for (JsonElement element : list) {
            JsonObject g = element.getAsJsonObject();
            if (!g.has("id")) {
                continue;
            }
            Set<String> skills = new HashSet<>();
            for (JsonElement skill : g.getAsJsonArray("skills")) {
                skills.add(skill.getAsString());
            }
            Map<String, int[]> ids = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> id : g.getAsJsonObject("ids").entrySet()) {
                JsonObject range = id.getValue().getAsJsonObject();
                int low = range.get("minimum").getAsInt();
                int high = range.get("maximum").getAsInt();
                if (low != 0 || high != 0) {
                    ids.put(id.getKey(), new int[] {low, high});
                }
            }
            JsonObject itemIds = g.getAsJsonObject("itemIDs");
            int[] reqs = new int[5];
            for (int i = 0; i < 5; i++) {
                JsonElement value = itemIds.get(SKILLS[i] + "Req");
                reqs[i] = value == null ? 0 : value.getAsInt();
            }
            JsonObject posMods = g.getAsJsonObject("posMods");
            int[] positions = new int[POSITIONS.length];
            for (int i = 0; i < POSITIONS.length; i++) {
                JsonElement value = posMods.get(POSITIONS[i]);
                positions[i] = value == null ? 0 : value.getAsInt();
            }
            JsonElement dura = itemIds.get("dura");
            this.ingredients.add(new Ingredient(g.get("id").getAsInt(), g.get("name").getAsString(),
                    g.get("lvl").getAsInt(), skills, ids, reqs, dura == null ? 0 : dura.getAsInt(), positions));
        }
    }

    private static int min(JsonObject o, String key) {
        return o.has(key) ? o.getAsJsonObject(key).get("minimum").getAsInt() : 0;
    }

    private static int max(JsonObject o, String key) {
        return o.has(key) ? o.getAsJsonObject(key).get("maximum").getAsInt() : 0;
    }
}
