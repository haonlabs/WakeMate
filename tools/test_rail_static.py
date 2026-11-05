#!/usr/bin/env python3
"""Checks for tools/generate_rail_static.py and the committed assets/rail_static.json.

Run: python3 -m unittest tools/test_rail_static.py   (offline, no Overpass call)
"""
import json, os, sys, unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import generate_rail_static as g  # noqa: E402

ASSET = os.path.join(HERE, "..", "app", "src", "main", "assets", "rail_static.json")


class PureFunctions(unittest.TestCase):
    def test_chain_ways_flips_to_connect(self):
        a = [(0.0, 0.0), (0.0, 0.001)]
        b = [(0.0, 0.002), (0.0, 0.001)]  # drawn backwards: its far end touches a's end
        c = [(0.0, 0.002), (0.0, 0.003)]
        out = g.chain_ways([a, b, c])
        self.assertEqual(out[0], (0.0, 0.0))
        self.assertEqual(out[-1], (0.0, 0.003))
        self.assertEqual(len(out), 6)

    def test_chain_ways_orients_first_way_by_second(self):
        first = [(0.0, 0.001), (0.0, 0.0)]  # stored backwards
        second = [(0.0, 0.001), (0.0, 0.002)]
        out = g.chain_ways([first, second])
        self.assertEqual((out[0], out[-1]), ((0.0, 0.0), (0.0, 0.002)))

    def test_days_mask(self):
        row = dict(monday="1", tuesday="1", wednesday="1", thursday="1", friday="1", saturday="0", sunday="1", end_date="20261231")
        self.assertEqual(g.days_mask(row, "20261105"), 0b1011111)  # Mon-Fri + Sun, no Saturday (bit 5)
        self.assertEqual(g.days_mask(row, "20270101"), 0)  # calendar already ended

    def test_plain_name_strips_sponsors(self):
        self.assertEqual(g.plain_name("Blok A VISA"), "Blok A")
        self.assertEqual(g.plain_name("Dukuh Atas Bank Syariah Indonesia"), "Dukuh Atas")
        self.assertEqual(g.plain_name("Harjamukti"), "Harjamukti")

    def test_text_color_contrast(self):
        self.assertEqual(g.text_color("FFD21F"), "000000")
        self.assertEqual(g.text_color("0E6938"), "FFFFFF")

    def test_hms_after_midnight(self):
        self.assertEqual(g.hms_to_sec("25:15:00"), 90900)


@unittest.skipUnless(os.path.exists(ASSET), "rail_static.json not generated")
class Dataset(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.d = json.load(open(ASSET, encoding="utf-8"))

    def test_kinds_present(self):
        self.assertEqual({r["kind"] for r in self.d["routes"]}, {"KRL", "MRT", "LRT Jakarta", "LRT Jabodebek"})

    def test_references_resolve(self):
        routes = {r["id"] for r in self.d["routes"]}
        for t in self.d["trips"]:
            self.assertIn(t["route"], routes)
            self.assertGreaterEqual(len(t["stops"]), 2)
            for s in t["stops"]:
                self.assertIn(s, self.d["halte"], t["id"])
            self.assertTrue(t["shape"], t["id"])

    def test_krl_times_are_monotonic_and_aligned(self):
        krl = [t for t in self.d["trips"] if t["route"].startswith("krl:")]
        self.assertGreater(len(krl), 1000)
        for t in krl:
            self.assertEqual(len(t["arr"]), len(t["stops"]), t["id"])
            self.assertEqual(t["arr"], sorted(t["arr"]), t["id"])
            self.assertEqual(t["freq"], [])
            self.assertEqual(t["days"] & (1 << 5), 0, "no Saturday data exists in the source")

    def test_no_invented_timetables_for_mrt_lrt(self):
        for t in self.d["trips"]:
            if not t["route"].startswith("krl:"):
                self.assertEqual(t["arr"], [], t["id"])
                self.assertEqual(t["freq"], [], t["id"])

    def test_meta_has_attribution_and_validity(self):
        self.assertRegex(self.d["meta"]["validUntil"], r"^\d{4}-\d{2}-\d{2}$")
        self.assertTrue(any("Open-KRL" in a for a in self.d["meta"]["attribution"]))


if __name__ == "__main__":
    unittest.main()
