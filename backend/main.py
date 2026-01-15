"""
FastAPI Backend for Face Recognition Attendance System
Optimized for AWS EC2 t3.micro (CPU-only, 1GB RAM)

This backend handles:
- Face enrollment (storing embeddings in Supabase)
- Face recognition (comparing against stored embeddings)
- Attendance marking with business rules
"""
from fastapi import FastAPI, HTTPException, Header, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
import logging
import base64
import cv2
import numpy as np
import json
from typing import Optional
import uvicorn

from config import get_settings
from models import (
    EnrollRequest,
    RecognizeRequest,
    EnrollResponse,
    RecognizeResponse,
    HealthResponse
)
from face_recognition_engine import face_engine
from database import database

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Load settings
settings = get_settings()

# Initialize FastAPI app
app = FastAPI(
    title="Face Recognition Attendance API",
    description="Production-ready face recognition backend for attendance system",
    version="1.0.0"
)

# CORS middleware for Android app
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # In production, specify your Android app's origin
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ============================================================================
# Startup & Shutdown Events
# ============================================================================

@app.on_event("startup")
async def startup_event():
    """
    Initialize models and connections at startup
    Models are loaded once and reused across all requests
    """
    logger.info("=" * 60)
    logger.info("Starting Face Recognition Attendance API")
    logger.info("=" * 60)
    
    # Check if face recognition model is loaded
    if face_engine.is_model_loaded():
        logger.info("✓ Face recognition engine initialized")
    else:
        logger.warning("⚠ Face recognition engine using fallback mode")
    
    # Test database connection
    if database.test_connection():
        logger.info("✓ Database connection established")
    else:
        logger.error("✗ Database connection failed")
    
    logger.info(f"Confidence threshold: {settings.confidence_threshold}")
    logger.info(f"Max attendance per day: {settings.max_attendance_per_day}")
    logger.info("=" * 60)


@app.on_event("shutdown")
async def shutdown_event():
    """Cleanup on shutdown"""
    logger.info("Shutting down Face Recognition Attendance API")


# ============================================================================
# Utility Functions
# ============================================================================

def verify_api_key(x_api_key: Optional[str] = Header(None)):
    """
    Verify API key from request header
    
    Args:
        x_api_key: API key from X-API-Key header
        
    Raises:
        HTTPException: If API key is invalid
    """
    if settings.api_key and x_api_key != settings.api_key:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid API key"
        )


def decode_base64_image(base64_string: str) -> Optional[np.ndarray]:
    """
    Decode base64 string to OpenCV image
    
    Args:
        base64_string: Base64 encoded image
        
    Returns:
        OpenCV image (BGR format) or None if failed
    """
    try:
        # Remove data URL prefix if present
        if ',' in base64_string:
            base64_string = base64_string.split(',')[1]
        
        # Decode base64
        image_bytes = base64.b64decode(base64_string)
        
        # Convert to numpy array
        nparr = np.frombuffer(image_bytes, np.uint8)
        
        # Decode image
        image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        
        if image is None:
            logger.error("Failed to decode image")
            return None
        
        return image
        
    except Exception as e:
        logger.error(f"Error decoding base64 image: {e}")
        return None


# ============================================================================
# API Endpoints
# ============================================================================

@app.get("/", tags=["Health"])
async def root():
    """Root endpoint - API information"""
    return {
        "name": "Face Recognition Attendance API",
        "version": "1.0.0",
        "status": "running"
    }


@app.get("/health", response_model=HealthResponse, tags=["Health"])
async def health_check():
    """
    Health check endpoint
    Returns status of model and database connection
    """
    model_loaded = face_engine.is_model_loaded()
    db_connected = database.test_connection()
    
    return HealthResponse(
        status="healthy" if (model_loaded and db_connected) else "degraded",
        model_loaded=model_loaded,
        database_connected=db_connected
    )


