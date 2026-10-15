#!/usr/bin/env python3
"""Parse bombardier JSON outputs into summary.json + summary.md."""

from __future__ import annotations

import json
import re
from pathlib import Path

RESULTS = Path(__file__).resolve().parent / "results"


def us_to_ms(v) -> float:
    """Bombardier JSON latency fields are microseconds."""
    return float(v or 0) / 1e3


def parse_json(path: Path) -> dict | None:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return None
    r = data.get("result", data)
    rps = r.get("rps") or {}
    lat = r.get("latency") or {}
    pct = lat.get("percentiles") or {}
    mean_rps = rps.get("mean")
    if mean_rps is None:
        return None
    return {
        "rps": float(mean_rps),
        "p50_ms": us_to_ms(pct.get("50") or pct.get(50)),
        "p99_ms": us_to_ms(pct.get("99") or pct.get(99)),
        "avg_ms": us_to_ms(lat.get("mean")),
        "req2xx": int(r.get("req2xx") or 0),
        "req5xx": int(r.get("req5xx") or 0),
        "file": path.name,
    }


def main() -> None:
    rows = []
    for path in sorted(RESULTS.glob("*_meas.json")):
        m = re.match(r"(kona|spring|fastapi)_(ping|json)_c(\d+)_meas\.json", path.name)
        if not m:
            continue
        parsed = parse_json(path)
        if not parsed:
            continue
        rows.append(
            {
                "framework": m.group(1),
                "endpoint": m.group(2),
                "concurrency": int(m.group(3)),
                **parsed,
            }
        )

    out = {"runs": rows}
    (RESULTS / "summary.json").write_text(json.dumps(out, indent=2), encoding="utf-8")

    lines = [
        "| Framework | Endpoint | C | RPS | p50 (ms) | p99 (ms) |",
        "|---|---|---:|---:|---:|---:|",
    ]
    for r in sorted(rows, key=lambda x: (x["endpoint"], x["concurrency"], x["framework"])):
        lines.append(
            f"| {r['framework']} | /{r['endpoint']} | {r['concurrency']} | "
            f"{r['rps']:.0f} | {r.get('p50_ms', 0):.3f} | {r.get('p99_ms', 0):.3f} |"
        )
    md = "\n".join(lines) + "\n"
    (RESULTS / "summary.md").write_text(md, encoding="utf-8")
    print(md)

    if rows:
        by_fw: dict[str, list[float]] = {}
        for r in rows:
            by_fw.setdefault(r["framework"], []).append(r["rps"])
        means = {k: sum(v) / len(v) for k, v in by_fw.items()}
        winner = max(means, key=means.get)  # type: ignore[arg-type]
        print("mean_rps_by_framework=", json.dumps(means, indent=2))
        print("overall_winner_by_mean_rps=", winner)


if __name__ == "__main__":
    main()
