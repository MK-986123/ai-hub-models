# ---------------------------------------------------------------------
# Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
# SPDX-License-Identifier: BSD-3-Clause
# ---------------------------------------------------------------------

from qai_hub_models.models.gear_guard_net.app import (
    GearGuardNetApp as App,
)

from .model import MODEL_ID
from .model import GearGuardNet as Model

__all__ = ["MODEL_ID", "App", "Model"]
