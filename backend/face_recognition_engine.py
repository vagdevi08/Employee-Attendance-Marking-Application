"""
CPU-Optimized Face Recognition Engine for Low-Resource Environments

Designed for Azure VM Standard B1s (1 vCPU, 1 GB RAM, NO GPU)
- Uses ONNXRuntime with CPUExecutionProvider only
- Single-threaded inference (intra_op_num_threads=1)
- Minimal graph optimizations to reduce memory footprint
- Avoids unnecessary NumPy copies
- Processes single RGB images only (no video streams)
- Stores embeddings only (no raw face images)

Expected RAM usage: ~150-300 MB (model + runtime)
Model: MobileFaceNet or ArcFace (112×112 input, ONNX format)
"""
import cv2
import numpy as np
import onnxruntime as ort
from typing import Optional
import logging
import os

logger = logging.getLogger(__name__)


def cosine_similarity(embedding1: np.ndarray, embedding2: np.ndarray) -> float:
    """
    Compute cosine similarity between two embedding vectors.
    
    Pure NumPy implementation - avoids sklearn dependency for lower memory.
    Cosine similarity = dot(a,b) / (norm(a) * norm(b))
    Returns value in range [-1, 1] where 1 = identical, -1 = opposite.
    
    Args:
        embedding1: First embedding vector (L2-normalized)
        embedding2: Second embedding vector (L2-normalized)
        
    Returns:
        Cosine similarity score [-1, 1]
    """
    # Since embeddings are L2-normalized, cosine similarity = dot product
    # This avoids computing norms repeatedly
    similarity = np.dot(embedding1, embedding2)
    return float(np.clip(similarity, -1.0, 1.0))


