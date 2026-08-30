#!/usr/bin/env python3
"""Tests for extract-changelog-section.py (stdlib unittest, no pytest needed).

Run:
  python3 tools/ci/extract-changelog-section.test.py
"""

from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

_MODULE_PATH = Path(__file__).with_name("extract-changelog-section.py")
_spec = importlib.util.spec_from_file_location("extract_changelog_section", _MODULE_PATH)
assert _spec and _spec.loader
_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_mod)

extract_section = _mod.extract_section
normalize = _mod.normalize

SAMPLE = """# Changelog

## Unreleased

## [v0.70.2] - 2026-08-30

### Added

- Novo modo de leitura em duas paginas.

### Fixed

- Corrigido bug no leitor de manga.

## [v0.60] - 2026-08-10

### Changed

- Reorganizada a secao Mais.

## [v0.29] - 2026-03-11

### Added

- Catalogo de fontes do leitor de novels.
"""


class NormalizeTests(unittest.TestCase):
    def test_strips_single_leading_v(self):
        self.assertEqual(normalize("v0.70.2"), "0.70.2")
        self.assertEqual(normalize("V0.70.2"), "0.70.2")
        self.assertEqual(normalize(" 0.70.2 "), "0.70.2")


class ExtractSectionTests(unittest.TestCase):
    def test_matches_with_v_prefix_on_query(self):
        body = extract_section(SAMPLE, "v0.70.2")
        self.assertIsNotNone(body)
        self.assertIn("Novo modo de leitura", body)
        self.assertIn("Corrigido bug no leitor de manga", body)

    def test_matches_without_v_prefix_on_query(self):
        body = extract_section(SAMPLE, "0.70.2")
        self.assertIsNotNone(body)
        self.assertIn("Novo modo de leitura", body)

    def test_stops_at_next_section(self):
        body = extract_section(SAMPLE, "v0.70.2")
        self.assertNotIn("Reorganizada a secao Mais", body)
        self.assertNotIn("## [v0.60]", body)

    def test_middle_section_bounded_both_sides(self):
        body = extract_section(SAMPLE, "v0.60")
        self.assertEqual(body, "### Changed\n\n- Reorganizada a secao Mais.")

    def test_last_section_runs_to_eof(self):
        body = extract_section(SAMPLE, "v0.29")
        self.assertIn("Catalogo de fontes", body)

    def test_missing_version_returns_none(self):
        self.assertIsNone(extract_section(SAMPLE, "v9.9.9"))


if __name__ == "__main__":
    unittest.main()
