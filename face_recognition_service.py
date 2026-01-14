"""
Face Recognition Service using Python
Uses face_recognition library for better accuracy
"""

from flask import Flask, request, jsonify
import face_recognition
import numpy as np
import cv2
import base64
import pickle
import os
import json
from datetime import datetime

app = Flask(__name__)

# Storage for enrolled faces
ENCODINGS_FILE = "face_encodings.pkl"
encodings_db = {}

def load_encodings():
    """Load saved face encodings from file"""
    global encodings_db
    if os.path.exists(ENCODINGS_FILE):
        with open(ENCODINGS_FILE, 'rb') as f:
            encodings_db = pickle.load(f)
        print(f"Loaded {len(encodings_db)} enrolled faces")
    else:
        encodings_db = {}
        print("No existing encodings found, starting fresh")

def save_encodings():
    """Save face encodings to file"""
    with open(ENCODINGS_FILE, 'wb') as f:
        pickle.dump(encodings_db, f)
    print(f"Saved {len(encodings_db)} face encodings")

def base64_to_image(base64_string):
    """Convert base64 string to OpenCV image"""
    img_data = base64.b64decode(base64_string)
    nparr = np.frombuffer(img_data, np.uint8)
    img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    return cv2.cvtColor(img, cv2.COLOR_BGR2RGB)

def image_to_base64(image):
    """Convert OpenCV image to base64 string"""
    _, buffer = cv2.imencode('.jpg', cv2.cvtColor(image, cv2.COLOR_RGB2BGR))
    return base64.b64encode(buffer).decode('utf-8')

@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    return jsonify({
        "status": "ok",
        "enrolled_count": len(encodings_db),
        "timestamp": datetime.now().isoformat()
    })

@app.route('/enroll', methods=['POST'])
def enroll_face():
    """
    Enroll a new face
    Expected JSON: {
        "employee_id": "string",
        "name": "string",
        "image": "base64_encoded_image"
    }
    """
    try:
        data = request.get_json()
        employee_id = data.get('employee_id')
        name = data.get('name')
        image_base64 = data.get('image')

        if not all([employee_id, name, image_base64]):
            return jsonify({"error": "Missing required fields"}), 400

        # Convert base64 to image
        image = base64_to_image(image_base64)

        # Detect faces
        face_locations = face_recognition.face_locations(image, model="hog")
        
        if len(face_locations) == 0:
            return jsonify({"error": "No face detected"}), 400
        
        if len(face_locations) > 1:
            return jsonify({"error": "Multiple faces detected"}), 400

        # Get face encoding
        face_encodings = face_recognition.face_encodings(image, face_locations)
        
        if len(face_encodings) == 0:
            return jsonify({"error": "Could not encode face"}), 400

        encoding = face_encodings[0]

        # Store encoding
        encodings_db[employee_id] = {
            "employee_id": employee_id,
            "name": name,
            "encoding": encoding.tolist(),
            "enrolled_at": datetime.now().isoformat()
        }

        save_encodings()

        return jsonify({
            "success": True,
            "message": f"Successfully enrolled {name}",
            "employee_id": employee_id,
            "face_location": face_locations[0]
        })

    except Exception as e:
        print(f"Error in enroll: {str(e)}")
        return jsonify({"error": str(e)}), 500

@app.route('/identify', methods=['POST'])
def identify_face():
    """
    Identify a face
    Expected JSON: {
        "image": "base64_encoded_image",
        "threshold": 0.6 (optional, default 0.6)
    }
    """
    try:
        data = request.get_json()
        image_base64 = data.get('image')
        threshold = data.get('threshold', 0.6)

        if not image_base64:
            return jsonify({"error": "Missing image"}), 400

        if len(encodings_db) == 0:
            return jsonify({"error": "No enrolled faces in database"}), 400

        # Convert base64 to image
        image = base64_to_image(image_base64)

        # Detect faces
        face_locations = face_recognition.face_locations(image, model="hog")
        
        if len(face_locations) == 0:
            return jsonify({
                "identified": False,
                "message": "No face detected"
            })

        # Get face encodings
        face_encodings = face_recognition.face_encodings(image, face_locations)
        
        if len(face_encodings) == 0:
            return jsonify({
                "identified": False,
                "message": "Could not encode face"
            })

        # Compare with all enrolled faces
        unknown_encoding = face_encodings[0]
        
        best_match_id = None
        best_match_distance = float('inf')
        best_match_name = None

        for employee_id, data in encodings_db.items():
            known_encoding = np.array(data['encoding'])
            
            # Calculate face distance (lower is better)
            distance = face_recognition.face_distance([known_encoding], unknown_encoding)[0]
            
            if distance < best_match_distance:
                best_match_distance = distance
                best_match_id = employee_id
                best_match_name = data['name']

        # Convert distance to similarity (0-1 scale, higher is better)
        similarity = 1.0 - best_match_distance

        if similarity >= threshold:
            return jsonify({
                "identified": True,
                "employee_id": best_match_id,
                "name": best_match_name,
                "similarity": float(similarity),
                "distance": float(best_match_distance),
                "face_location": face_locations[0]
            })
        else:
            return jsonify({
                "identified": False,
                "message": "No match found",
                "best_match": {
                    "name": best_match_name,
                    "similarity": float(similarity)
                }
            })

    except Exception as e:
        print(f"Error in identify: {str(e)}")
        return jsonify({"error": str(e)}), 500

@app.route('/list', methods=['GET'])
def list_enrolled():
    """List all enrolled faces"""
    enrolled = []
    for employee_id, data in encodings_db.items():
        enrolled.append({
            "employee_id": employee_id,
            "name": data['name'],
            "enrolled_at": data.get('enrolled_at', 'Unknown')
        })
    return jsonify({
        "count": len(enrolled),
        "enrolled": enrolled
    })

@app.route('/delete/<employee_id>', methods=['DELETE'])
def delete_face(employee_id):
    """Delete an enrolled face"""
    if employee_id in encodings_db:
        name = encodings_db[employee_id]['name']
        del encodings_db[employee_id]
        save_encodings()
        return jsonify({
            "success": True,
            "message": f"Deleted {name}"
        })
    else:
        return jsonify({"error": "Employee not found"}), 404

@app.route('/clear', methods=['POST'])
def clear_all():
    """Clear all enrolled faces"""
    global encodings_db
    count = len(encodings_db)
    encodings_db = {}
    save_encodings()
    return jsonify({
        "success": True,
        "message": f"Cleared {count} enrolled faces"
    })

if __name__ == '__main__':
    load_encodings()
    print("Starting Face Recognition Service...")
    print("Enrolled faces:", len(encodings_db))
    app.run(host='0.0.0.0', port=5000, debug=True)
