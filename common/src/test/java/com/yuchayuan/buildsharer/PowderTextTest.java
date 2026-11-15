package com.yuchayuan.buildsharer;

import com.yuchayuan.buildsharer.share.PowderText;
import com.yuchayuan.buildsharer.share.Snapshot;

import java.util.List;

public final class PowderTextTest {

    private static int failures;

    public static void main(String[] args) {
        String gap = cp(0xCFFFF);
        List<String> stardew = List.of(
                "§f" + cp(0xE002) + " §bConcentration" + cp(0xCFFB6) + cp(0xD0073) + "§7Tier VII",
                "§7Increase your §b" + cp(0xE004) + " Water Damage",
                "§7by §f0.2%§7 for §f1 second§7 when using",
                "",
                "§f" + cp(0xE003) + gap + " §bWater VII" + cp(0xD0011) + " §f" + cp(0xE003) + gap + " §bWater VII" + cp(0xD000D),
                "§f" + cp(0xD002E) + cp(0xD0030) + cp(0xF002) + cp(0xCFF97) + cp(0xD0040)
                        + "§#455449ff" + cp(0xE000) + cp(0xD0004) + "§#ffea80ff" + cp(0xE000) + cp(0xD0004)
                        + "§#455449ff" + cp(0xE000));
        List<Snapshot.Powder> powders = PowderText.parse(stardew);
        check("兩格水 VII", powders.equals(List.of(new Snapshot.Powder(2, 7), new Snapshot.Powder(2, 7))));
        int[] page = PowderText.page(stardew);
        check("第 2 頁、共 3 頁", page[0] == 1 && page[1] == 3);

        List<String> mixed = List.of(
                "§f" + cp(0xE000) + gap + " §bEarth VI" + cp(0xD0021) + " §f" + cp(0xE000) + gap + " §8Empty" + cp(0xD001D),
                "§f" + cp(0xE000) + gap + " §bAir IV" + cp(0xD0021) + cp(0xD0046));
        check("土 VI、空格、風 IV", PowderText.parse(mixed).equals(
                List.of(new Snapshot.Powder(0, 6), new Snapshot.Powder(4, 4))));

        List<String> empty = List.of("§f" + cp(0xE000) + gap + " §8Empty" + cp(0xD0021) + " §f" + cp(0xE000) + gap + " §8Empty");
        check("全空沒有粉末", PowderText.parse(empty).isEmpty());

        System.out.println(failures == 0 ? "粉末解析：全部通過" : "粉末解析：" + failures + " 項失敗");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static String cp(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "  [PASS] " : "  [FAIL] ") + what);
        if (!ok) {
            failures++;
        }
    }
}
