"""
Supabase database integration
Handles all database operations for face embeddings and attendance records
"""
from supabase import create_client, Client  # type: ignore
from typing import Optional, List, Dict, Any
import numpy as np
import json
from datetime import datetime, date
import logging
from config import get_settings

logger = logging.getLogger(__name__)


class Database:
    """
    Database handler for Supabase PostgreSQL
    Manages face embeddings and attendance records
    """
    
    _instance = None
    _client: Optional[Client] = None
    
    def __new__(cls):
        """Singleton pattern"""
        if cls._instance is None:
            cls._instance = super(Database, cls).__new__(cls)
        return cls._instance
    
    def __init__(self):
        """Initialize Supabase client"""
        if self._client is None:
            settings = get_settings()
            try:
                self._client = create_client(
                    settings.supabase_url,
                    settings.supabase_key
                )
                logger.info("Supabase client initialized successfully")
            except Exception as e:
                logger.warning(f"Failed to initialize Supabase client: {e}")
                logger.warning("Running in degraded mode - database operations will fail")
                self._client = None
    
    def store_face_embedding(
        self,
        user_id: str,
        name: str,
        embedding: np.ndarray
    ) -> bool:
        """
        Store face embedding in database
        
        Args:
            user_id: Unique user identifier
            name: User's full name
            embedding: Face embedding vector
            
        Returns:
            True if successful, False otherwise
        """
        try:
            # Convert embedding to list for JSON storage
            embedding_list = embedding.tolist()
            
            # Check if user already exists
            existing = self._client.table('face_embeddings').select('*').eq('user_id', user_id).execute()
            
            if existing.data:
                # Update existing embedding
                result = self._client.table('face_embeddings').update({
                    'name': name,
                    'embedding': json.dumps(embedding_list),
                    'updated_at': datetime.utcnow().isoformat()
                }).eq('user_id', user_id).execute()
                
                logger.info(f"Updated embedding for user: {user_id}")
            else:
                # Insert new embedding
                result = self._client.table('face_embeddings').insert({
                    'user_id': user_id,
                    'name': name,
                    'embedding': json.dumps(embedding_list),
                    'created_at': datetime.utcnow().isoformat(),
                    'updated_at': datetime.utcnow().isoformat()
                }).execute()
                
                logger.info(f"Inserted new embedding for user: {user_id}")
            
            return True
            
        except Exception as e:
            logger.error(f"Failed to store face embedding: {e}")
            return False
    
    def get_face_embedding(self, user_id: str) -> Optional[np.ndarray]:
        """
        Retrieve face embedding for a user
        
        Args:
            user_id: User identifier
            
        Returns:
            Embedding vector or None if not found
        """
        try:
            result = self._client.table('face_embeddings').select('embedding').eq('user_id', user_id).execute()
            
            if not result.data:
                logger.warning(f"No embedding found for user: {user_id}")
                return None
            
            # Parse JSON embedding
            embedding_json = result.data[0]['embedding']
            embedding_list = json.loads(embedding_json)
            embedding = np.array(embedding_list, dtype=np.float32)
            
            logger.debug(f"Retrieved embedding for user: {user_id}")
            return embedding
            
        except Exception as e:
            logger.error(f"Failed to retrieve face embedding: {e}")
            return None
    
    def get_all_face_embeddings(self) -> List[Dict[str, Any]]:
        """
        Retrieve all face embeddings from database
        
        Returns:
            List of dicts with user_id, name, and embedding
        """
        if self._client is None:
            logger.error("Database client not initialized")
            return []
            
        try:
            result = self._client.table('face_embeddings').select('user_id, name, embedding').execute()
            
            if not result.data:
                logger.info("No face embeddings found in database")
                return []
            
            logger.debug(f"Retrieved {len(result.data)} face embeddings")
            return result.data
            
        except Exception as e:
            logger.error(f"Failed to retrieve all face embeddings: {e}")
            return []
    
    def get_attendance_count_today(self, user_id: str) -> int:
        """
        Get number of attendance records for user today
        
        Args:
            user_id: User identifier
            
        Returns:
            Count of attendance records today
        """
        try:
            today = date.today().isoformat()
            
            result = self._client.table('attendance').select('id').eq('user_id', user_id).gte('timestamp', f"{today}T00:00:00").lte('timestamp', f"{today}T23:59:59").execute()
            
            count = len(result.data)
            logger.debug(f"User {user_id} has {count} attendance records today")
            return count
            
        except Exception as e:
            logger.error(f"Failed to get attendance count: {e}")
            return 0
    
    def insert_attendance(
        self,
        user_id: str,
        name: str,
        confidence: float
    ) -> Optional[str]:
        """
        Insert attendance record
        
        Args:
            user_id: User identifier
            name: User's name
            confidence: Recognition confidence score
            
        Returns:
            Attendance record ID or None if failed
        """
        try:
            result = self._client.table('attendance').insert({
                'user_id': user_id,
                'name': name,
                'timestamp': datetime.utcnow().isoformat(),
                'confidence': confidence
            }).execute()
            
            if result.data:
                attendance_id = result.data[0]['id']
                logger.info(f"Inserted attendance for user {user_id} with ID: {attendance_id}")
                return str(attendance_id)
            
            return None
            
        except Exception as e:
            logger.error(f"Failed to insert attendance: {e}")
            return None
    
    def test_connection(self) -> bool:
        """
        Test database connection
        
        Returns:
            True if connected, False otherwise
        """
        try:
            # Simple query to test connection
            result = self._client.table('face_embeddings').select('user_id').limit(1).execute()
            logger.info("Database connection test successful")
            return True
        except Exception as e:
            logger.error(f"Database connection test failed: {e}")
            return False


# Global instance
database = Database()
