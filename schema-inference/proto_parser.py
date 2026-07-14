"""Protobuf-to-OpenAPI converter.

Parses .proto file content and converts message definitions to OpenAPI 3.1 schema
objects under components/schemas.
"""

import re
from typing import Any

WELL_KNOWN_TYPES: dict[str, dict[str, str]] = {
    "google.protobuf.Timestamp": {"type": "string", "format": "date-time"},
    "google.protobuf.Duration": {"type": "string", "format": "duration"},
    "google.protobuf.Struct": {"type": "object"},
    "google.protobuf.Value": {"oneOf": [{"type": "string"}, {"type": "number"}, {"type": "boolean"}, {"type": "object"}, {"type": "array"}]},
    "google.protobuf.ListValue": {"type": "array", "items": {"oneOf": [{"type": "string"}, {"type": "number"}, {"type": "boolean"}, {"type": "object"}]}},
    "google.protobuf.Empty": {"type": "object"},
    "google.protobuf.Any": {"type": "object"},
}

PROTO_SCALAR_TO_OPENAPI: dict[str, dict[str, str]] = {
    "double": {"type": "number", "format": "double"},
    "float": {"type": "number", "format": "float"},
    "int32": {"type": "integer", "format": "int32"},
    "int64": {"type": "integer", "format": "int64"},
    "uint32": {"type": "integer", "format": "int64"},
    "uint64": {"type": "integer", "format": "int64"},
    "sint32": {"type": "integer", "format": "int32"},
    "sint64": {"type": "integer", "format": "int64"},
    "fixed32": {"type": "integer", "format": "int64"},
    "fixed64": {"type": "integer", "format": "int64"},
    "sfixed32": {"type": "integer", "format": "int32"},
    "sfixed64": {"type": "integer", "format": "int64"},
    "bool": {"type": "boolean"},
    "string": {"type": "string"},
    "bytes": {"type": "string", "format": "byte"},
}

_MAP_RE = re.compile(r"map<\s*(\S+)\s*,\s*(\S+)\s*>")
_ONEOF_RE = re.compile(r"oneof\s+(\w+)\s*\{([^}]+)\}", re.MULTILINE)
_ENUM_RE = re.compile(r"enum\s+(\w+)\s*\{([^}]+)\}", re.MULTILINE)
_MESSAGE_RE = re.compile(r"message\s+(\w+)\s*\{", re.MULTILINE)
_FIELD_RE = re.compile(
    r"^\s*(optional\s+|repeated\s+)?(\S+)\s+(\w+)\s*=\s*\d+",
    re.MULTILINE,
)


def _resolve_type(type_name: str, package: str, defined: dict[str, Any]) -> dict[str, Any]:
    """Resolve a proto type name to an OpenAPI schema fragment."""
    if type_name in PROTO_SCALAR_TO_OPENAPI:
        return dict(PROTO_SCALAR_TO_OPENAPI[type_name])

    if type_name in WELL_KNOWN_TYPES:
        return dict(WELL_KNOWN_TYPES[type_name])

    # Qualified well-known
    full_wk = type_name if "." in type_name else f"google.protobuf.{type_name}"
    if full_wk in WELL_KNOWN_TYPES:
        return dict(WELL_KNOWN_TYPES[full_wk])

    # Enum already parsed
    if type_name in defined and defined[type_name].get("type") == "string":
        return dict(defined[type_name])

    # Another message in same file → $ref
    ref_name = type_name.split(".")[-1]
    if ref_name in defined:
        return {"$ref": f"#/components/schemas/{ref_name}"}

    return {"type": "object"}


def _parse_enum(body: str) -> dict[str, list[str]]:
    """Return {enum_name: [value1, value2, ...]}."""
    result: dict[str, list[str]] = {}
    for m in _ENUM_RE.finditer(body):
        name = m.group(1)
        values = re.findall(r"(\w+)\s*=", m.group(2))
        result[name] = values
    return result


def _parse_oneof(body: str) -> list[dict[str, Any]]:
    """Parse oneof blocks from a message body."""
    results: list[dict[str, Any]] = []
    for m in _ONEOF_RE.finditer(body):
        group_name = m.group(1)
        inner = m.group(2)
        fields = _FIELD_RE.findall(inner)
        one_of_items: list[dict[str, Any]] = []
        for optional_kw, field_type, field_name in fields:
            schema = _resolve_type_raw(field_type)
            one_of_items.append({
                "type": "object",
                "properties": {field_name: schema},
                "required": [field_name],
            })
        if one_of_items:
            results.append({"oneOf": one_of_items, "title": group_name})
    return results


