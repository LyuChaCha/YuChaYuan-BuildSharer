package com.yuchayuan.buildsharer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yuchayuan.buildsharer.craft.Craft;
import com.yuchayuan.buildsharer.craft.CraftData;
import com.yuchayuan.buildsharer.craft.CraftSolver;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class CraftSolverTest {

    private static int failures;

    public static void main(String[] args) throws Exception {
        String dir = System.getenv("WB_DATA");
        if (dir == null) {
            System.out.println("  [略過] 沒設 WB_DATA");
            System.exit(0);
        }
        Path data = Path.of(dir);
        CraftData crafting = new CraftData(
                JsonParser.parseString(Files.readString(data.resolve("recipes.json"), StandardCharsets.UTF_8)).getAsJsonObject(),
                JsonParser.parseString(Files.readString(data.resolve("ingreds.json"), StandardCharsets.UTF_8)));
        JsonArray cases;
        try (InputStream in = CraftSolverTest.class.getResourceAsStream("/crafts.json")) {
            cases = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray();
        }
        for (JsonElement element : cases) {
            JsonObject c = element.getAsJsonObject();
            int[] reqs = new int[5];
            for (int i = 0; i < 5; i++) {
                reqs[i] = c.getAsJsonArray("reqs").get(i).getAsInt();
            }
            Map<String, Integer> ids = new HashMap<>();
            for (Map.Entry<String, JsonElement> id : c.getAsJsonObject("ids").entrySet()) {
                ids.put(id.getKey(), id.getValue().getAsInt());
            }
            CraftSolver.CraftedStats stats = new CraftSolver.CraftedStats(c.get("type").getAsString(),
                    c.get("level").getAsInt(), c.get("attackSpeed").isJsonNull() ? null : c.get("attackSpeed").getAsString(),
                    c.get("durability").getAsInt(), c.get("health").getAsInt(), c.get("damageLow").getAsInt(),
                    c.get("damageHigh").getAsInt(), reqs, ids);
            long started = System.currentTimeMillis();
            Optional<Craft> craft = new CraftSolver(crafting, 4_000_000, 4_000).solve(stats);
            long took = System.currentTimeMillis() - started;
            String name = c.get("recipe").getAsString();
            check(name + " 推得出吻合的配方（" + took + "ms）", craft.isPresent() && craft.get().exact());
            craft.ifPresent(found -> System.out.println("      " + found.crafterUrl() + "  正解：" + c.get("truth")));
        }
        System.out.println(failures == 0 ? "配方推算：全部通過" : "配方推算：" + failures + " 項失敗");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "  [PASS] " : "  [FAIL] ") + what);
        if (!ok) {
            failures++;
        }
    }
}