@app.post("/enroll", response_model=EnrollResponse, tags=["Face Recognition"])
async def enroll_face(
    request: EnrollRequest,
    x_api_key: Optional[str] = Header(None)
):
    """
    Enroll a new face
    
    Process:
    1. Decode base64 image
    2. Detect and align face
    3. Extract embedding
    4. Store embedding in Supabase (NOT raw image)
    
    Args:
        request: Enrollment request with user_id, name, and base64 image
        x_api_key: API key for authentication
        
    Returns:
        EnrollResponse with success status and message
    """
    # Verify API key
    verify_api_key(x_api_key)
    
    logger.info(f"Enrollment request for user: {request.user_id}")
    
    try:
        # Decode image
        image = decode_base64_image(request.image)
        if image is None:
            return EnrollResponse(
                success=False,
                message="Failed to decode image"
            )
        
        # Detect and align face
        face = face_engine.detect_and_align_face(image)
        if face is None:
            return EnrollResponse(
                success=False,
                message="No face detected in image. Please ensure face is clearly visible."
            )
        
        # Extract embedding
        embedding = face_engine.extract_embedding(face)
        if embedding is None:
            return EnrollResponse(
                success=False,
                message="Failed to extract face features"
            )
        
        # Store in database (only embedding, NOT raw image)
        success = database.store_face_embedding(
            user_id=request.user_id,
            name=request.name,
            embedding=embedding
        )
        
        if success:
            logger.info(f"Successfully enrolled user: {request.user_id}")
            return EnrollResponse(
                success=True,
                message=f"Successfully enrolled {request.name}",
                user_id=request.user_id
            )
        else:
            return EnrollResponse(
                success=False,
                message="Failed to store face data in database"
            )
        
    except Exception as e:
        logger.error(f"Enrollment error: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Enrollment failed: {str(e)}"
        )


@app.post("/recognize", response_model=RecognizeResponse, tags=["Face Recognition"])
async def recognize_face(
    request: RecognizeRequest,
    x_api_key: Optional[str] = Header(None)
):
    """
    Recognize face and mark attendance
    
    Process:
    1. Decode base64 image
    2. Detect and align face
    3. Extract embedding
    4. Compare with stored embedding
    5. If matched and confidence > threshold:
        - Check attendance count today
        - Insert attendance record if allowed
    
    Business Rules:
    - Max 2 attendance records per day
    - Confidence threshold configurable (default 0.8)
    
    Args:
        request: Recognition request with user_id and base64 image
        x_api_key: API key for authentication
        
    Returns:
        RecognizeResponse with match status and attendance info
    """
    # Verify API key
    verify_api_key(x_api_key)
    
    logger.info(f"Recognition request for user: {request.user_id}")
    
    try:
        # Decode image
        image = decode_base64_image(request.image)
        if image is None:
            return RecognizeResponse(
                matched=False,
                message="Failed to decode image"
            )
        
        # Detect and align face
        face = face_engine.detect_and_align_face(image)
        if face is None:
            return RecognizeResponse(
                matched=False,
                message="No face detected. Please ensure face is clearly visible."
            )
        
        # Extract embedding
        current_embedding = face_engine.extract_embedding(face)
        if current_embedding is None:
            return RecognizeResponse(
                matched=False,
                message="Failed to extract face features"
            )
        
        # Get stored embedding from database
        stored_embedding = database.get_face_embedding(request.user_id)
        if stored_embedding is None:
            return RecognizeResponse(
                matched=False,
                message=f"User {request.user_id} not enrolled. Please enroll first."
            )
        
        # Compare embeddings
        confidence = face_engine.compare_embeddings(current_embedding, stored_embedding)
        logger.info(f"Recognition confidence for {request.user_id}: {confidence:.3f}")
        
        # Check if confidence meets threshold
        if confidence < settings.confidence_threshold:
            return RecognizeResponse(
                matched=False,
                confidence=confidence,
                message=f"Face does not match. Confidence: {confidence:.2f}"
            )
        
        # Face matched! Check attendance rules
        attendance_count = database.get_attendance_count_today(request.user_id)
        
        if attendance_count >= settings.max_attendance_per_day:
            return RecognizeResponse(
                matched=True,
                confidence=confidence,
                message=f"Attendance already marked {attendance_count} times today. Maximum allowed: {settings.max_attendance_per_day}",
                attendance_count_today=attendance_count
            )
        
        # Get user name (fallback to user_id if database query fails)
        user_name = request.user_id
        try:
            if database._client is not None:
                user_info = database._client.table('face_embeddings').select('name').eq('user_id', request.user_id).execute()
                if user_info.data:
                    user_name = user_info.data[0]['name']
        except Exception as e:
            logger.warning(f"Could not retrieve user name for {request.user_id}: {e}")
        
        # Insert attendance record
        attendance_id = database.insert_attendance(
            user_id=request.user_id,
            name=user_name,
            confidence=confidence
        )
        
        if attendance_id:
            logger.info(f"Attendance marked for {request.user_id}, ID: {attendance_id}")
            return RecognizeResponse(
                matched=True,
                confidence=confidence,
                message=f"Attendance marked successfully for {user_name}",
                attendance_id=attendance_id,
                attendance_count_today=attendance_count + 1
            )
        else:
            return RecognizeResponse(
                matched=True,
                confidence=confidence,
                message="Face matched but failed to record attendance"
            )
        
    except Exception as e:
        logger.error(f"Recognition error: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Recognition failed: {str(e)}"
        )


