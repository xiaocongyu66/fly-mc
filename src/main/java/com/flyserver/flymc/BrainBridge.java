package com.flyserver.flymc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal fly-server client: login → /v1/chat/simulate (stateless
 * create→observe→step→read actions→delete inside the server).
 */
public class BrainBridge {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final BrainConfig config;
    private String token;
    private long tokenAt = 0;

    /** One decoded motor action from the brain's VNC readout. */
    public record Action(long neuronId, double rate) {}

    public BrainBridge(BrainConfig config) {
        this.config = config;
    }

    /** Runs one brain drive; returns the motor actions (empty on any failure).
     *  offset (0-1) = where in the visual field the stimulus lands (retinotopy). */
    public List<Action> drive(String region, float offset, float current, int steps) {
        try {
            ensureToken();
            JsonObject body = new JsonObject();
            JsonObject target = new JsonObject();
            target.addProperty("region", region);
            target.addProperty("offset", offset);
            body.add("target", target);
            body.addProperty("current", current);
            body.addProperty("steps", steps);
            JsonObject resp = post("/v1/chat/simulate", body.toString(), true);
            List<Action> out = new ArrayList<>();
            JsonArray actions = resp.getAsJsonArray("actions");
            if (actions != null) {
                for (JsonElement el : actions) {
                    JsonObject a = el.getAsJsonObject();
                    out.add(new Action(a.get("neuron_id").getAsLong(),
                            a.get("rate").getAsDouble()));
                }
            }
            return out;
        } catch (Exception e) {
            System.err.println("[flybrain] drive failed: " + e.getMessage());
            return List.of();
        }
    }

    private void ensureToken() throws Exception {
        if (token != null && System.currentTimeMillis() - tokenAt < 240_000) return;
        JsonObject body = new JsonObject();
        body.addProperty("username", config.username);
        body.addProperty("password", config.password);
        JsonObject resp = post("/v1/admin/login", body.toString(), false);
        token = resp.get("token").getAsString();
        tokenAt = System.currentTimeMillis();
    }

    private JsonObject post(String path, String json, boolean auth) throws Exception {
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl + path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (auth && token != null) rb.header("Authorization", "Bearer " + token);
        HttpResponse<String> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IllegalStateException(path + " -> HTTP " + resp.statusCode()
                    + ": " + resp.body());
        }
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }
}
