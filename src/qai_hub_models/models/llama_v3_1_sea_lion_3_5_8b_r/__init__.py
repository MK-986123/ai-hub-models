# ---------------------------------------------------------------------
# Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
# SPDX-License-Identifier: BSD-3-Clause
# ---------------------------------------------------------------------

from qai_hub_models.models._shared.llama3.model import (
    LlamaPositionProcessor as PositionProcessor,
)
from qai_hub_models.models._shared.llm.app import ChatApp as App

from .model import MODEL_ID
from .model import Llama3_1_SEALION_3_5_8B_R as FP_Model
from .model import Llama3_1_SEALION_3_5_8B_R_AIMETOnnx as Model
from .model import Llama3_1_SEALION_3_5_8B_R_QNN as QNN_Model

__all__ = ["MODEL_ID", "App", "FP_Model", "Model", "PositionProcessor", "QNN_Model"]
