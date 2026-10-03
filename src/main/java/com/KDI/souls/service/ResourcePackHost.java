package com.KDI.souls.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ResourcePackHost {
    private static final String PACK_RESOURCE = "resources/souls-resource-pack.zip";
    private static final String PACK_PATH = "/souls-resource-pack.zip";
    private static final String[] REQUIRED_FILES = {
            "pack.mcmeta",
            "assets/minecraft/items/prismarine_shard.json",
            "assets/minecraft/items/silence_armor_trim_smithing_template.json",
            "assets/minecraft/items/netherite_hoe.json",
            "assets/minecraft/items/netherite_axe.json",
            "assets/minecraft/models/item/prismarine_shard.json",
            "assets/minecraft/models/item/silence_armor_trim_smithing_template.json",
            "assets/minecraft/models/item/netherite_hoe.json",
            "assets/minecraft/models/item/netherite_axe.json",
            "assets/minecraft/optifine/cit/souls/soul_shard.properties",
            "assets/minecraft/optifine/cit/souls/book_of_boc.properties",
            "assets/minecraft/optifine/cit/souls/sarculum.properties",
            "assets/souls/models/item/soul_shard.json",
            "assets/souls/models/item/book_of_boc.json",
            "assets/souls/models/item/the_sarculum.json",
            "assets/souls/textures/item/soul_shard.png",
            "assets/souls/textures/item/book_of_boc.png",
            "assets/souls/textures/item/the_sarculum.png"
    };

    private final JavaPlugin plugin;
    private HttpServer server;
    private ExecutorService executor;
    private volatile PackSnapshot pack;
    private volatile String publicUrl;

    public ResourcePackHost(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() throws IOException {
        refreshBundledPack();
        startHttpServer();
    }

    public AuditResult repairAndAudit() {
        List<String> messages = new ArrayList<>();
        byte[] bundled;
        try {
            bundled = readBundledPack();
        } catch (IOException exception) {
            messages.add("ERROR: " + exception.getMessage());
            return new AuditResult(false, false, false, messages);
        }

        Map<String, byte[]> entries;
        try {
            entries = readArchive(bundled);
        } catch (IOException exception) {
            messages.add("ERROR: Bundled resource-pack ZIP is invalid: " + exception.getMessage());
            return new AuditResult(false, false, false, messages);
        }
        List<String> validation = validatePack(entries);
        boolean valid = validation.stream().noneMatch(line -> line.startsWith("ERROR:"));
        messages.addAll(validation);
        if (!valid) {
            return new AuditResult(false, false, false, messages);
        }

        boolean repaired;
        try {
            repaired = syncPackFile(bundled);
            setPack(bundled);
        } catch (IOException | NoSuchAlgorithmException exception) {
            messages.add("ERROR: Could not restore the server resource-pack file: " + exception.getMessage());
            return new AuditResult(false, false, false, messages);
        }
        messages.add(repaired
                ? "OK: Restored the plugin's bundled pack to plugins/Souls/resource-packs/souls-resource-pack.zip."
                : "OK: Server resource-pack ZIP is present and matches the plugin bundle.");

        if (server == null) {
            try {
                startHttpServer();
                messages.add("OK: Embedded resource-pack HTTP server is running.");
            } catch (IOException | IllegalArgumentException exception) {
                messages.add("ERROR: Resource-pack HTTP server is offline: " + exception.getMessage());
            }
        }
        updatePublicUrl();
        boolean hostReady = server != null && publicUrl != null;
        if (hostReady) {
            messages.add("OK: Pack endpoint is configured at " + publicUrl);
            messages.add("OK: Pack SHA-1 " + pack.sha1());
        } else if (server != null) {
            messages.add("ERROR: HTTP server is running, but resource-pack.host is blank or invalid.");
        }
        messages.add("INFO: Client-loaded status confirms download/reload; the server cannot inspect rendered pixels on a player's screen.");
        return new AuditResult(true, hostReady, repaired, messages);
    }

    private void refreshBundledPack() throws IOException {
        byte[] bundled = readBundledPack();
        Map<String, byte[]> entries = readArchive(bundled);
        List<String> errors = validatePack(entries).stream()
                .filter(line -> line.startsWith("ERROR:"))
                .toList();
        if (!errors.isEmpty()) {
            throw new IOException(String.join(" ", errors));
        }
        syncPackFile(bundled);
        try {
            setPack(bundled);
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-1 is unavailable.", exception);
        }
    }

    private byte[] readBundledPack() throws IOException {
        try (InputStream input = plugin.getResource(PACK_RESOURCE)) {
            if (input == null) {
                throw new IOException("Resource pack is missing from the plugin JAR: " + PACK_RESOURCE);
            }
            return input.readAllBytes();
        }
    }

    private void startHttpServer() throws IOException {
        if (server != null) {
            updatePublicUrl();
            return;
        }
        int port = plugin.getConfig().getInt("resource-pack.port", 8765);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("resource-pack.port must be between 1 and 65535.");
        }
        String bindAddress = plugin.getConfig().getString("resource-pack.bind-address", "0.0.0.0").trim();
        InetSocketAddress socketAddress = bindAddress.isEmpty()
                ? new InetSocketAddress(port)
                : new InetSocketAddress(bindAddress, port);

        HttpServer newServer = HttpServer.create(socketAddress, 0);
        newServer.createContext(PACK_PATH, this::servePack);
        ExecutorService newExecutor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "Souls-Resource-Pack-HTTP");
            thread.setDaemon(true);
            return thread;
        });
        newServer.setExecutor(newExecutor);
        try {
            newServer.start();
        } catch (RuntimeException exception) {
            newExecutor.shutdownNow();
            throw exception;
        }
        server = newServer;
        executor = newExecutor;
        updatePublicUrl();
    }

    private void updatePublicUrl() {
        publicUrl = null;
        if (server == null) {
            return;
        }
        int port = plugin.getConfig().getInt("resource-pack.port", 8765);
        String host = plugin.getConfig().getString("resource-pack.host", "").trim();
        if (host.isEmpty()) {
            plugin.getLogger().warning("Resource pack HTTP server is listening on port " + port
                    + ", but resource-pack.host is blank; players will not receive the pack until it is configured.");
            return;
        }

        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        try {
            URI uri = new URI("http", null, host, port, PACK_PATH, "sha1=" + pack.sha1(), null);
            if (uri.getHost() == null) {
                throw new URISyntaxException(host, "Invalid host");
            }
            publicUrl = uri.toASCIIString();
        } catch (URISyntaxException exception) {
            plugin.getLogger().warning("Invalid resource-pack.host; use a public hostname or IP without a scheme.");
            return;
        }

        plugin.getLogger().info("Serving resource pack at " + publicUrl);
    }

    private void servePack(HttpExchange exchange) throws IOException {
        try {
            PackSnapshot currentPack = pack;
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if (!PACK_PATH.equals(path)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            if (currentPack == null) {
                exchange.sendResponseHeaders(503, -1);
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Content-Length", Integer.toString(currentPack.bytes().length));
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"souls-resource-pack.zip\"");
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=0, must-revalidate");
            exchange.getResponseHeaders().set("ETag", "\"" + currentPack.sha1() + "\"");
            if ("HEAD".equals(method)) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            exchange.sendResponseHeaders(200, currentPack.bytes().length);
            exchange.getResponseBody().write(currentPack.bytes());
        } finally {
            exchange.close();
        }
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public byte[] getSha1Bytes() {
        PackSnapshot currentPack = pack;
        return currentPack == null ? new byte[0] : currentPack.sha1Bytes().clone();
    }

    private void setPack(byte[] bytes) throws NoSuchAlgorithmException {
        byte[] sha1Bytes = MessageDigest.getInstance("SHA-1").digest(bytes);
        pack = new PackSnapshot(bytes, sha1Bytes, HexFormat.of().formatHex(sha1Bytes));
    }

    private boolean syncPackFile(byte[] bytes) throws IOException {
        Path directory = plugin.getDataFolder().toPath().resolve("resource-packs");
        Path target = directory.resolve("souls-resource-pack.zip");
        boolean changed = !Files.isRegularFile(target) || !Arrays.equals(Files.readAllBytes(target), bytes);
        Files.createDirectories(directory);
        if (!changed) {
            return false;
        }
        Path temporary = directory.resolve("souls-resource-pack.zip.tmp");
        Files.write(temporary, bytes);
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return true;
    }

    private Map<String, byte[]> readArchive(byte[] bytes) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zip.readAllBytes());
                }
                zip.closeEntry();
            }
        }
        return entries;
    }

    private List<String> validatePack(Map<String, byte[]> entries) {
        List<String> messages = new ArrayList<>();
        for (String file : REQUIRED_FILES) {
            if (!entries.containsKey(file)) {
                messages.add("ERROR: Pack is missing " + file + ".");
            }
        }
        if (messages.stream().anyMatch(line -> line.startsWith("ERROR:"))) {
            return messages;
        }

        checkModel(entries, messages, "assets/minecraft/items/prismarine_shard.json", "19000", "souls:item/soul_shard");
        checkModel(entries, messages, "assets/minecraft/items/silence_armor_trim_smithing_template.json", "19001", "souls:item/book_of_boc");
        checkModel(entries, messages, "assets/minecraft/items/netherite_hoe.json", "19002", "souls:item/the_sarculum");
        String sarculumCit = new String(entries.get("assets/minecraft/optifine/cit/souls/sarculum.properties"),
                java.nio.charset.StandardCharsets.UTF_8);
        if (!sarculumCit.contains("items=minecraft:netherite_hoe")
                || !sarculumCit.contains("nbt.display.Name=The Sarculum")
                || !sarculumCit.contains("texture=souls:item/the_sarculum")) {
            messages.add("ERROR: Sarculum CIT rule does not match its hoe item name and texture.");
        }
        checkTexture(entries, messages, "assets/souls/textures/item/soul_shard.png");
        checkTexture(entries, messages, "assets/souls/textures/item/book_of_boc.png");
        checkTexture(entries, messages, "assets/souls/textures/item/the_sarculum.png");
        checkTextureReference(entries, messages, "assets/souls/models/item/soul_shard.json", "souls:item/soul_shard");
        checkTextureReference(entries, messages, "assets/souls/models/item/book_of_boc.json", "souls:item/book_of_boc");
        checkTextureReference(entries, messages, "assets/souls/models/item/the_sarculum.json", "souls:item/the_sarculum");
        checkModelParent(entries, messages, "assets/souls/models/item/the_sarculum.json", "minecraft:item/netherite_axe");
        checkModelParent(entries, messages, "assets/minecraft/models/item/sarculum_named.json", "minecraft:item/netherite_axe");

        if (!new String(entries.get("pack.mcmeta"), java.nio.charset.StandardCharsets.UTF_8).contains("\"pack_format\"")) {
            messages.add("ERROR: pack.mcmeta has no pack_format; the client may reject the pack.");
        }
        Map<String, String> hashes = new HashMap<>();
        for (String texture : List.of(
                "assets/souls/textures/item/soul_shard.png",
                "assets/souls/textures/item/book_of_boc.png",
                "assets/souls/textures/item/the_sarculum.png")) {
            try {
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(entries.get(texture)));
                String previous = hashes.putIfAbsent(hash, texture);
                if (previous != null) {
                    messages.add("WARNING: " + texture + " is pixel-identical to " + previous
                            + "; those items will look the same until their PNGs are replaced.");
                }
            } catch (NoSuchAlgorithmException exception) {
                messages.add("ERROR: SHA-256 is unavailable while checking texture files.");
            }
        }
        if (messages.stream().noneMatch(line -> line.startsWith("ERROR:"))) {
            messages.add("OK: All three vanilla item-model overrides, CIT rules, model files, and PNG textures are present and linked.");
        }
        return messages;
    }

    private void checkModel(Map<String, byte[]> entries, List<String> messages, String path, String modelData, String model) {
        String json = new String(entries.get(path), java.nio.charset.StandardCharsets.UTF_8);
        if (!json.contains("\"property\": \"minecraft:custom_model_data\"")
                || !json.contains("\"threshold\": " + modelData)
                || !json.contains("\"model\": \"" + model + "\"")) {
            messages.add("ERROR: " + path + " does not map custom model data " + modelData + " to " + model + ".");
        }
    }

    private void checkTexture(Map<String, byte[]> entries, List<String> messages, String path) {
        byte[] png = entries.get(path);
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        if (png.length < signature.length || !Arrays.equals(Arrays.copyOf(png, signature.length), signature)) {
            messages.add("ERROR: " + path + " is missing or is not a valid PNG file.");
        }
    }

    private void checkTextureReference(Map<String, byte[]> entries, List<String> messages, String modelPath, String texture) {
        String json = new String(entries.get(modelPath), java.nio.charset.StandardCharsets.UTF_8);
        if (!json.contains("\"layer0\": \"" + texture + "\"")) {
            messages.add("ERROR: " + modelPath + " does not reference texture " + texture + ".");
        }
    }

    private void checkModelParent(Map<String, byte[]> entries, List<String> messages, String modelPath, String parent) {
        byte[] model = entries.get(modelPath);
        if (model == null || !new String(model, java.nio.charset.StandardCharsets.UTF_8)
                .contains("\"parent\": \"" + parent + "\"")) {
            messages.add("ERROR: " + modelPath + " does not inherit the " + parent + " handheld model.");
        }
    }

    public record AuditResult(boolean valid, boolean hostReady, boolean repaired, List<String> messages) {
        public AuditResult {
            messages = List.copyOf(messages);
        }
    }

    private record PackSnapshot(byte[] bytes, byte[] sha1Bytes, String sha1) {
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}