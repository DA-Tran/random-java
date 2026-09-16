package com.randomjava.cube;

import com.randomjava.lib.Project;
import com.randomjava.lib.Shell;

import java.util.List;

/**
 * Builds the browser page every twisty puzzle shares: an unfolded net you can
 * watch change, turn buttons, a solver that steps through its own explanation,
 * a single-hint mode, and a box for typing in the cube on your desk.
 */
final class CubeUi {

    private CubeUi() {
    }

    static String page(Project project, int size, boolean hasSolver,
            List<String> methods, String note) {
        StringBuilder html = new StringBuilder();

        if (!note.isBlank()) {
            html.append("<div class=\"stack-note\">").append(Shell.escape(note)).append("</div>");
        }

        html.append("""
                <div class="card">
                  <h2>The puzzle</h2>
                  <p class="hint" id="status">Loading.</p>
                  <div id="net" class="net"></div>
                  <div class="buttons">
                    <button data-cube="scramble" data-primary>Scramble</button>
                    <button class="ghost" data-cube="reset">Reset</button>
                """);

        if (hasSolver) {
            html.append("""
                        <button class="ghost" data-cube="solve">Solve and explain</button>
                        <button class="quiet" data-cube="hint">One hint only</button>
                    """);
        }
        html.append("  </div>\n");

        if (hasSolver && methods.size() > 1) {
            html.append("  <div class=\"row\" style=\"margin-top:16px\">")
                    .append("<label class=\"field\"><span>Method</span><select id=\"method\">");
            for (String method : methods) {
                html.append("<option value=\"").append(Shell.escape(method)).append("\">")
                        .append(Shell.escape(method)).append("</option>");
            }
            html.append("</select></label></div>\n");
        }
        html.append("</div>\n");

        html.append("""
                <div class="card">
                  <h2>Turn it yourself</h2>
                  <p class="hint">
                    Standard notation. A letter turns that face clockwise, an apostrophe turns it
                    back, a 2 turns it twice. Lowercase or a w turns two layers at once, and
                    x y z turn the whole puzzle.
                  </p>
                  <div class="row">
                    <label class="field">
                      <span>Turns</span>
                      <input id="moves" placeholder="R U R' U'" autocomplete="off" spellcheck="false">
                    </label>
                    <div class="tight"><button class="ghost" data-cube="move">Apply</button></div>
                  </div>
                  <div class="buttons" id="quickturns"></div>
                </div>

                <div id="solution"></div>

                <div class="card">
                  <h2>Type in your own puzzle</h2>
                  <p class="hint">
                    Read each face left to right and top to bottom, in the order
                    U R F D L B, writing the letter of the face each sticker belongs to.
                    Impossible cubes are rejected with the reason, so a solver is never
                    sent looking for something that is not there.
                  </p>
                  <label class="field">
                    <span>Stickers</span>
                    <textarea id="facelets" spellcheck="false" style="min-height:90px"></textarea>
                  </label>
                  <div class="buttons">
                    <button class="ghost" data-cube="load">Load this cube</button>
                    <button class="quiet" id="copycurrent">Copy the current one in</button>
                  </div>
                </div>
                """);

        html.append("<style>").append(css(size)).append("</style>");
        html.append("<script>").append(script()).append("</script>");
        return html.toString();
    }

    private static String css(int size) {
        int cell = size <= 3 ? 26 : size <= 5 ? 18 : size <= 9 ? 13 : size <= 20 ? 7 : size <= 40 ? 4 : 2;
        return """
                .net { display: grid; grid-template-columns: repeat(4, auto); gap: 6px;
                       justify-content: start; margin: 4px 0 20px; }
                .net .face { display: grid; gap: 2px; }
                .net .face .sticker { width: __CELL__px; height: __CELL__px; border-radius: 3px; }
                .net .slot { visibility: hidden; }
                .sticker.cU { background: #f2f4f7; }
                .sticker.cR { background: #e05252; }
                .sticker.cF { background: #4caf6d; }
                .sticker.cD { background: #e8c547; }
                .sticker.cL { background: #e08a3c; }
                .sticker.cB { background: #4a80d8; }
                .steps { counter-reset: step; }
                .step { border-left: 2px solid var(--line); padding: 12px 0 12px 16px; cursor: pointer; }
                .step:hover { border-left-color: var(--accent); }
                .step.active { border-left-color: var(--accent); }
                .step .name { font-weight: 600; }
                .step .why { color: var(--muted); font-size: 13px; margin: 4px 0 8px; }
                .step .seq { font-family: ui-monospace, Consolas, monospace; font-size: 13px;
                             color: var(--accent); word-break: break-word; }
                .step .alg { color: var(--muted); font-size: 12px; margin-top: 4px; }
                #quickturns button { min-width: 44px; font-family: ui-monospace, Consolas, monospace; }
                """.replace("__CELL__", String.valueOf(cell));
    }

