package com.example.data.model

import androidx.annotation.Keep
import com.example.engine.MatchInput
import com.example.engine.PredictionRecordBuilder
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class MatchDoc(
  @DocumentId val id: String = "",
  val userId: String = "",
  val home: String = "",
  val away: String = "",
  val hg: Long = 0L,
  val ag: Long = 0L,
  val matchDate: String = "",
  val source: String = "manual",
  val createdAt: Timestamp? = null,
  val updatedAt: Timestamp? = null,
) {
  fun toMatchInput(): MatchInput = MatchInput(
    id = id,
    home = home,
    away = away,
    hg = hg.toInt(),
    ag = ag.toInt(),
    date = matchDate,
    source = source,
  )

  fun toCreateMap(uid: String): Map<String, Any> = mapOf(
    "userId" to uid,
    "home" to home.trim(),
    "away" to away.trim(),
    "hg" to hg,
    "ag" to ag,
    "matchDate" to matchDate.trim(),
    "source" to source,
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp(),
  )
}

@Keep
@IgnoreExtraProperties
data class PredictionRecordDoc(
  @DocumentId val id: String = "",
  val userId: String = "",
  val fixtureId: String = "",
  val homeTeam: String = "",
  val awayTeam: String = "",
  val kickoffUtc: String = "",
  val modelVersion: String = "",
  val createdUtc: String = "",
  val payloadJson: String = "",
  val sha256: String = "",
  val safePick: String = "",
  val smartPick: String = "",
  val exactScore: String = "",
  val createdAt: Timestamp? = null,
) {
  fun isDigestValid(): Boolean = PredictionRecordBuilder.verifyDigest(
    fixtureId = fixtureId,
    kickoffUtc = kickoffUtc,
    modelVersion = modelVersion,
    payloadJson = payloadJson,
    storedSha256 = sha256,
  )
}
