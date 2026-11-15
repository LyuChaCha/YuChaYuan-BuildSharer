package com.yuchayuan.buildsharer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

final class Badge {

    private static final String LABEL = "BUILDSHARER";
    private static final String NAME = "BuildSharer";

    private static final FontDescription FONT =
            new FontDescription.Resource(Identifier.withDefaultNamespace("banner/pill"));

    private static final int LEFT_EDGE = 0xE060;
    private static final int RIGHT_EDGE = 0xE062;
    private static final int BACK_A = 0xE030;
    private static final int FRONT_A = 0xE000;
    private static final int SPACE_ZERO = 0xD0000;

    private static final int EDGE_WIDTH = 2;
    private static final int LETTER_WIDTH = 6;
    private static final int NARROW_WIDTH = 4;
    private static final String NARROW = "I";
    private static final int GAP = 6;
    private static final int INNER = inner();
    private static final int WIDTH = INNER + EDGE_WIDTH + GAP;

    private static final int[] STOPS = {0x3FA9F0, 0x6FC2F5, 0xB6E2FC};
    private static final int INK = 0x0A1A2C;

    private static final Component PILL = pill();
    private static final Component TEXT = text();

    private Badge() {}

    static Component prefixed(Component line) {
        boolean drawn = Minecraft.getInstance().font.width(PILL) == WIDTH;
        return Component.empty().append(drawn ? PILL : TEXT).append(line);
    }

    private static Component pill() {
        int count = LABEL.length() + 2;
        String step = space(-1);
        MutableComponent out = Component.empty();
        out.append(back(glyph(LEFT_EDGE) + step, 0, count));
        StringBuilder front = new StringBuilder();
        for (int i = 0; i < LABEL.length(); i++) {
            int letter = LABEL.charAt(i) - 'A';
            out.append(back(glyph(BACK_A + letter) + step, i + 1, count));
            front.append(glyph(FRONT_A + letter));
        }
        out.append(back(glyph(RIGHT_EDGE) + space(-INNER - EDGE_WIDTH), count - 1, count));
        Style ink = Style.EMPTY.withFont(FONT).withColor(INK).withoutShadow();
        out.append(Component.literal(front.toString()).setStyle(ink));
        out.append(Component.literal(space(GAP)).setStyle(ink));
        return out;
    }

    private static int inner() {
        int width = 0;
        for (int i = 0; i < LABEL.length(); i++) {
            width += NARROW.indexOf(LABEL.charAt(i)) >= 0 ? NARROW_WIDTH : LETTER_WIDTH;
        }
        return width;
    }

    private static Component back(String text, int index, int count) {
        return Component.literal(text).setStyle(Style.EMPTY.withFont(FONT).withColor(colour(index, count)));
    }

    private static Component text() {
        MutableComponent out = Component.empty();
        out.append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY));
        for (int i = 0; i < NAME.length(); i++) {
            out.append(Component.literal(NAME.substring(i, i + 1))
                    .setStyle(Style.EMPTY.withColor(colour(i, NAME.length()))));
        }
        return out.append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String glyph(int codePoint) {
        return String.valueOf((char) codePoint);
    }

    private static String space(int pixels) {
        return new String(Character.toChars(SPACE_ZERO + pixels));
    }

    private static int colour(int index, int count) {
        double at = (double) index / (count - 1) * (STOPS.length - 1);
        int stop = Math.min((int) at, STOPS.length - 2);
        double part = at - stop;
        int out = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int from = STOPS[stop] >> shift & 0xFF;
            int to = STOPS[stop + 1] >> shift & 0xFF;
            out |= (int) Math.round(from + (to - from) * part) << shift;
        }
        return out;
    }
}
