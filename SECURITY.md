# Security Architecture & Limitations

## Overview
HisabKitab enforces a multi-layer security posture across Firestore Security Rules, Android client-side validation, and defensive calculation guards.

---

## 1. Guarantees Enforced in Firestore Security Rules (`firestore.rules`)

* **Authentication & Authorization:**
  * Only authenticated group members can read group data, members, expenses, settlements, and activities.
  * Expenses can only be created by the authenticated user (`createdByUid == request.auth.uid`).
* **Membership Validation:**
  * Payer (`paidByUid`) must be an existing group member.
  * All participants (`participantIds`) must be existing group members.
* **Structural & Field Validation:**
  * `description`: Non-empty string with maximum length of 200 characters.
  * `amountPaise`: Positive 64-bit integer (`> 0`).
  * `splitType`: Constrained strictly to `'equal'` or `'custom'`.
  * `participantIds`: Non-empty list.
* **Key-Set Parity & Duplicate Prevention:**
  * `sharesByUid` must be a map whose keys match `participantIds` exactly (`hasAll` and `hasOnly`).
  * `participantIds.size() == sharesByUid.keys().size()` prevents duplicate participant IDs in the list.

---

## 2. Platform Limitation in Firestore Security Rules

Firestore Security Rules (Rules version 2) intentionally omit general-purpose imperative loops, recursive functions, map value iterators (`Map.values()`), and mathematical reduction operators (`sum()`).

> **Platform Limitation:**
> Firestore Security Rules cannot dynamically iterate across arbitrary map keys to verify that all values in `sharesByUid` are non-negative integers and that their sum equals `amountPaise`.

Attempting to enforce dynamic map summation in Rules requires either brittle fixed-arity workarounds or invalid rules syntax that fails evaluation.

---

## 3. Client-Side Defensive Measures

To mitigate this platform limitation in the current MVP architecture:

1. **Pre-Write Validation:**
   * `SplitCalculator.validateCustomShares()` rejects custom share maps where keys do not match participants, values are negative, all values are zero, or the sum does not equal `amountPaise`.
   * Participant IDs are normalized to distinct non-blank strings before write batches are prepared.
2. **Defensive Ledger Protection (Read Guard):**
   * `BalanceCalculator.calculateGroupBalances()` and `GroupViewModel` filter all incoming expenses through `SplitCalculator.isValidExpense()`.
   * Any malformed custom-split document (e.g. injected by a compromised client with negative shares, key mismatches, or sum deviations) is discarded and will **not** affect group balances or user ledger totals.

---

## 4. Recommended Production Architecture: Firebase Callable Cloud Functions

To achieve end-to-end cryptographic and business-logic enforcement for arbitrary split math:

1. **Trusted Write Path:**
   * Migrate expense creation and settlement recording to **Firebase Callable Cloud Functions** (or Cloud Run).
   * The Cloud Function executes in a trusted Node.js/Kotlin environment where arbitrary-sized participant maps can be summed and validated with full integer precision before writing to Firestore using the Firebase Admin SDK.
2. **Lock Client Writes:**
   * Once Cloud Functions are in place, set `allow create: false` for direct client writes on `/groups/{groupId}/expenses` and `/groups/{groupId}/settlements`.
   * Firestore Security Rules then only need to govern read access.
