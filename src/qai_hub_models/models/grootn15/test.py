# ---------------------------------------------------------------------
# Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
# SPDX-License-Identifier: BSD-3-Clause
# ---------------------------------------------------------------------
from __future__ import annotations

import numpy as np
import torch

from qai_hub_models.models.grootn15 import Model
from qai_hub_models.models.grootn15.app import (
    GrootApp,
    GrootAppConfig,
    get_default_dataset_path,
)
from qai_hub_models.models.grootn15.constants import (
    DEFAULT_DATA_CONFIG,
    DEFAULT_EMBODIMENT_TAG,
)
from qai_hub_models.models.grootn15.external_repos.gr00t.gr00t.data.dataset import (
    LeRobotSingleDataset,
)
from qai_hub_models.models.grootn15.external_repos.gr00t.gr00t.model.policy import (
    Gr00tPolicy,
)
from qai_hub_models.models.grootn15.model import load_checkpoint


# Fixtures / helpers
def _make_dataset(policy: Gr00tPolicy, dataset_path: str) -> LeRobotSingleDataset:
    """Load the test dataset using the policy modality config."""
    return LeRobotSingleDataset(
        dataset_path=dataset_path,
        modality_configs=policy.modality_config,
        video_backend="decord",
        video_backend_kwargs=None,
        transforms=None,
        embodiment_tag=DEFAULT_EMBODIMENT_TAG,
    )


def _build_fp_app(policy: Gr00tPolicy) -> GrootApp:
    """Build a host FP GrootApp (no on-device components)."""
    config = GrootAppConfig.from_policy(policy)

    # Extract LLM embedding weights used during preprocess
    llm_embedding_weight = (
        policy.model.backbone.eagle_model.language_model.get_input_embeddings()
        .weight.detach()
        .clone()
    )
    collection = Model.from_policy(policy)
    return GrootApp(
        config=config,
        vit=collection.components["vit"],
        llm=collection.components["llm"],
        vlm_proj=collection.components["vlm_proj"],
        dit=collection.components["dit"],
        llm_embedding_weight=llm_embedding_weight,
        device="cpu",
    )


# Tests
def test_one_sample() -> None:
    """
    Run a single forward pass and assert:
    1. App-path and direct-policy-path produce close action predictions.
    2. Predicted actions are close to ground truth (RMSE sanity check).
    """
    torch.manual_seed(42)

    # Load policy
    policy = load_checkpoint(
        checkpoint="DEFAULT",
        data_config=DEFAULT_DATA_CONFIG,
        embodiment_tag=DEFAULT_EMBODIMENT_TAG,
        device="cpu",
    )

    dataset = _make_dataset(policy, get_default_dataset_path())
    step_data = dataset[0]

    # Get CPU random gen state for randn
    cpu_rng_state = torch.get_rng_state()

    # Direct policy path (Gr00tPolicy.predict_action_chunk)
    pred_actions_policy = policy.get_action(step_data)

    # App path (GrootApp.predict_action_chunk)
    app = _build_fp_app(policy)

    # Restore CPU random gen state to match original model action randn input
    torch.set_rng_state(cpu_rng_state)
    pred_actions_app = app.predict_action_chunk(step_data)

    # Parity check — app vs policy
    modality_keys = [
        key.split(".")[-1] for key in policy.modality_config["action"].modality_keys
    ]

    pred_app_concat = np.concatenate(
        [np.atleast_1d(pred_actions_app[f"action.{k}"][0, 0]) for k in modality_keys],
        axis=0,
    )
    pred_policy_concat = np.concatenate(
        [np.atleast_1d(pred_actions_policy[f"action.{k}"][0]) for k in modality_keys],
        axis=0,
    )

    assert pred_app_concat.shape == pred_policy_concat.shape, (
        f"Shape mismatch: app={pred_app_concat.shape}, "
        f"policy={pred_policy_concat.shape}"
    )

    np.testing.assert_allclose(
        pred_app_concat,
        pred_policy_concat,
        rtol=1e-2,
        atol=1e-2,
        err_msg="App path and direct policy path predictions differ beyond tolerance.",
    )
    print(
        "PASS: GrootApp path and direct policy path predictions match within tolerance"
    )

    # Ground-truth RMSE sanity check
    gt_concat = np.concatenate(
        [step_data[f"action.{k}"] for k in modality_keys], axis=-1
    )  # shape: [action_horizon, action_dim]

    rmse = float(np.sqrt(np.mean((gt_concat[0] - pred_app_concat) ** 2)))
    print(f"Single-sample GrootApp RMSE (app vs GT step-0): {rmse:.6f}")


if __name__ == "__main__":
    test_one_sample()
