package com.jvmvisualizer.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Local, file-based session persistence (FR-14). Chosen over the SRS's
 * optional JDBC/PostgreSQL backing for v1: no external database to install
 * just to save a Playground script. Each session is one .json file under
 * {@code ./sessions/}.
 */
public class SessionStore {

    private final Path sessionsDir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public SessionStore() {
        this(Paths.get("sessions"));
    }

    public SessionStore(Path sessionsDir) {
        this.sessionsDir = sessionsDir;
    }

    public void save(Session session) throws IOException {
        Files.createDirectories(sessionsDir);
        Path file = sessionsDir.resolve(sanitize(session.name) + ".json");
        Files.writeString(file, gson.toJson(session), StandardCharsets.UTF_8);
    }

    public Session load(String name) throws IOException {
        Path file = sessionsDir.resolve(sanitize(name) + ".json");
        String json = Files.readString(file, StandardCharsets.UTF_8);
        return gson.fromJson(json, Session.class);
    }

    public List<String> listSessionNames() {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(sessionsDir)) {
            return names;
        }
        try (Stream<Path> files = Files.list(sessionsDir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(p -> {
                        String fileName = p.getFileName().toString();
                        names.add(fileName.substring(0, fileName.length() - ".json".length()));
                    });
        } catch (IOException ignored) {
            // No sessions saved yet.
        }
        return names;
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
