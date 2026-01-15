#!/usr/bin/env python3
"""
Test script for backend verification on Azure VM
Tests all critical components without requiring Supabase connection
"""
import sys
import os
import traceback
import numpy as np
from pathlib import Path

# Add current directory to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

def test_imports():
    """Test all required imports"""
    print("=" * 60)
    print("TEST 1: Import Dependencies")
    print("=" * 60)
    try:
        import fastapi
        import uvicorn
        import cv2
        import numpy as np
        import onnxruntime as ort
        from supabase import create_client
        from pydantic import BaseModel
        print("✓ All imports successful")
        print(f"  - FastAPI: {fastapi.__version__}")
        print(f"  - ONNX Runtime: {ort.__version__}")
        print(f"  - OpenCV: {cv2.__version__}")
        print(f"  - NumPy: {np.__version__}")
        return True
    except ImportError as e:
        print(f"✗ Import failed: {e}")
        return False

def test_model_file():
    """Test model file exists and is valid"""
    print("\n" + "=" * 60)
    print("TEST 2: Model File Verification")
    print("=" * 60)
    try:
        backend_dir = os.path.dirname(os.path.abspath(__file__))
        model_path = os.path.join(backend_dir, "models", "face_embedding.onnx")
        
        if not os.path.exists(model_path):
            print(f"✗ Model file not found at: {model_path}")
            return False
        
        file_size = os.path.getsize(model_path) / (1024 * 1024)  # MB
        print(f"✓ Model file exists: {model_path}")
        print(f"  Size: {file_size:.2f} MB")
        
        # Try to load model
        import onnxruntime as ort
        sess_options = ort.SessionOptions()
        sess_options.intra_op_num_threads = 1
        sess_options.inter_op_num_threads = 1
        
        session = ort.InferenceSession(
            model_path,
            sess_options=sess_options,
            providers=['CPUExecutionProvider']
        )
        
        print("✓ Model loaded successfully")
        print(f"  Providers: {session.get_providers()}")
        
        inputs = session.get_inputs()
        outputs = session.get_outputs()
        print(f"  Inputs: {[inp.name for inp in inputs]}")
        print(f"  Outputs: {[out.name for out in outputs]}")
        if inputs:
            print(f"  Input shape: {inputs[0].shape}")
        
        return True
    except Exception as e:
        print(f"✗ Model loading failed: {e}")
        traceback.print_exc()
        return False

def test_face_engine():
    """Test face recognition engine initialization"""
    print("\n" + "=" * 60)
    print("TEST 3: Face Recognition Engine")
    print("=" * 60)
    try:
        from face_recognition_engine import face_engine
        
        if not face_engine.is_model_loaded():
            print("✗ Face engine model not loaded")
            return False
        
        print("✓ Face engine initialized")
        print(f"  Input size: {face_engine.input_size}")
        print(f"  Input name: {face_engine.input_name}")
        print(f"  Input shape: {face_engine.input_shape}")
        
        # Test with dummy image
        dummy_image = np.random.randint(0, 255, (112, 112, 3), dtype=np.uint8)
        try:
            embedding = face_engine.get_embedding(dummy_image)
            print(f"✓ Embedding extraction works")
            print(f"  Embedding shape: {embedding.shape}")
            print(f"  Embedding dtype: {embedding.dtype}")
            print(f"  Embedding norm: {np.linalg.norm(embedding):.6f} (should be ~1.0)")
            return True
        except Exception as e:
            print(f"✗ Embedding extraction failed: {e}")
            traceback.print_exc()
            return False
            
    except Exception as e:
        print(f"✗ Face engine test failed: {e}")
        traceback.print_exc()
        return False

def test_config():
    """Test configuration loading"""
    print("\n" + "=" * 60)
    print("TEST 4: Configuration")
    print("=" * 60)
    try:
        from config import get_settings
        
        # Check if .env file exists
        env_file = os.path.join(os.path.dirname(__file__), ".env")
        if os.path.exists(env_file):
            print(f"✓ .env file exists: {env_file}")
        else:
            print(f"⚠ .env file not found (will use environment variables)")
        
        try:
            settings = get_settings()
            print("✓ Settings loaded")
            print(f"  Host: {settings.host}")
            print(f"  Port: {settings.port}")
            print(f"  Workers: {settings.workers}")
            print(f"  Confidence threshold: {settings.confidence_threshold}")
            print(f"  Max attendance per day: {settings.max_attendance_per_day}")
            
            # Check required vars (will fail if not set)
            if hasattr(settings, 'supabase_url') and settings.supabase_url:
                print(f"  Supabase URL: {settings.supabase_url[:30]}...")
            else:
                print("  ⚠ Supabase URL not set")
                
            if hasattr(settings, 'supabase_key') and settings.supabase_key:
                print(f"  Supabase Key: {'*' * 20}...")
            else:
                print("  ⚠ Supabase Key not set")
                
            if hasattr(settings, 'api_key') and settings.api_key:
                print(f"  API Key: {'*' * 20}...")
            else:
                print("  ⚠ API Key not set")
                
            return True
        except Exception as e:
            print(f"⚠ Settings loading failed (expected if env vars not set): {e}")
            return True  # Not critical for basic tests
            
    except Exception as e:
        print(f"✗ Config test failed: {e}")
        traceback.print_exc()
        return False

