# ---------------------------------------------------------------------
# Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
# SPDX-License-Identifier: BSD-3-Clause
# ---------------------------------------------------------------------

from qai_hub_models.models.pi05.app import (
    Pi05App as App,
)
from qai_hub_models.models.pi05.model import (
    MODEL_ID,
)
from qai_hub_models.models.pi05.model import (
    Pi05CollectionQuantized as Model,
)

__all__ = ["MODEL_ID", "App", "Model"]
