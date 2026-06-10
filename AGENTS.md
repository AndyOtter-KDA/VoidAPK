# Void Chat Code Base Rules & Compliance

## Strict Core Mandates
1. **ZERO Mocks, Fakes, or Simulations**: Strictly forbidden to use mock classes, simulated objects, local mock data fallbacks, or sandbox modes.
2. **Real Firestore Only**: Every database transaction, query, insert, update, or listener MUST utilize real Firebase Firestore operations.
3. **No Fallback Checks**: No conditional routing like `if (useMock)` or `if (firestoreAvailable)`. If Firebase connectivity is absent or fails, propagate the error directly to the UI layer to alert the user.
4. **No Hardcoded Accounts**: No hardcoded test values, mock handles, or static test users (such as `"mockA"`, `"mockB"`, or `"testUser"`).
5. **Operation Logging**: Every single Firebase Firestore operation, success, and failure must emit descriptive and tracing diagnostic logs via `Log.d("VoidFirestore", "...")` or `Log.e("VoidFirestore", "...")`.
