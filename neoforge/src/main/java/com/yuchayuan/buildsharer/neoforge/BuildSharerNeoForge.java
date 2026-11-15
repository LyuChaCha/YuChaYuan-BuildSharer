package com.yuchayuan.buildsharer.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import com.yuchayuan.buildsharer.BuildSharer;
import net.minecraft.client.KeyMapping;
import net.minecraft.commands.Commands;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod(value = BuildSharer.MOD_ID, dist = Dist.CLIENT)
public final class BuildSharerNeoForge {

    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(BuildSharer.MOD_ID, "main"));
    private static final KeyMapping SHARE = new KeyMapping("key.yuchayuan_buildsharer.share",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);

    public BuildSharerNeoForge(IEventBus modBus, ModContainer container) {
        BuildSharer.init(container.getModInfo().getVersion().toString());
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.registerCategory(CATEGORY);
            event.register(SHARE);
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            while (SHARE.consumeClick()) {
                BuildSharer.share();
            }
        });
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, event ->
                event.getDispatcher().register(Commands.literal(BuildSharer.COMMAND).executes(context -> {
                    BuildSharer.share();
                    return 1;
                })));
    }
}
