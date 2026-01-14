"""
Configuration management using Pydantic Settings
All secrets are loaded from environment variables
"""
from pydantic_settings import BaseSettings
from functools import lru_cache


class Settings(BaseSettings):
    """Application settings loaded from environment variables"""
    
    # Supabase
    supabase_url: str
    supabase_key: str
    
    # Face Recognition
    confidence_threshold: float = 0.80
    max_attendance_per_day: int = 2
    
    # Server
    host: str = "0.0.0.0"
    port: int = 8000
    workers: int = 1
    
    # Security
    api_key: str
    
    # Debug
    debug: bool = False
    
    class Config:
        env_file = ".env"
        case_sensitive = False


@lru_cache()
def get_settings() -> Settings:
    """
    Cached settings instance
    Only loads once and reuses across requests
    """
    return Settings()
