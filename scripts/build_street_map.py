"""Pack locally saved OpenStreetMap API extracts into Sitefinch's offline street map."""

import argparse
import gzip
import struct
import xml.etree.ElementTree as ET
from pathlib import Path


ROAD_CLASSES = {
    "motorway": 0, "trunk": 0, "primary": 0, "secondary": 0,
    "tertiary": 1, "residential": 1, "unclassified": 1, "living_street": 1,
    "service": 2, "pedestrian": 2,
}


def load_extracts(paths):
    nodes, ways, bounds = {}, {}, []
    for path in paths:
        for _, item in ET.iterparse(path, events=("end",)):
            if item.tag == "node":
                nodes[item.get("id")] = (float(item.get("lon")), float(item.get("lat")))
            elif item.tag == "way":
                tags = {tag.get("k"): tag.get("v") for tag in item.iterfind("tag")}
                road_class = ROAD_CLASSES.get(tags.get("highway"))
                if road_class is None and tags.get("waterway") in {"river", "canal", "stream"}:
                    road_class = 3
                if road_class is not None:
                    ways[item.get("id")] = (
                        road_class, tags.get("name", ""), [node.get("ref") for node in item.iterfind("nd")]
                    )
            elif item.tag == "bounds":
                bounds.append(tuple(float(item.get(key)) for key in ("minlon", "minlat", "maxlon", "maxlat")))
            if item.tag in {"node", "way", "relation"}:
                item.clear()
    return nodes, ways, bounds


def quantize(point):
    return tuple(round(value * 100_000) for value in point)


def build(paths, output):
    nodes, ways, bounds = load_extracts(paths)
    if not bounds or not ways:
        raise ValueError("The extracts must include bounds and streets.")
    features = []
    for road_class, name, refs in ways.values():
        points = []
        for ref in refs:
            point = quantize(nodes[ref])
            if not points or point != points[-1]:
                points.append(point)
        if len(points) >= 2:
            features.append((road_class, name.encode("utf-8"), points))

    with output.open("wb") as raw:
        with gzip.GzipFile(fileobj=raw, filename="", mode="wb", mtime=0) as packed:
            packed.write(struct.pack(">4sHH", b"SFS1", len(bounds), len(features)))
            for box in bounds:
                packed.write(struct.pack(">4i", *(round(value * 100_000) for value in box)))
            for road_class, name, points in features:
                packed.write(struct.pack(">BHH", road_class, len(name), len(points)))
                packed.write(name)
                for point in points:
                    packed.write(struct.pack(">2i", *point))
    print(f"Packed {len(features)} streets and waterways across {len(bounds)} extracts.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("extracts", type=Path, nargs="+")
    options = parser.parse_args()
    build(options.extracts, options.output)
