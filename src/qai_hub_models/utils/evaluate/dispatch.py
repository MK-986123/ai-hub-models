# ---------------------------------------------------------------------
# Copyright (c) 2026 Qualcomm Technologies, Inc. and/or its subsidiaries.
# SPDX-License-Identifier: BSD-3-Clause
# ---------------------------------------------------------------------
"""Select and bind the evaluate pipeline for a resolved model."""

from __future__ import annotations

# mypy: disable-error-code="assignment"
import inspect
from collections.abc import Callable
from dataclasses import fields
from functools import partial
from typing import Any

from qai_hub_models.utils.base_collection_model import CollectionModel
from qai_hub_models.utils.export.dispatch import ResolvedModel


def select_evaluate_pipeline(resolved: ResolvedModel) -> Callable[..., Any]:
    """Return the pipeline ``evaluate_model`` for *resolved* with its context bound.

    The right ``evaluate_model`` is chosen from ``resolved.model_cls``. Only the
    ``ResolvedModel`` fields that appear in that pipeline's signature are
    bound as kwargs — pipelines that don't need e.g. ``source_dir`` or
    ``app_cls`` don't have to declare placeholder params for them.

    Parameters
    ----------
    resolved
        Model metadata from :func:`qai_hub_models.utils.export.dispatch.resolve_model`.

    Returns
    -------
    Callable[..., Any]
        Callable equivalent to the selected pipeline's ``evaluate_model`` with
        the applicable resolved fields pre-bound.
    """
    model_cls = resolved.model_cls
    if issubclass(model_cls, CollectionModel):
        from .collection_pipeline import evaluate_model

        pipeline_fn: Callable[..., Any] = evaluate_model
    else:
        from .pipeline import evaluate_model

        pipeline_fn = evaluate_model

    sig = inspect.signature(pipeline_fn)
    # ``model_id`` is excluded so positional callers like
    # ``evaluate_model(model_id, ...)`` don't collide with a bound kwarg.
    bind_kwargs = {
        f.name: getattr(resolved, f.name)
        for f in fields(resolved)
        if f.name in sig.parameters and f.name != "model_id"
    }
    bound = partial(pipeline_fn, **bind_kwargs)
    # Give the partial a signature/docstring the CLI parser can introspect:
    #   * __signature__ drops the bound kwargs so inspect.signature(bound)
    #     surfaces only user-facing params.
    #   * __doc__ preserves FunctionDoc parameter descriptions.
    bound.__signature__ = sig.replace(  # type: ignore[attr-defined]
        parameters=[p for n, p in sig.parameters.items() if n not in bind_kwargs]
    )
    bound.__doc__ = pipeline_fn.__doc__
    return bound
