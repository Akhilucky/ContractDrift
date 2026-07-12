import json
from copy import deepcopy
from typing import Any, Dict, List

from genson import SchemaBuilder

from field_analyzer import (
    analyze_field_presence,
    detect_enums,
    detect_optional_fields,
    detect_probabilistic_types,
)


def _json_schema_to_openapi31(schema: dict, optional_fields: List[str], enums: Dict[str, List[str]], probabilistic: Dict[str, Any]) -> dict:
    result = deepcopy(schema)
    result["openapi"] = "3.1.0"
    result["info"] = {"title": "Inferred Schema", "version": "1.0.0"}
    result["jsonSchemaDialect"] = "https://spec.openapis.org/oas/3.1/dialect/base"

    if "properties" in result:
        for prop_name, prop_schema in result["properties"].items():
            if prop_name in enums:
                prop_schema["x-enum-suggestion"] = enums[prop_name]
            if prop_name in probabilistic:
                info = probabilistic[prop_name]
                prop_schema["x-observed-types"] = info["observed_types"]
                if len(info["observed_types"]) > 1 and prop_schema.get("type") != "object":
                    type_hierarchy = {"integer": "number", "number": "number"}
                    if info["dominant_type"] in type_hierarchy:
                        prop_schema["type"] = type_hierarchy[info["dominant_type"]]

        required_fields = [f for f in result.get("required", []) if f not in optional_fields]
        if required_fields:
            result["required"] = required_fields
        else:
            result.pop("required", None)

    return result


def _apply_openapi_properties(schema: dict) -> dict:
    if "type" not in schema and "properties" in schema:
        schema["type"] = "object"
    for key, value in list(schema.items()):
        if isinstance(value, dict):
            _apply_openapi_properties(value)
        elif isinstance(value, list):
            for item in value:
                if isinstance(item, dict):
                    _apply_openapi_properties(item)
    if "items" in schema and isinstance(schema["items"], dict):
        _apply_openapi_properties(schema["items"])
    return schema


def infer(samples: List[str]) -> dict:
    parsed_samples: List[dict] = []
    for s in samples:
        try:
            parsed = json.loads(s)
            if isinstance(parsed, dict):
                parsed_samples.append(parsed)
        except (json.JSONDecodeError, ValueError):
            continue

    if not parsed_samples:
        return {"openapi": "3.1.0", "info": {"title": "Inferred Schema", "version": "1.0.0"}, "type": "object"}

    builder = SchemaBuilder()
    for sample in parsed_samples:
        builder.add_object(sample)
    base_schema = builder.to_schema()

    _apply_openapi_properties(base_schema)

    field_stats = analyze_field_presence(parsed_samples)
    optional_fields = detect_optional_fields(field_stats)
    enums = detect_enums(field_stats, cardinality_threshold=20, min_samples=100)
    probabilistic = detect_probabilistic_types(field_stats)

    result = _json_schema_to_openapi31(base_schema, optional_fields, enums, probabilistic)

    return result
