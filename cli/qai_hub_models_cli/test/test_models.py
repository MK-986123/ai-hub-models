# ---------------------------------------------------------------------
# Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
# SPDX-License-Identifier: BSD-3-Clause
# ---------------------------------------------------------------------
from collections.abc import Generator
from unittest.mock import patch

import pytest

from qai_hub_models_cli.cli import main
from qai_hub_models_cli.proto.info_pb2 import ModelDomain
from qai_hub_models_cli.proto.manifest_pb2 import (
    ManifestModelEntry,
    ReleaseManifest,
)


def _fake_manifest() -> ReleaseManifest:
    return ReleaseManifest(
        version="0.99.0",
        models=[
            ManifestModelEntry(
                id="mobilenet_v2",
                display_name="MobileNet V2",
                domain=ModelDomain.MODEL_DOMAIN_COMPUTER_VISION,
            ),
            ManifestModelEntry(
                id="whisper_small",
                display_name="Whisper Small",
                domain=ModelDomain.MODEL_DOMAIN_AUDIO,
            ),
        ],
    )


@pytest.fixture(autouse=True)
def _skip_version_check() -> Generator[None]:
    with patch("qai_hub_models_cli.cli._check_version_match"):
        yield


@pytest.fixture
def manifest() -> Generator[None]:
    with patch("qai_hub_models_cli.cli.get_manifest", return_value=_fake_manifest()):
        yield


def test_models_table(manifest: None, capsys: pytest.CaptureFixture[str]) -> None:
    main(["models"])
    output = capsys.readouterr().out
    assert "MobileNet V2 (mobilenet_v2)" in output
    assert "Whisper Small (whisper_small)" in output
    assert "Computer Vision" in output
    assert "Audio" in output
    assert "Total: 2 models" in output


def test_models_quiet(manifest: None, capsys: pytest.CaptureFixture[str]) -> None:
    main(["models", "-q"])
    lines = capsys.readouterr().out.strip().splitlines()
    assert lines == ["mobilenet_v2", "whisper_small"]


def test_models_domain_filter(
    manifest: None, capsys: pytest.CaptureFixture[str]
) -> None:
    main(["models", "-d", "audio", "-q"])
    lines = capsys.readouterr().out.strip().splitlines()
    assert lines == ["whisper_small"]
