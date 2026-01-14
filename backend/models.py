"""
Pydantic models for request/response validation
"""
from pydantic import BaseModel, Field, validator
from typing import Optional
import base64


class EnrollRequest(BaseModel):
    """Request model for face enrollment"""
    user_id: str = Field(..., description="Unique user identifier")
    name: str = Field(..., description="User's full name")
    image: str = Field(..., description="Base64 encoded face image")
    
    @validator('image')
    def validate_base64(cls, v):
        """Validate base64 image string"""
        try:
            # Try to decode to verify it's valid base64
            base64.b64decode(v)
            return v
        except Exception:
            raise ValueError("Invalid base64 encoded image")
    
    @validator('user_id')
    def validate_user_id(cls, v):
        """Validate user_id format"""
        if not v or len(v.strip()) == 0:
            raise ValueError("user_id cannot be empty")
        return v.strip()


class RecognizeRequest(BaseModel):
    """Request model for face recognition"""
    user_id: str = Field(..., description="User ID attempting to mark attendance")
    image: str = Field(..., description="Base64 encoded face image")
    
    @validator('image')
    def validate_base64(cls, v):
        """Validate base64 image string"""
        try:
            base64.b64decode(v)
            return v
        except Exception:
            raise ValueError("Invalid base64 encoded image")
    
    @validator('user_id')
    def validate_user_id(cls, v):
        """Validate user_id format"""
        if not v or len(v.strip()) == 0:
            raise ValueError("user_id cannot be empty")
        return v.strip()


class EnrollResponse(BaseModel):
    """Response model for enrollment"""
    success: bool
    message: str
    user_id: Optional[str] = None


class RecognizeResponse(BaseModel):
    """Response model for recognition"""
    matched: bool
    confidence: Optional[float] = None
    message: str
    attendance_id: Optional[str] = None
    attendance_count_today: Optional[int] = None


class HealthResponse(BaseModel):
    """Health check response"""
    status: str
    model_loaded: bool
    database_connected: bool
