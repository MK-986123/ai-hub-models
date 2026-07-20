# ---------------------------------------------------------------------
# Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
# SPDX-License-Identifier: BSD-3-Clause
# ---------------------------------------------------------------------
"""
Generic test helpers usable from any model unit test.

Does not depend on scorecard.
"""

from __future__ import annotations

import numpy as np

__all__ = [
    "assert_most_close",
    "assert_most_same",
]


def assert_most_same(arr1: np.ndarray, arr2: np.ndarray, diff_tol: float) -> None:
    """
    Checks whether most values in the two numpy arrays are the same.

    Particularly for image models, slight differences in the PIL/cv2 envs
    may cause image <-> tensor conversion to be slightly different.

    Instead of using np.assert_allclose, this may be a better way to test image outputs.

    Parameters
    ----------
    arr1
        First input image array.
    arr2
        Second input image array.
    diff_tol
        Float in range [0,1] representing percentage of values
        that can be different while still having the assertion pass.

    Raises
    ------
    AssertionError
        If input arrays are different size, or too many values are different.
    """
    different_values = arr1 != arr2
    assert np.mean(different_values) <= diff_tol, (
        f"More than {diff_tol * 100}% of values were different."
    )


def assert_most_close(
    arr1: np.ndarray,
    arr2: np.ndarray,
    diff_tol: float,
    rtol: float = 0.0,
    atol: float = 0.0,
) -> None:
    """
    Checks whether most values in the two numpy arrays are close.

    Particularly for image models, slight differences in the PIL/cv2 envs
    may cause image <-> tensor conversion to be slightly different.

    Instead of using np.assert_allclose, this may be a better way to test image outputs.

    Parameters
    ----------
    arr1
        First input image array.
    arr2
        Second input image array.
    diff_tol
        Float in range [0,1] representing percentage of values
        that can be different while still having the assertion pass.
    rtol
        Two values a, b are considered close if the following expresion is true
        `absolute(a - b) <= (atol + rtol * absolute(b))`
        Documentation copied from `np.isclose`.
        Default is 0.0.
    atol
        See rtol documentation.
        Default is 0.0.

    Raises
    ------
    AssertionError
        If input arrays are different size, or too many values are not close.
    """
    not_close_values = ~np.isclose(arr1, arr2, atol=atol, rtol=rtol)
    value_percentage = np.mean(not_close_values)
    assert value_percentage <= diff_tol, (
        f"{value_percentage * 100}% of values were not close (expected: < {diff_tol * 100}% of values)."
    )
