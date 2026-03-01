#!/usr/bin/env python3
"""
Export QNN-optimized models from qai_hub_models for the DiffusionLab Android app.

This script exports all models needed by the app using Qualcomm AI Hub's
export pipeline. The exported models are ONNX files with embedded pre-compiled
QNN graphs, runnable via ONNX Runtime's QNN Execution Provider on Snapdragon NPUs.

Usage:
    # Export all models for Samsung Galaxy S25
    python export_models.py --device "Samsung Galaxy S25 (Family)"

    # Export only SD v1.5 pipeline
    python export_models.py --device "Samsung Galaxy S25 (Family)" --models sd_v1_5

    # Export for specific chipset family
    python export_models.py --device "Samsung Galaxy S24 (Family)" --models all

Prerequisites:
    - Qualcomm AI Hub account and API key
    - pip install qai_hub qai_hub_models
    - Set QAI_HUB_API_TOKEN environment variable

Output:
    Exported models are saved to ./export_assets/<model_name>/
    These files should be uploaded to your model hosting server.
"""

import argparse
import subprocess
import sys
from pathlib import Path

# Models used by DiffusionLab and their export configurations
MODELS = {
    "sd_v1_5": {
        "module": "qai_hub_models.models.stable_diffusion_v1_5.export",
        "runtime": "precompiled_qnn_onnx",
        "description": "Stable Diffusion v1.5 (text_encoder + unet + vae_decoder)",
    },
    "real_esrgan": {
        "module": "qai_hub_models.models.real_esrgan_x4plus.export",
        "runtime": "onnx",
        "description": "Real-ESRGAN x4plus upscaler (16.7M params, w8a8)",
    },
    "xlsr": {
        "module": "qai_hub_models.models.xlsr.export",
        "runtime": "onnx",
        "description": "XLSR lightweight upscaler (28K params)",
    },
    "lama": {
        "module": "qai_hub_models.models.lama_dilated.export",
        "runtime": "onnx",
        "description": "LaMa-Dilated inpainter (45.6M params)",
    },
    "aotgan": {
        "module": "qai_hub_models.models.aotgan.export",
        "runtime": "onnx",
        "description": "AOT-GAN fast inpainter (15.2M params)",
    },
    "fastsam": {
        "module": "qai_hub_models.models.fastsam_s.export",
        "runtime": "onnx",
        "description": "FastSAM-S segmenter for auto-masking (11.8M params)",
    },
    "controlnet": {
        "module": "qai_hub_models.models.controlnet_canny.export",
        "runtime": "precompiled_qnn_onnx",
        "description": "ControlNet Canny edge-guided generation",
    },
    "sd_v2_1": {
        "module": "qai_hub_models.models.stable_diffusion_v2_1.export",
        "runtime": "precompiled_qnn_onnx",
        "description": "Stable Diffusion v2.1 (higher quality alternative)",
    },
}


def export_model(model_key: str, device: str, output_dir: str):
    """Export a single model using the qai_hub_models export pipeline."""
    config = MODELS[model_key]
    print(f"\n{'='*60}")
    print(f"Exporting: {config['description']}")
    print(f"Module: {config['module']}")
    print(f"Runtime: {config['runtime']}")
    print(f"Device: {device}")
    print(f"{'='*60}\n")

    cmd = [
        sys.executable, "-m", config["module"],
        "--device", device,
        "--target-runtime", config["runtime"],
        "--output-dir", output_dir,
        "--skip-profiling",
        "--skip-inferencing",
    ]

    result = subprocess.run(cmd, capture_output=False)

    if result.returncode != 0:
        print(f"WARNING: Export of {model_key} failed with code {result.returncode}")
        return False

    print(f"Successfully exported {model_key}")
    return True


def main():
    parser = argparse.ArgumentParser(
        description="Export QNN-optimized models for DiffusionLab"
    )
    parser.add_argument(
        "--device",
        type=str,
        default="Samsung Galaxy S25 (Family)",
        help="Target device for export",
    )
    parser.add_argument(
        "--models",
        type=str,
        default="all",
        help="Comma-separated model keys to export, or 'all'",
    )
    parser.add_argument(
        "--output-dir",
        type=str,
        default="./export_assets",
        help="Output directory for exported models",
    )
    args = parser.parse_args()

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    if args.models == "all":
        model_keys = list(MODELS.keys())
    else:
        model_keys = [k.strip() for k in args.models.split(",")]
        for k in model_keys:
            if k not in MODELS:
                print(f"Unknown model: {k}")
                print(f"Available: {', '.join(MODELS.keys())}")
                sys.exit(1)

    print(f"Exporting {len(model_keys)} models to {output_dir}")
    print(f"Target device: {args.device}\n")

    results = {}
    for key in model_keys:
        success = export_model(key, args.device, str(output_dir))
        results[key] = success

    print(f"\n{'='*60}")
    print("Export Summary")
    print(f"{'='*60}")
    for key, success in results.items():
        status = "OK" if success else "FAILED"
        print(f"  {key:20s} [{status}]")

    failed = [k for k, v in results.items() if not v]
    if failed:
        print(f"\n{len(failed)} model(s) failed. Check output above for details.")
        sys.exit(1)
    else:
        print(f"\nAll {len(results)} models exported successfully!")
        print(f"Models saved to: {output_dir.absolute()}")


if __name__ == "__main__":
    main()
