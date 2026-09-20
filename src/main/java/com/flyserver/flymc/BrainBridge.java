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
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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

    /** WebSocket live activity stream: one HTTP handshake, then persistent
     *  frames at simulation speed. Each frame = one ActivityEvent JSON
     *  (spike_sample = VNC neurons that fired this brain tick). Blocks;
     *  run on a worker thread. Reconnects by returning on any failure. */
    public void wsActivityStream(String sessionId, java.util.function.Consumer<List<Long>> onTick) {
        try {
            ensureToken();
            URI u = URI.create(config.baseUrl);
            String host = u.getHost() == null ? "127.0.0.1" : u.getHost();
            int port = u.getPort() > 0 ? u.getPort() : 80;
            try (Socket sock = new Socket()) {
                sock.connect(new InetSocketAddress(host, port), 3000);
                sock.setSoTimeout(60_000);
                OutputStream out = sock.getOutputStream();
                InputStream in = sock.getInputStream();
                String key = java.util.Base64.getEncoder().encodeToString(
                        ("flybrain" + System.nanoTime()).getBytes(StandardCharsets.UTF_8));
                String handshake = "GET /v1/sessions/" + sessionId + "/ws HTTP/1.1\r\n"
                        + "Host: " + host + ":" + port + "\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: " + key + "\r\n"
                        + "Sec-WebSocket-Version: 13\r\n"
                        + "Authorization: Bearer " + token + "\r\n\r\n";
                out.write(handshake.getBytes(StandardCharsets.UTF_8));
                out.flush();
                BufferedReader hr = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String status = hr.readLine();
                if (status == null || !status.contains("101")) return;  // no upgrade
                String line;
                while ((line = hr.readLine()) != null && !line.isEmpty()) { /* headers */ }

                // frame loop (server frames are unmasked)
                ByteArrayOutputStream payload = new ByteArrayOutputStream();
                while (true) {
                    int b1 = in.read(), b2 = in.read();
                    if (b1 < 0 || b2 < 0) return;
                    boolean masked = (b2 & 0x80) != 0;
                    long len = b2 & 0x7F;
                    if (len == 126) {
                        len = ((in.read() & 0xFFL) << 8) | (in.read() & 0xFFL);
                    } else if (len == 127) {
                        len = 0;
                        for (int i = 0; i < 8; i++) len = (len << 8) | (in.read() & 0xFFL);
                    }
                    byte[] mask = new byte[4];
                    if (masked && in.readNBytes(mask, 0, 4) != 4) return;
                    payload.reset();
                    byte[] buf = new byte[(int) len];
                    if (len > 0 && in.readNBytes(buf, 0, (int) len) != len) return;
                    if (masked) for (int i = 0; i < buf.length; i++) buf[i] ^= mask[i % 4];
                    int opcode = b1 & 0x0F;
                    if (opcode == 0x9) { // ping -> pong (masked, client frame)
                        byte[] pong = new byte[buf.length];
                        System.arraycopy(buf, 0, pong, 0, buf.length);
                        out.write(new byte[]{(byte) 0x8A, (byte) (0x80 | Math.min(125, pong.length))});
                        byte[] m = {(byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78};
                        out.write(m);
                        byte[] maskedPong = new byte[pong.length];
                        for (int i = 0; i < pong.length; i++) maskedPong[i] = (byte) (pong[i] ^ m[i % 4]);
                        out.write(maskedPong);
                        out.flush();
                        continue;
                    }
                    if (opcode == 0x8) return;  // close
                    if (opcode == 0x1) {  // text
                        payload.write(buf);
                        JsonObject ev = JsonParser.parseString(
                                payload.toString(StandardCharsets.UTF_8)).getAsJsonObject();
                        JsonArray sp = ev.getAsJsonArray("spike_sample");
                        if (sp == null) continue;
                        List<Long> ids = new ArrayList<>();
                        for (JsonElement e : sp) ids.add(e.getAsLong());
                        onTick.accept(ids);
                    }
                }
            }
        } catch (Exception e) {
            // stream ended — caller may reconnect
        }
    }

    /** Subscribes to the session's live activity stream (SSE). Each event
     *  carries the VNC neurons that spiked this brain tick — the caller
     *  receives them at simulation speed (~130ms per tick), not per batch.
     *  Blocks until the stream breaks; run on a worker thread. */
    public void activityStream(String sessionId, java.util.function.Consumer<List<Long>> onTick) {
        try {
            ensureToken();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl + "/v1/sessions/" + sessionId + "/activity"))
                    .timeout(Duration.ofMinutes(30))
                    .header("Authorization", "Bearer " + token)
                    .GET().build();
            HttpResponse<java.io.InputStream> resp = http.send(req,
                    HttpResponse.BodyHandlers.ofInputStream());
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(resp.body()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (!line.startsWith("data: ")) continue;
                    JsonObject ev = JsonParser.parseString(line.substring(6))
                            .getAsJsonObject();
                    JsonArray sp = ev.getAsJsonArray("spike_sample");
                    if (sp == null) continue;
                    List<Long> ids = new ArrayList<>();
                    for (JsonElement e : sp) ids.add(e.getAsLong());
                    onTick.accept(ids);
                }
            }
        } catch (Exception e) {
            // stream ended (session gone / server restart) — caller reconnects
        }
    }

    /** Creates a persistent brain session (membrane state lives across drives). */
    public String createSession() {
        try {
            ensureToken();
            JsonObject body = new JsonObject();
            body.addProperty("substrate", config.substrate);
            JsonObject resp = post("/v1/sessions", body.toString(), true);
            return resp.get("id").getAsString();
        } catch (Exception e) {
            System.err.println("[flybrain] session create failed: " + e.getMessage());
            return null;
        }
    }

    /** Runs one drive on a persistent session: stimulate, think, read motors.
     *  offset (0-1) = where in the visual field the stimulus lands (retinotopy). */
    public List<Action> drive(String sessionId, String region, float offset, float current, int steps) {
        try {
            ensureToken();
            JsonObject target = new JsonObject();
            target.addProperty("region", region);
            target.addProperty("offset", offset);
            JsonObject ob = new JsonObject();
            ob.addProperty("modality", "current");
            ob.add("target", target);
            ob.addProperty("current", current);
            ob.addProperty("duration_ticks", 1);
            post("/v1/sessions/" + sessionId + "/observe", ob.toString(), true);

            JsonObject sb = new JsonObject();
            sb.addProperty("steps", steps);
            JsonObject resp = post("/v1/sessions/" + sessionId + "/step", sb.toString(), true);
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
