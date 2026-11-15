package com.yuchayuan.buildsharer.read;

import com.wynntils.core.components.Models;
import com.wynntils.core.text.StyledText;
import com.wynntils.utils.mc.LoreUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class ItemDump {

    private ItemDump() {}

    public static Path write(Path dir) throws IOException {
        List<ItemStack> stacks = new ArrayList<>(Models.Inventory.getEquippedItems());
        stacks.add(Minecraft.getInstance().player.getMainHandItem());
        StringBuilder out = new StringBuilder("# ").append(LocalDateTime.now()).append('\n');
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            out.append("\n== ").append(StyledText.fromComponent(stack.getHoverName()).getString()).append('\n');
            out.append("wynntils: ").append(Models.Item.getWynnItem(stack).map(Object::toString).orElse("-")).append('\n');
            for (Component line : LoreUtils.getTooltipLines(stack)) {
                out.append("  | ").append(StyledText.fromComponent(line).getString()).append('\n');
            }
            out.append("components: ").append(stack.getComponents()).append('\n');
        }
        Files.createDirectories(dir);
        Path file = dir.resolve("item-dump.txt");
        Files.writeString(file, out, StandardCharsets.UTF_8);
        return file;
    }
}
