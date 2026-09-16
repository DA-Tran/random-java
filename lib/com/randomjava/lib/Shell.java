package com.randomjava.lib;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The page chrome served around every project's {@code ui.html} fragment:
 * one stylesheet, one small javascript helper, and the hub index.
 *
 * <p>Keeping this in one place is what makes each project's own UI file three
 * or four lines long instead of a whole HTML document.
 */
public final class Shell {

    private Shell() {
    }

    // ------------------------------------------------------------------
    // Stylesheet
    // ------------------------------------------------------------------

    public static String css() {
        return """
                :root {
                  --bg: #0d0f12;
                  --panel: #14181d;
                  --panel-2: #1a1f26;
                  --line: #242b34;
                  --text: #e6e9ee;
                  --muted: #8b95a3;
                  --accent: #4fd1c5;
                  --accent-dim: #1f4f4a;
                  --warn: #f6ad55;
                  --bad: #fc8181;
                  --radius: 10px;
                }
                * { box-sizing: border-box; }
                html, body { margin: 0; padding: 0; }
                body {
                  background: var(--bg);
                  color: var(--text);
                  font: 15px/1.6 ui-sans-serif, system-ui, -apple-system, Segoe UI, Roboto, Helvetica, Arial, sans-serif;
                  -webkit-font-smoothing: antialiased;
                }
                code, pre, .mono { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
                a { color: var(--accent); text-decoration: none; }
                a:hover { text-decoration: underline; }

                header.top {
                  border-bottom: 1px solid var(--line);
                  padding: 18px 28px;
                  display: flex;
                  align-items: baseline;
                  gap: 16px;
                  flex-wrap: wrap;
                  position: sticky;
                  top: 0;
                  background: rgba(13, 15, 18, 0.92);
                  backdrop-filter: blur(8px);
                  z-index: 10;
                }
                header.top h1 { font-size: 17px; margin: 0; font-weight: 600; letter-spacing: -0.01em; }
                header.top .sub { color: var(--muted); font-size: 13px; }
                header.top .spacer { flex: 1; }

                main { max-width: 1100px; margin: 0 auto; padding: 28px; }
                main.wide { max-width: 1400px; }

                .lede { color: var(--muted); margin: 0 0 26px; max-width: 70ch; }

                .card {
                  background: var(--panel);
                  border: 1px solid var(--line);
                  border-radius: var(--radius);
                  padding: 20px 22px;
                  margin-bottom: 18px;
                }
                .card h2 { margin: 0 0 4px; font-size: 15px; font-weight: 600; }
                .card h2 + .hint { margin-top: 0; }
                .hint { color: var(--muted); font-size: 13px; margin: 0 0 16px; }

                .row { display: flex; gap: 14px; flex-wrap: wrap; align-items: flex-end; }
                .row > * { flex: 1 1 160px; }
                .row > .tight { flex: 0 0 auto; }

                label.field { display: block; }
                label.field > span {
                  display: block;
                  font-size: 12px;
                  text-transform: uppercase;
                  letter-spacing: 0.06em;
                  color: var(--muted);
                  margin-bottom: 6px;
                }
                input, select, textarea {
                  width: 100%;
                  background: var(--bg);
                  border: 1px solid var(--line);
                  color: var(--text);
                  border-radius: 8px;
                  padding: 10px 12px;
                  font: inherit;
                }
                textarea { min-height: 120px; resize: vertical; }
                input:focus, select:focus, textarea:focus {
                  outline: none;
                  border-color: var(--accent);
                  box-shadow: 0 0 0 3px var(--accent-dim);
                }

                button {
                  font: inherit;
                  font-weight: 550;
                  cursor: pointer;
                  border-radius: 8px;
                  padding: 10px 18px;
                  border: 1px solid var(--accent);
                  background: var(--accent);
                  color: #08201e;
                  transition: filter 120ms ease, transform 120ms ease;
                }
                button:hover { filter: brightness(1.08); }
                button:active { transform: translateY(1px); }
                button.ghost { background: transparent; color: var(--accent); }
                button.quiet { background: transparent; color: var(--muted); border-color: var(--line); }
                button.quiet:hover { color: var(--text); border-color: var(--muted); }
                .buttons { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 16px; }

                .out { min-height: 24px; }
                .out .headline {
                  font-size: 30px;
                  font-weight: 650;
                  letter-spacing: -0.02em;
                  color: var(--accent);
                  word-break: break-word;
                }
                .out .detail { color: var(--muted); margin-top: 6px; white-space: pre-wrap; }
                .out .bad { color: var(--bad); font-weight: 550; }

                ul.items { list-style: none; margin: 0; padding: 0; }
                ul.items li {
                  display: flex;
                  align-items: center;
                  gap: 12px;
                  padding: 11px 2px;
                  border-bottom: 1px solid var(--line);
                }
                ul.items li:last-child { border-bottom: none; }
                ul.items li .grow { flex: 1; }
                ul.items li .meta { color: var(--muted); font-size: 13px; }
                ul.items li.done .grow { text-decoration: line-through; color: var(--muted); }
                .empty { color: var(--muted); font-style: italic; }

                table.data { width: 100%; border-collapse: collapse; font-size: 14px; }
                table.data th, table.data td { text-align: left; padding: 9px 10px; border-bottom: 1px solid var(--line); }
                table.data th { color: var(--muted); font-size: 12px; text-transform: uppercase; letter-spacing: 0.06em; font-weight: 600; }

                .board { display: inline-grid; gap: 3px; margin-top: 6px; }
                .board .cell {
                  width: 30px;
                  height: 30px;
                  display: grid;
                  place-items: center;
                  background: var(--panel-2);
                  border-radius: 5px;
                  font-family: ui-monospace, Consolas, monospace;
                  font-size: 14px;
                }
                .board .cell.on { background: var(--accent); color: #08201e; font-weight: 600; }
                .board .cell.wall { background: #2c333d; color: var(--muted); }
                .board .cell.mark { background: var(--warn); color: #241504; font-weight: 600; }

                .bars { display: flex; align-items: flex-end; gap: 2px; height: 220px; margin-top: 6px; }
                .bars .bar { flex: 1; background: var(--accent); border-radius: 2px 2px 0 0; min-width: 2px; }
                .bars .bar.mark { background: var(--warn); }

                .badge {
                  display: inline-block;
                  font-size: 11px;
                  letter-spacing: 0.05em;
                  text-transform: uppercase;
                  padding: 3px 8px;
                  border-radius: 999px;
                  border: 1px solid var(--line);
                  color: var(--muted);
                }
                .badge.done { color: var(--accent); border-color: var(--accent-dim); }
                .badge.diff-beginner { color: #9ae6b4; border-color: #22543d; }
                .badge.diff-intermediate { color: var(--warn); border-color: #5a3d1a; }
                .badge.diff-advanced { color: var(--bad); border-color: #5a2626; }

                .stack-note {
                  border-left: 2px solid var(--line);
                  padding: 12px 16px;
                  border-radius: 0 8px 8px 0;
                  background: var(--panel);
                  color: var(--muted);
                  font-size: 13px;
                  margin-bottom: 18px;
                  max-width: 80ch;
                }
                .stack-note strong { color: var(--text); font-weight: 600; }

                .cat { margin: 36px 0 14px; font-size: 13px; text-transform: uppercase; letter-spacing: 0.08em; color: var(--muted); }
                .cat:first-of-type { margin-top: 8px; }
                .cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); gap: 12px; }
                a.tile {
                  display: block;
                  background: var(--panel);
                  border: 1px solid var(--line);
                  border-radius: var(--radius);
                  padding: 15px 16px;
                  color: inherit;
                  transition: border-color 120ms ease, transform 120ms ease;
                }
                a.tile:hover { border-color: var(--accent); transform: translateY(-2px); text-decoration: none; }
                a.tile .num { color: var(--muted); font-size: 12px; font-family: ui-monospace, Consolas, monospace; }
                a.tile .name { font-weight: 600; margin: 3px 0 5px; }
                a.tile .desc { color: var(--muted); font-size: 13px; line-height: 1.45; }
                a.tile .foot { margin-top: 11px; display: flex; gap: 7px; align-items: center; }

                .search { max-width: 380px; }
                .toast {
                  position: fixed;
                  bottom: 22px;
                  left: 50%;
                  transform: translateX(-50%);
                  background: var(--panel-2);
                  border: 1px solid var(--line);
                  padding: 11px 18px;
                  border-radius: 999px;
                  opacity: 0;
                  transition: opacity 200ms ease;
                  pointer-events: none;
                }
                .toast.show { opacity: 1; }
                .stub-note {
                  border-left: 2px solid var(--warn);
                  background: rgba(246, 173, 85, 0.07);
                  padding: 12px 16px;
                  border-radius: 0 8px 8px 0;
                  color: var(--muted);
                  font-size: 13px;
                  margin-bottom: 18px;
                }
                """;
    }

