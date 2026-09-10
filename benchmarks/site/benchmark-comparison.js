'use strict';

// The history action encodes JMH parameters in each benchmark's display name.
// Compare methods within one run, class, parameter set, and measurement unit.
(() => {
  function parseBenchmark(bench) {
    const match = bench.name.match(/^(.*?)\s+\(\s*(\{.*\})\s*\)$/);
    const name = match ? match[1] : bench.name;
    const separator = name.lastIndexOf('.');
    if (separator < 0 || !Number.isFinite(bench.value)) return null;

    let params = {};
    if (match) {
      try {
        params = JSON.parse(match[2]);
      } catch {
        return null;
      }
    }

    return {
      className: name.slice(0, separator),
      mode: name.slice(separator + 1),
      params: Object.keys(params).sort().map(key => [key, params[key]]),
      bench,
    };
  }

  function groupComparisons(run) {
    const groups = new Map();
    for (const bench of run.benches) {
      const parsed = parseBenchmark(bench);
      if (!parsed) continue;
      const { className, params } = parsed;
      const key = JSON.stringify([className, params, bench.unit]);
      if (!groups.has(key)) {
        groups.set(key, { className, params, unit: bench.unit, modes: [] });
      }
      groups.get(key).modes.push(parsed);
    }

    const compare = (left, right) => left.localeCompare(right, undefined, { numeric: true });
    return [...groups.values()]
      .sort((left, right) => compare(left.className, right.className)
        || compare(JSON.stringify(left.params), JSON.stringify(right.params))
        || compare(left.unit, right.unit))
      .map(group => ({ ...group, modes: group.modes.sort((left, right) => compare(left.mode, right.mode)) }));
  }

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }

  const formatScore = value => new Intl.NumberFormat(undefined, { maximumSignificantDigits: 6 }).format(value);

  function direction(unit) {
    if (/^(ns|us|µs|ms|s|min|h)\/op$/.test(unit)) return 'Lower is better';
    if (/^ops?\/(ns|us|µs|ms|s|min|h)$/.test(unit)) return 'Higher is better';
    return '';
  }

  function renderCard(group, grid, colors) {
    const { className, params, unit, modes } = group;
    const card = element('section', 'benchmark-card benchmark-comparison-card');
    const title = element('h3', 'benchmark-card-title', className.replace(/^fuse\.benchmarks\./, ''));
    title.title = className;
    const parameterLabel = params.map(([key, value]) => `${key}=${value}`).join(', ') || 'No parameters';
    const parameters = element('p', 'benchmark-comparison-params', parameterLabel);
    const hint = element('p', 'benchmark-comparison-hint', [unit, direction(unit)].filter(Boolean).join(' · '));
    const frame = element('div', 'benchmark-chart-frame');
    frame.style.height = `${Math.max(220, modes.length * 32 + 40)}px`;
    const canvas = element('canvas', 'benchmark-comparison-chart');
    canvas.setAttribute('role', 'img');
    canvas.setAttribute('aria-label', `${className}, ${parameterLabel}: comparison in ${unit}. Values are available below.`);
    frame.appendChild(canvas);

    const values = element('details', 'benchmark-comparison-values');
    values.appendChild(element('summary', '', 'Values'));
    const table = element('table');
    table.setAttribute('aria-label', `${className}, ${parameterLabel}`);
    const head = table.createTHead().insertRow();
    for (const label of ['Mode', unit]) {
      const th = element('th', '', label);
      th.scope = 'col';
      head.appendChild(th);
    }
    const body = table.createTBody();
    for (const { mode, bench } of modes) {
      const row = body.insertRow();
      const name = element('th', '', mode);
      name.scope = 'row';
      row.appendChild(name);
      const score = row.insertCell();
      score.textContent = formatScore(bench.value);
      score.title = String(bench.value);
    }
    values.appendChild(table);
    card.append(title, parameters, hint, frame, values);
    grid.appendChild(card);

    return new Chart(canvas, {
      type: 'horizontalBar',
      data: {
        labels: modes.map(({ mode }) => mode),
        datasets: [{
          label: unit,
          data: modes.map(({ bench }) => bench.value),
          backgroundColor: modes.map(({ mode }) => colors.get(mode)),
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        legend: { display: false },
        scales: {
          xAxes: [{ ticks: { beginAtZero: true, maxTicksLimit: 4, fontSize: 11 } }],
          yAxes: [{ ticks: { fontSize: 11 }, gridLines: { display: false } }],
        },
        tooltips: {
          callbacks: {
            label: item => `${formatScore(modes[item.index].bench.value)} ${unit}`,
          },
        },
      },
    });
  }

  function renderComparisons(set, entries) {
    const runs = entries.filter(entry => entry.tool === 'jmh').slice().sort((left, right) => right.date - left.date);
    if (runs.length === 0) return;

    const palette = ['#0f766e', '#2563eb', '#7c3aed', '#b45309', '#be123c', '#475569', '#0284c7', '#4d7c0f', '#a21caf', '#9a3412'];
    const modeNames = [...new Set(runs.flatMap(run => run.benches.map(parseBenchmark).filter(Boolean).map(result => result.mode)))].sort();
    const colors = new Map(modeNames.map((mode, index) => [mode, palette[index % palette.length]]));
    const section = element('section', 'benchmark-comparisons');
    section.appendChild(element('h2', 'benchmark-section-title', 'Compare modes'));
    section.appendChild(element('p', 'benchmark-comparison-description', 'Each card compares the methods of one benchmark class with the same parameters and unit, within one execution.'));

    const controls = element('div', 'benchmark-comparison-controls');
    const label = element('label', '', 'Execution');
    const select = element('select', 'benchmark-run-select');
    for (const [index, run] of runs.entries()) {
      const option = element('option', '', `${index === 0 ? 'Latest · ' : ''}${run.commit.id.slice(0, 7)} · ${new Date(run.date).toLocaleString()}`);
      option.value = String(index);
      select.appendChild(option);
    }
    label.appendChild(select);
    const commit = element('a', 'benchmark-comparison-commit');
    commit.rel = 'noopener';
    controls.append(label, commit);
    section.appendChild(controls);

    const grid = element('div', 'benchmark-graphs benchmark-comparison-graphs');
    section.appendChild(grid);
    const history = set.querySelector('.benchmark-graphs');
    set.insertBefore(section, history);
    set.insertBefore(element('h2', 'benchmark-section-title', 'History by commit'), history);

    let charts = [];
    function renderRun() {
      charts.forEach(chart => chart.destroy());
      grid.replaceChildren();
      const run = runs[Number(select.value)];
      commit.href = run.commit.url;
      commit.textContent = `Commit ${run.commit.id.slice(0, 7)}`;
      commit.title = run.commit.message;
      const groups = groupComparisons(run);
      charts = groups.map(group => renderCard(group, grid, colors));
      if (groups.length === 0) grid.appendChild(element('p', '', 'No comparable JMH results in this execution.'));
    }
    select.addEventListener('change', renderRun);
    renderRun();
  }

  // The action renders the per-method history first; add comparisons above it.
  document.addEventListener('DOMContentLoaded', () => {
    for (const set of document.querySelectorAll('#main > .benchmark-set')) {
      const name = set.querySelector('.benchmark-title').textContent;
      renderComparisons(set, window.BENCHMARK_DATA.entries[name]);
    }
  });
})();
