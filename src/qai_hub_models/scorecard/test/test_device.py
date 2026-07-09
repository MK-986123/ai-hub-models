# ---------------------------------------------------------------------
# Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
# SPDX-License-Identifier: BSD-3-Clause
# ---------------------------------------------------------------------
from qai_hub_models.scorecard.device import ScorecardDevice, cs_universal


def test_exactly_one_default() -> None:
    num_defaults = 0
    for device in ScorecardDevice._registry.values():
        if device.is_default:
            num_defaults += 1
    assert num_defaults == 1, (
        f"Must be exactly one default device, found {num_defaults}"
    )


def test_get_universal_reference_returns_default() -> None:
    """
    Test that querying by cs_universal's reference device name returns
    the default device, not the universal device itself.
    """
    result = ScorecardDevice.get(cs_universal.reference_device_name)
    assert result.is_default, (
        f"Expected default device, got {result.name} (is_default={result.is_default})"
    )
    assert result.name != "universal", (
        f"Expected default device, not universal. Got {result.name}"
    )
