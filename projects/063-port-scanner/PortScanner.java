package com.randomjava.projects.portscanner;

import com.randomjava.lib.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

/**
 * Port Scanner - finds which TCP ports are listening on this machine.
 *
 * <p><b>Scoped to loopback on purpose.</b> The same code pointed at an
 * arbitrary host is network reconnaissance, which is the first step of an
 * intrusion and in many places unlawful without written permission. Scanning
 * your own machine answers the question a developer actually has - what is
 * listening, and should it be - so that is what this does, and
 * {@link #resolve} refuses anything else rather than leaving it to the
 * operator's judgement.
 *
 * <p>The distinction worth getting right is <b>closed versus filtered</b>,
 * because they look similar and mean different things:
 *
 * <ul>
 *   <li><b>Open</b> - the handshake completed. Something is listening.</li>
 *   <li><b>Closed</b> - the connection was actively refused. Nothing is
 *       listening, and the machine said so immediately.</li>
 *   <li><b>Filtered</b> - nothing came back at all, and the attempt timed out.
 *       A firewall is dropping packets rather than refusing them.</li>
 * </ul>
 *
 * <p>Collapsing the last two into "not open" throws away the interesting half.
 * A refusal proves the host is reachable and that port is genuinely free;
 * silence proves nothing about either, and a scan that reports a timeout as
 * "closed" is asserting something it did not observe.
 *
 * <p>Ports are probed in parallel with a short timeout, since a serial scan of
 * a thousand ports at one second each is quarter of an hour of mostly waiting.
 */
public final class PortScanner implements Project {

    public static final Meta META = new Meta(63, "port-scanner", "Port Scanner", "Cybersecurity", Kind.LIST,
            Difficulty.ADVANCED, "Probe this machine for open TCP ports and name the usual services.",
            "", true);

    public enum State { OPEN, CLOSED, FILTERED }

    public record Result(int port, State state, String service, long millis) { }

