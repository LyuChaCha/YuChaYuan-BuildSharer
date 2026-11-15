package com.yuchayuan.buildsharer.share;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PowderText {

    private static final List<String> ELEMENTS = List.of("Earth", "Thunder", "Water", "Fire", "Air");
    private static final List<String> TIERS = List.of("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X");
    private static final Pattern FORMATTING = Pattern.compile("§#[0-9a-fA-F]{8}|§.");
    private static final String SLOT = "(?:(Earth|Thunder|Water|Fire|Air) ([IVX]+)|Empty)";
    private static final Pattern ROW = Pattern.compile("\\s*" + SLOT + "(?:\\s+" + SLOT + ")*\\s*");
    private static final Pattern ONE = Pattern.compile(SLOT);
    private static final Pattern PAGE_DOT = Pattern.compile("(§#ffea80ff)?");

    private PowderText() {}

    public static List<Snapshot.Powder> parse(List<String> lines) {
        List<Snapshot.Powder> powders = new ArrayList<>();
        for (String line : lines) {
            String plain = plain(line);
            if (plain.isBlank() || !ROW.matcher(plain).matches()) {
                continue;
            }
            Matcher slot = ONE.matcher(plain);
            while (slot.find()) {
                if (slot.group(1) != null) {
                    int tier = TIERS.indexOf(slot.group(2)) + 1;
                    if (tier > 0) {
                        powders.add(new Snapshot.Powder(ELEMENTS.indexOf(slot.group(1)), tier));
                    }
                }
            }
        }
        return powders;
    }

    public static int[] page(List<String> lines) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            Matcher dot = PAGE_DOT.matcher(lines.get(i));
            int count = 0;
            int current = 0;
            while (dot.find()) {
                if (dot.group(1) != null) {
                    current = count;
                }
                count++;
            }
            return new int[] {current, count};
        }
        return new int[] {0, 0};
    }

    static String plain(String line) {
        String stripped = FORMATTING.matcher(line).replaceAll("");
        StringBuilder out = new StringBuilder(stripped.length());
        stripped.codePoints().filter(c -> c < 0xE000).forEach(out::appendCodePoint);
        return out.toString();
    }
}
