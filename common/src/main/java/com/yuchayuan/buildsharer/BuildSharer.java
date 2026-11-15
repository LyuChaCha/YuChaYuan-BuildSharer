package com.yuchayuan.buildsharer;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wynntils.core.components.Models;
import com.yuchayuan.buildsharer.data.DataSource;
import com.yuchayuan.buildsharer.data.WynnBuilderData;
import com.yuchayuan.buildsharer.read.CharacterReader;
import com.yuchayuan.buildsharer.share.Assembler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BuildSharer {

    public static final String MOD_ID = "yuchayuan_buildsharer";
    public static final String COMMAND = "buildsharer";

    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static String version = "dev";
    private static DataSource source;
    private static Settings settings;

    private BuildSharer() {}

    public static void init(String modVersion) {
        version = modVersion;
    }

    public static <S> LiteralArgumentBuilder<S> command() {
        return LiteralArgumentBuilder.<S>literal(COMMAND)
                .executes(context -> run(BuildSharer::share))
                .then(LiteralArgumentBuilder.<S>literal("tomes")
                        .then(LiteralArgumentBuilder.<S>literal("on").executes(context -> run(() -> tomes(true))))
                        .then(LiteralArgumentBuilder.<S>literal("off").executes(context -> run(() -> tomes(false)))));
    }

    private static int run(Runnable action) {
        action.run();
        return 1;
    }

    private static Path folder() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(MOD_ID);
    }

    private static DataSource source() {
        if (source == null) {
            source = new DataSource(folder(), "YuChaYuan-BuildSharer/" + version);
        }
        return source;
    }

    private static Settings settings() {
        if (settings == null) {
            settings = new Settings(folder().resolve("settings.json"));
        }
        return settings;
    }

    private static void tomes(boolean include) {
        try {
            settings().includeTomes(include);
            say(Component.translatable(include ? "yuchayuan_buildsharer.tomes_on" : "yuchayuan_buildsharer.tomes_off")
                    .withStyle(ChatFormatting.GREEN));
        } catch (IOException e) {
            say(Component.translatable("yuchayuan_buildsharer.failed", e.getMessage()).withStyle(ChatFormatting.RED));
        }
    }

    public static void share() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !Models.WorldState.onWorld()) {
            say(Component.translatable("yuchayuan_buildsharer.not_in_world").withStyle(ChatFormatting.RED));
            return;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            say(Component.translatable("yuchayuan_buildsharer.busy").withStyle(ChatFormatting.YELLOW));
            return;
        }
        say(Component.translatable("yuchayuan_buildsharer.reading").withStyle(ChatFormatting.GRAY));
        CompletableFuture<WynnBuilderData> data = source().get();
        CharacterReader.read(settings().includeTomes())
                .thenCombineAsync(data, (snapshot, loaded) -> new Assembler(loaded).assemble(snapshot))
                .whenComplete((result, error) -> mc.execute(() -> {
                    RUNNING.set(false);
                    if (error != null) {
                        fail(error);
                    } else {
                        done(result);
                    }
                }));
    }

    private static void done(Assembler.Result result) {
        Minecraft.getInstance().keyboardHandler.setClipboard(result.url());
        MutableComponent link = Component.literal(result.url()).withStyle(Style.EMPTY
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(result.url())))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("yuchayuan_buildsharer.open"))));
        say(Component.translatable("yuchayuan_buildsharer.done", link).withStyle(ChatFormatting.GREEN));
        warn("yuchayuan_buildsharer.unknown_items", result.unknownItems());
        warn("yuchayuan_buildsharer.unknown_abilities", result.unknownAbilities());
        warn("yuchayuan_buildsharer.unknown_stats", result.unknownStats());
        warn("yuchayuan_buildsharer.approximate_crafts", result.approximateCrafts());
    }

    private static void warn(String key, List<String> names) {
        if (!names.isEmpty()) {
            say(Component.translatable(key, String.join(", ", names)).withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void fail(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        if (cause instanceof Assembler.MissingWeaponException missing) {
            say((missing.name() == null ? Component.translatable("yuchayuan_buildsharer.no_weapon")
                    : Component.translatable("yuchayuan_buildsharer.unknown_weapon", missing.name()))
                    .withStyle(ChatFormatting.RED));
            return;
        }
        say(Component.translatable("yuchayuan_buildsharer.failed", String.valueOf(cause.getMessage()))
                .withStyle(ChatFormatting.RED));
    }

    private static void say(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[BuildSharer] ").withStyle(ChatFormatting.DARK_AQUA)
                    .append(message), false);
        }
    }
}
