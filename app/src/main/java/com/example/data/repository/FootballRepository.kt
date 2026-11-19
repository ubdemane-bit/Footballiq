package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.MatchDoc
import com.example.data.model.PredictionRecordDoc
import com.example.engine.ImmutablePredictionPayload
import com.example.engine.MatchInput
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
  CREATE("create"),
  UPDATE("update"),
  DELETE("delete"),
  LIST("list"),
  GET("get"),
  WRITE("write"),
}

fun handleFirestoreError(
  exception: Throwable,
  operationType: OperationType,
  path: String?,
): String {
  val causes = generateSequence(exception) { it.cause }
  val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
    ?: causes.firstOrNull { it !is CancellationException }
    ?: exception

  val currentUser = Firebase.auth.currentUser
  val providerInfoList = currentUser?.providerData?.map { provider ->
    JSONObject().apply {
      put("providerId", provider.providerId)
      put("email", provider.email)
    }
  } ?: emptyList()

  val authInfoJson = JSONObject().apply {
    put("userId", currentUser?.uid)
    put("email", currentUser?.email)
    put("emailVerified", currentUser?.isEmailVerified)
    put("tenantId", currentUser?.tenantId)
    put("providerInfo", JSONArray(providerInfoList))
  }

  val errorInfoJson = JSONObject().apply {
    put("error", rootError.message ?: rootError.toString())
    put("operationType", operationType.value)
    put("path", path)
    put("authInfo", authInfoJson)
  }

  val jsonString = errorInfoJson.toString()
  Log.e("FirestoreError", "Firestore Error: $jsonString")
  return jsonString
}

class FootballRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  fun observeMatches(userId: String): Flow<List<MatchDoc>> = flow {
    val uid = requireUserId()
    check(uid == userId) { "Signed-in user mismatch" }
    val path = "users/$uid/matches"
    emitAll(
      db.collection("users").document(uid).collection("matches")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            MatchDoc::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          ).sortedWith(compareByDescending<MatchDoc> { it.matchDate }.thenBy { it.home })
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  fun observePredictionRecords(userId: String): Flow<List<PredictionRecordDoc>> = flow {
    val uid = requireUserId()
    check(uid == userId) { "Signed-in user mismatch" }
    val path = "users/$uid/predictionRecords"
    emitAll(
      db.collection("users").document(uid).collection("predictionRecords")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            PredictionRecordDoc::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          ).sortedByDescending { it.createdUtc }
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun fetchPredictionRecords(userId: String): Result<List<PredictionRecordDoc>> {
    var colPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/predictionRecords"
    return try {
      val uid = requireUserId()
      check(uid == userId) { "Signed-in user mismatch" }
      colPath = "users/$uid/predictionRecords"
      val snapshot = db.collection("users").document(uid).collection("predictionRecords")
        .get()
        .await()
      val items = snapshot.toObjects(
        PredictionRecordDoc::class.java,
        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
      ).sortedByDescending { it.createdUtc }
      Result.success(items)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.LIST, colPath)
      Result.failure(e)
    }
  }

  suspend fun addMatch(match: MatchInput): Result<Unit> {
    var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/matches"
    return try {
      val uid = requireUserId()
      val homeClean = match.home.trim().take(60)
      val awayClean = match.away.trim().take(60)
      require(homeClean.isNotEmpty() && awayClean.isNotEmpty()) { "Team names cannot be blank." }
      require(homeClean != awayClean) { "Home and away teams must be different." }
      require(match.hg in 0..30 && match.ag in 0..30) { "Goals must be between 0 and 30." }
      require(Regex("""^\d{4}-\d{2}-\d{2}$""").matches(match.date.trim())) {
        "Date must be YYYY-MM-DD."
      }
      val validSource = if (match.source in setOf("manual", "api", "seed")) match.source else "manual"

      val docRef = db.collection("users").document(uid).collection("matches").document()
      docPath = docRef.path
      val payload = mapOf(
        "userId" to uid,
        "home" to homeClean,
        "away" to awayClean,
        "hg" to match.hg.toLong(),
        "ag" to match.ag.toLong(),
        "matchDate" to match.date.trim(),
        "source" to validSource,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      docRef.set(payload).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun importMatchesBatch(matches: List<MatchInput>): Result<Int> {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    val colPath = "users/$uid/matches"
    return try {
      val validMatches = matches.filter {
        val h = it.home.trim()
        val a = it.away.trim()
        h.isNotEmpty() && h.length <= 60 &&
          a.isNotEmpty() && a.length <= 60 &&
          h != a &&
          it.hg in 0..30 &&
          it.ag in 0..30 &&
          Regex("""^\d{4}-\d{2}-\d{2}$""").matches(it.date.trim())
      }
      if (validMatches.isEmpty()) {
        return Result.failure(IllegalArgumentException("No valid matches to import."))
      }

      val chunks = validMatches.chunked(400)
      for (chunk in chunks) {
        val batch = db.batch()
        for (m in chunk) {
          val docRef = db.collection("users").document(uid).collection("matches").document()
          val validSource = if (m.source in setOf("manual", "api", "seed")) m.source else "api"
          batch.set(
            docRef,
            mapOf(
              "userId" to uid,
              "home" to m.home.trim().take(60),
              "away" to m.away.trim().take(60),
              "hg" to m.hg.toLong(),
              "ag" to m.ag.toLong(),
              "matchDate" to m.date.trim(),
              "source" to validSource,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
        batch.commit().await()
      }
      Result.success(validMatches.size)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, colPath)
      Result.failure(e)
    }
  }

  suspend fun deleteMatch(matchId: String): Result<Unit> {
    var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/matches/$matchId"
    return try {
      val uid = requireUserId()
      require(matchId.isNotBlank()) { "Match ID cannot be blank." }
      val docRef = db.collection("users").document(uid).collection("matches").document(matchId)
      docPath = docRef.path
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docPath)
      Result.failure(e)
    }
  }

  suspend fun clearMatches(matchIds: List<String>): Result<Unit> {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    val colPath = "users/$uid/matches"
    return try {
      for (chunk in matchIds.filter { it.isNotBlank() }.chunked(400)) {
        val batch = db.batch()
        for (id in chunk) {
          batch.delete(db.collection("users").document(uid).collection("matches").document(id))
        }
        batch.commit().await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, colPath)
      Result.failure(e)
    }
  }

  /**
   * Append-only INSERT of an immutable pre-kickoff PredictionRecord.
   */
  suspend fun lockPredictionRecord(record: ImmutablePredictionPayload): Result<Unit> {
    var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/predictionRecords"
    return try {
      val uid = requireUserId()
      val docRef = db.collection("users").document(uid).collection("predictionRecords").document()
      docPath = docRef.path
      val payload = mapOf(
        "userId" to uid,
        "fixtureId" to record.fixtureId.trim().take(128),
        "homeTeam" to record.homeTeam.trim().take(60),
        "awayTeam" to record.awayTeam.trim().take(60),
        "kickoffUtc" to record.kickoffUtc.trim().take(40),
        "modelVersion" to record.modelVersion.trim().take(64),
        "createdUtc" to record.createdUtc.trim().take(40),
        "payloadJson" to record.payloadJson.take(16384),
        "sha256" to record.sha256,
        "safePick" to record.safePick.take(40),
        "smartPick" to record.smartPick.take(80),
        "exactScore" to record.exactScore.take(12),
        "createdAt" to FieldValue.serverTimestamp(),
      )
      docRef.set(payload).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docPath)
      Result.failure(e)
    }
  }
}
