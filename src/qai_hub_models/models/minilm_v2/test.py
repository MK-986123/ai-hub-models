# ---------------------------------------------------------------------
# Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
# SPDX-License-Identifier: BSD-3-Clause
# ---------------------------------------------------------------------
from __future__ import annotations

import torch

from qai_hub_models.models.minilm_v2.app import MiniLMApp
from qai_hub_models.models.minilm_v2.demo import main as demo_main
from qai_hub_models.models.minilm_v2.model import (
    EMBEDDING_DIM,
    AllMiniLML6V2,
)


def test_task() -> None:
    model = AllMiniLML6V2.from_pretrained()
    model.eval()
    app = MiniLMApp(model)

    embeddings = app.encode(
        [
            "The cat sat on the mat.",
            "A feline rested on the rug.",
            "Stock prices rose sharply today.",
        ]
    )

    assert embeddings.shape == (3, EMBEDDING_DIM)
    norms = embeddings.norm(dim=1)
    assert torch.allclose(norms, torch.ones(3), atol=1e-5)

    # Pin cosine similarities to golden values (verifies model produces
    # the expected sentence-transformers/all-MiniLM-L6-v2 embeddings).
    sim_similar = torch.nn.functional.cosine_similarity(
        embeddings[0:1], embeddings[1:2]
    ).item()
    sim_different_a = torch.nn.functional.cosine_similarity(
        embeddings[0:1], embeddings[2:3]
    ).item()
    sim_different_b = torch.nn.functional.cosine_similarity(
        embeddings[1:2], embeddings[2:3]
    ).item()
    assert abs(sim_similar - 0.5560) < 0.01, f"got {sim_similar:.4f}"
    assert abs(sim_different_a - 0.0573) < 0.01, f"got {sim_different_a:.4f}"
    assert abs(sim_different_b - 0.0756) < 0.01, f"got {sim_different_b:.4f}"


def test_demo() -> None:
    demo_main(is_test=True)
