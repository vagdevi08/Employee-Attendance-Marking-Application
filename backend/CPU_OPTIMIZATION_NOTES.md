# CPU Optimization Notes

## Face Recognition Engine - Memory & CPU Optimizations

This document explains the optimizations implemented for running on Azure VM Standard B1s (1 vCPU, 1 GB RAM, NO GPU).

### Key Optimizations

#### 1. **CPU-Only Execution**
- ✅ Explicitly uses `CPUExecutionProvider` only
- ✅ No GPU, CUDA, or TensorRT dependencies
- ✅ Provider options configured for low memory usage

#### 2. **Single-Threaded Inference**
- ✅ `intra_op_num_threads = 1` - prevents thread pool overhead on single vCPU
- ✅ `inter_op_num_threads = 1` - no inter-operator parallelism
- ✅ Reduces context switching overhead

#### 3. **Memory-Optimized Graph Settings**
- ✅ `graph_optimization_level = ORT_ENABLE_BASIC` - avoids memory-heavy optimizations
- ✅ `enable_mem_pattern = False` - prevents pre-allocation of large buffers
- ✅ `enable_mem_arena = False` - uses less memory (slightly slower but acceptable)

#### 4. **Minimal Dependencies**
- ✅ Removed `scikit-learn` - using pure NumPy for cosine similarity
- ✅ Saves ~50-100 MB of memory

#### 5. **Efficient Preprocessing**
- ✅ In-place operations where possible
- ✅ Minimal NumPy copies
- ✅ Fixed normalization constants (no dynamic computation)

#### 6. **Model Loading**
- ✅ Singleton pattern - model loaded once at startup
- ✅ Reused inference session for all requests
- ✅ No model reloading per request

### Expected Memory Usage

| Component | Memory Usage |
|-----------|--------------|
| ONNX Model (MobileFaceNet) | ~4-20 MB |
| ONNXRuntime (CPU) | ~50-100 MB |
| Python Runtime | ~30-50 MB |
| FastAPI Server | ~20-30 MB |
| Per-request buffers | ~5-10 MB |
| **Total (idle)** | **~150-250 MB** |
| **Total (under load)** | **~200-300 MB** |

✅ **Well under 1 GB limit** - leaves ~700 MB headroom for OS and other processes

### Usage

```python
from face_recognition_engine import face_engine, cosine_similarity

# Model is automatically loaded at import time
# Ensure models/mobilefacenet.onnx exists

# Get embedding from RGB face image
face_image = ...  # NumPy array (H, W, 3), RGB format
embedding = face_engine.get_embedding(face_image)

# Compare two embeddings
similarity = face_engine.compare_embeddings(embedding1, embedding2)
# Returns [0, 1] where 1.0 = identical, 0.5 = neutral, 0.0 = opposite

# Or use helper function directly
similarity = cosine_similarity(embedding1, embedding2)
# Returns [-1, 1] where 1.0 = identical, -1.0 = opposite
```

### Model Requirements

- **Format**: ONNX
- **Input size**: 112×112 pixels (typical for MobileFaceNet/ArcFace)
- **Input format**: RGB, normalized to [-1, 1]
- **Output**: Face embedding vector (typically 128-512 dimensions)

### Performance Characteristics

- **Inference time**: ~50-150 ms per face (on 1 vCPU)
- **Throughput**: ~7-20 faces/second (single-threaded)
- **Memory per request**: ~5-10 MB (temporary buffers)

### Monitoring

To verify CPU-only execution:
```python
print(face_engine.session.get_providers())
# Should output: ['CPUExecutionProvider']
```

To check model status:
```python
print(face_engine.is_model_loaded())
# Should output: True
```
