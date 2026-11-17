Privacy Policy
--------------

WLED Grabber is an open-source Android screen-capture client for WLED controllers. This policy describes what the app does with data.

**What the app processes**

- Screen content is captured locally through Android MediaProjection solely to compute per-LED colors.
- Only per-LED color values and WLED discovery/control requests are sent over the local network.
- Screen images are not stored, uploaded, or shared.

**Network activity**

- The app communicates only with the WLED controller(s) you configure on your local network.
- LAN traffic uses cleartext HTTP/UDP without authentication; other devices on the same LAN may control WLED.
- The app does not include advertising, analytics, crash-reporting, or Google Play Services SDKs.

**Permissions**

- Screen capture, network access, and notifications are used only for the functions described above.
- Boot/background access is optional and used only for the controlled start behavior described in the app.
- The app does not request microphone or audio capture.

**Data retention**

- Configuration is stored locally on the device. The app has no server-side account, cloud storage, or remote logging.

**Security and limitations**

- DRM/FLAG_SECURE content is not captured or bypassed.
- If the process is killed, lights-off is not guaranteed; the app reports RELEASE_UNCONFIRMED when it cannot confirm the device state.

**Changes to this policy**

- This policy may be updated as the app changes. The current version is published in this repository.

**Contact**

- Questions or issues: https://github.com/charli117/WLED-Grabber/issues