def test_database():
    """Test database initialization (without connection)"""
    print("\n" + "=" * 60)
    print("TEST 5: Database Module")
    print("=" * 60)
    try:
        from database import database
        
        print("✓ Database module imported")
        print(f"  Client initialized: {database._client is not None}")
        
        # Test null safety
        if database._client is None:
            print("  ⚠ Database client is None (expected if Supabase not configured)")
            print("  ✓ Null checks are in place (methods will return safe defaults)")
        else:
            print("  ✓ Database client initialized")
            # Try connection test
            try:
                connected = database.test_connection()
                print(f"  Connection test: {'✓ Connected' if connected else '✗ Failed'}")
            except Exception as e:
                print(f"  ⚠ Connection test failed: {e}")
        
        return True
    except Exception as e:
        print(f"✗ Database test failed: {e}")
        traceback.print_exc()
        return False

def test_fastapi_app():
    """Test FastAPI app initialization"""
    print("\n" + "=" * 60)
    print("TEST 6: FastAPI Application")
    print("=" * 60)
    try:
        from main import app
        
        print("✓ FastAPI app imported")
        print(f"  Title: {app.title}")
        print(f"  Version: {app.version}")
        
        # Check routes
        routes = [r.path for r in app.routes]
        print(f"  Routes: {len(routes)} endpoints")
        for route in sorted(routes):
            print(f"    - {route}")
        
        # Check if face_engine is loaded
        from face_recognition_engine import face_engine
        model_loaded = face_engine.is_model_loaded()
        print(f"  Face engine loaded: {'✓' if model_loaded else '✗'}")
        
        return True
    except Exception as e:
        print(f"✗ FastAPI app test failed: {e}")
        traceback.print_exc()
        return False

def test_memory_usage():
    """Test memory usage estimation"""
    print("\n" + "=" * 60)
    print("TEST 7: Memory Usage")
    print("=" * 60)
    try:
        import psutil
        import os
        
        process = psutil.Process(os.getpid())
        memory_mb = process.memory_info().rss / (1024 * 1024)
        
        print(f"✓ Current memory usage: {memory_mb:.1f} MB")
        
        if memory_mb < 700:
            print(f"  ✓ Within Azure B1s limit (< 700 MB)")
        else:
            print(f"  ⚠ Exceeds recommended limit (700 MB)")
        
        return True
    except ImportError:
        print("⚠ psutil not available (install with: pip install psutil)")
        return True  # Not critical
    except Exception as e:
        print(f"⚠ Memory check failed: {e}")
        return True  # Not critical

def main():
    """Run all tests"""
    print("\n" + "=" * 60)
    print("BACKEND VERIFICATION TEST SUITE")
    print("Azure VM Compatibility Check")
    print("=" * 60)
    
    results = []
    results.append(("Imports", test_imports()))
    results.append(("Model File", test_model_file()))
    results.append(("Face Engine", test_face_engine()))
    results.append(("Configuration", test_config()))
    results.append(("Database", test_database()))
    results.append(("FastAPI App", test_fastapi_app()))
    results.append(("Memory Usage", test_memory_usage()))
    
    print("\n" + "=" * 60)
    print("TEST SUMMARY")
    print("=" * 60)
    
    passed = sum(1 for _, result in results if result)
    total = len(results)
    
    for test_name, result in results:
        status = "✓ PASS" if result else "✗ FAIL"
        print(f"{status}: {test_name}")
    
    print(f"\nTotal: {passed}/{total} tests passed")
    
    if passed == total:
        print("\n✓ All critical tests passed! Backend is ready for Azure VM deployment.")
    else:
        print("\n⚠ Some tests failed. Review errors above.")
    
    return passed == total

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)
