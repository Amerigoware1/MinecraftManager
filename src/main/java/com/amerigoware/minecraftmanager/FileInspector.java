package com.amerigoware.minecraftmanager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.*;

public class FileInspector {

    public enum ContentType {
        MOD, SHADER, RESOURCE_PACK, DATAPACK, WORLD, UNKNOWN
    }

    public record FileAnalysis(ContentType type, String detectedVersion) {}

    public static FileAnalysis inspect(Path path) {
        if (Files.isDirectory(path)) {
            return inspectDirectory(path);
        } else if (path.toString().endsWith(".jar")) {
            return inspectJar(path);
        } else if (path.toString().endsWith(".zip")) {
            return inspectZip(path);
        }
        return new FileAnalysis(ContentType.UNKNOWN, null);
    }

    private static FileAnalysis inspectJar(Path jarPath) {
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            // Fabric / Quilt Mod
            ZipEntry fabricEntry = zip.getEntry("fabric.mod.json");
            if (fabricEntry != null) {
                String content = readStream(zip.getInputStream(fabricEntry));
                String version = extractJsonValue(content, "minecraft");
                return new FileAnalysis(ContentType.MOD, version);
            }

            // NeoForge / Modern Forge Mod
            ZipEntry tomlEntry = zip.getEntry("META-INF/mods.toml");
            if (tomlEntry != null) {
                String content = readStream(zip.getInputStream(tomlEntry));
                String version = extractTomlValue(content, "loaderVersion");
                return new FileAnalysis(ContentType.MOD, version);
            }

            // Legacy Forge Mod
            ZipEntry mcmodEntry = zip.getEntry("mcmod.info");
            if (mcmodEntry != null) {
                String content = readStream(zip.getInputStream(mcmodEntry));
                String version = extractJsonValue(content, "mcversion");
                return new FileAnalysis(ContentType.MOD, version);
            }
        } catch (IOException e) {
            System.err.println("Failed to read JAR metadata: " + e.getMessage());
        }
        return new FileAnalysis(ContentType.MOD, null); // Fallback to generic mod
    }

    private static FileAnalysis inspectZip(Path zipPath) {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            if (zip.getEntry("pack.mcmeta") != null) {
                return new FileAnalysis(ContentType.RESOURCE_PACK, null);
            }
            if (zip.stream().anyMatch(e -> e.getName().startsWith("shaders/"))) {
                return new FileAnalysis(ContentType.SHADER, null);
            }
            if (zip.getEntry("level.dat") != null) {
                return new FileAnalysis(ContentType.WORLD, null);
            }
        } catch (IOException e) {
            System.err.println("Failed to inspect ZIP archive: " + e.getMessage());
        }
        return new FileAnalysis(ContentType.UNKNOWN, null);
    }

    private static FileAnalysis inspectDirectory(Path dir) {
        if (Files.exists(dir.resolve("level.dat"))) {
            return new FileAnalysis(ContentType.WORLD, parseWorldVersion(dir.resolve("level.dat")));
        }
        if (Files.exists(dir.resolve("pack.mcmeta"))) {
            return Files.exists(dir.resolve("data"))
                    ? new FileAnalysis(ContentType.DATAPACK, null)
                    : new FileAnalysis(ContentType.RESOURCE_PACK, null);
        }
        return new FileAnalysis(ContentType.UNKNOWN, null);
    }

    private static String parseWorldVersion(Path levelDat) {
        // Reads uncompressed string fragments inside GZIP level.dat NBT
        try (InputStream fis = Files.newInputStream(levelDat);
             GZIPInputStream gis = new GZIPInputStream(fis);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int len;
            while ((len = gis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            String rawNbt = baos.toString(StandardCharsets.UTF_8);

            // Extract version string (e.g., 1.20.1) directly from NBT tag binary pattern
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("1\\.\\d+(\\.\\d+)?").matcher(rawNbt);
            if (matcher.find()) {
                return matcher.group();
            }
        } catch (IOException ignored) {}
        return null;
    }

    private static String readStream(InputStream stream) throws IOException {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String extractJsonValue(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + key + "\":\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static String extractTomlValue(String toml, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(key + "\\s*=\\s*\"([^\"]+)\"").matcher(toml);
        return m.find() ? m.group(1) : null;
    }
}