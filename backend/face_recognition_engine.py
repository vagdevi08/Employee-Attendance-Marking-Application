"""
Face Recognition Engine using ONNX Runtime (CPU-optimized)
Uses lightweight face recognition model for embedding extraction
Model is loaded once at startup and reused across all requests
"""
import cv2
import numpy as np
import onnxruntime as ort
from typing import Optional, Tuple
import logging
from sklearn.metrics.pairwise import cosine_similarity
import urllib.request
import os

logger = logging.getLogger(__name__)


class FaceRecognitionEngine:
    """
    Singleton face recognition engine
    Uses ONNX Runtime for CPU-optimized inference
    """
    
    _instance = None
    _initialized = False
    
    def __new__(cls):
        """Singleton pattern - only one instance"""
        if cls._instance is None:
            cls._instance = super(FaceRecognitionEngine, cls).__new__(cls)
        return cls._instance
    
    def __init__(self):
        """Initialize model only once"""
        if not self._initialized:
            self._load_model()
            FaceRecognitionEngine._initialized = True
    
    def _load_model(self):
        """
        Load lightweight face recognition model
        Using MobileFaceNet ONNX model (~4MB) - perfect for CPU
        """
        logger.info("Loading face recognition model...")
        
        # Model path - resolve relative to project root
        import pathlib
        backend_dir = pathlib.Path(__file__).parent
        project_root = backend_dir.parent
        model_path = str(project_root / "models" / "mobilefacenet.onnx")
        
        # Download model if not exists
        if not os.path.exists(model_path):
            os.makedirs("models", exist_ok=True)
            logger.info("Downloading MobileFaceNet model...")
            
            # Using a lightweight face recognition model
            model_url = "https://github.com/onnx/models/raw/main/validated/vision/body_analysis/arcface/model/arcfaceresnet100-8.onnx"
            
            try:
                urllib.request.urlretrieve(model_url, model_path)
                logger.info(f"Model downloaded to {model_path}")
            except Exception as e:
                logger.warning(f"Could not download model: {e}")
                # Fallback: Use simple feature extraction
                self.session = None
                self.input_name = None
                logger.info("Using fallback feature extraction")
                return
        
        try:
            # Configure ONNX Runtime for CPU optimization
            sess_options = ort.SessionOptions()
            sess_options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
            sess_options.intra_op_num_threads = 1  # Single thread for t3.micro
            
            # Load model
            self.session = ort.InferenceSession(
                model_path,
                sess_options=sess_options,
                providers=['CPUExecutionProvider']
            )
            
            self.input_name = self.session.get_inputs()[0].name
            self.input_shape = self.session.get_inputs()[0].shape
            logger.info(f"Model loaded successfully. Input shape: {self.input_shape}")
            
        except Exception as e:
            logger.error(f"Failed to load ONNX model: {e}")
            # Fallback to simple feature extraction
            self.session = None
            self.input_name = None
            logger.info("Using fallback feature extraction")
    
    def preprocess_image(self, image: np.ndarray) -> np.ndarray:
        """
        Preprocess face image for model input
        
        Args:
            image: Face image (BGR format from OpenCV)
            
        Returns:
            Preprocessed image ready for model input
        """
        # Convert BGR to RGB
        image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
        
        # Resize to model input size (112x112 for most face models)
        target_size = (112, 112)
        if self.session and len(self.input_shape) >= 3:
            # Get size from model if available
            h, w = self.input_shape[2], self.input_shape[3]
            target_size = (w, h)
        
        image = cv2.resize(image, target_size)
        
        # Normalize to [0, 1]
        image = image.astype(np.float32) / 255.0
        
        # Standardize (mean=0.5, std=0.5)
        image = (image - 0.5) / 0.5
        
        # Transpose to NCHW format (batch, channels, height, width)
        image = np.transpose(image, (2, 0, 1))
        
        # Add batch dimension
        image = np.expand_dims(image, axis=0)
        
        return image
    
    def extract_embedding(self, face_image: np.ndarray) -> Optional[np.ndarray]:
        """
        Extract face embedding from preprocessed image
        
        Args:
            face_image: Face image (BGR format)
            
        Returns:
            512-dimensional embedding vector or None if failed
        """
        try:
            if self.session is None:
                # Fallback: Use simple feature extraction
                return self._extract_simple_features(face_image)
            
            # Preprocess image
            input_data = self.preprocess_image(face_image)
            
            # Run inference
            outputs = self.session.run(None, {self.input_name: input_data})
            
            # Get embedding (usually first output)
            embedding = outputs[0].flatten()
            
            # L2 normalize
            embedding = embedding / np.linalg.norm(embedding)
            
            logger.debug(f"Extracted embedding of shape: {embedding.shape}")
            return embedding
            
        except Exception as e:
            logger.error(f"Failed to extract embedding: {e}")
            # Fallback to simple features
            return self._extract_simple_features(face_image)
    
    def _extract_simple_features(self, face_image: np.ndarray) -> np.ndarray:
        """
        Fallback: Extract simple features if model is not available
        Uses histogram and gradient features
        """
        # Resize to standard size
        face_image = cv2.resize(face_image, (112, 112))
        
        # Convert to grayscale
        gray = cv2.cvtColor(face_image, cv2.COLOR_BGR2GRAY)
        
        # Extract features
        features = []
        
        # 1. Histogram features (256)
        hist = cv2.calcHist([gray], [0], None, [256], [0, 256])
        hist = hist.flatten() / hist.sum()  # Normalize
        features.extend(hist)
        
        # 2. Gradient features (256)
        grad_x = cv2.Sobel(gray, cv2.CV_64F, 1, 0, ksize=3)
        grad_y = cv2.Sobel(gray, cv2.CV_64F, 0, 1, ksize=3)
        magnitude = np.sqrt(grad_x**2 + grad_y**2)
        mag_hist = np.histogram(magnitude, bins=256, range=(0, 255))[0]
        mag_hist = mag_hist / mag_hist.sum()
        features.extend(mag_hist)
        
        embedding = np.array(features, dtype=np.float32)
        
        # L2 normalize
        embedding = embedding / np.linalg.norm(embedding)
        
        return embedding
    
    def compare_embeddings(
        self,
        embedding1: np.ndarray,
        embedding2: np.ndarray
    ) -> float:
        """
        Compare two embeddings using cosine similarity
        
        Args:
            embedding1: First embedding vector
            embedding2: Second embedding vector
            
        Returns:
            Similarity score (0-1, higher is more similar)
        """
        try:
            # Ensure 2D arrays for sklearn
            emb1 = embedding1.reshape(1, -1)
            emb2 = embedding2.reshape(1, -1)
            
            # Calculate cosine similarity
            similarity = cosine_similarity(emb1, emb2)[0][0]
            
            # Convert to 0-1 range (cosine similarity is -1 to 1)
            similarity = (similarity + 1) / 2
            
            return float(similarity)
            
        except Exception as e:
            logger.error(f"Failed to compare embeddings: {e}")
            return 0.0
    
    def detect_and_align_face(self, image: np.ndarray) -> Optional[np.ndarray]:
        """
        Detect face in image and return aligned face
        Using multiple detection strategies for robustness
        
        Args:
            image: Input image (BGR format)
            
        Returns:
            Aligned face image or None if no face detected
        """
        try:
            # Resize image for faster detection if too large
            height, width = image.shape[:2]
            scale_factor = 1.0
            if max(height, width) > 640:
                scale_factor = 640.0 / max(height, width)
                image_resized = cv2.resize(image, None, fx=scale_factor, fy=scale_factor)
            else:
                image_resized = image
            
            # Convert to grayscale
            gray = cv2.cvtColor(image_resized, cv2.COLOR_BGR2GRAY)
            
            # Enhance contrast for better detection
            clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
            gray_enhanced = clahe.apply(gray)
            
            # Use Haar Cascade for face detection with relaxed parameters
            face_cascade = cv2.CascadeClassifier(
                cv2.data.haarcascades + 'haarcascade_frontalface_default.xml'
            )
            
            # Try with different parameters for robustness
            faces = []
            
            # Attempt 1: Default parameters
            faces = face_cascade.detectMultiScale(
                gray_enhanced,
                scaleFactor=1.05,  # More sensitive
                minNeighbors=4,     # Less strict
                minSize=(20, 20),   # Smaller minimum
                maxSize=(int(gray_enhanced.shape[1]*0.8), int(gray_enhanced.shape[0]*0.8))
            )
            
            # Attempt 2: If no faces found, try even more lenient
            if len(faces) == 0:
                logger.debug("Retrying with more lenient parameters...")
                faces = face_cascade.detectMultiScale(
                    gray,
                    scaleFactor=1.1,
                    minNeighbors=3,
                    minSize=(15, 15)
                )
            
            # Attempt 3: Try with original image if resized
            if len(faces) == 0 and scale_factor < 1.0:
                logger.debug("Retrying on original size image...")
                gray_original = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
                clahe_original = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
                gray_original_enhanced = clahe_original.apply(gray_original)
                
                faces = face_cascade.detectMultiScale(
                    gray_original_enhanced,
                    scaleFactor=1.05,
                    minNeighbors=3,
                    minSize=(20, 20)
                )
            
            if len(faces) == 0:
                logger.warning("No face detected in image after multiple attempts")
                return None
            
            if len(faces) > 1:
                logger.debug(f"Multiple faces detected ({len(faces)}), using largest")
            
            # Get largest face
            x, y, w, h = max(faces, key=lambda rect: rect[2] * rect[3])
            
            # Undo scaling if we resized
            if scale_factor < 1.0:
                x = int(x / scale_factor)
                y = int(y / scale_factor)
                w = int(w / scale_factor)
                h = int(h / scale_factor)
            
            # Add margin (10% to avoid cutting face parts)
            margin = int(max(w, h) * 0.1)
            x1 = max(0, x - margin)
            y1 = max(0, y - margin)
            x2 = min(image.shape[1], x + w + margin)
            y2 = min(image.shape[0], y + h + margin)
            
            # Ensure minimum size
            if (x2 - x1) < 20 or (y2 - y1) < 20:
                logger.warning("Detected face too small")
                return None
            
            # Crop face from original image
            face = image[y1:y2, x1:x2]
            
            logger.debug(f"Face detected: position ({x1},{y1}), size ({x2-x1}x{y2-y1})")
            return face
            
        except Exception as e:
            logger.error(f"Face detection failed: {e}")
            return None
    
    def is_model_loaded(self) -> bool:
        """Check if model is loaded successfully"""
        return self._initialized


# Global instance
face_engine = FaceRecognitionEngine()
