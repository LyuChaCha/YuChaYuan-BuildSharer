package com.yuchayuan.buildsharer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Settings {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final class Values {
        boolean includeTomes;
    }

    private final Path file;
    private Values values = new Values();

    Settings(Path file) {
        this.file = file;
        if (Files.isRegularFile(file)) {
            try {
                Values loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Values.class);
                if (loaded != null) {
                    values = loaded;
                }
            } catch (IOException | JsonParseException e) {
                values = new Values();
            }
        }
    }

    public boolean includeTomes() {
        return values.includeTomes;
    }

    public void includeTomes(boolean include) throws IOException {
        values.includeTomes = include;
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(values), StandardCharsets.UTF_8);
    }
}
