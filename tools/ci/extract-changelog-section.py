#!/usr/bin/env python3
"""Print a single version section from a Keep-a-Changelog style CHANGELOG.md.

Used by the release workflow to build the GitHub Release body (which the in-app
"O que ha de novo" sheet reads back verbatim), and referenced by the /release
skill so the skill and CI agree on where a section starts and ends.

Usage:
  python tools/ci/extract-changelog-section.py v0.70.2 CHANGELOG.md
  python tools/ci/extract-changelog-section.py 0.70.2 CHANGELOG.md

Matches a header line of the form:
  ## [<version>] - <date>
tolerating a leading "v" on either side (so "v0.70.2" matches "## [0.70.2] - ..."
and vice versa). Prints everything from just after that header up to the next
"## [" header (or EOF), with leading/trailing blank lines trimmed.

Exit codes:
  0  section found and printed
  1  section not found (CI should fail loudly rather than publish an empty body)
  2  changelog file not found
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

SECTION_RE = re.compile(r"^##\s+\[(?P<version>[^\]]+)\]")


def normalize(version: str) -> str:
    """Strip a single leading 'v'/'V' and surrounding whitespace."""
    version = version.strip()
    if version[:1] in ("v", "V"):
        version = version[1:]
    return version


def extract_section(text: str, wanted: str) -> str | None:
    wanted_norm = normalize(wanted)
    lines = text.splitlines()

    start: int | None = None
    for index, line in enumerate(lines):
        match = SECTION_RE.match(line)
        if match and normalize(match.group("version")) == wanted_norm:
            start = index + 1
            break

    if start is None:
        return None

    end = len(lines)
    for index in range(start, len(lines)):
        if lines[index].startswith("## ["):
            end = index
            break

    body = "\n".join(lines[start:end]).strip("\n")
    # Collapse trailing whitespace-only lines, keep interior formatting.
    return body.strip()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("version", help="Version tag, e.g. v0.70.2 or 0.70.2")
    parser.add_argument(
        "changelog",
        nargs="?",
        default="CHANGELOG.md",
        help="Path to the changelog file (default: CHANGELOG.md)",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    changelog_path = Path(args.changelog)
    if not changelog_path.exists():
        print(f"Changelog file not found: {changelog_path}", file=sys.stderr)
        return 2

    section = extract_section(
        changelog_path.read_text(encoding="utf-8"),
        args.version,
    )
    if section is None:
        print(
            f"No changelog section for '{args.version}' in {changelog_path}. "
            f"Run the /release skill to add it before tagging.",
            file=sys.stderr,
        )
        return 1

    print(section)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
