package com.randomjava.test;

import com.randomjava.Catalog;
import com.randomjava.lib.Json;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;
import com.randomjava.lib.Shell;
import com.randomjava.lib.WebHub;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Checks the suite as a whole rather than any one project: that the catalogue
 * is internally consistent, and that all 185 projects construct, describe
 * themselves, serve a page and refuse nonsense politely.
 *
 * <p>This is the net that catches a scaffold edited into a broken state. A
 * project that throws on construction or blows up on an unknown action would
 * otherwise only be found by clicking on it.
 */
final class SuiteTests {

    private SuiteTests() {
    }

    static void run(Harness h) {
        catalogue(h);
        everyProject(h);
        pages(h);
        folders(h);
    }

    // ------------------------------------------------------------------

    private static void catalogue(Harness h) {
        h.group("Catalogue integrity", t -> {
            List<Meta> all = Catalog.all();
            t.check("the catalogue is not empty", !all.isEmpty());

            Set<String> slugs = new HashSet<>();
            Set<Integer> ids = new HashSet<>();
            boolean contiguous = true;
            for (int i = 0; i < all.size(); i++) {
                Meta meta = all.get(i);
                contiguous &= meta.id() == i + 1;
                t.check("id " + meta.id() + " has a slug", !meta.slug().isBlank());
                t.check(meta.slug() + " has a name", !meta.name().isBlank());
                t.check(meta.slug() + " has a description", meta.description().length() > 15);
                t.check(meta.slug() + " has a category", !meta.category().isBlank());
                t.check(meta.slug() + " has a kind", meta.kind() != null);
                t.check(meta.slug() + " has a difficulty", meta.difficulty() != null);
                t.check(meta.slug() + " has a unique slug", slugs.add(meta.slug()));
                t.check("id " + meta.id() + " is unique", ids.add(meta.id()));
                t.equal(meta.slug() + " folder name is derived correctly",
                        String.format("%03d-%s", meta.id(), meta.slug()), meta.folder());
            }
            t.check("ids run 1..n with no gaps", contiguous);

            // The catalogue holds Meta literals so that listing the projects
            // does not load all of them. That buys a fast start and costs the
            // guarantee that the literal still matches what the project says
            // about itself, so the two are compared here - the one place where
            // loading every class is the point rather than a waste.
            for (Meta meta : all) {
                Project project = Catalog.factories().get(meta.slug()).get();
                t.equal(meta.slug() + ": catalogue metadata matches the project's own",
                        project.meta(), meta);
            }

            Map<String, Supplier<Project>> factories = Catalog.factories();
            t.equal("there is a factory for every project", all.size(), factories.size());
            for (Meta meta : all) {
                t.check(meta.slug() + " has a factory", factories.containsKey(meta.slug()));
            }

            t.check("slugs are url safe",
                    all.stream().allMatch(m -> m.slug().matches("[a-z0-9-]+")));
            t.check("descriptions end in a full stop",
                    all.stream().allMatch(m -> m.description().endsWith(".")));
        });
    }

    private static void everyProject(Harness h) {
        h.group("Every project behaves", t -> {
            for (Meta meta : Catalog.all()) {
                Project project;
                try {
                    project = Catalog.factories().get(meta.slug()).get();
                } catch (Throwable error) {
                    t.check(meta.slug() + " constructs without throwing ("
                            + error.getClass().getSimpleName() + ")", false);
                    continue;
                }

                t.check(meta.slug() + " reports metadata", project.meta() != null);
                t.equal(meta.slug() + " reports the same id", meta.id(), project.meta().id());
                t.equal(meta.slug() + " reports the same slug", meta.slug(), project.meta().slug());
                t.equal(meta.slug() + " reports the same name", meta.name(), project.meta().name());
                t.equal(meta.slug() + " reports the same done flag",
                        meta.done(), project.meta().done());

                String fragment = project.uiFragment();
                t.check(meta.slug() + " serves a page fragment",
                        fragment != null && fragment.length() > 50);
                t.absent(meta.slug() + " fragment has no missing-file message",
                        fragment, "No <code>ui.html</code> found");
                t.absent(meta.slug() + " fragment did not fail to load",
                        fragment, "Could not load ui.html");

                // An unknown action must come back as data, not an exception.
                try {
                    Object response = project.api("definitely-not-an-action", Json.map());
                    t.check(meta.slug() + " answers an unknown action with a value",
                            response != null);
                    if (response instanceof Map<?, ?> map) {
                        t.check(meta.slug() + " marks an unknown action as not ok",
                                Boolean.FALSE.equals(map.get("ok")));
                    }
                } catch (Throwable error) {
                    t.check(meta.slug() + " does not throw on an unknown action ("
                            + error.getClass().getSimpleName() + ")", false);
                }
            }
        });
    }

    private static void pages(Harness h) {
        h.group("Rendered pages", t -> {
            String hub = Shell.hubPage(Catalog.all());
            t.check("the hub renders", hub.length() > 1000);
            t.equal("the hub shows every project", Catalog.all().size(),
                    countOf(hub, "class=\"tile\""));
            t.contains("the hub loads the stylesheet", hub, "/assets/shell.css");
            t.contains("the hub loads the helper", hub, "/assets/shell.js");
            t.check("the stylesheet is served", Shell.css().length() > 500);
            t.check("the helper is served", Shell.js().length() > 500);

            for (Meta meta : Catalog.all()) {
                Project project = Catalog.factories().get(meta.slug()).get();
                String page = Shell.projectPage(meta, project.uiFragment());
                t.check(meta.slug() + " page renders", page.contains("<!doctype html>"));
                t.check(meta.slug() + " page names the project",
                        page.contains(Shell.escape(meta.name())));
                t.check(meta.slug() + " page shows its difficulty",
                        page.contains(meta.difficulty().label()));
                t.equal(meta.slug() + " page marks scaffolds honestly",
                        !meta.done(), page.contains("working scaffold"));
            }

            t.check("escaping neutralises markup",
                    Shell.escape("<script>").equals("&lt;script&gt;"));
            t.check("escaping handles quotes", Shell.escape("\"").equals("&quot;"));
            t.check("escaping handles ampersands first", Shell.escape("&lt;").equals("&amp;lt;"));
            t.check("a factory map can be built", WebHub.newFactoryMap() != null);
        });
    }

    private static void folders(Harness h) {
        h.group("Files on disk", t -> {
            Path projects = Path.of("projects");
            if (!Files.isDirectory(projects)) {
                t.check("skipped: run from the repository root to check folders", true);
                return;
            }
            for (Meta meta : Catalog.all()) {
                Path folder = projects.resolve(meta.folder());
                t.check(meta.folder() + " exists", Files.isDirectory(folder));
                t.check(meta.folder() + " has a README",
                        Files.isRegularFile(folder.resolve("README.md")));
                try (var stream = Files.list(folder)) {
                    boolean hasJava = stream.anyMatch(p -> p.toString().endsWith(".java"));
                    t.check(meta.folder() + " has a java file", hasJava);
                } catch (Exception e) {
                    t.check(meta.folder() + " is readable", false);
                }
            }
        });
    }

    private static int countOf(String haystack, String needle) {
        int count = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            count++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return count;
    }
}