    /** Hosts that mean "this machine". Anything else is refused. */
    private static final Set<String> LOOPBACK = Set.of(
            "localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "");

    private static final Map<Integer, String> SERVICES = new LinkedHashMap<>();
    static {
        int[] ports = {20, 21, 22, 23, 25, 53, 67, 68, 69, 80, 110, 123, 135, 137, 139, 143,
                161, 389, 443, 445, 465, 514, 587, 631, 636, 993, 995, 1080, 1433, 1521,
                3000, 3306, 3389, 4444, 5000, 5432, 5672, 5900, 6379, 8000, 8080, 8443,
                8888, 9000, 9090, 9200, 11211, 27017};
        String[] names = {"ftp-data", "ftp", "ssh", "telnet", "smtp", "dns", "dhcp-server",
                "dhcp-client", "tftp", "http", "pop3", "ntp", "msrpc", "netbios-ns",
                "netbios-ssn", "imap", "snmp", "ldap", "https", "smb", "smtps", "syslog",
                "smtp-submission", "ipp", "ldaps", "imaps", "pop3s", "socks", "mssql",
                "oracle", "dev-server", "mysql", "rdp", "often-a-backdoor", "dev-server",
                "postgresql", "amqp", "vnc", "redis", "dev-server", "http-alt", "https-alt",
                "http-alt", "dev-server", "prometheus", "elasticsearch", "memcached",
                "mongodb"};
        for (int i = 0; i < ports.length; i++) { SERVICES.put(ports[i], names[i]); }
    }

    /** The ports worth checking first on a developer machine. */
    public static final List<Integer> COMMON = List.copyOf(SERVICES.keySet());

    private final List<Result> results = new ArrayList<>();
    private int timeoutMillis = 250;

    @Override public Meta meta() { return META; }

    // ------------------------------------------------------------------
    // Scanning
    // ------------------------------------------------------------------

    public static String service(int port) {
        return SERVICES.getOrDefault(port, "unknown");
    }

    /** Accepts only loopback. Anything else is somebody else's machine. */
    public static String resolve(String host) {
        String wanted = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        if (!LOOPBACK.contains(wanted)) {
            throw new IllegalArgumentException(
                    "This scans localhost only. Pointing a port scanner at another host is "
                    + "reconnaissance, and needs that host owner's permission.");
        }
        return "127.0.0.1";
    }

    public void setTimeout(int millis) {
        if (millis < 10 || millis > 5000) {
            throw new IllegalArgumentException("Use a timeout between 10 and 5000 ms.");
        }
        timeoutMillis = millis;
    }

    public int timeout() { return timeoutMillis; }

    /**
     * Probes one port. The exception type is the evidence: a refusal arrives as
     * ConnectException, a firewall drop as SocketTimeoutException, and they are
     * genuinely different findings.
     */
    public Result probe(String host, int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port " + port + " is not in 1 to 65535.");
        }
        String address = resolve(host);
        long started = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, port), timeoutMillis);
            return finish(port, State.OPEN, started);
        } catch (java.net.SocketTimeoutException e) {
            return finish(port, State.FILTERED, started);
        } catch (IOException e) {
            return finish(port, State.CLOSED, started);
        }
    }

    private static Result finish(int port, State state, long started) {
        return new Result(port, state, service(port),
                Math.round((System.nanoTime() - started) / 1_000_000.0));
    }

    /** Scans a list of ports in parallel and keeps the findings. */
    public List<Result> scan(String host, List<Integer> ports) {
        resolve(host);
        if (ports.isEmpty()) { throw new IllegalArgumentException("No ports to scan."); }
        if (ports.size() > 2000) {
            throw new IllegalArgumentException("Scan at most 2000 ports at a time.");
        }
        int threads = Math.min(64, Math.max(4, ports.size() / 4));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Result>> futures = new ArrayList<>();
            for (int port : ports) { futures.add(pool.submit(() -> probe(host, port))); }
            List<Result> found = new ArrayList<>();
            for (Future<Result> future : futures) {
                try {
                    found.add(future.get(timeoutMillis * 4L, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("The scan was interrupted.");
                } catch (ExecutionException | TimeoutException e) {
                    // A probe that neither connected nor failed in time is
                    // filtered, which is exactly what this means.
                    found.add(new Result(0, State.FILTERED, "unknown", timeoutMillis));
                }
            }
            found.sort(Comparator.comparingInt(Result::port));
            results.clear();
            results.addAll(found);
            return List.copyOf(found);
        } finally {
            pool.shutdownNow();
        }
    }

    public List<Integer> range(int from, int to) {
        if (from < 1 || to > 65535 || from > to) {
            throw new IllegalArgumentException("Give a range inside 1 to 65535, low to high.");
        }
        List<Integer> out = new ArrayList<>();
        for (int port = from; port <= to; port++) { out.add(port); }
        return out;
    }

    public List<Result> results() { return List.copyOf(results); }
    public List<Result> open() {
        return results.stream().filter(r -> r.state() == State.OPEN).toList();
    }
    public void clear() { results.clear(); }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    private String summary() {
        if (results.isEmpty()) {
            return "Nothing scanned yet. This checks localhost only.";
        }
        Map<State, Integer> counts = new EnumMap<>(State.class);
        for (Result result : results) { counts.merge(result.state(), 1, Integer::sum); }
        StringBuilder sb = new StringBuilder(String.format(
                "%d ports probed at a %d ms timeout.", results.size(), timeoutMillis));
        counts.forEach((state, count) -> sb.append(String.format("%n  %-9s %d", state, count)));
        if (!open().isEmpty()) {
            sb.append("\n\n  Listening:");
            for (Result result : open()) {
                sb.append(String.format("%n    %5d  %s", result.port(), result.service()));
            }
        }
        sb.append("\n\n  Closed means actively refused - nothing is listening. Filtered means "
                + "nothing answered at all, which is a firewall dropping packets rather than "
                + "the machine refusing them.");
        return sb.toString();
    }

    private List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Result result : results) {
            if (result.state() == State.CLOSED && results.size() > 30) { continue; }
            out.add(Json.map("id", result.port(),
                    "label", result.port() + "  " + result.service(),
                    "meta", result.state() + " in " + result.millis() + " ms",
                    "done", result.state() == State.OPEN));
        }
        return out;
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Localhost only. Scanning somebody else's machine needs their permission.");
        while (true) {
            io.println();
            io.muted(summary().replace("\n", "\n  "));
            int choice = io.menu("Port Scanner", List.of(
                    "Scan the common ports", "Scan a range", "Probe one port", "Set the timeout"));
            if (choice < 0) { return; }
            try {
                switch (choice) {
                    case 0 -> {
                        scan("localhost", COMMON);
                        io.ok(open().size() + " listening.");
                    }
                    case 1 -> {
                        int from = io.askInt("from:", 1, 65535, 1);
                        int to = io.askInt("to:", 1, 65535, Math.min(from + 200, 65535));
                        scan("localhost", range(from, to));
                        io.ok(open().size() + " listening.");
                    }
                    case 2 -> {
                        Result result = probe("localhost", io.askInt("port:", 1, 65535, 80));
                        io.result(result.state() + " (" + result.service() + ")",
                                result.millis() + " ms");
                    }
                    default -> setTimeout(io.askInt("timeout ms:", 10, 5000, timeoutMillis));
                }
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "add" -> {
                    String what = Json.str(body, "label", "").trim();
                    if (what.isEmpty() || what.equalsIgnoreCase("common")) {
                        scan("localhost", COMMON);
                    } else if (what.contains("-")) {
                        String[] parts = what.split("-", 2);
                        scan("localhost", range(Integer.parseInt(parts[0].trim()),
                                Integer.parseInt(parts[1].trim())));
                    } else {
                        scan("localhost", List.of(Integer.parseInt(what)));
                    }
                    return Json.ok("items", snapshot(),
                            "message", open().size() + " listening",
                            "detail", summary());
                }
                case "remove", "toggle" -> {
                    return Json.ok("items", snapshot(), "detail", summary());
                }
                case "clear" -> {
                    clear();
                    return Json.ok("items", snapshot(), "message", "Cleared",
                            "detail", summary());
                }
                case "list" -> { return Json.ok("items", snapshot(), "detail", summary()); }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (NumberFormatException e) {
            return Json.error("Type a port, a range like 8000-8100, or \"common\".");
        } catch (RuntimeException e) {
            return Json.error(e.getMessage());
        }
    }
}
