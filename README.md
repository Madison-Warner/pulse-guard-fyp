# PulseGuard FYP
**Overview**

PulseGuard is a heart-rate monitoring and emergency escalation system developed as a Final Year Project for the BSc (Hons) in Software Development.

The system continuously monitors the user’s heart rate using a Samsung Galaxy Watch4 running Wear OS. Heart-rate data is processed locally on the smartwatch using a native C++ edge-processing module to detect potential cardiac anomalies in real time. In the event of a detected abnormality, PulseGuard initiates a cross-device alert workflow between the smartwatch and companion Android mobile application.

If the user is unresponsive during the alert countdown, the system can automatically escalate the situation by sending SMS emergency notifications to preconfigured emergency contacts through the mobile application.

PulseGuard was designed with a strong focus on:

- Real-time health monitoring
- Cross-device reliability
- Emergency response workflows
- Edge computing
- User-centred design for health-critical systems

 # Features
 
- Real-time heart-rate monitoring on Wear OS
- Native C++ edge anomaly detection
- Live heart-rate streaming between smartwatch and phone
- BLE/Wearable API communication
- Cross-device alert synchronization
- Emergency escalation workflow
- SMS emergency contact notification
- Firebase Authentication
- Firestore cloud log storage
- 24-hour HR baseline storage
- Adaptive thresholding using stored HR data
- Activity-aware tachycardia adjustment using pedometer data
- Live HR graph visualization
- Firestore log retention management
- Unit and integration testing

# System Architecture

**Smartwatch (Wear OS)**
The smartwatch application is responsible for:
- Collecting heart-rate sensor data
- Monitoring activity/step count data
- Running edge anomaly detection using native C++
- Displaying live BPM data
- Displaying emergency alert workflows
- Sending HR/event data to the mobile application
- Receiving adaptive threshold updates from the phone

**Mobile Application**
The Android mobile application is responsible for:
- Receiving live HR/event data from the watch
- Displaying heart-rate dashboards and alerts
- Managing emergency contacts
- Sending SMS emergency notifications
- Storing HR log buckets in Firestore
- Calculating adaptive thresholds from 24-hour HR history
- Synchronizing alert responses with the smartwatch

**Cloud Services**
Firebase services are used for:
- User authentication
- Firestore cloud storage
- Secure HR log retention
- Emergency contact storage

# Technologies Used

- Kotlin
- Jetpack Compose
- Wear OS
- C++
- JNI
- Firebase Authentication
- Cloud Firestore
- Google Play Services Wearable API
- Android Sensor APIs
- SMS Manager API
- BLE/Wearable communication
- Android Foreground Services

# Requirements
  
**Hardware**
- Samsung Galaxy Watch4 (or compatible Wear OS device)
- Android smartphone

**Software**
- Android Studio
- Firebase project
- Wear OS emulator or physical device
- Android SDK

# Project Structure

pulse-guard-fyp/
│
├── mobile/        # Android mobile application
├── watch/         # Wear OS smartwatch application
└── README.md

# Setup Instructions

**1. Clone the Repository**
git clone https://github.com/YOUR_USERNAME/pulse-guard-fyp.git

**2. Create a Firebase Project**
1. Create a Firebase project
2. Enable:
  - Firebase Authentication
  - Cloud Firestore
3. Download the Firebase configuration file:
  - google-services.json

**3. Add Firebase Configuration**
Place the Firebase configuration file inside:
  mobile/app/google-services.json
If using Firebase on the watch module separately, also place:
  watch/app/google-services.json

**4. Configure Firestore Rules**
Example Firestore rules:

rules_version = '2';

service cloud.firestore {
  match /databases/{database}/documents {

    match /users/{userId}/emergencyContacts/{contactId} {
      allow read, write:
      if request.auth != null &&
         request.auth.uid == userId;
    }

    match /users/{userId}/hrLogs/{logId} {
      allow read, write:
      if request.auth != null &&
         request.auth.uid == userId;
    }
  }
}

**5. Build and Run**
Mobile App
Run the mobile module first:
  mobile/app

Watch App
Run the Wear OS module after pairing the smartwatch/emulator:
watch/app

# Testing

The project was tested using:
- Unit testing
- Integration testing
- Manual testing
- Cross-device synchronization testing
- BLE reliability testing
- Firestore validation
- SMS escalation testing
- Continuous runtime testing

**Unit Testing**

Unit tests were implemented for:
- HR log aggregation
- Threshold logic
- BPM averaging and retention logic

# Adaptive Thresholding

PulseGuard implements adaptive thresholding using:
1. 24-hour HR baseline data stored in Firestore
2. Activity-aware pedometer adjustments
The system dynamically adjusts tachycardia thresholds during periods of physical activity to reduce false positives.

If adaptive threshold data is unavailable due to connectivity or cloud issues, the smartwatch automatically falls back to safe static thresholds.

# Known Limitations

- PulseGuard is a prototype system and not a certified medical device.
- The anomaly detection algorithm is intended for educational and research purposes only.
- Adaptive thresholds may require further clinical validation.
- Continuous HR monitoring may impact smartwatch battery life.
- Sensor accuracy may vary depending on device placement and movement conditions.

# Security Notes

- Firebase configuration files are excluded from version control using .gitignore
- Sensitive API keys should never be committed publicly
- Firestore access is restricted using authenticated security rules

# Future Improvements

Potential future improvements include:
- Additional health metric integration
- Improved adaptive algorithms
- Advanced exercise/activity classification
- Battery optimization
- Real-time cloud synchronization dashboards
- Expanded wearable device support

# Author

Madison Warner
BSc (Hons) Software Development
Munster Technological University

# Disclaimer

PulseGuard is an academic prototype developed for research and educational purposes. It is not intended to replace professional medical advice, diagnosis, or certified medical monitoring systems.
