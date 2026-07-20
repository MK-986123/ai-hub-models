# ---------------------------------------------------------------------
# Copyright (c) 2024-2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
# SPDX-License-Identifier: BSD-3-Clause
# ---------------------------------------------------------------------

from qai_hub_models.models.yolov5.app import YoloV5DetectionApp as App

from .model import MODEL_ID
from .model import YoloV5 as Model

__all__ = ["MODEL_ID", "App", "Model"]
