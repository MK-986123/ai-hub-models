# ---------------------------------------------------------------------
# Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
# SPDX-License-Identifier: BSD-3-Clause
# ---------------------------------------------------------------------

from .app import YoloV10DetectionApp as App
from .model import MODEL_ID
from .model import YoloV10Detector as Model

__all__ = ["MODEL_ID", "App", "Model"]
