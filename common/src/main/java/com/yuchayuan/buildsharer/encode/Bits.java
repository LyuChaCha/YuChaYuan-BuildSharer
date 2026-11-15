package com.yuchayuan.buildsharer.encode;

import java.util.BitSet;

public final class Bits {

    static final String DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz+-";

    private final BitSet bits = new BitSet();
    private int length;

    public int length() {
        return length;
    }

    public Bits append(long value, int width) {
        if (width < 0 || width > 32) {
            throw new IllegalArgumentException("width " + width);
        }
        long mask = width == 32 ? 0xFFFFFFFFL : (1L << width) - 1;
        if ((value & mask) != value) {
            throw new IllegalArgumentException(value + " does not fit in " + width + " bits");
        }
        for (int i = 0; i < width; i++) {
            bits.set(length + i, ((value >>> i) & 1) == 1);
        }
        length += width;
        return this;
    }

    public Bits appendSigned(int value, int width) {
        long mask = width == 32 ? 0xFFFFFFFFL : (1L << width) - 1;
        return append(value & mask, width);
    }

    public Bits append(Bits other) {
        for (int i = 0; i < other.length; i++) {
            bits.set(length + i, other.bits.get(i));
        }
        length += other.length;
        return this;
    }

    public Bits appendBase64(String text) {
        for (int i = 0; i < text.length(); i++) {
            int digit = DIGITS.indexOf(text.charAt(i));
            if (digit < 0) {
                throw new IllegalArgumentException("not base64: " + text);
            }
            append(digit, 6);
        }
        return this;
    }

    public String toBase64() {
        StringBuilder out = new StringBuilder((length + 5) / 6);
        for (int at = 0; at < length; at += 6) {
            int digit = 0;
            for (int i = 0; i < 6 && at + i < length; i++) {
                if (bits.get(at + i)) {
                    digit |= 1 << i;
                }
            }
            out.append(DIGITS.charAt(digit));
        }
        return out.toString();
    }
}
