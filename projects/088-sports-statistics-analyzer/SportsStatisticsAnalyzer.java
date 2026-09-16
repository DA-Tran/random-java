package com.randomjava.projects.sportsstatisticsanalyzer;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Sports Statistics - per-game rates and shooting efficiency.
 *
 * <p>Totals flatter whoever played most. A player with 400 points in 40 games
 * is not better than one with 300 in 20, so everything here is per game.
 *
 * <p>Shooting is measured by true shooting percentage rather than raw field
 * goal percentage, because raw FG% treats a three-pointer as worth the same as
 * a layup and ignores free throws entirely. TS% weights by points actually
 * scored per shooting possession, which is the number that reflects value.
 */
public final class SportsStatisticsAnalyzer implements Project {

    public static final Meta META = new Meta(88, "sports-statistics-analyzer", "Sports Statistics Analyzer", "Data Science", Kind.TOOL,
            Difficulty.INTERMEDIATE, "Compute per-game and efficiency stats and rank players.",
            "", true);

    public record Player(String name, int games, int points, int rebounds, int assists,
                         int fieldGoals, int fieldGoalAttempts, int freeThrowAttempts) {

        public double perGame(int total) { return games == 0 ? 0 : (double) total / games; }

        /** True shooting: points per shooting possession, free throws included. */
        public double trueShooting() {
            double possessions = fieldGoalAttempts + 0.44 * freeThrowAttempts;
            return possessions == 0 ? 0 : points / (2 * possessions);
        }

        /** A rough all-round contribution per game. */
        public double impact() {
            return perGame(points) + 1.2 * perGame(rebounds) + 1.5 * perGame(assists);
        }
    }

    @Override public Meta meta() { return META; }

    /** Parses "name games points rebounds assists fg fga fta" per line. */
    public List<Player> parse(String text) {
        List<Player> out = new ArrayList<>();
        for (String line : text.split("[\\n;]+")) {
            String row = line.trim();
            if (row.isEmpty()) { continue; }
            String[] bits = row.split("[\\s,]+");
            if (bits.length < 5) {
                throw new IllegalArgumentException("Each line needs at least: "
                        + "name games points rebounds assists   (optionally fg fga fta)");
            }
            try {
                int[] n = new int[7];
                for (int i = 1; i < bits.length && i <= 7; i++) { n[i - 1] = Integer.parseInt(bits[i]); }
                out.add(new Player(bits[0], n[0], n[1], n[2], n[3], n[4], n[5],
                        bits.length >= 8 ? Integer.parseInt(bits[7]) : 0));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'" + row + "' has a value that is not a number.");
            }
        }
        if (out.isEmpty()) { throw new IllegalArgumentException("No players given."); }
        return out;
    }

    public List<Player> ranked(List<Player> players) {
        List<Player> copy = new ArrayList<>(players);
        copy.sort((a, b) -> Double.compare(b.impact(), a.impact()));
        return copy;
    }

    private String detail(List<Player> players) {
        StringBuilder sb = new StringBuilder("Per game, not totals, so playing time does not flatter:");
        for (Player p : ranked(players)) {
            sb.append(String.format("%n  %-12s %5.1f pts  %4.1f reb  %4.1f ast   impact %5.1f",
                    p.name(), p.perGame(p.points()), p.perGame(p.rebounds()),
                    p.perGame(p.assists()), p.impact()));
            if (p.fieldGoalAttempts() > 0) {
                sb.append(String.format("   TS%% %.1f", 100 * p.trueShooting()));
            }
        }
        Player best = ranked(players).get(0);
        sb.append("\n\nTop contribution: ").append(best.name())
          .append(String.format(" at %.1f per game.", best.impact()));
        Player volume = players.stream().max(Comparator.comparingInt(Player::points)).orElse(best);
        if (!volume.name().equals(best.name())) {
            sb.append("\n").append(volume.name()).append(" has the most total points, but ")
              .append(best.name()).append(" contributes more per game played.");
        }
        return sb.toString();
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("One player per line: name games points rebounds assists [fg fga fta]");
        io.muted("A line with only . ends the input.");
        StringBuilder buffer = new StringBuilder();
        while (true) {
            String line = io.ask(">");
            if (line.equals(".")) { break; }
            if (line.isEmpty() && buffer.length() == 0) { return; }
            buffer.append(line).append('\n');
        }
        try {
            List<Player> players = parse(buffer.toString());
            io.result(ranked(players).get(0).name(), "highest contribution per game");
            io.println("  " + detail(players).replace("\n", "\n  "));
        } catch (RuntimeException e) { io.error(e.getMessage()); }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) { return Json.error("Unknown action: " + action); }
        try {
            List<Player> players = parse(Json.str(body, "input", ""));
            List<Double> bars = new ArrayList<>();
            for (Player p : ranked(players)) { bars.add(p.impact()); }
            return Json.ok("result", ranked(players).get(0).name() + " leads on contribution per game",
                    "detail", detail(players), "bars", bars);
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
