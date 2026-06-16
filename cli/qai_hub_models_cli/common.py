# ---------------------------------------------------------------------
# Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
# SPDX-License-Identifier: BSD-3-Clause
# ---------------------------------------------------------------------
from __future__ import annotations

from pathlib import Path

import platformdirs
from packaging.version import Version

CACHE_DIR = Path(platformdirs.user_cache_dir("qai_hub_models")) / "cli"
STORE_URL = "https://qaihub-public-assets.s3.us-west-2.amazonaws.com"
ASSET_FOLDER = "qai-hub-models/models/{model_id}/releases/v{version}"

GITHUB_REPO_URL = "https://github.com/qualcomm/ai-hub-models"
AIHUB_MODELS_URL = "https://aihub.qualcomm.com/models"


def model_repo_url(model_id: str, version: Version) -> str:
    """URL to the model's source code on GitHub for the given release version."""
    ref = f"v{version}" if not version.is_devrelease else "main"
    return f"{GITHUB_REPO_URL}/tree/{ref}/src/qai_hub_models/models/{model_id}"
