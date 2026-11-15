package com.yuchayuan.buildsharer;

import com.yuchayuan.buildsharer.craft.Craft;
import com.yuchayuan.buildsharer.data.WynnBuilderData;
import com.yuchayuan.buildsharer.encode.Bits;
import com.yuchayuan.buildsharer.encode.Build;
import com.yuchayuan.buildsharer.share.Assembler;
import com.yuchayuan.buildsharer.share.Snapshot;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RoundTripTest {

    private static int failures;

    public static void main(String[] args) throws Exception {
        bits();
        String dir = System.getenv("WB_DATA");
        if (dir == null) {
            System.out.println("  [略過] 沒設 WB_DATA，跳過 WynnBuilder 資料的部分");
        } else {
            withData(Path.of(dir));
        }
        System.out.println(failures == 0 ? "往返：全部通過" : "往返：" + failures + " 項失敗");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void bits() {
        check("6 位元的 12 是 C", new Bits().append(12, 6).toBase64().equals("C"));
        check("低位元先寫", new Bits().append(1, 1).append(0, 5).toBase64().equals("1"));
        check("跨字元", new Bits().append(0b111111, 6).append(1, 1).toBase64().equals("-1"));
        check("負數照補數截斷", new Bits().appendSigned(-1, 6).toBase64().equals("-"));
        check("Base64 原樣接回", new Bits().appendBase64("Az-9").toBase64().equals("Az-9"));
    }

    private static void withData(Path dir) throws Exception {
        Map<String, String> files = new HashMap<>();
        for (String name : WynnBuilderData.FILES) {
            files.put(name, Files.readString(dir.resolve(name), StandardCharsets.UTF_8));
        }
        files.put(WynnBuilderData.MAPPINGS, Files.readString(dir.resolve(WynnBuilderData.MAPPINGS), StandardCharsets.UTF_8));
        WynnBuilderData data = new WynnBuilderData(dir.getFileName().toString(),
                Integer.parseInt(System.getenv().getOrDefault("WB_VERSION_ID", "35")), files);

        check("裝備名稱查得到", data.item("Heretic").orElse(-1) == 4991);
        check("顯示名查得到", data.item("Ionic Spark").isPresent());
        check("技能名 I 對 1", data.tree("Warrior").flatMap(t -> t.find("Spear Proficiency I")).isPresent());
        check("技能名補 I", data.tree("Warrior").flatMap(t -> t.find("Cheaper Bash")).isPresent());

        Snapshot snapshot = new Snapshot(List.of(
                new Snapshot.Gear("Heretic", List.of(3, 3, 3, 3)),
                new Snapshot.Gear("Coal Fire", List.of(0, 4, 0)),
                new Snapshot.Gear("Regime Busters", List.of()),
                new Snapshot.Gear("Bleeding Soles", List.of(2)),
                new Snapshot.Gear("Burning Brand", List.of()),
                new Snapshot.Gear("Cannon Coin", List.of()),
                new Snapshot.Gear("Reverence", List.of()),
                new Snapshot.Gear("Pendulum", List.of()),
                new Snapshot.Gear("Propitious", List.of(1, 1))),
                new int[] {10, 20, 0, 30, 45}, 106, "Warrior",
                List.of(new Snapshot.NamedAspect("Aspect of Rallying Fervor", 2)),
                List.of("Bash", "Spear Proficiency I", "Cheaper Bash"));
        Assembler.Result result = new Assembler(data).assemble(snapshot);
        System.out.println("      配裝網址：" + result.url());
        check("沒有找不到的裝備", result.unknownItems().isEmpty());
        check("沒有找不到的技能", result.unknownAbilities().isEmpty());

        Craft craft = new Craft(618, false, new int[] {3, 2}, List.of(4000, 4000, 4000, 4000, 4000, 4000), "NORMAL", true);
        System.out.println("      配方網址：" + craft.crafterUrl());

        List<Build.Piece> pieces = new ArrayList<>(List.of(
                new Build.Crafted(craft, List.of(0, 0)), new Build.Empty(), new Build.Empty(), new Build.Empty(),
                new Build.Empty(), new Build.Empty(), new Build.Empty(), new Build.Empty(),
                new Build.Crafted(new Craft(627, true, new int[] {3, 3},
                        List.of(4000, 4000, 4000, 4000, 4000, 4000), "FAST", true), List.of())));
        Build build = new Build(pieces, null, 105, List.of(), data.tree("Warrior").orElseThrow(), Set.of());
        System.out.println("      含製作物的配裝網址：" + data.encoder().url(build));
    }

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "  [PASS] " : "  [FAIL] ") + what);
        if (!ok) {
            failures++;
        }
    }
}
