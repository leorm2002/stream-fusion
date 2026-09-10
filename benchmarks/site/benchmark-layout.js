'use strict';

// Give each Chart.js canvas its own responsive container inside the grid.
Chart.plugins.register({
  id: 'benchmark-grid-layout',
  beforeInit(chart) {
    const canvas = chart.canvas;
    if (!canvas.classList.contains('benchmark-chart')) return;

    const label = chart.data.datasets[0].label;
    const card = document.createElement('section');
    card.className = 'benchmark-card';

    const title = document.createElement('h2');
    title.className = 'benchmark-card-title';
    title.textContent = label.replace(/^fuse\.benchmarks\./, '');
    title.title = label;

    const frame = document.createElement('div');
    frame.className = 'benchmark-chart-frame';

    canvas.parentElement.insertBefore(card, canvas);
    card.append(title, frame);
    frame.appendChild(canvas);
    canvas.setAttribute('role', 'img');
    canvas.setAttribute('aria-label', label);

    chart.options.responsive = true;
    chart.options.maintainAspectRatio = false;
    // The HTML heading wraps long benchmark names without shrinking the plot.
    chart.options.legend.display = false;
  },
});
