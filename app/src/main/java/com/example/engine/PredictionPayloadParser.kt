package com.example.engine

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

@JsonClass(generateAdapter = true)
data class ExpectedGoalsJson(
  val home: Double = 0.0,
  val away: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class ProbabilitiesJson(
  val home: Double = 0.0,
  val draw: Double = 0.0,
  val away: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class OptionSimpleJson(
  val pick: String = "",
  val probability: Double = 0.0,
  val confidence: String = "Low",
  val why: String = "",
)

@JsonClass(generateAdapter = true)
data class OptionAdvancedJson(
  @Json(name = "correct_score") val correctScore: String = "",
  val probability: Double = 0.0,
  @Json(name = "half_time") val halfTime: String = "",
  @Json(name = "half_time_probability") val halfTimeProbability: Double = 0.0,
  @Json(name = "expected_total_goals") val expectedTotalGoals: Double = 0.0,
  val confidence: String = "Low",
  val why: String = "",
)

@JsonClass(generateAdapter = true)
data class ParsedPredictionPayload(
  val status: String = "",
  @Json(name = "expected_goals") val expectedGoals: ExpectedGoalsJson = ExpectedGoalsJson(),
  val probabilities: ProbabilitiesJson = ProbabilitiesJson(),
  @Json(name = "option_1_safe") val option1Safe: OptionSimpleJson = OptionSimpleJson(),
  @Json(name = "option_2_smart") val option2Smart: OptionSimpleJson = OptionSimpleJson(),
  @Json(name = "option_3_advanced") val option3Advanced: OptionAdvancedJson = OptionAdvancedJson(),
)

object PredictionPayloadParser {
  private val moshi: Moshi = Moshi.Builder().build()
  private val adapter = moshi.adapter(ParsedPredictionPayload::class.java)

  fun parse(payloadJson: String): ParsedPredictionPayload? {
    if (payloadJson.isBlank()) return null
    return try {
      adapter.fromJson(payloadJson)
    } catch (_: Exception) {
      null
    }
  }
}
