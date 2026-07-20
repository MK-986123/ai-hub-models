# ---------------------------------------------------------------------
# Copyright (c) 2026 Qualcomm Technologies, Inc. and/or its subsidiaries.
# SPDX-License-Identifier: BSD-3-Clause
# ---------------------------------------------------------------------
"""End-to-end evaluate pipeline for collection models."""

from __future__ import annotations

import argparse
import warnings
from typing import Any

import qai_hub as hub

from qai_hub_models import Precision, TargetRuntime
from qai_hub_models.utils.args import get_model_kwargs
from qai_hub_models.utils.base_app import CollectionAppEvaluateProtocol
from qai_hub_models.utils.base_collection_model import WorkbenchModelCollection
from qai_hub_models.utils.base_dataset import BaseDataset
from qai_hub_models.utils.evaluate.helpers import (
    _load_quant_cpu_onnx,
    evaluate_on_dataset,
)
from qai_hub_models.utils.export.dispatch import resolve_model, select_pipeline
from qai_hub_models.utils.inference import AsyncOnDeviceModel, compile_model_from_args
from qai_hub_models.utils.input_spec import InputSpec


def evaluate_model(
    model_id: str,
    model_cls: type[WorkbenchModelCollection],
    app_cls: type[CollectionAppEvaluateProtocol] | None,
    device: hub.Device,
    supports_quant_cpu: bool = False,
    target_runtime: TargetRuntime = TargetRuntime.TFLITE,
    precision: Precision = Precision.float,
    compile_options: str = "",
    profile_options: str = "",
    quantize_options: str = "",
    hub_model_id: str | None = None,
    dataset_cls: type[BaseDataset] | None = None,
    samples_per_job: int | None = None,
    num_samples: int | None = None,
    seed: int | None = None,
    use_dataset_cache: bool = False,
    skip_torch_accuracy: bool = False,
    skip_device_accuracy: bool = False,
    compute_quant_cpu_accuracy: bool = False,
    num_calibration_samples: int | None = None,
    **kwargs: Any,
) -> None:
    """
    Evaluate a collection model's accuracy on a dataset.

    Parameters
    ----------
    model_id
        Model folder name (e.g. ``mediapipe_hand``).
    model_cls
        Model class (e.g. ``qai_hub_models.models.mediapipe_hand.Model``).
    app_cls
        App class (e.g. ``qai_hub_models.models.mediapipe_hand.App``), or None.
    device
        Device object (e.g. ``hub.Device("Samsung Galaxy S25 (Family)")``).
    supports_quant_cpu
        If True and precision != float, adds a "quant cpu" executor via
        :func:`_load_quant_cpu_onnx` for the QDQ ONNX accuracy check.
    target_runtime
        Target runtime (e.g. ``TargetRuntime.TFLITE``).
    precision
        Precision (e.g. ``Precision.float``).
    compile_options
        Additional options to pass when submitting the compile job.
    profile_options
        Additional options to pass when submitting the profile job.
    quantize_options
        Additional options to pass when submitting the quantize job.
    hub_model_id
        A comma-separated list of compiled hub model ids (one per component).
    dataset_cls
        Dataset class to use for evaluation.
    samples_per_job
        Max size to be submitted in a single inference job.
    num_samples
        Number of samples to run. If set to -1, will run on full dataset.
    seed
        Random seed to use when shuffling the data. If not set, samples data deterministically.
    use_dataset_cache
        If set, will store hub dataset ids in a local file and re-use for subsequent evaluations on the same dataset.
    skip_torch_accuracy
        If flag is set, skips computing accuracy with the torch model.
    skip_device_accuracy
        If flag is set, skips computing accuracy on device.
    compute_quant_cpu_accuracy
        If flag is set, computes the accuracy of the quantized onnx model on the CPU.
    num_calibration_samples
        The number of calibration data samples to use for quantization.
    **kwargs
        Additional kwargs for model instantiation (from_pretrained).
    """
    warnings.filterwarnings("ignore")

    model_kwargs = get_model_kwargs(model_cls, kwargs)

    eval_dataset_classes = model_cls.get_eval_dataset_classes()
    if len(eval_dataset_classes) == 0:
        # PSNR fallback: no datasets, just run export with inference enabled
        print(
            "Model does not have evaluation dataset specified. Evaluating PSNR on a single sample."
        )
        export_model = select_pipeline(resolve_model(model_id))

        export_kwargs = {
            "device": device,
            "target_runtime": target_runtime,
            "skip_downloading": True,
            "skip_profiling": True,
            "compile_options": compile_options,
            "profile_options": profile_options,
            **model_kwargs,
        }
        if num_calibration_samples is not None:
            export_kwargs["num_calibration_samples"] = num_calibration_samples

        export_model(**export_kwargs)
        return

    # Verify App implements the protocol (runtime check only - mypy can't verify)
    if app_cls is None:
        raise TypeError(
            "app_cls is required for collection model evaluation with datasets"
        )
    if not (isinstance(app_cls, type) and hasattr(app_cls, "from_components")):
        raise TypeError(
            f"App must implement CollectionAppEvaluateProtocol when eval_datasets is specified, got {app_cls}"
        )

    if use_dataset_cache:
        raise ValueError("Collection models do not support use_dataset_cache.")

    collection_model = model_cls.from_pretrained(**model_kwargs)
    num_components = len(collection_model.component_names)

    input_spec: InputSpec | None = None
    torch_model_list = list(collection_model.components.values())
    model_executors: dict[str, CollectionAppEvaluateProtocol] = {}
    on_device_model_list: list[AsyncOnDeviceModel] = []

    if not skip_torch_accuracy:
        model_executors["torch"] = app_cls.from_components(torch_model_list)
        input_spec = torch_model_list[0].get_input_spec()

    # On-device or quant-cpu path
    if not skip_device_accuracy or (supports_quant_cpu and compute_quant_cpu_accuracy):
        if hub_model_id is not None:
            hub_model_id_list = hub_model_id.split(",")
            assert len(hub_model_id_list) == num_components, (
                f"Number of hub_model_ids ({len(hub_model_id_list)}) must equal "
                f"number of components ({num_components})"
            )
            compiled_model_list = [
                hub.get_model(model_id) for model_id in hub_model_id_list
            ]
        else:
            # Reconstruct a Namespace for compile_model_from_args
            args_for_compile = argparse.Namespace(
                device=device,
                chipset=None,
                target_runtime=target_runtime,
                precision=precision,
                compile_options=compile_options,
                profile_options=profile_options,
                quantize_options=quantize_options,
                num_calibration_samples=num_calibration_samples,
            )
            compiled_result = compile_model_from_args(
                model_id,
                args_for_compile,
                model_kwargs,
            )
            assert isinstance(compiled_result, list)
            compiled_model_list = compiled_result

        for compiled_model in compiled_model_list:
            if compiled_model.get_producer() is None:
                raise ValueError(
                    "Compiled models must be compiled with AI Hub Workbench; they cannot be uploaded manually."
                )
            on_device_model_list.append(
                AsyncOnDeviceModel(
                    model=compiled_model,
                    input_names=None,
                    device=device,
                    inference_options=profile_options,
                )
            )

        if not skip_device_accuracy:
            model_executors["on-device"] = app_cls.from_components(on_device_model_list)

        if (
            supports_quant_cpu
            and compute_quant_cpu_accuracy
            and precision != Precision.float
        ):
            quant_cpu_model_list = [
                _load_quant_cpu_onnx(model) for model in compiled_model_list
            ]
            model_executors["quant cpu"] = app_cls.from_components(quant_cpu_model_list)  # type: ignore[arg-type]

        input_spec = on_device_model_list[0].get_input_spec()

    if input_spec is None:
        raise ValueError("Cannot extract input spec.")

    if dataset_cls is None:
        raise ValueError("dataset_cls is required for evaluation on a dataset.")

    evaluate_on_dataset(
        evaluator_func=collection_model.get_evaluator,  # type: ignore[attr-defined]
        dataset_cls=dataset_cls,
        model_executors=model_executors,
        input_spec=input_spec,
        samples_per_job=samples_per_job,
        num_samples=num_samples,
        seed=seed,
        use_cache=use_dataset_cache,
    )
