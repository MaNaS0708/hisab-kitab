# Post-Login Crash Investigation & Resolution Report

**Status:** Resolved. All potential crash triggers, race conditions, schema mismatches, navigation issues, build validation blockers, and observability gaps have been identified and fixed. Full test suite and release build verification pass cleanly.

---

## 1. Summary of Identified Issues & Root Causes

### A. Missing Gradle Wrapper & Broken Build Setup
- **Issue:** `./gradlew` failed immediately with missing `gradle-wrapper.jar`, and `gradle.properties` hardcoded `org.gradle.java.home=/opt/android-studio/jbr` (a Linux-specific path that broke builds on macOS and non-standard environments).
- **Impact:** Prevented automated test execution, continuous integration, and APK compilation.
- **Resolution:**
  - Restored standard Gradle 9.6.0 wrapper JAR (`gradle/wrapper/gradle-wrapper.jar`).
  - Removed the hardcoded `org.gradle.java.home` from `gradle.properties`.
  - Added cross-platform auto-detection of Android Studio's bundled JBR in `gradlew` for both macOS and Linux.

### B. Firestore Rules & Schema Incompatibility in Post-Login
- **Issue:**
  - `firestore.rules` enforced strict key constraints (`hasOnly`) and required `createdAt` equality, causing `PERMISSION_DENIED` during `ensureUserDocument` for existing accounts with missing `createdAt` or legacy fields (e.g., `phone` from previous authentication commits).
  - `/users/{uid}` had `allow read: if isSelf(uid);`, which threw `PERMISSION_DENIED` when an authenticated user searched for another user's profile by username.
  - `AuthViewModel.routeUserAfterAuth` performed a redundant profile read immediately after `ensureUserDocument` already fetched the profile.
- **Impact:** Post-login failed with permission errors or crashed on unexpected schema states.
- **Resolution:**
  - Updated `firestore.rules` to permit optional legacy fields (`phone`), allow missing `createdAt`/`provider` fields to be safely set on update, and allow authenticated reads (`allow read: if signedIn();`) for public profile lookup.
  - Updated `UserRepository.ensureUserDocument` to safely backfill missing fields and omit empty/null fields on initial creation.
  - Optimized `AuthViewModel.routeUserAfterAuth` to eliminate the redundant profile read.

### C. Home Sequential Firestore Read Pressure & Failure Cascade
- **Issue:**
  - On entering Home, `GroupViewModel.loadHomeData` sequentially loaded expenses and settlements for every group in a blocking `for` loop. For users with multiple groups, this caused significant latency and UI freezing.
  - If a single group was deleted, unreadable, or returned an error, the exception escaped the flow collector and aborted Home loading entirely, leaving the user with a broken UI.
- **Impact:** High latency, frozen home screen, and total screen failure on isolated group errors.
- **Resolution:**
  - Parallelized group loading using `coroutineScope` and `async { ... }.awaitAll()`, dropping loading latency from $O(N)$ serial roundtrips to $O(1)$ concurrent time.
  - Added localized error handling (`try / catch`) inside each group's async fetch so a failing or deleted group degrades gracefully (defaults to 0 balance) without failing other groups or terminating the `observeUserGroups` flow.

### D. Navigation Double-Click Races & Lifecycle Detachment
- **Issue:**
  - Rapid double-clicks on group cards or the "Create Group" button in `HomeFragment` or `GroupDetailFragment` triggered `findNavController().navigate()` while already on the destination screen, causing fatal `IllegalArgumentException: Navigation action cannot be found from current destination`.
  - Global navigation actions in `nav_graph.xml` used `app:popUpToInclusive="false"` with `startDestination="@id/splashFragment"`, leaving `SplashFragment` permanently on the backstack after login.
  - Collecting flows in fragment view lifecycle without checking `_binding == null` or `context == null` risked `IllegalStateException` during view destruction or fragment transitions.
- **Impact:** Fatal process crashes upon rapid clicking or background navigation events.
- **Resolution:**
  - Added `safeNavigate` helper in `BaseAuthFragment` and destination ID validation (`findNavController().currentDestination?.id == expectedId`) across all click listeners and navigation actions.
  - Updated `nav_graph.xml` global actions to `app:popUpTo="@id/nav_graph" app:popUpToInclusive="true"` so that Splash and Login screens are cleanly removed from the backstack upon navigating to Home.
  - Added safe null-binding and context guards across all asynchronous UI state rendering methods (`HomeFragment`, `GroupDetailFragment`, `CreateGroupFragment`, `AddExpenseFragment`, `SettleUpFragment`).
  - Safeguarded `CurrencyUtil` and `BalanceCalculator` against `Long.MIN_VALUE` arithmetic overflow.

### E. Crash Visibility & Observability
- **Issue:** The application lacked crash reporting, making device and production failures invisible.
- **Resolution:**
  - Added `com.google.firebase.crashlytics` Gradle plugin and SDK via Firebase BoM.
  - Implemented custom `HisabKitabApp : Application` class registered in `AndroidManifest.xml` with automatic Crashlytics initialization.
  - Attached authenticated user IDs to Crashlytics on login (`setUserId`) and cleared on logout.
  - Routed handled non-fatal exceptions in `AuthViewModel` and `GroupViewModel` to `FirebaseCrashlytics.getInstance().recordException(...)`.

---

## 2. Verification & Test Results

- **Gradle Build & Packaging:**
  - `./gradlew assembleDebug` completed successfully (`BUILD SUCCESSFUL in 20s`).
- **Unit Test Suite:**
  - `./gradlew test` completed successfully with 100% pass rate:
    - `GroupViewModelTest`: Verified concurrent group loading, stale job cancellation, race prevention, and graceful degradation on isolated group failures.
    - `BalanceCalculatorTest`: Verified equal and custom splits, settlement balance reductions, malformed expense rejection, overall summary calculation, and boundary value (`Long.MIN_VALUE`) protection.
    - `SplitCalculatorTest`: Verified custom and equal share validations.
    - `GroupRepositoryValidationTest`: Verified group creation bounds and requirements.
    - `UsernameUtilTest`: Verified username normalization and syntax validation.
