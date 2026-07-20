# ---------------------------------------------------------------------
# Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
# SPDX-License-Identifier: BSD-3-Clause
# ---------------------------------------------------------------------
from qai_hub_models_cli import common as cli_common
from qai_hub_models_cli import fetch as cli_fetch

from qai_hub_models.utils.asset_loaders import ASSET_CONFIG


def test_cli_asset_urls_match_models() -> None:
    """CLI asset URL constants must stay in sync with models asset config."""
    assert cli_common.STORE_URL.rstrip("/") == ASSET_CONFIG.asset_url.rstrip("/")
    assert cli_common.ASSET_FOLDER.strip(
        "/"
    ) == ASSET_CONFIG.released_asset_folder.strip("/")
    assert ASSET_CONFIG.released_asset_filename == cli_fetch.ASSET_FILENAME
    assert (
        ASSET_CONFIG.released_asset_with_chipset_filename
        == cli_fetch.ASSET_CHIPSET_FILENAME
    )
