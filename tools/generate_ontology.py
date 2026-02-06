#!/usr/bin/env python3
import re
import sys
from pathlib import Path

DEFAULT_PREFIXES = {
    "dcterms": "http://purl.org/dc/terms/",
    "ffa": "https://data.sfa.se/termer/1.0/",
    "mimer": "https://data.sfa.se/mimer/1.0/",
    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
    "owl": "http://www.w3.org/2002/07/owl#",
    "schema": "http://schema.org/",
    "xsd": "http://www.w3.org/2001/XMLSchema#",
}

DATATYPE_TO_XSD = {
    "TIME": "xsd:dateTime",
    "INTEGER": "xsd:integer",
    "LONG": "xsd:long",
    "DOUBLE": "xsd:double",
    "BOOLEAN": "xsd:boolean",
    "STRING": "xsd:string",
    "DATA": "xsd:base64Binary",
}

SCALAR_TO_XSD = {
    "String": "xsd:string",
    "Int": "xsd:integer",
    "Float": "xsd:double",
    "Boolean": "xsd:boolean",
    "ID": "xsd:string",
    "Long": "xsd:long",
    "DateTime": "xsd:dateTime",
    "Bytes": "xsd:base64Binary",
}

SKIP_ENUMS = {"Attributes", "DataTypes"}


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
        desc_val = None
        m = re.search(r"description\s*:\s*\"([^\"]+)\"", body)
        if m:
            desc_val = m.group(1)
        entries.append(
            {
                "term": name,
                "datatype": datatype,
                "name": name_val,
                "uri": uri_val,
                "description": desc_val,
            }
        )
    return entries


def strip_comments(line: str) -> str:
    if "#" in line:
        return line.split("#", 1)[0]
    return line


def parse_enum_values(block: str):
    values = []
    for raw_line in block.splitlines():
        line = strip_comments(raw_line).strip()
        if not line:
            continue
        if line.startswith('"'):
            continue
        m = re.match(r"^([A-Za-z_][A-Za-z0-9_]*)\b", line)
        if m:
            values.append(m.group(1))
    return values


def parse_union_defs(text: str):
    unions = {}
    for m in re.finditer(r"\bunion\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*([^\n]+)", text):
        name = m.group(1)
        rhs = strip_comments(m.group(2)).strip()
        members = [part.strip() for part in rhs.split("|") if part.strip()]
        if members:
            unions[name] = members
    return unions


def iter_type_blocks(text: str):
    for m in re.finditer(r"\btype\s+([A-Za-z_][A-Za-z0-9_]*)\b", text):
        name = m.group(1)
        brace = text.find("{", m.end())
        if brace == -1:
            continue
        depth = 0
        for i in range(brace, len(text)):
            if text[i] == "{":
                depth += 1
            elif text[i] == "}":
                depth -= 1
                if depth == 0:
                    header = text[m.end() : brace]
                    block = text[brace + 1 : i]
                    yield name, header, block
                    break


def parse_fields(block: str):
    fields = []
    for m in re.finditer(
        r"^\s*([A-Za-z_][A-Za-z0-9_]*)\s*:\s*([^\n]+)$", block, re.M
    ):
        field_name = m.group(1)
        tail = m.group(2).strip()
        if tail.startswith("["):
            type_part = tail.split("]", 1)[0][1:].strip()
            after = tail.split("]", 1)[1]
            gql_type = type_part
        else:
            gql_type = tail.split()[0]
            after = tail[len(gql_type) :]
        use_attr = None
        m_use = re.search(r"@use\(attribute:\s*([A-Za-z_][A-Za-z0-9_]*)\)", after)
        if m_use:
            use_attr = m_use.group(1)
        fields.append({"name": field_name, "type": gql_type, "use": use_attr})
    return fields


def iri_from_attribute(attr_entry, fallback_prefix: str, fallback_name: str):
    if attr_entry:
        return attr_entry.get("uri") or attr_entry.get("name")
    return f"{fallback_prefix}{fallback_name}"


