package cn.cauccsa.flagbridge.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class CsaFlagBridgeState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public Map<String, Registration> registrations = new LinkedHashMap<>();
    public Map<String, String> playerBindings = new LinkedHashMap<>();
    public Set<String> claimedPlayers = new LinkedHashSet<>();

    private transient Path path;

    public static CsaFlagBridgeState load(Path configDir) throws IOException {
        Path dir = configDir.resolve(CsaFlagBridgeServerMod.CONFIG_DIR);
        Files.createDirectories(dir);

        Path path = dir.resolve("state.json");
        CsaFlagBridgeState state;
        if (Files.exists(path)) {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            state = GSON.fromJson(raw, CsaFlagBridgeState.class);
            if (state == null) {
                state = new CsaFlagBridgeState();
            }
        } else {
            state = new CsaFlagBridgeState();
        }

        state.path = path;
        state.normalize();
        state.save();
        return state;
    }

    public synchronized void save() throws IOException {
        Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
    }

    public synchronized void putRegistration(Registration registration) throws IOException {
        registrations.put(registration.token, registration);
        save();
    }

    public synchronized Registration getRegistration(String token) {
        Registration registration = registrations.get(token);
        if (registration == null || registration.isExpired()) {
            return null;
        }
        return registration;
    }

    public synchronized String getBinding(String playerUuid) {
        return playerBindings.get(playerUuid);
    }

    public synchronized void bind(String playerUuid, String token) throws IOException {
        playerBindings.put(playerUuid, token);
        Registration registration = registrations.get(token);
        if (registration != null) {
            registration.boundPlayers.add(playerUuid);
        }
        save();
    }

    public synchronized void unbind(String playerUuid) throws IOException {
        String token = playerBindings.remove(playerUuid);
        if (token != null) {
            Registration registration = registrations.get(token);
            if (registration != null) {
                registration.boundPlayers.remove(playerUuid);
            }
        }
        save();
    }

    public synchronized boolean hasClaimed(String playerUuid) {
        return claimedPlayers.contains(playerUuid);
    }

    public synchronized void markClaimed(String playerUuid) throws IOException {
        claimedPlayers.add(playerUuid);
        save();
    }

    private void normalize() {
        if (registrations == null) {
            registrations = new LinkedHashMap<>();
        }
        if (playerBindings == null) {
            playerBindings = new LinkedHashMap<>();
        }
        if (claimedPlayers == null) {
            claimedPlayers = new LinkedHashSet<>();
        }
        for (Registration registration : registrations.values()) {
            registration.normalize();
        }
    }

    public static final class Registration {
        public String token = "";
        public String flag = "";
        public String teamId = "";
        public String claimCallbackUrl = "";
        public String claimCallbackSecret = "";
        public long registeredAtMillis = Instant.now().toEpochMilli();
        public long expiresAtMillis = 0;
        public Set<String> boundPlayers = new LinkedHashSet<>();
        public boolean consumed = false;

        public boolean isExpired() {
            return expiresAtMillis > 0 && Instant.now().toEpochMilli() > expiresAtMillis;
        }

        private void normalize() {
            if (token == null) {
                token = "";
            }
            if (flag == null) {
                flag = "";
            }
            if (teamId == null) {
                teamId = "";
            }
            if (claimCallbackUrl == null) {
                claimCallbackUrl = "";
            }
            if (claimCallbackSecret == null) {
                claimCallbackSecret = "";
            }
            if (boundPlayers == null) {
                boundPlayers = new LinkedHashSet<>();
            }
        }
    }
}
