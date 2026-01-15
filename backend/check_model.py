#!/usr/bin/env python3
"""
ONNX Model Validation Script
Checks if model file is valid and compatible with ONNX Runtime
"""
import os
import sys

def check_model_with_onnx():
    """Check model using ONNX library"""
    try:
        import onnx
        model_path = 'models/face_embedding.onnx'
        
        if not os.path.exists(model_path):
            print(f"✗ Model file not found: {model_path}")
            return False
        
        print(f"Loading model with ONNX library...")
        model = onnx.load(model_path)
        
        print("✓ Model loaded successfully")
        print(f"  IR version: {model.ir_version}")
        print(f"  Producer: {model.producer_name} {model.producer_version}")
        
        print("\nValidating model structure...")
        onnx.checker.check_model(model)
        print("✓ Model structure is valid")
        
        # Check inputs/outputs
        print("\nModel inputs:")
        for inp in model.graph.input:
            print(f"  - {inp.name}: {[d.dim_value for d in inp.type.tensor_type.shape.dim]}")
        
        print("\nModel outputs:")
        for out in model.graph.output:
            print(f"  - {out.name}: {[d.dim_value for d in out.type.tensor_type.shape.dim]}")
        
        return True
    except ImportError:
        print("⚠ ONNX library not installed. Install with: pip install onnx")
        return False
    except Exception as e:
        print(f"✗ Model validation failed: {e}")
        import traceback
        traceback.print_exc()
        return False

def check_model_with_onnxruntime():
    """Check model using ONNX Runtime"""
    try:
        import onnxruntime as ort
        model_path = 'models/face_embedding.onnx'
        
        if not os.path.exists(model_path):
            print(f"✗ Model file not found: {model_path}")
            return False
        
        print(f"\nLoading model with ONNX Runtime...")
        print(f"ONNX Runtime version: {ort.__version__}")
        print(f"Available providers: {ort.get_available_providers()}")
        
        sess_options = ort.SessionOptions()
        sess_options.intra_op_num_threads = 1
        sess_options.inter_op_num_threads = 1
        
        session = ort.InferenceSession(
            model_path,
            sess_options=sess_options,
            providers=['CPUExecutionProvider']
        )
        
        print("✓ Model loaded successfully with ONNX Runtime")
        print(f"  Active providers: {session.get_providers()}")
        
        inputs = session.get_inputs()
        outputs = session.get_outputs()
        
        print("\nModel inputs:")
        for inp in inputs:
            print(f"  - {inp.name}: shape={inp.shape}, type={inp.type}")
        
        print("\nModel outputs:")
        for out in outputs:
            print(f"  - {out.name}: shape={out.shape}, type={out.type}")
        
        return True
    except ImportError:
        print("⚠ ONNX Runtime not installed")
        return False
    except Exception as e:
        print(f"✗ ONNX Runtime loading failed: {e}")
        import traceback
        traceback.print_exc()
        return False

def main():
    print("=" * 60)
    print("ONNX Model Validation")
    print("=" * 60)
    
    model_path = 'models/face_embedding.onnx'
    
    if not os.path.exists(model_path):
        print(f"✗ Model file not found: {model_path}")
        return False
    
    file_size = os.path.getsize(model_path) / (1024 * 1024)
    print(f"\nModel file: {os.path.abspath(model_path)}")
    print(f"Size: {file_size:.2f} MB")
    
    # Check file header
    with open(model_path, 'rb') as f:
        header = f.read(16)
        print(f"Header (hex): {header.hex()}")
    
    # Try ONNX library check
    onnx_ok = check_model_with_onnx()
    
    # Try ONNX Runtime check
    ort_ok = check_model_with_onnxruntime()
    
    print("\n" + "=" * 60)
    if onnx_ok and ort_ok:
        print("✓ Model is valid and compatible!")
        return True
    elif onnx_ok and not ort_ok:
        print("⚠ Model structure is valid but ONNX Runtime cannot load it")
        print("  This suggests a compatibility issue with ONNX Runtime version")
        return False
    else:
        print("✗ Model validation failed")
        return False

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)