    // ------------------------------------------------------------------
    // Client helper
    // ------------------------------------------------------------------

    public static String js() {
        return """
                const RJ = (function () {
                  function $(sel, root) { return (root || document).querySelector(sel); }
                  function $$(sel, root) { return Array.from((root || document).querySelectorAll(sel)); }

                  function esc(value) {
                    const d = document.createElement('div');
                    d.textContent = value === null || value === undefined ? '' : String(value);
                    return d.innerHTML;
                  }

                  function fields() {
                    const data = {};
                    $$('[data-field]').forEach(function (el) {
                      if (el.type === 'checkbox') { data[el.dataset.field] = el.checked; }
                      else { data[el.dataset.field] = el.value; }
                    });
                    return data;
                  }

                  async function api(action, body) {
                    const payload = Object.assign({}, fields(), body || {}, { action: action });
                    const res = await fetch('api', {
                      method: 'POST',
                      headers: { 'Content-Type': 'application/json' },
                      body: JSON.stringify(payload)
                    });
                    if (!res.ok) { throw new Error('server returned ' + res.status); }
                    return await res.json();
                  }

                  function toast(message) {
                    let el = $('.toast');
                    if (!el) { el = document.createElement('div'); el.className = 'toast'; document.body.appendChild(el); }
                    el.textContent = message;
                    el.classList.add('show');
                    clearTimeout(el._t);
                    el._t = setTimeout(function () { el.classList.remove('show'); }, 2200);
                  }

                  function board(cells, target) {
                    const host = target || $('#board');
                    if (!host || !cells || !cells.length) { return; }
                    const columns = cells[0].length;
                    host.innerHTML = '';
                    const wrap = document.createElement('div');
                    wrap.className = 'board';
                    wrap.style.gridTemplateColumns = 'repeat(' + columns + ', 30px)';
                    cells.forEach(function (row) {
                      row.forEach(function (value) {
                        const cell = document.createElement('div');
                        const text = value === null || value === undefined ? '' : String(value);
                        cell.className = 'cell';
                        if (text === '#') { cell.classList.add('wall'); }
                        else if (text === '*') { cell.classList.add('mark'); }
                        else if (text !== '' && text !== '.' && text !== ' ') { cell.classList.add('on'); }
                        cell.textContent = text === '.' ? '' : text;
                        wrap.appendChild(cell);
                      });
                    });
                    host.appendChild(wrap);
                  }

                  function bars(values, highlight, target) {
                    const host = target || $('#board');
                    if (!host || !values) { return; }
                    const max = Math.max.apply(null, values.concat([1]));
                    const marks = highlight || [];
                    host.innerHTML = '';
                    const wrap = document.createElement('div');
                    wrap.className = 'bars';
                    values.forEach(function (value, index) {
                      const bar = document.createElement('div');
                      bar.className = 'bar' + (marks.indexOf(index) >= 0 ? ' mark' : '');
                      bar.style.height = Math.max(2, Math.round((value / max) * 100)) + '%';
                      wrap.appendChild(bar);
                    });
                    host.appendChild(wrap);
                  }

                  function items(list, target) {
                    const host = target || $('#items');
                    if (!host) { return; }
                    if (!list || !list.length) { host.innerHTML = '<p class="empty">Nothing here yet.</p>'; return; }
                    const ul = document.createElement('ul');
                    ul.className = 'items';
                    list.forEach(function (item) {
                      const li = document.createElement('li');
                      if (item.done) { li.className = 'done'; }
                      let html = '<span class="grow">' + esc(item.label || item.text || item.name) + '</span>';
                      if (item.meta) { html += '<span class="meta">' + esc(item.meta) + '</span>'; }
                      if (item.id !== undefined) {
                        html += '<button class="quiet" data-row-toggle="' + esc(item.id) + '">toggle</button>';
                        html += '<button class="quiet" data-row-remove="' + esc(item.id) + '">remove</button>';
                      }
                      li.innerHTML = html;
                      ul.appendChild(li);
                    });
                    host.innerHTML = '';
                    host.appendChild(ul);
                  }

                  function render(data) {
                    if (!data) { return; }
                    if (data.ok === false) {
                      const out = $('#out');
                      if (out) { out.innerHTML = '<div class="bad">' + esc(data.error || 'Something went wrong.') + '</div>'; }
                      else { toast(data.error || 'Something went wrong.'); }
                      return;
                    }
                    const out = $('#out');
                    if (out && (data.result !== undefined || data.detail !== undefined)) {
                      let html = '';
                      if (data.result !== undefined) { html += '<div class="headline">' + esc(data.result) + '</div>'; }
                      if (data.detail !== undefined) { html += '<div class="detail">' + esc(data.detail) + '</div>'; }
                      out.innerHTML = html;
                    }
                    if (data.html !== undefined && out) { out.innerHTML = data.html; }
                    if (data.board !== undefined) { board(data.board); }
                    if (data.bars !== undefined) { bars(data.bars, data.highlight); }
                    if (data.items !== undefined) { items(data.items); }
                    if (data.message) { toast(data.message); }
                  }

                  async function send(action, body) {
                    try {
                      const data = await api(action, body);
                      render(data);
                      return data;
                    } catch (err) {
                      toast(String(err.message || err));
                      return null;
                    }
                  }

                  function wire() {
                    document.addEventListener('click', function (event) {
                      const actionEl = event.target.closest('[data-action]');
                      if (actionEl) { event.preventDefault(); send(actionEl.dataset.action); return; }
                      const toggleEl = event.target.closest('[data-row-toggle]');
                      if (toggleEl) { send('toggle', { id: toggleEl.dataset.rowToggle }); return; }
                      const removeEl = event.target.closest('[data-row-remove]');
                      if (removeEl) { send('remove', { id: removeEl.dataset.rowRemove }); return; }
                    });
                    document.addEventListener('keydown', function (event) {
                      if (event.key !== 'Enter') { return; }
                      if (event.target.matches('input[data-field]')) {
                        const primary = $('[data-action][data-primary]') || $('[data-action]');
                        if (primary) { event.preventDefault(); send(primary.dataset.action); }
                      }
                    });
                    const boot = $('[data-boot]');
                    if (boot) { send(boot.dataset.boot); }
                  }

                  document.addEventListener('DOMContentLoaded', wire);
                  return { $: $, $$: $$, api: api, send: send, render: render, esc: esc,
                           toast: toast, board: board, bars: bars, items: items, fields: fields };
                })();
                """;
    }

