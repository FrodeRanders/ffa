#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path

DEFAULT_PREFIXES = {
    "dcterms": "http://purl.org/dc/terms/",
    "ffa": "https://data.sfa.se/termer/1.0/",
    "mimer": "https://data.sfa.se/mimer/1.0/",
    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "xsd": "http://www.w3.org/2001/XMLSchema#",
}

DEFAULT_TERMS = {
    "id": "@id",
    "version": "ffa:version",
    "typ": "ffa:typ",
    "varde": "rdf:value",
}

TIME_TYPES = {"TIME"}


def extract_enum_block(text: str, enum_name: str) -> str:
    pattern = re.compile(r"enum\s+%s\b" % re.escape(enum_name))
    match = pattern.search(text)
    if not match:
        return ""
    start = text.find("{", match.end())
    if start == -1:
        return ""
    depth = 0
    for i in range(start, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return text[start + 1 : i]
    return ""


def parse_attributes(block: str):
    entries = []
    pattern = re.compile(r"(?s)([A-Za-z_][A-Za-z0-9_]*)\s+@attribute\((.*?)\)")
    for name, body in pattern.findall(block):
        datatype = None
        m = re.search(r"datatype\s*:\s*([A-Z_]+)", body)
        if m:
            datatype = m.group(1)
        name_val = None
        m = re.search(r"name\s*:\s*\"([^\"]+)\"", body)
        if m:
            name_val = m.group(1)
        uri_val = None
        m = re.search(r"uri\s*:\s*\"([^\"]+)\"", body)
        if m:
            uri_val = m.group(1)
        entries.append({"term": name, "datatype": datatype, "name": name_val, "uri": uri_val})
    return entries


def build_context(entries):
    context = {}
    context.update(DEFAULT_TERMS)
    context.update(DEFAULT_PREFIXES)

    for entry in entries:
        term = entry["term"]
        uri = entry["uri"] or entry["name"]
        if not uri:
            continue
        if entry["datatype"] in TIME_TYPES:
            context[term] = {"@id": uri, "@type": "xsd:dateTime"}
        else:
            context[term] = uri

    # keep a stable order for readability
    ordered = {"@context": {}}
    ordered["@context"].update({"id": context.pop("id")})
    for key in ("dcterms", "ffa", "mimer", "rdf", "xsd"):
        ordered["@context"][key] = context.pop(key)
    for key in ("version", "typ", "varde"):
        ordered["@context"][key] = context.pop(key)

    for key in sorted(context.keys()):
        ordered["@context"][key] = context[key]

    return ordered


def main():
    if len(sys.argv) != 3:
        print("Usage: generate_context.py <schema.graphqls> <output.jsonld>")
        return 2

    schema_path = Path(sys.argv[1])
    out_path = Path(sys.argv[2])
    text = schema_path.read_text(encoding="utf-8")
    block = extract_enum_block(text, "Attributes")
    if not block:
        print("No Attributes enum found in schema.")
        return 1

    entries = parse_attributes(block)
    context = build_context(entries)
    out_path.write_text(json.dumps(context, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