class FaceRecognitionEngine:
    """
    CPU-optimized face recognition engine for low-resource environments.
    
    Key optimizations:
    - Model loaded once at startup, reused for all requests
    - Single-threaded inference (prevents thread overhead)
    - Minimal graph optimizations (reduces memory)
    - In-place operations where possible (reduces allocations)
    - Fixed-size embeddings (predictable memory usage)
    
    Memory profile:
    - Model weights: ~4-20 MB (MobileFaceNet/ArcFace)
    - Runtime overhead: ~50-100 MB (ONNXRuntime)
    - Per-request: ~5-10 MB (temporary buffers)
    - Total: ~150-300 MB (well under 1 GB limit)
    """
    
    _instance = None
    _initialized = False
    
    def __new__(cls):
        """Singleton pattern - ensures only one model instance in memory"""
        if cls._instance is None:
            cls._instance = super(FaceRecognitionEngine, cls).__new__(cls)
        return cls._instance
    
    def __init__(self):
        """Initialize model only once - called at application startup"""
        if not FaceRecognitionEngine._initialized:
            self.session = None
            self.input_name = None
            self.input_shape = None
            self.input_size = (112, 112)  # Default for MobileFaceNet/ArcFace
            self._load_model()
            FaceRecognitionEngine._initialized = True
    
    def _load_model(self):
        """
        Load ONNX model with CPU-only optimizations.
        
        Critical settings for low-memory environment:
        - CPUExecutionProvider only (explicitly no GPU)
        - intra_op_num_threads=1 (prevents thread pool overhead)
        - graph_optimization_level=ORT_ENABLE_BASIC (avoids memory-heavy optimizations)
        - enable_mem_pattern=False (prevents memory pattern optimizations that use more RAM)
        """
        logger.info("Loading face recognition model (CPU-optimized)...")

        # Resolve model path relative to this file and convert to absolute path
        # NOTE: We use an absolute path so that uvicorn/gunicorn workers and
        # different working directories cannot break model loading.
        backend_dir = os.path.dirname(os.path.abspath(__file__))
        model_path = os.path.abspath(
            os.path.join(backend_dir, "models", "face_embedding.onnx")
        )

        # Fail fast with a clear error if the ONNX model is missing
        if not os.path.exists(model_path):
            msg = (
                f"ONNX face embedding model not found at: {model_path}. "
                "Expected CPU-only model file 'face_embedding.onnx' in the backend/models/ directory."
            )
            logger.error(msg)
            # Raising here prevents the application from starting in a bad state
            raise FileNotFoundError(msg)
        
        try:
            # Configure ONNX Runtime session options for low-memory CPU execution
            sess_options = ort.SessionOptions()
            
            # CRITICAL: Single thread to avoid thread pool overhead
            # Multi-threading on 1 vCPU causes context switching overhead
            sess_options.intra_op_num_threads = 1
            sess_options.inter_op_num_threads = 1
            
            # CRITICAL: Use BASIC optimizations only - advanced optimizations increase memory
            # ORT_ENABLE_ALL can use 2-3x more memory for graph fusion optimizations
            sess_options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_BASIC
            
            # CRITICAL: Disable memory pattern optimizations
            # These can pre-allocate large buffers that increase peak memory usage
            sess_options.enable_mem_pattern = False
            
            # CRITICAL: Disable memory arena - uses less memory but slightly slower
            # For 1GB RAM, we prioritize memory over speed
            # Note: Attribute name is 'enable_cpu_mem_arena' in ONNX Runtime 1.22+
            if hasattr(sess_options, 'enable_cpu_mem_arena'):
                sess_options.enable_cpu_mem_arena = False
            elif hasattr(sess_options, 'enable_mem_arena'):
                sess_options.enable_mem_arena = False
            
            # CRITICAL: Explicitly use CPU only - no GPU, CUDA, TensorRT.
            # Forcing CPUExecutionProvider keeps the deployment compatible with
            # low-cost CPU-only instances (e.g. Azure B1s) and avoids accidental
            # GPU/CUDA dependencies that could break production.
            providers = ['CPUExecutionProvider']
            
            # Configure CPU provider options for low memory
            provider_options = [{
                'arena_extend_strategy': 'kSameAsRequested',  # Don't pre-allocate large arenas
            }]
            
            # Load model with CPU-only execution
            self.session = ort.InferenceSession(
                model_path,
                sess_options=sess_options,
                providers=providers,
                provider_options=provider_options
            )
            
            # Get model input metadata
            input_meta = self.session.get_inputs()[0]
            self.input_name = input_meta.name
            self.input_shape = input_meta.shape
            
            # Extract input size from shape (typically [batch, channels, height, width])
            if len(self.input_shape) >= 4:
                # Dynamic batch size handling
                h = self.input_shape[2] if self.input_shape[2] > 0 else 112
                w = self.input_shape[3] if self.input_shape[3] > 0 else 112
                self.input_size = (w, h)
            elif len(self.input_shape) >= 2:
                # Handle flattened input (uncommon)
                logger.warning(f"Unexpected input shape: {self.input_shape}")
            
            logger.info(f"✓ Model loaded successfully")
            logger.info(f"  Input name: {self.input_name}")
            logger.info(f"  Input shape: {self.input_shape}")
            logger.info(f"  Input size: {self.input_size}")
            logger.info(f"  Providers: {self.session.get_providers()}")
            
            # Verify CPU-only execution
            if 'CPUExecutionProvider' not in self.session.get_providers():
                logger.warning("⚠ CPUExecutionProvider not in active providers!")
            
        except Exception as e:
            logger.error(f"Failed to load ONNX model: {e}")
            logger.error("Face recognition model initialization failed; aborting startup.")
            # Propagate as a runtime error so the process fails fast instead of
            # running in a degraded mode without a working face engine.
            raise RuntimeError("Failed to initialize face recognition ONNX model") from e
    
    def _preprocess_image(self, face_image: np.ndarray) -> np.ndarray:
        """
        Preprocess RGB face image for model input.
        
        Optimizations:
        - Minimal operations to reduce memory allocations
        - In-place operations where possible
        - Fixed normalization constants (no dynamic computation)
        
        Args:
            face_image: RGB face image (H, W, 3) as NumPy array
            
        Returns:
            Preprocessed image (1, 3, H, W) ready for ONNX inference
        """
        # Ensure input is RGB (if BGR, convert)
        if len(face_image.shape) == 3 and face_image.shape[2] == 3:
            # Check if BGR (OpenCV default) - convert to RGB
            # Most face models expect RGB input
            face_image = cv2.cvtColor(face_image, cv2.COLOR_BGR2RGB)
        
        # Resize to model input size
        # Using INTER_LINEAR (default) - fastest, good enough for face recognition
        resized = cv2.resize(face_image, self.input_size, interpolation=cv2.INTER_LINEAR)
        
        # Normalize to [0, 1] and convert to float32 in one step
        # Using astype with division is more memory-efficient than separate operations
        normalized = resized.astype(np.float32) * (1.0 / 255.0)
        
        # Standardize: (x - 0.5) / 0.5 = 2*x - 1
        # This maps [0, 1] to [-1, 1] which is common for face models
        # Using in-place operation to avoid extra allocation
        normalized = normalized * 2.0 - 1.0
        
        # Transpose from HWC to CHW format (channels first)
        # Most ONNX models expect NCHW: (batch, channels, height, width)
        transposed = np.transpose(normalized, (2, 0, 1))
        
        # Add batch dimension: (channels, height, width) -> (1, channels, height, width)
        batched = np.expand_dims(transposed, axis=0)
        
        return batched
    
    def get_embedding(self, face_image: np.ndarray) -> np.ndarray:
        """
        Extract face embedding from RGB face image.
        
        This is the main method for face recognition pipeline:
        1. Preprocess image (resize, normalize)
        2. Run ONNX inference (CPU)
        3. L2-normalize embedding
        4. Return fixed-size embedding vector
        
        Args:
            face_image: RGB face image as NumPy array (H, W, 3)
                       Can also accept BGR (will be converted)
            
        Returns:
            L2-normalized embedding vector (1D NumPy array, dtype=float32)
            Typical size: 128-512 dimensions depending on model
            
        Raises:
            RuntimeError: If model is not loaded or inference fails
        """
        if self.session is None:
            raise RuntimeError("Face recognition model not loaded. Cannot extract embedding.")
        
        try:
            # Preprocess image
            input_data = self._preprocess_image(face_image)
            
            # Run ONNX inference (CPU-only)
            # Using None for output names uses all outputs
            outputs = self.session.run(None, {self.input_name: input_data})
            
            # Extract embedding from first output
            # Flatten to 1D array (handles any output shape)
            embedding = outputs[0].flatten().astype(np.float32)
            
            # L2-normalize embedding
            # Normalization is critical for cosine similarity comparison
            # Using in-place operation where possible
            norm = np.linalg.norm(embedding)
            if norm > 1e-8:  # Avoid division by zero
                embedding = embedding / norm
            else:
                logger.warning("Embedding norm too small, using zero vector")
                embedding = np.zeros_like(embedding)
            
            return embedding
            
        except Exception as e:
            logger.error(f"Failed to extract embedding: {e}")
            raise RuntimeError(f"Embedding extraction failed: {str(e)}")
    
    def compare_embeddings(
        self,
        embedding1: np.ndarray,
        embedding2: np.ndarray
    ) -> float:
        """
        Compare two embeddings using cosine similarity.
        
        Since embeddings are L2-normalized, cosine similarity = dot product.
        Returns similarity score in range [0, 1] for easier interpretation.
        
        Args:
            embedding1: First L2-normalized embedding vector
            embedding2: Second L2-normalized embedding vector
            
        Returns:
            Similarity score [0, 1] where:
            - 1.0 = identical faces
            - 0.5 = neutral (orthogonal vectors)
            - 0.0 = opposite faces (rare in practice)
        """
        try:
            # Compute cosine similarity (dot product for normalized vectors)
            similarity = cosine_similarity(embedding1, embedding2)
            
            # Convert from [-1, 1] to [0, 1] for easier interpretation
            # This maps: -1 -> 0, 0 -> 0.5, 1 -> 1.0
            normalized_similarity = (similarity + 1.0) / 2.0
            
            return float(np.clip(normalized_similarity, 0.0, 1.0))
            
        except Exception as e:
            logger.error(f"Failed to compare embeddings: {e}")
            return 0.0
    
    def detect_and_align_face(self, image: np.ndarray) -> Optional[np.ndarray]:
        """
        Detect face in image and return cropped face region.
        
        This method is kept for backward compatibility with existing API.
        For CPU-only, low-memory environments, uses lightweight Haar Cascade.
        
        Args:
            image: Input image (BGR format from OpenCV)
            
        Returns:
            Cropped face image (RGB format) or None if no face detected
        """
        try:
            # Resize for faster detection if image is large
            height, width = image.shape[:2]
            max_dim = max(height, width)
            
            if max_dim > 640:
                # Scale down for detection (faster, less memory)
                scale = 640.0 / max_dim
                small_height = int(height * scale)
                small_width = int(width * scale)
                image_small = cv2.resize(image, (small_width, small_height))
            else:
                image_small = image
                scale = 1.0
            
            # Convert to grayscale for detection
            gray = cv2.cvtColor(image_small, cv2.COLOR_BGR2GRAY)
            
            # Load Haar Cascade (lightweight, CPU-only)
            face_cascade = cv2.CascadeClassifier(
                cv2.data.haarcascades + 'haarcascade_frontalface_default.xml'
            )
            
            # Detect faces
            faces = face_cascade.detectMultiScale(
                gray,
                scaleFactor=1.1,
                minNeighbors=5,
                minSize=(30, 30)
            )
            
            if len(faces) == 0:
                logger.debug("No face detected")
                return None
            
            # Get largest face
            x, y, w, h = max(faces, key=lambda rect: rect[2] * rect[3])
            
            # Scale coordinates back to original image
            if scale < 1.0:
                x = int(x / scale)
                y = int(y / scale)
                w = int(w / scale)
                h = int(h / scale)
            
            # Add small margin
            margin = int(max(w, h) * 0.1)
            x1 = max(0, x - margin)
            y1 = max(0, y - margin)
            x2 = min(image.shape[1], x + w + margin)
            y2 = min(image.shape[0], y + h + margin)
            
            # Crop face
            face_bgr = image[y1:y2, x1:x2]
            
            # Convert to RGB (models expect RGB)
            face_rgb = cv2.cvtColor(face_bgr, cv2.COLOR_BGR2RGB)
            
            return face_rgb
            
        except Exception as e:
            logger.error(f"Face detection failed: {e}")
            return None
    
    def is_model_loaded(self) -> bool:
        """Check if model is successfully loaded"""
        return self.session is not None
    
    # Backward compatibility alias
    def extract_embedding(self, face_image: np.ndarray) -> Optional[np.ndarray]:
        """
        Alias for get_embedding() for backward compatibility.
        Prefer using get_embedding() for new code.
        """
        try:
            return self.get_embedding(face_image)
        except RuntimeError:
            return None


# Global singleton instance
# Initialized once at application startup, reused for all requests
face_engine = FaceRecognitionEngine()
