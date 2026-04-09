# Privacy Policy

**HAdo — Home Assistant To-Do Widget**
**Effective Date:** April 9, 2026
**Developer:** IT-BAER (Bruno Miglar)
**Contact:** admin@it-baer.net

## Overview

HAdo is a privacy-first Android widget app for managing Home Assistant To-Do lists. The app communicates directly with your personal Home Assistant instance and does not collect, transmit, or share any data with third parties.

## Data Collection

### What data does HAdo access?

- **Home Assistant server URL:** The URL of your personal Home Assistant instance, entered by you during setup.
- **Authentication tokens:** OAuth2 access and refresh tokens issued by your Home Assistant instance, used to authenticate API requests.
- **To-Do list data:** Your to-do list names, items, descriptions, due dates, and completion status — fetched directly from your Home Assistant instance.

### What data does HAdo store on your device?

- **Server URL and authentication tokens:** Stored securely in Android's EncryptedSharedPreferences.
- **Widget configuration:** Your widget layout preferences (selected lists, font size, background opacity, etc.) stored in SharedPreferences.
- **List icon overrides:** Custom emoji or image icons you assign to lists, stored locally in the app's private storage.
- **Cached list data:** A local cache of your to-do items for fast widget rendering, stored in Glance widget preferences.

### What data does HAdo NOT collect?

- No personal information (name, email, phone number)
- No device identifiers (IMEI, advertising ID, hardware serial)
- No location data
- No usage analytics or telemetry
- No crash reports sent to external services
- No cookies or web tracking

## Data Transmission

All communication between HAdo and your Home Assistant instance occurs over **HTTPS** (or the protocol you configure). Data is transmitted exclusively between your Android device and your Home Assistant server. No data is sent to IT-BAER, Google, or any third party.

## Third-Party Services

HAdo does **not** integrate any third-party SDKs, analytics platforms, advertising networks, or cloud services. The app has zero external dependencies beyond your own Home Assistant instance.

## Data Sharing

HAdo does **not** share, sell, or transfer any user data to third parties under any circumstances.

## Data Retention and Deletion

- All data is stored locally on your device.
- You can delete all app data at any time by clearing the app's storage in Android Settings, or by uninstalling the app.
- Logging out from within the app removes stored authentication tokens.
- No data is retained on any external server by the developer.

## Permissions

HAdo requests only one Android permission:

| Permission | Purpose |
|---|---|
| `INTERNET` | Required to communicate with your Home Assistant instance over your network. |

## Children's Privacy

HAdo is not directed at children under the age of 13 and does not knowingly collect any data from children.

## Changes to This Policy

We may update this Privacy Policy from time to time. Changes will be posted to this page with an updated effective date. Continued use of the app after changes constitutes acceptance of the updated policy.

## Contact

If you have questions or concerns about this Privacy Policy, contact us at:

**Email:** admin@it-baer.net
**GitHub:** https://github.com/IT-BAER/hado