def ttl_literal(text: str) -> str:
    escaped = text.replace("\\", "\\\\").replace('"', "\\\"")
    return f'"{escaped}"'


def ttl_lang_literal(text: str) -> str:
    m = re.match(r"^\s*\[([a-zA-Z]{2,3}(?:-[A-Za-z0-9]+)*)\]\s*(.+)$", text)
    if not m:
        return ttl_literal(text)
    lang = m.group(1).lower()
    body = m.group(2).strip()
    escaped = body.replace("\\", "\\\\").replace('"', "\\\"")
    return f'"{escaped}"@{lang}'


def ttl_iri(curie_or_iri: str) -> str:
    if curie_or_iri.startswith("http://") or curie_or_iri.startswith("https://"):
        return f"<{curie_or_iri}>"
    return curie_or_iri


def ttl_list(items):
    if not items:
        return "()"
    return "( " + " ".join(items) + " )"


def main():
    include_includes = False
    args = sys.argv[1:]
    if "--include-includes" in args:
        include_includes = True
        args.remove("--include-includes")

    if len(args) != 2:
        print("Usage: generate_ontology.py [--include-includes] <schema.graphqls> <output.ttl>")
        return 2

    schema_path = Path(args[0])
    out_path = Path(args[1])
    text = schema_path.read_text(encoding="utf-8")

    attr_block = extract_enum_block(text, "Attributes")
    if not attr_block:
        print("No Attributes enum found in schema.")
        return 1
    attr_entries = parse_attributes(attr_block)
    attributes = {entry["term"]: entry for entry in attr_entries}

    enums = {}
    for m in re.finditer(r"\benum\s+([A-Za-z_][A-Za-z0-9_]*)\b", text):
        name = m.group(1)
        if name in SKIP_ENUMS:
            continue
        block = extract_enum_block(text, name)
        if block:
            enums[name] = parse_enum_values(block)

    unions = parse_union_defs(text)

    type_defs = {}
    for name, header, block in iter_type_blocks(text):
        if "@record" not in header and "@template" not in header:
            continue
        record_attr = None
        m = re.search(r"@record\(attribute:\s*([A-Za-z_][A-Za-z0-9_]*)\)", header)
        if m:
            record_attr = m.group(1)
        fields = parse_fields(block)
        type_defs[name] = {"record_attr": record_attr, "fields": fields}

    class_iris = {}
    for type_name, info in type_defs.items():
        record_attr = info["record_attr"]
        attr_entry = attributes.get(record_attr) if record_attr else None
        if record_attr:
            class_iri = iri_from_attribute(attr_entry, DEFAULT_PREFIXES["ffa"], record_attr)
        else:
            class_iri = f"{DEFAULT_PREFIXES['ffa']}{type_name}"
        class_iris[type_name] = class_iri

    enum_iris = {name: f"{DEFAULT_PREFIXES['ffa']}{name}" for name in enums}
    union_iris = {}
    for name in unions:
        attr_entry = attributes.get(name) or attributes.get(name.lower())
        if attr_entry:
            union_iris[name] = iri_from_attribute(attr_entry, DEFAULT_PREFIXES["ffa"], name)
        else:
            union_iris[name] = f"{DEFAULT_PREFIXES['ffa']}{name}"

    property_defs = {}
    property_domains = {}
    property_ranges = {}

    def note_domain(prop_iri, domain_iri):
        property_domains.setdefault(prop_iri, set()).add(domain_iri)

    def note_range(prop_iri, range_iri):
        property_ranges.setdefault(prop_iri, set()).add(range_iri)

    for type_name, info in type_defs.items():
        domain_iri = class_iris[type_name]
        for field in info["fields"]:
            field_type = field["type"]
            use_attr = field["use"]
            attr_entry = attributes.get(use_attr or field["name"])
            prop_iri = iri_from_attribute(
                attr_entry, DEFAULT_PREFIXES["ffa"], use_attr or field["name"]
            )

            prop = property_defs.setdefault(
                prop_iri,
                {
                    "label": use_attr or field["name"],
                    "comment": attr_entry.get("description") if attr_entry else None,
                    "kind": None,
                },
            )

            if field_type in SCALAR_TO_XSD:
                prop["kind"] = "owl:DatatypeProperty"
                note_range(prop_iri, SCALAR_TO_XSD[field_type])
            elif field_type in enums:
                prop["kind"] = "owl:ObjectProperty"
                note_range(prop_iri, enum_iris[field_type])
            elif field_type in unions:
                prop["kind"] = "owl:ObjectProperty"
                note_range(prop_iri, union_iris[field_type])
            elif field_type in class_iris:
                prop["kind"] = "owl:ObjectProperty"
                note_range(prop_iri, class_iris[field_type])
            else:
                prop["kind"] = prop.get("kind") or "owl:DatatypeProperty"
                note_range(prop_iri, "xsd:string")

            note_domain(prop_iri, domain_iri)

    lines = []
    for prefix, uri in DEFAULT_PREFIXES.items():
        lines.append(f"@prefix {prefix}: <{uri}> .")
    lines.append("")

    lines.append("ffa:Ontology a owl:Ontology .")
    lines.append("")

    for type_name, class_iri in class_iris.items():
        class_curie = ttl_iri(class_iri)
        lines.append(f"{class_curie} a owl:Class ;")
        lines.append(f"  rdfs:label {ttl_literal(type_name)} .")
        lines.append("")

    for enum_name, values in enums.items():
        enum_iri = enum_iris[enum_name]
        enum_curie = ttl_iri(enum_iri)
        lines.append(f"{enum_curie} a owl:Class ;")
        lines.append(f"  rdfs:label {ttl_literal(enum_name)} .")
        lines.append("")
        for value in values:
            value_iri = f"{DEFAULT_PREFIXES['ffa']}{enum_name}#{value}"
            lines.append(f"{ttl_iri(value_iri)} a {ttl_iri(enum_iri)} ;")
            lines.append(f"  rdfs:label {ttl_literal(value)} .")
            lines.append("")

    for union_name, members in unions.items():
        union_iri = union_iris[union_name]
        member_iris = []
        for member in members:
            if member in class_iris:
                member_iris.append(ttl_iri(class_iris[member]))
            elif member in enum_iris:
                member_iris.append(ttl_iri(enum_iris[member]))
            else:
                member_iris.append(ttl_iri(f"{DEFAULT_PREFIXES['ffa']}{member}"))
        lines.append(f"{ttl_iri(union_iri)} a owl:Class ;")
        lines.append(f"  rdfs:label {ttl_literal(union_name)} ;")
        lines.append(f"  owl:unionOf {ttl_list(member_iris)} .")
        lines.append("")

    for prop_iri, info in sorted(property_defs.items()):
        prop_curie = ttl_iri(prop_iri)
        lines.append(f"{prop_curie} a {info['kind']} ;")
        lines.append(f"  rdfs:label {ttl_literal(info['label'])} ;")
        if info.get("comment"):
            lines.append(f"  rdfs:comment {ttl_lang_literal(info['comment'])} ;")
        domains = property_domains.get(prop_iri, set())
        ranges = property_ranges.get(prop_iri, set())
        if len(domains) == 1:
            lines.append(f"  rdfs:domain {ttl_iri(next(iter(domains)))} ;")
        elif include_includes and len(domains) > 1:
            for domain in sorted(domains):
                lines.append(f"  schema:domainIncludes {ttl_iri(domain)} ;")
        if len(ranges) == 1:
            lines.append(f"  rdfs:range {ttl_iri(next(iter(ranges)))} ;")
        elif include_includes and len(ranges) > 1:
            for rng in sorted(ranges):
                lines.append(f"  schema:rangeIncludes {ttl_iri(rng)} ;")
        if lines[-1].endswith(";"):
            lines[-1] = lines[-1][:-1] + " ."
        else:
            lines.append(".")
        lines.append("")

    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
