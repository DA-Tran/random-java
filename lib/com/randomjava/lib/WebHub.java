package com.randomjava.lib;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Serves every project in the suite from a single JVM on a single port, using
 * only the JDK's built-in HTTP server. No servlet container, no external jars.
 *
 * <p>Routes:
 * <pre>
 *   GET  /                  the hub index
 *   GET  /p/{slug}/         one project's page
 *   POST /p/{slug}/api      that project's {@code api(action, body)}
 *   GET  /assets/shell.css  shared stylesheet
 *   GET  /assets/shell.js   shared client helper
 * </pre>
 *
 * <p>Project instances are created lazily on first request and then kept, so a
 * project that holds state (a to-do list, a game in progress) behaves the way
 * you would expect while the server is up.
 */
public final class WebHub {

    private final List<Meta> catalog;
    private final Map<String, Supplier<Project>> factories;
    private final Map<String, Meta> metaBySlug = new HashMap<>();
    private final Map<String, Project> live = new ConcurrentHashMap<>();

    /**
     * The hub index is the same for every request and now lists 185 tiles, so
     * it is built once rather than reassembled on each page load.
     */
    private volatile String cachedHub;

    public WebHub(List<Meta> catalog, Map<String, Supplier<Project>> factories) {
        this.catalog = catalog;
        this.factories = factories;
        for (Meta meta : catalog) {
            metaBySlug.put(meta.slug(), meta);
        }
    }

    /**
     * Binds a port and starts serving. Tries a few ports above the requested one
     * if it is already taken, so a stale server does not block a fresh run.
     */
    public void start(int preferredPort, boolean openBrowser) throws IOException {
        HttpServer server = bind(preferredPort);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/", this::route);
        server.start();

        int port = server.getAddress().getPort();
        String url = "http://localhost:" + port + "/";
        System.out.println();
        System.out.println("  random-java web hub is running");
        System.out.println("  " + url);
        System.out.println("  " + catalog.size() + " projects  |  press Ctrl+C to stop");
        System.out.println();
        if (openBrowser) {
            openInBrowser(url);
        }
    }

    private static HttpServer bind(int preferredPort) throws IOException {
        IOException last = null;
        for (int port = preferredPort; port < preferredPort + 12; port++) {
            try {
                return HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            } catch (IOException e) {
                last = e;
            }
        }
        throw last == null ? new IOException("could not bind a port") : last;
    }

    private static void openInBrowser(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception ignored) {
            // headless or locked down; the URL is printed above either way
        }
        System.out.println("  (open that URL yourself - this machine has no default browser hook)");
    }

    // ------------------------------------------------------------------
    // Routing
    // ------------------------------------------------------------------

    private void route(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        try {
            if (path.equals("/") || path.isEmpty()) {
                String hub = cachedHub;
                if (hub == null) {
                    hub = Shell.hubPage(catalog);
                    cachedHub = hub;
                }
                sendHtml(exchange, 200, hub);
                return;
            }
            if (path.equals("/assets/shell.css")) {
                send(exchange, 200, "text/css; charset=utf-8", Shell.css());
                return;
            }
            if (path.equals("/assets/shell.js")) {
                send(exchange, 200, "application/javascript; charset=utf-8", Shell.js());
                return;
            }
            if (path.equals("/favicon.ico")) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            if (path.startsWith("/p/")) {
                routeProject(exchange, path);
                return;
            }
            sendHtml(exchange, 404, Shell.page("Not found", null,
                    "<main><p class=\"lede\">No route for <code>" + Shell.escape(path)
                            + "</code>.</p><p><a href=\"/\">Back to all projects</a></p></main>", false));
        } catch (Exception e) {
            sendHtml(exchange, 500, Shell.page("Error", null,
                    "<main><p class=\"lede\">Server error: " + Shell.escape(String.valueOf(e.getMessage()))
                            + "</p></main>", false));
        }
    }

    private void routeProject(HttpExchange exchange, String path) throws Exception {
        String rest = path.substring("/p/".length());
        boolean isApi = rest.endsWith("/api");
        if (isApi) {
            rest = rest.substring(0, rest.length() - "/api".length());
        }
        boolean hadSlash = rest.endsWith("/");
        String slug = hadSlash ? rest.substring(0, rest.length() - 1) : rest;

        Meta meta = metaBySlug.get(slug);
        if (meta == null) {
            sendHtml(exchange, 404, Shell.page("Not found", null,
                    "<main><p class=\"lede\">There is no project called <code>" + Shell.escape(slug)
                            + "</code>.</p><p><a href=\"/\">Back to all projects</a></p></main>", false));
            return;
        }

        // The page must live at a trailing slash so its relative "api" fetch
        // resolves to /p/{slug}/api rather than /p/api.
        if (!isApi && !hadSlash) {
            exchange.getResponseHeaders().add("Location", "/p/" + slug + "/");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
            return;
        }

        Project project = live.computeIfAbsent(slug, key -> factories.get(key).get());

        if (isApi) {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                send(exchange, 405, "application/json; charset=utf-8",
                        Json.write(Json.error("Use POST for the api endpoint.")));
                return;
            }
            String requestBody = readBody(exchange);
            Map<String, Object> parsed = Json.readObject(requestBody);
            String action = Json.str(parsed, "action", "");
            Object response;
            try {
                response = project.api(action, parsed);
            } catch (Exception e) {
                response = Json.error(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            if (response == null) {
                response = Json.ok();
            }
            send(exchange, 200, "application/json; charset=utf-8", Json.write(response));
            return;
        }

        sendHtml(exchange, 200, Shell.projectPage(meta, project.uiFragment()));
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void sendHtml(HttpExchange exchange, int status, String body) throws IOException {
        send(exchange, status, "text/html; charset=utf-8", body);
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.getResponseHeaders().add("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** Convenience for building the slug to factory map in generated code. */
    public static Map<String, Supplier<Project>> newFactoryMap() {
        return new LinkedHashMap<>();
    }
}