    // ------------------------------------------------------------------
    // Pages
    // ------------------------------------------------------------------

    /** Wraps a body fragment in the standard page chrome. */
    public static String page(String title, String headerRight, String body, boolean wide) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>__TITLE__</title>
                <link rel="stylesheet" href="/assets/shell.css">
                </head>
                <body>
                <header class="top">
                  <h1><a href="/">random-java</a></h1>
                  <span class="sub">Java project suite</span>
                  <span class="spacer"></span>
                  __HEADER_RIGHT__
                </header>
                __BODY__
                <script src="/assets/shell.js"></script>
                </body>
                </html>
                """
                .replace("__TITLE__", escape(title))
                .replace("__HEADER_RIGHT__", headerRight == null ? "" : headerRight)
                .replace("__BODY__", body.replace("<main>", wide ? "<main class=\"wide\">" : "<main>"));
    }

    /** The project page: title, description, then the project's own fragment. */
    public static String projectPage(Meta meta, String fragment) {
        StringBuilder body = new StringBuilder();
        body.append("<main>");
        body.append("<p class=\"lede\"><span class=\"badge\">")
                .append(escape(meta.category()))
                .append("</span> <span class=\"badge\">")
                .append(escape(meta.kind().label()))
                .append("</span> <span class=\"badge diff-")
                .append(meta.difficulty().name().toLowerCase(java.util.Locale.ROOT))
                .append("\">")
                .append(escape(meta.difficulty().label()))
                .append("</span>");
        if (meta.done()) {
            body.append(" <span class=\"badge done\">implemented</span>");
        }
        body.append("</p>");
        body.append("<h2 style=\"margin:0 0 6px;font-size:26px;letter-spacing:-0.02em\">")
                .append(escape(meta.name()))
                .append("</h2>");
        body.append("<p class=\"lede\">").append(escape(meta.description())).append("</p>");
        if (meta.hasStack()) {
            body.append("<div class=\"stack-note\"><strong>Recommended stack for a real build:</strong> ")
                    .append(escape(meta.stack()))
                    .append("<br>This suite is dependency-free by design, so the version here uses ")
                    .append("in-memory storage and the shared web hub instead.</div>");
        }
        if (!meta.done()) {
            body.append("<div class=\"stub-note\">This project is a working scaffold. ")
                    .append("Both front ends run and talk to each other, but the core logic is still a stub. ")
                    .append("Implement it in <code>")
                    .append(escape(meta.folder()))
                    .append("</code> and the terminal and browser both pick it up.</div>");
        }
        body.append(fragment);
        body.append("</main>");
        String back = "<a href=\"/\">&larr; all projects</a>";
        return page(meta.name() + " - random-java", back, body.toString(), false);
    }

    /** The hub index listing every project, grouped by category, with a filter. */
    public static String hubPage(List<Meta> catalog) {
        Map<String, List<Meta>> byCategory = new LinkedHashMap<>();
        for (Meta meta : catalog) {
            byCategory.computeIfAbsent(meta.category(), key -> new java.util.ArrayList<>()).add(meta);
        }
        long done = catalog.stream().filter(Meta::done).count();

        StringBuilder body = new StringBuilder();
        body.append("<main>");
        body.append("<p class=\"lede\">Every project runs two ways: in your terminal, and here in the browser. ")
                .append("Both front ends call the same Java. ")
                .append(done).append(" of ").append(catalog.size())
                .append(" are fully implemented; the rest are runnable scaffolds.</p>");
        body.append("<div class=\"card\"><label class=\"field search\"><span>Filter</span>")
                .append("<input id=\"filter\" placeholder=\"Try: maze, beginner, advanced, spring boot, game\" ")
                .append("autocomplete=\"off\"></label>")
                .append("<p class=\"hint\" style=\"margin:12px 0 0\">Matches name, description, category, ")
                .append("shape, difficulty and recommended stack.</p></div>");

        for (Map.Entry<String, List<Meta>> entry : byCategory.entrySet()) {
            body.append("<div class=\"cat\">").append(escape(entry.getKey()))
                    .append(" &middot; ").append(entry.getValue().size()).append("</div>");
            body.append("<div class=\"cards\">");
            for (Meta meta : entry.getValue()) {
                String difficulty = meta.difficulty().name().toLowerCase(java.util.Locale.ROOT);
                String haystack = (meta.name() + " " + meta.description() + " " + meta.category()
                        + " " + meta.kind().label() + " " + difficulty + " " + meta.stack())
                        .toLowerCase(java.util.Locale.ROOT);
                body.append("<a class=\"tile\" href=\"/p/").append(meta.slug()).append("/\" data-search=\"")
                        .append(escape(haystack))
                        .append("\">")
                        .append("<div class=\"num\">").append(String.format("%03d", meta.id())).append("</div>")
                        .append("<div class=\"name\">").append(escape(meta.name())).append("</div>")
                        .append("<div class=\"desc\">").append(escape(meta.description())).append("</div>")
                        .append("<div class=\"foot\"><span class=\"badge")
                        .append(meta.done() ? " done" : "").append("\">")
                        .append(meta.done() ? "implemented" : "scaffold").append("</span>")
                        .append("<span class=\"badge\">").append(escape(meta.kind().label())).append("</span>")
                        .append("<span class=\"badge diff-").append(difficulty).append("\">")
                        .append(escape(meta.difficulty().label())).append("</span></div>")
                        .append("</a>");
            }
            body.append("</div>");
        }
        body.append("</main>");
        body.append("""
                <script>
                document.addEventListener('DOMContentLoaded', function () {
                  const box = document.getElementById('filter');
                  box.addEventListener('input', function () {
                    const term = box.value.trim().toLowerCase();
                    document.querySelectorAll('a.tile').forEach(function (tile) {
                      tile.style.display = !term || tile.dataset.search.indexOf(term) >= 0 ? '' : 'none';
                    });
                    document.querySelectorAll('.cat').forEach(function (heading) {
                      const group = heading.nextElementSibling;
                      const any = Array.from(group.querySelectorAll('a.tile')).some(function (t) {
                        return t.style.display !== 'none';
                      });
                      heading.style.display = any ? '' : 'none';
                      group.style.display = any ? '' : 'none';
                    });
                  });
                  box.focus();
                });
                </script>
                """);
        return page("random-java - 128 projects", null, body.toString(), true);
    }

    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
