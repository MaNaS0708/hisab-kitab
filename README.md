# HisabKitab (Android MVP)

HisabKitab is an Android group expense-sharing and ledger balancing application built with Kotlin, ViewBinding, Navigation Component, and Cloud Firestore.

---

## Architecture

* **UI Layer:** XML layouts, ViewBinding, Fragments, Navigation Component.
* **State Management:** Android ViewModel with Kotlin Coroutines (`viewModelScope`) and `StateFlow`.
* **Data Layer:** Cloud Firestore real-time listeners (`callbackFlow`) and batched ledger writes.
* **Calculations:** Deterministic equal and custom share calculation and net balance consolidation via `SplitCalculator` and `BalanceCalculator`.

---

## Firestore Rules Deployment

> [!IMPORTANT]
> This repository contains the Firestore security rules in `firestore.rules`. There is **no Firebase Hosting or automated Firebase CLI configuration** in this repository. The rules must be deployed manually.

### Option 1: Firebase Console (Recommended for MVP)
1. Open the [Firebase Console](https://console.firebase.google.com/).
2. Select your Firebase project.
3. In the left navigation, go to **Build** → **Firestore Database**.
4. Click on the **Rules** tab.
5. Copy the entire contents of [`firestore.rules`](file:///home/minmin/AndroidStudioProjects/HisabKitab/firestore.rules) and paste them into the editor.
6. Click **Publish**.

### Option 2: Firebase CLI
If you have the Firebase CLI installed locally:
```bash
firebase deploy --only firestore:rules --project <your-firebase-project-id>
```

---

## Testing & Build

To assemble the debug build and run all unit tests:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest --console=plain
```

Unit tests cover:
* `SplitCalculatorTest`: Equal splits, exact paise rounding, custom shares validation, duplicate participant normalization, key-participant mismatches, and empty keys.
* `BalanceCalculatorTest`: Equal/custom splits, settlement offsets, summary consolidation, and rejection of malformed custom expenses.
* `GroupViewModelTest`: Active job replacement, cancellation of inactive screen listeners, stale emission guards, and operation state transitions.

For security guarantees and platform limitations, see [SECURITY.md](file:///home/minmin/AndroidStudioProjects/HisabKitab/SECURITY.md).
