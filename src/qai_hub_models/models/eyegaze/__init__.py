# ---------------------------------------------------------------------
# Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
# SPDX-License-Identifier: BSD-3-Clause
# ---------------------------------------------------------------------

from .app import EyeGazeApp as App
from .model import MODEL_ID
from .model import EyeGaze as Model

__all__ = ["MODEL_ID", "App", "Model"]
