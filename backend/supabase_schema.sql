-- ============================================================================
-- Supabase Database Schema for Face Recognition Attendance System
-- Run these SQL commands in your Supabase SQL Editor
-- ============================================================================

-- Enable pgvector extension (required for vector similarity search)
CREATE EXTENSION IF NOT EXISTS vector;

-- Table: face_embeddings
-- Stores face embeddings (NOT raw images) for each user
-- Note: Current code stores embeddings as JSON TEXT for compatibility
-- For optimal performance with pgvector, consider migrating to vector type
CREATE TABLE IF NOT EXISTS face_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    embedding TEXT NOT NULL, -- JSON array of floats (compatible with current code)
    -- Future: embedding vector(512) -- Use pgvector for similarity search
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Index for fast user_id lookups
CREATE INDEX IF NOT EXISTS idx_face_embeddings_user_id ON face_embeddings(user_id);

-- Note: If migrating to pgvector vector type, add this index:
-- CREATE INDEX ON face_embeddings USING ivfflat (embedding vector_cosine_ops);

-- Table: attendance
-- Stores attendance records with timestamps
CREATE TABLE IF NOT EXISTS attendance (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    confidence FLOAT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES face_embeddings(user_id) ON DELETE CASCADE
);

-- Indexes for fast queries
CREATE INDEX IF NOT EXISTS idx_attendance_user_id ON attendance(user_id);
CREATE INDEX IF NOT EXISTS idx_attendance_timestamp ON attendance(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_attendance_user_date ON attendance(user_id, DATE(timestamp));

-- Enable Row Level Security (RLS)
ALTER TABLE face_embeddings ENABLE ROW LEVEL SECURITY;
ALTER TABLE attendance ENABLE ROW LEVEL SECURITY;

-- Policy: Allow service role to do everything
CREATE POLICY "Service role full access to face_embeddings"
ON face_embeddings FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

CREATE POLICY "Service role full access to attendance"
ON attendance FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

-- ============================================================================
-- Useful Queries
-- ============================================================================

-- Get attendance count for a user today
-- SELECT COUNT(*) FROM attendance 
-- WHERE user_id = 'USER_ID' 
-- AND DATE(timestamp) = CURRENT_DATE;

-- Get all attendance records for today
-- SELECT * FROM attendance 
-- WHERE DATE(timestamp) = CURRENT_DATE 
-- ORDER BY timestamp DESC;

-- Get user's attendance history
-- SELECT * FROM attendance 
-- WHERE user_id = 'USER_ID' 
-- ORDER BY timestamp DESC 
-- LIMIT 30;

-- Delete all attendance records (for testing)
-- DELETE FROM attendance;

-- Delete all face embeddings (for testing)
-- DELETE FROM face_embeddings;
