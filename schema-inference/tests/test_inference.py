import json

import pytest

from inference_engine import infer


SAMPLE_BASIC = [
    json.dumps({"name": "Alice", "age": 30, "active": True}),
    json.dumps({"name": "Bob", "age": 25, "active": False}),
    json.dumps({"name": "Charlie", "age": 35, "active": True}),
]

SAMPLE_NESTED = [
    json.dumps({"user": {"name": "Alice", "address": {"city": "NYC", "zip": "10001"}}}),
    json.dumps({"user": {"name": "Bob", "address": {"city": "LA", "zip": "90001"}}}),
    json.dumps({"user": {"name": "Charlie", "address": {"city": "SF", "zip": "94102"}}}),
]

SAMPLE_ARRAY = [
    json.dumps({"items": [{"id": 1, "val": "a"}, {"id": 2, "val": "b"}]}),
    json.dumps({"items": [{"id": 3, "val": "c"}, {"id": 4, "val": "d"}]}),
    json.dumps({"items": [{"id": 5, "val": "e"}]}),
]

SAMPLE_OPTIONAL = [
    json.dumps({"name": "A", "email": "a@x.com"}),
    json.dumps({"name": "B", "email": "b@x.com"}),
    json.dumps({"name": "C"}),
    json.dumps({"name": "D", "email": "d@x.com"}),
    json.dumps({"name": "E", "email": "e@x.com"}),
]

SAMPLE_ENUM = [
    json.dumps({"status": s}) for s in ["active", "inactive", "pending"] * 40
]

SAMPLE_PROBABILISTIC = [
    json.dumps({"val": 1, "label": "x"}) for _ in range(95)
] + [
    json.dumps({"val": 1.5, "label": "x"}) for _ in range(5)
]

SAMPLE_EMPTY: list = []

SAMPLE_BOOL_MIXED = [
    json.dumps({"flag": True, "val": 0}),
    json.dumps({"flag": False, "val": 1}),
    json.dumps({"flag": True, "val": 2}),
]

SAMPLE_NULLABLE = [
    json.dumps({"field": "hello"}),
    json.dumps({"field": "world"}),
    json.dumps({"field": None}),
]

SAMPLE_MULTITYPE_ARRAY = [
    json.dumps({"tags": ["a", "b"]}),
    json.dumps({"tags": ["c"]}),
]

SAMPLE_LARGE_ENUM = [
    json.dumps({"code": f"ERR_{i}"}) for i in range(50)
]


class TestSchemaInference:
    def test_basic_object_inference(self):
        schema = infer(SAMPLE_BASIC)
        assert schema.get("openapi") == "3.1.0"
        props = schema.get("properties", {})
        assert "name" in props
        assert props["name"]["type"] == "string"
        assert "age" in props
        assert props["age"]["type"] == "integer"

    def test_nested_object_inference(self):
        schema = infer(SAMPLE_NESTED)
        user_prop = schema.get("properties", {}).get("user", {})
        assert user_prop.get("type") == "object"
        address = user_prop.get("properties", {}).get("address", {})
        assert address.get("type") == "object"

    def test_array_field_inference(self):
        schema = infer(SAMPLE_ARRAY)
        items_prop = schema.get("properties", {}).get("items", {})
        assert items_prop.get("type") == "array"
        assert items_prop.get("items", {}).get("type") == "object"

    def test_optional_field_detection(self):
        schema = infer(SAMPLE_OPTIONAL)
        required = schema.get("required", [])
        assert "name" in required
        assert "email" not in required
        props = schema.get("properties", {})
        assert "email" in props

    def test_enum_inference(self):
        schema = infer(SAMPLE_ENUM)
        status_prop = schema.get("properties", {}).get("status", {})
        x_enum = status_prop.get("x-enum-suggestion")
        assert x_enum is not None
        assert sorted(x_enum) == sorted(["active", "inactive", "pending"])

    def test_probabilistic_type_inference(self):
        schema = infer(SAMPLE_PROBABILISTIC)
        val_prop = schema.get("properties", {}).get("val", {})
        x_types = val_prop.get("x-observed-types")
        assert x_types is not None
        assert "integer" in x_types
        assert "number" in x_types

    def test_empty_samples(self):
        schema = infer(SAMPLE_EMPTY)
        assert schema.get("openapi") == "3.1.0"

    def test_boolean_fields(self):
        schema = infer(SAMPLE_BOOL_MIXED)
        flag_prop = schema.get("properties", {}).get("flag", {})
        assert flag_prop.get("type") == "boolean"

    def test_nullable_fields(self):
        schema = infer(SAMPLE_NULLABLE)
        field_prop = schema.get("properties", {}).get("field", {})
        assert field_prop is not None

    def test_array_items_type(self):
        schema = infer(SAMPLE_MULTITYPE_ARRAY)
        tags_prop = schema.get("properties", {}).get("tags", {})
        assert tags_prop.get("type") == "array"

    def test_large_enum_not_inferred(self):
        schema = infer(SAMPLE_LARGE_ENUM)
        code_prop = schema.get("properties", {}).get("code", {})
        x_enum = code_prop.get("x-enum-suggestion")
        assert x_enum is None
