package com.yuchayuan.buildsharer.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import com.yuchayuan.buildsharer.BuildSharer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class BuildSharerFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BuildSharer.init(FabricLoader.getInstance().getModContainer(BuildSharer.MOD_ID)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("dev"));
        KeyMapping key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.yuchayuan_buildsharer.share",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath(BuildSharer.MOD_ID, "main"))));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) {
                BuildSharer.share();
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal(BuildSharer.COMMAND).executes(context -> {
                    BuildSharer.share();
                    return 1;
                })));
    }
}