@app.post("/identify", tags=["Face Recognition"])
async def identify_face(
    request: RecognizeRequest,
    x_api_key: Optional[str] = Header(None)
):
    """
    Identify an unknown face by comparing against all stored faces
    
    This endpoint is used when you don't know the user_id beforehand.
    It searches through all enrolled faces to find a match.
    
    Args:
        request: RecognizeRequest with image (user_id is ignored)
        x_api_key: API key for authentication
        
    Returns:
        JSON with identified user info or no match found
    """
    # Verify API key
    verify_api_key(x_api_key)
    
    logger.info("Identification request (searching all faces)")
    
    try:
        # Decode image
        image = decode_base64_image(request.image)
        if image is None:
            return JSONResponse(
                content={"identified": False, "message": "Failed to decode image"},
                status_code=400
            )
        
        # Detect and align face
        face = face_engine.detect_and_align_face(image)
        if face is None:
            return JSONResponse(
                content={"identified": False, "message": "No face detected"},
                status_code=200
            )
        
        # Extract embedding
        current_embedding = face_engine.extract_embedding(face)
        if current_embedding is None:
            return JSONResponse(
                content={"identified": False, "message": "Failed to extract face features"},
                status_code=200
            )
        
        # Get all stored faces
        all_faces = database.get_all_face_embeddings()
        if not all_faces:
            return JSONResponse(
                content={"identified": False, "message": "No faces enrolled yet"},
                status_code=200
            )
        
        # Find best match
        best_match = None
        best_confidence = 0.0
        
        for face_data in all_faces:
            stored_embedding = np.array(json.loads(face_data['embedding']))
            confidence = face_engine.compare_embeddings(current_embedding, stored_embedding)
            
            if confidence > best_confidence:
                best_confidence = confidence
                best_match = face_data
        
        # Check if best match meets threshold
        if best_match and best_confidence >= settings.confidence_threshold:
            user_id = best_match['user_id']
            name = best_match['name']
            
            logger.info(f"Identified as {name} ({user_id}) with confidence {best_confidence:.3f}")
            
            # Check attendance rules
            attendance_count = database.get_attendance_count_today(user_id)
            
            if attendance_count >= settings.max_attendance_per_day:
                return JSONResponse(content={
                    "identified": True,
                    "employee_id": user_id,
                    "name": name,
                    "similarity": float(best_confidence),
                    "attendance_marked": False,
                    "message": f"Already marked {attendance_count} times today"
                })
            
            # Mark attendance
            attendance_id = database.insert_attendance(
                user_id=user_id,
                name=name,
                confidence=best_confidence
            )
            
            return JSONResponse(content={
                "identified": True,
                "employee_id": user_id,
                "name": name,
                "similarity": float(best_confidence),
                "attendance_marked": True,
                "attendance_id": attendance_id,
                "message": f"Attendance marked for {name}"
            })
        else:
            logger.info(f"No match found. Best confidence: {best_confidence:.3f}")
            return JSONResponse(content={
                "identified": False,
                "message": "No matching face found",
                "best_confidence": float(best_confidence)
            })
        
    except Exception as e:
        logger.error(f"Identification error: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Identification failed: {str(e)}"
        )


# ============================================================================
# Main Entry Point
# ============================================================================

if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host=settings.host,
        port=settings.port,
        workers=settings.workers,
        log_level="info"
    )
