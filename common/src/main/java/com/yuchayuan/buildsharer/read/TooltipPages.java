package com.yuchayuan.buildsharer.read;

import com.wynntils.core.components.Managers;
import com.wynntils.core.text.StyledText;
import com.wynntils.utils.mc.LoreUtils;
import com.wynntils.utils.wynn.ContainerUtils;
import com.yuchayuan.buildsharer.share.PowderText;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

final class TooltipPages {

    private static final int INVENTORY_MENU = 0;
    private static final int SWAP_OFFHAND = 40;
    private static final int SETTLE_TICKS = 8;
    private static final int MAX_ROUNDS = 6;

    private TooltipPages() {}

    static CompletableFuture<Map<Integer, List<List<String>>>> all(List<Integer> slots) {
        CompletableFuture<Map<Integer, List<List<String>>>> result = new CompletableFuture<>();
        Map<Integer, List<List<String>>> pages = new LinkedHashMap<>();
        Map<Integer, Integer> start = new HashMap<>();
        for (int slot : slots) {
            List<String> lines = lines(stack(slot));
            int[] page = PowderText.page(lines);
            List<List<String>> seen = new ArrayList<>();
            seen.add(lines);
            pages.put(slot, seen);
            if (page[1] > 1) {
                start.put(slot, page[0]);
            }
        }
        round(new ArrayList<>(start.keySet()), start, pages, 0, result);
        return result;
    }

    private static void round(List<Integer> pending, Map<Integer, Integer> start,
                              Map<Integer, List<List<String>>> pages, int round,
                              CompletableFuture<Map<Integer, List<List<String>>>> result) {
        if (pending.isEmpty() || round >= MAX_ROUNDS) {
            result.complete(pages);
            return;
        }
        for (int slot : pending) {
            press(slot);
        }
        Managers.TickScheduler.scheduleLater(() -> {
            List<Integer> next = new ArrayList<>();
            for (int slot : pending) {
                List<String> lines = lines(stack(slot));
                if (PowderText.page(lines)[0] == start.get(slot)) {
                    continue;
                }
                pages.get(slot).add(lines);
                next.add(slot);
            }
            round(next, start, pages, round + 1, result);
        }, SETTLE_TICKS);
    }

    static ItemStack stack(int slot) {
        return Minecraft.getInstance().player.inventoryMenu.getSlot(slot).getItem();
    }

    private static List<String> lines(ItemStack stack) {
        List<String> out = new ArrayList<>();
        if (stack.isEmpty()) {
            return out;
        }
        for (Component line : LoreUtils.getTooltipLines(stack)) {
            out.add(StyledText.fromComponent(line).getString());
        }
        return out;
    }

    private static void press(int slot) {
        ContainerUtils.pressKeyOnSlot(slot, INVENTORY_MENU, SWAP_OFFHAND,
                Minecraft.getInstance().player.inventoryMenu.getItems());
    }
}
