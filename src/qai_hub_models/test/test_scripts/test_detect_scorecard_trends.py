# ---------------------------------------------------------------------
# Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
# SPDX-License-Identifier: BSD-3-Clause
# ---------------------------------------------------------------------
from qai_hub_models.scorecard.results.performance_diff import SevereRegression
from qai_hub_models.scripts.detect_scorecard_trends import detect_trends


def _make_regression(
    model_id: str, device: str = "Galaxy S24", runtime: str = "tflite"
) -> SevereRegression:
    return SevereRegression.model_validate(
        {"Model ID": model_id, "Device": device, "Runtime": runtime}
    )


def test_detect_trends_basic_classification() -> None:
    """Test that regressions are correctly classified as sustained, new, or flaky."""
    # History: resnet regressed in 3/4 runs, mobilenet in 1/4
    historical = [
        [_make_regression("resnet50"), _make_regression("mobilenet_v2")],
        [_make_regression("resnet50")],
        [_make_regression("resnet50")],
        [],
    ]

    # Current: resnet still regressing, efficientnet is new
    current = [
        _make_regression("resnet50"),
        _make_regression("efficientnet_b0"),
    ]

    report = detect_trends(current, historical, threshold=2)

    assert len(report.sustained) == 1
    assert report.sustained[0].model_id == "resnet50"

    assert len(report.new) == 1
    assert report.new[0].model_id == "efficientnet_b0"

    assert len(report.flaky) == 0


def test_detect_trends_recovered() -> None:
    """Models that were sustained in history but not in current are 'recovered'."""
    historical = [
        [_make_regression("resnet50")],
        [_make_regression("resnet50")],
        [_make_regression("resnet50")],
    ]
    current: list[SevereRegression] = []  # resnet recovered

    report = detect_trends(current, historical, threshold=2)

    assert len(report.recovered) == 1
    assert "resnet50" in report.recovered[0].key
    assert report.sustained == []
    assert report.new == []


def test_detect_trends_flaky() -> None:
    """Models appearing in only 1 historical run are classified as flaky."""
    historical = [
        [_make_regression("mobilenet_v2")],
        [],
        [],
    ]
    current = [_make_regression("mobilenet_v2")]

    report = detect_trends(current, historical, threshold=2)

    assert len(report.flaky) == 1
    assert report.flaky[0].model_id == "mobilenet_v2"
    assert report.sustained == []
