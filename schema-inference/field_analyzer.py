from collections import Counter
from typing import Any, Dict, List, Optional, Tuple


def _flatten_dict(d: dict, parent_key: str = "", separator: str = ".") -> Dict[str, Any]:
    items: Dict[str, Any] = {}
    for key, value in d.items():
        new_key = f"{parent_key}{separator}{key}" if parent_key else key
        if isinstance(value, dict):
            items.update(_flatten_dict(value, new_key, separator=separator))
        else:
            items[new_key] = value
    return items


def _get_field_types(value: Any) -> List[str]:
    if isinstance(value, bool):
        return ["boolean"]
    if isinstance(value, int):
        return ["integer"]
    if isinstance(value, float):
        return ["number"]
    if isinstance(value, str):
        return ["string"]
    if isinstance(value, list):
        return ["array"]
    if isinstance(value, dict):
        return ["object"]
    if value is None:
        return ["null"]
    return [type(value).__name__]


def analyze_field_presence(samples: List[dict]) -> Dict[str, dict]:
    field_stats: Dict[str, dict] = {}
    for sample in samples:
        flat = _flatten_dict(sample)
        seen_fields = set(flat.keys())
        for field_path, value in flat.items():
            if field_path not in field_stats:
                field_stats[field_path] = {
                    "present_count": 0,
                    "total_count": 0,
                    "types": Counter(),
                    "values": Counter(),
                }
            field_stats[field_path]["present_count"] += 1
            for t in _get_field_types(value):
                field_stats[field_path]["types"][t] += 1
            field_stats[field_path]["values"][str(value)] += 1
        for fp in field_stats:
            field_stats[fp]["total_count"] += 1 if fp in seen_fields else 0
    for fp in field_stats:
        field_stats[fp]["total_count"] = len(samples)
    return field_stats


def detect_optional_fields(
    field_stats: Dict[str, dict], threshold: float = 0.8
) -> List[str]:
    optional: List[str] = []
    for field_path, stats in field_stats.items():
        presence_ratio = stats["present_count"] / stats["total_count"] if stats["total_count"] > 0 else 0
        if presence_ratio < threshold:
            optional.append(field_path)
    return optional


def detect_enums(
    field_stats: Dict[str, dict],
    cardinality_threshold: int = 20,
    min_samples: int = 100,
) -> Dict[str, List[str]]:
    enums: Dict[str, List[str]] = {}
    for field_path, stats in field_stats.items():
        if stats["total_count"] >= min_samples:
            unique_values = [v for v in stats["values"].keys() if v != "None"]
            if 1 < len(unique_values) <= cardinality_threshold:
                enums[field_path] = unique_values
    return enums


def detect_probabilistic_types(
    field_stats: Dict[str, dict], confidence_threshold: float = 0.95
) -> Dict[str, Any]:
    probabilistic: Dict[str, Any] = {}
    for field_path, stats in field_stats.items():
        total = sum(stats["types"].values())
        if total == 0:
            continue
        dominant_type, dominant_count = stats["types"].most_common(1)[0]
        if dominant_count / total >= confidence_threshold:
            other_types = [t for t, c in stats["types"].items() if t != dominant_type and c > 0]
            if other_types:
                probabilistic[field_path] = {
                    "dominant_type": dominant_type,
                    "observed_types": list(stats["types"].keys()),
                }
    return probabilistic
