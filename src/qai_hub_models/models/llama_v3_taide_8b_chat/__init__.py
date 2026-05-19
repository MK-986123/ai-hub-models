# ---------------------------------------------------------------------
# Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
# SPDX-License-Identifier: BSD-3-Clause
# ---------------------------------------------------------------------
from qai_hub_models.models._shared.llama3.model import (
    LlamaPositionProcessor as PositionProcessor,
)
from qai_hub_models.models._shared.llm.app import ChatApp as App

from .model import MODEL_ID
from .model import Llama3_TAIDE as FP_Model
from .model import Llama3_TAIDE_AIMETOnnx as Model
from .model import Llama3_TAIDE_QNN as QNN_Model

__all__ = ["MODEL_ID", "App", "FP_Model", "Model", "PositionProcessor", "QNN_Model"]
