package com.yuchayuan.buildsharer.data;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class DataSource {

    private static final String SITE = "https://wynnbuilder.github.io/";
    private static final String LATEST = "latest.txt";

    private final Path cache;
    private final String userAgent;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private volatile CompletableFuture<WynnBuilderData> loading;

    public DataSource(Path cache, String userAgent) {
        this.cache = cache;
        this.userAgent = userAgent;
    }

    public synchronized CompletableFuture<WynnBuilderData> get() {
        CompletableFuture<WynnBuilderData> current = loading;
        if (current == null || current.isCompletedExceptionally()) {
            current = CompletableFuture.supplyAsync(this::load);
            loading = current;
        }
        return current;
    }

    private WynnBuilderData load() {
        try {
            String[] latest = latest();
            String version = latest[0];
            int id = Integer.parseInt(latest[1]);
            Path dir = cache.resolve(version);
            Map<String, String> files = new HashMap<>();
            for (String name : WynnBuilderData.FILES) {
                files.put(name, cached(dir.resolve(name), "data/" + version + "/" + name));
            }
            files.put(WynnBuilderData.MAPPINGS,
                    cached(dir.resolve(WynnBuilderData.MAPPINGS), "py_script/" + WynnBuilderData.MAPPINGS));
            return new WynnBuilderData(version, id, files);
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private String[] latest() throws IOException, InterruptedException {
        Path pointer = cache.resolve(LATEST);
        try {
            List<String> names = WynnBuilderData.versionNames(fetch("js/load_item.js"));
            String[] latest = {names.get(names.size() - 1), Integer.toString(names.size() - 1)};
            write(pointer, latest[0] + "\n" + latest[1] + "\n");
            return latest;
        } catch (IOException | IllegalStateException e) {
            if (!Files.isRegularFile(pointer)) {
                throw e;
            }
            List<String> lines = Files.readAllLines(pointer, StandardCharsets.UTF_8);
            return new String[] {lines.get(0).strip(), lines.get(1).strip()};
        }
    }

    private String cached(Path file, String remote) throws IOException, InterruptedException {
        if (Files.isRegularFile(file)) {
            return Files.readString(file, StandardCharsets.UTF_8);
        }
        String body = fetch(remote);
        write(file, body);
        return body;
    }

    private String fetch(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(SITE + path))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", userAgent)
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException(path + " returned HTTP " + response.statusCode());
        }
        return response.body();
    }

    private static void write(Path file, String body) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, body, StandardCharsets.UTF_8);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
