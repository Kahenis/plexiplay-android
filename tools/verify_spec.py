#!/usr/bin/env python3
"""Spec preroll, vérifiée avant compilation Android."""

def parse(raw):
    value = (raw or "").strip()
    if not value:
        return None
    has_comma = "," in value
    has_semi = ";" in value
    if has_comma and has_semi:
        mode = "staged"
        stages = [[p.strip() for p in stage.split(";") if p.strip()] for stage in value.split(",")]
    elif has_semi:
        mode = "random"
        stages = [[p.strip() for p in value.split(";") if p.strip()]]
    else:
        mode = "sequential"
        stages = [[p.strip()] for p in value.split(",") if p.strip()]
    stages = [s for s in stages if s]
    return {"mode": mode, "stages": stages}

def select(spec, random_index):
    if spec["mode"] == "sequential":
        return [s[0] for s in spec["stages"]]
    if spec["mode"] == "random":
        pool = spec["stages"][0]
        return [pool[random_index]]
    return [stage[min(random_index, len(stage) - 1)] for stage in spec["stages"]]

def queue(preroll, trailers, feature, count):
    kinds = ["preroll"] * len(preroll) + ["trailer"] * min(count, len(trailers)) + ["feature"]
    return kinds

assert parse("  ") is None
assert parse("11,22,33")["mode"] == "sequential"
assert select(parse("11,22,33"), 0) == ["11", "22", "33"]
assert select(parse("11;22;33"), 1) == ["22"]
assert select(parse("11;12,21"), 1) == ["12", "21"]
assert queue(["p"], ["t1", "t2"], "film", 1) == ["preroll", "trailer", "feature"]
assert queue(["p"], ["t1"], "film", 0) == ["preroll", "feature"]
print("spec ok")