    private static String script() {
        return """
                (function () {
                  const FACES = ['U', 'R', 'F', 'D', 'L', 'B'];
                  let current = null;

                  function faceDiv(cells, n) {
                    const face = document.createElement('div');
                    face.className = 'face';
                    face.style.gridTemplateColumns = 'repeat(' + n + ', auto)';
                    cells.forEach(function (colour) {
                      const sticker = document.createElement('div');
                      sticker.className = 'sticker c' + colour;
                      face.appendChild(sticker);
                    });
                    return face;
                  }

                  function slot() {
                    const empty = document.createElement('div');
                    empty.className = 'slot';
                    return empty;
                  }

                  function drawFaces(faces, n) {
                    const net = document.getElementById('net');
                    net.innerHTML = '';
                    const order = [null, 0, null, null, 4, 2, 1, 5, null, 3, null, null];
                    order.forEach(function (index) {
                      net.appendChild(index === null ? slot() : faceDiv(faces[index], n));
                    });
                  }

                  function drawFacelets(facelets, n) {
                    const per = n * n;
                    const faces = [];
                    for (let i = 0; i < 6; i++) {
                      faces.push(facelets.substr(i * per, per).split(''));
                    }
                    drawFaces(faces, n);
                  }

                  function drawSolution(data) {
                    const host = document.getElementById('solution');
                    if (!data.solution) { host.innerHTML = ''; return; }
                    const s = data.solution;
                    let html = '<div class="card"><h2>' + RJ.esc(s.method) + '</h2>';
                    html += '<p class="hint">' + s.count + ' moves in ' + s.steps.length +
                            ' stages. Click a stage to see the puzzle at that point.</p>';
                    html += '<div class="steps">';
                    s.steps.forEach(function (step, i) {
                      html += '<div class="step" data-step="' + i + '" data-facelets="' +
                              step.facelets + '">';
                      html += '<div class="name">' + RJ.esc(step.stage) + '  (' + step.count +
                              ' moves)</div>';
                      html += '<div class="why">' + RJ.esc(step.explanation) + '</div>';
                      html += '<div class="seq">' + RJ.esc(step.moves) + '</div>';
                      if (step.algorithm) {
                        html += '<div class="alg">Algorithm: ' + RJ.esc(step.algorithm) + '</div>';
                      }
                      html += '</div>';
                    });
                    html += '</div>';
                    html += '<div class="buttons"><button class="quiet" id="playall">' +
                            'Play the whole solution on the net</button></div>';
                    html += '</div>';
                    host.innerHTML = html;
                  }

                  function drawHint(hint) {
                    const host = document.getElementById('solution');
                    host.innerHTML = '<div class="card"><h2>Hint</h2>' +
                      '<div class="step active"><div class="name">' + RJ.esc(hint.stage) +
                      '</div><div class="why">' + RJ.esc(hint.explanation) +
                      '</div><div class="seq">' + RJ.esc(hint.moves) + '</div>' +
                      (hint.algorithm ? '<div class="alg">Algorithm: ' +
                        RJ.esc(hint.algorithm) + '</div>' : '') + '</div>' +
                      '<div class="buttons"><button class="ghost" id="playhint" data-moves="' +
                      RJ.esc(hint.moves) + '">Play just this step</button></div></div>';
                  }

                  function render(data) {
                    if (!data || data.ok === false) {
                      if (data && data.error) { RJ.toast(data.error); }
                      return;
                    }
                    current = data;
                    drawFaces(data.faces, data.size);
                    const status = document.getElementById('status');
                    let text = data.solved ? 'Solved.' :
                      data.correct + ' of ' + data.total + ' stickers are on the right face.';
                    if (data.scramble) { text += '  Scramble: ' + data.scramble; }
                    if (!data.hasSolver) {
                      text += '  This puzzle turns and scrambles, but its solver is not written yet.';
                    }
                    status.textContent = text;
                    if (data.hint) { drawHint(data.hint); } else { drawSolution(data); }
                    if (data.message) { RJ.toast(data.message); }
                  }

                  async function call(action, extra) {
                    const body = Object.assign({ action: action }, extra || {});
                    const method = document.getElementById('method');
                    if (method) { body.method = method.value; }
                    const res = await fetch('api', {
                      method: 'POST',
                      headers: { 'Content-Type': 'application/json' },
                      body: JSON.stringify(body)
                    });
                    render(await res.json());
                  }

                  function quickTurns(n) {
                    const host = document.getElementById('quickturns');
                    const faces = n === 1 ? [] : ['U', 'R', 'F', 'D', 'L', 'B'];
                    faces.forEach(function (face) {
                      [face, face + "'", face + '2'].forEach(function (turn) {
                        const button = document.createElement('button');
                        button.className = 'quiet';
                        button.textContent = turn;
                        button.addEventListener('click', function () { call('move', { moves: turn }); });
                        host.appendChild(button);
                      });
                    });
                  }

                  document.addEventListener('click', function (event) {
                    const action = event.target.closest('[data-cube]');
                    if (action) {
                      const name = action.dataset.cube;
                      if (name === 'move') {
                        call('move', { moves: document.getElementById('moves').value });
                      } else if (name === 'load') {
                        call('load', { facelets: document.getElementById('facelets').value });
                      } else {
                        call(name, {});
                      }
                      return;
                    }
                    const step = event.target.closest('[data-step]');
                    if (step) {
                      document.querySelectorAll('.step').forEach(function (el) {
                        el.classList.remove('active');
                      });
                      step.classList.add('active');
                      drawFacelets(step.dataset.facelets, current.size);
                      return;
                    }
                    if (event.target.id === 'playhint') {
                      call('apply', { moves: event.target.dataset.moves });
                      return;
                    }
                    if (event.target.id === 'playall' && current && current.solution) {
                      call('apply', { moves: current.solution.moves });
                      return;
                    }
                    if (event.target.id === 'copycurrent' && current) {
                      document.getElementById('facelets').value = current.facelets;
                    }
                  });

                  document.getElementById('moves').addEventListener('keydown', function (event) {
                    if (event.key === 'Enter') { call('move', { moves: this.value }); }
                  });

                  document.addEventListener('DOMContentLoaded', function () {
                    call('state', {}).then(function () {
                      quickTurns(current ? current.size : 3);
                    });
                  });
                })();
                """;
    }
}
