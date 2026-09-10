"""Add the repository's layout and comparisons to the github-action-benchmark dashboard."""

import argparse
from pathlib import Path
import shutil


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('site', type=Path, help='Directory containing the generated index.html')
    site = parser.parse_args().site
    index = site / 'index.html'
    html = index.read_text(encoding='utf-8')

    additions = [
        ('benchmark-layout-style', '</head>',
         '<link id="benchmark-layout-style" rel="stylesheet" href="benchmark-layout.css" />'),
        ('benchmark-layout-script', '<script id="main-script">',
         '<script id="benchmark-layout-script" src="benchmark-layout.js"></script>'),
        ('benchmark-comparison-script', '<script id="main-script">',
         '<script id="benchmark-comparison-script" src="benchmark-comparison.js"></script>'),
    ]
    for element_id, marker, addition in additions:
        if f'id="{element_id}"' in html:
            continue
        if marker not in html:
            raise SystemExit(f'Cannot apply benchmark layout: missing {marker} in {index}')
        html = html.replace(marker, f'{addition}\n    {marker}', 1)

    for name in ('benchmark-layout.css', 'benchmark-layout.js', 'benchmark-comparison.js'):
        shutil.copyfile(Path(__file__).parent / name, site / name)
    index.write_text(html, encoding='utf-8')


if __name__ == '__main__':
    main()