def _resolve_type_raw(type_name: str) -> dict[str, Any]:
    """Resolve without access to defined messages (used for simple cases)."""
    if type_name in PROTO_SCALAR_TO_OPENAPI:
        return dict(PROTO_SCALAR_TO_OPENAPI[type_name])
    full_wk = type_name if "." in type_name else f"google.protobuf.{type_name}"
    if full_wk in WELL_KNOWN_TYPES:
        return dict(WELL_KNOWN_TYPES[full_wk])
    return {"type": "object"}


def _strip_comments(text: str) -> str:
    return re.sub(r"//[^\n]*", "", text)


def _parse_message(name: str, body: str, package: str, enums: dict[str, Any], messages: dict[str, Any]) -> dict[str, Any]:
    """Convert a single message body to an OpenAPI schema dict."""
    properties: dict[str, Any] = {}
    required: list[str] = []

    # Enum definitions
    local_enums = _parse_enum(body)
    for ename, evals in local_enums.items():
        enums[ename] = {"type": "string", "enum": evals}

    # Nested messages
    nested = _MESSAGE_RE.finditer(body)
    for nm in nested:
        nested_name = nm.group(1)
        # Extract nested body
        start = nm.end()
        depth = 1
        idx = start
        while idx < len(body) and depth > 0:
            if body[idx] == "{":
                depth += 1
            elif body[idx] == "}":
                depth -= 1
            idx += 1
        nested_body = body[start:idx - 1]
        messages[nested_name] = _parse_message(nested_name, nested_body, package, enums, messages)

    # Oneof groups
    oneof_groups = _parse_oneof(body)
    oneof_schemas: list[dict[str, Any]] = []
    oneof_field_names: set[str] = set()
    for group in oneof_groups:
        for item in group.get("oneOf", []):
            oneof_field_names.update(item.get("properties", {}).keys())
        oneof_schemas.append(group)

    # Regular fields
    for optional_kw, field_type, field_name in _FIELD_RE.findall(body):
        if field_name in oneof_field_names:
            continue

        # Map field
        map_match = _MAP_RE.match(field_type)
        if map_match:
            key_type, val_type = map_match.group(1), map_match.group(2)
            val_schema = _resolve_type(val_type, package, {**enums, **messages})
            properties[field_name] = {
                "type": "object",
                "additionalProperties": val_schema,
            }
            continue

        # Repeated → array
        is_repeated = (optional_kw or "").strip() == "repeated"
        schema = _resolve_type(field_type, package, {**enums, **messages})

        if is_repeated:
            schema = {"type": "array", "items": schema}

        if optional_kw is None and not is_repeated:
            # proto3 fields are implicitly optional (nullable)
            pass

        properties[field_name] = schema

    result: dict[str, Any] = {
        "type": "object",
        "properties": properties,
    }

    if oneof_schemas:
        result["oneOf"] = oneof_schemas

    return result


def _extract_message_body(content: str, msg_name: str) -> str | None:
    """Extract the body of a top-level message by name."""
    pattern = re.compile(rf"message\s+{re.escape(msg_name)}\s*\{{", re.MULTILINE)
    m = pattern.search(content)
    if not m:
        return None
    start = m.end()
    depth = 1
    idx = start
    while idx < len(content) and depth > 0:
        if content[idx] == "{":
            depth += 1
        elif content[idx] == "}":
            depth -= 1
        idx += 1
    return content[start : idx - 1]


def parse_proto_to_openapi(proto_content: str, package_name: str) -> dict[str, Any]:
    """Parse a .proto file and return an OpenAPI 3.1 document with components/schemas."""
    cleaned = _strip_comments(proto_content)

    enums: dict[str, Any] = {}
    messages: dict[str, Any] = {}
    schemas: dict[str, Any] = {}

    # Gather all message names
    msg_names = [m.group(1) for m in _MESSAGE_RE.finditer(cleaned)]

    # Parse enums at package level
    for ename, evals in _parse_enum(cleaned).items():
        enums[ename] = {"type": "string", "enum": evals}

    # Parse each message
    for msg_name in msg_names:
        body = _extract_message_body(cleaned, msg_name)
        if body is None:
            continue
        messages[msg_name] = _parse_message(msg_name, body, package_name, enums, messages)

    # Build final schemas dict
    for msg_name in msg_names:
        schemas[msg_name] = messages[msg_name]

    # Add standalone enums
    for ename, eschema in enums.items():
        if ename not in schemas:
            schemas[ename] = eschema

    openapi: dict[str, Any] = {
        "openapi": "3.1.0",
        "info": {
            "title": f"{package_name} gRPC schema",
            "version": "1.0.0",
        },
        "paths": {},
        "components": {
            "schemas": schemas,
        },
    }

    return openapi
