package com.example.engine

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Deterministic FOOTBALL IQ AI core prediction engine.
 * Pure mathematical port: no randomness, data in -> probabilities out.
 */
object FootballIqConstants {
  const val MAX_GOALS = 8
  const val MIN_MATCHES = 5
  const val HT_SHARE = 0.45
  const val PRIOR_GAMES = 6
  const val MODEL_VERSION = "football-iq-poisson-elo-v1"
}

object SeasonFilter {
  const val SEASON_26_27_LABEL = "2026/27"
  const val SEASON_26_27_START = "2026-07-01"
  const val SEASON_26_27_END = "2027-06-30"

  fun isSeason2627(isoDate: String): Boolean {
    val clean = isoDate.trim().take(10)
    return clean >= SEASON_26_27_START && clean <= SEASON_26_27_END
  }

  fun filterSeason2627(matches: List<MatchInput>): List<MatchInput> =
    matches.filter { isSeason2627(it.date) }
}

data class RecentMatchDetail(
  val date: String,
  val opponent: String,
  val isHome: Boolean,
  val goalsFor: Int,
  val goalsAgainst: Int,
  val result: String, // "W", "D", "L"
)

data class TeamRecentAnalysis(
  val team: String,
  val recentGames: List<RecentMatchDetail>, // newest -> oldest
  val wins: Int,
  val draws: Int,
  val losses: Int,
  val goalsFor: Int,
  val goalsAgainst: Int,
  val pointsPerGame: Double,
  val goalsForPerGame: Double,
  val goalsAgainstPerGame: Double,
)

data class MatchInput(
  val id: String = "",
  val home: String,
  val away: String,
  val hg: Int,
  val ag: Int,
  val date: String, // ISO YYYY-MM-DD
  val source: String = "manual",
)

enum class ConfidenceLevel(val label: String) {
  HIGH("High"),
  MEDIUM("Medium"),
  LOW("Low"),
}

data class TeamRatingSnapshot(
  val team: String,
  val elo: Double,
  val attackStrength: Double,
  val defenseStrength: Double,
  val sampleGames: Int,
  val totalMatches: Int,
  val recentForm: List<String>, // "W", "D", "L" (oldest -> newest)
)

data class SafeOption(
  val pick: String,
  val probability: Double,
  val confidence: ConfidenceLevel,
  val why: String,
)

data class SmartOption(
  val pick: String,
  val probability: Double,
  val confidence: ConfidenceLevel,
  val why: String,
)

data class AdvancedOption(
  val correctScore: String,
  val probability: Double,
  val halfTime: String,
  val halfTimeProbability: Double,
  val expectedTotalGoals: Double,
  val confidence: ConfidenceLevel,
  val why: String,
)

sealed interface PredictionOutcome {
  data class InsufficientData(
    val home: String,
    val away: String,
    val homeGames: Int,
    val awayGames: Int,
    val minRequired: Int = FootballIqConstants.MIN_MATCHES,
    val message: String = "Not enough information for a reliable prediction.",
  ) : PredictionOutcome

  data class Ready(
    val home: String,
    val away: String,
    val homeSample: Int,
    val awaySample: Int,
    val homeElo: Double,
    val awayElo: Double,
    val homeAttack: Double,
    val homeDefense: Double,
    val awayAttack: Double,
    val awayDefense: Double,
    val expectedHomeGoals: Double,
    val expectedAwayGoals: Double,
    val probHome: Double,
    val probDraw: Double,
    val probAway: Double,
    val bttsYes: Double,
    val over25: Double,
    val scoreMatrix: List<List<Double>>,
    val htProbs: Map<String, Double>,
    val option1Safe: SafeOption,
    val option2Smart: SmartOption,
    val option3Advanced: AdvancedOption,
    val canonicalPayloadJson: String,
  ) : PredictionOutcome
}

data class BacktestPoint(
  val index: Int,
  val date: String,
  val fixtureLabel: String,
  val cumulativeAccuracy: Double,
  val cumulativeBrier: Double,
  val predictedOutcome: Int,
  val actualOutcome: Int,
)

data class BacktestSummary(
  val evaluatedCount: Int,
  val warmupUsed: Int,
  val accuracy1x2: Double,
  val brier: Double,
  val logLoss: Double,
  val homeWinPredictedRate: Double,
  val homeWinActualRate: Double,
  val drawPredictedRate: Double,
  val drawActualRate: Double,
  val awayWinPredictedRate: Double,
  val awayWinActualRate: Double,
  val timeline: List<BacktestPoint>,
)

data class ImmutablePredictionPayload(
  val fixtureId: String,
  val homeTeam: String,
  val awayTeam: String,
  val kickoffUtc: String,
  val modelVersion: String,
  val createdUtc: String,
  val payloadJson: String,
  val sha256: String,
  val safePick: String,
  val smartPick: String,
  val exactScore: String,
)

object MathUtils {
  private fun factorial(k: Int): Double {
    var res = 1.0
    for (i in 2..k) {
      res *= i.toDouble()
    }
    return res
  }

  fun poisson(k: Int, lam: Double): Double {
    if (lam <= 0.0) return if (k == 0) 1.0 else 0.0
    return exp(-lam) * lam.pow(k.toDouble()) / factorial(k)
  }

  fun scoreMatrix(lh: Double, la: Double, n: Int = FootballIqConstants.MAX_GOALS): List<List<Double>> {
    val raw = List(n + 1) { i ->
      val pi = poisson(i, lh)
      List(n + 1) { j ->
        pi * poisson(j, la)
      }
    }
    val total = raw.sumOf { row -> row.sum() }
    if (total <= 0.0) return raw
    return raw.map { row -> row.map { p -> p / total } }
  }

  fun roundTo(value: Double, decimals: Int): Double {
    val factor = 10.0.pow(decimals.toDouble())
    return (value * factor).roundToInt() / factor
  }

  fun formatFixed(value: Double, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", value)

  fun sha256Hex(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }
}

class FootballIqModel(results: List<MatchInput>) {
  val sortedResults: List<MatchInput> = results.sortedWith(compareBy({ it.date }, { it.home }, { it.away }))
  val avgHomeGoals: Double
  val avgAwayGoals: Double
  val eloRatings: Map<String, Double>
  val allTeams: List<String>

  init {
    val n = sortedResults.size
    avgHomeGoals = if (n > 0) sortedResults.sumOf { it.hg }.toDouble() / n else 1.45
    avgAwayGoals = if (n > 0) sortedResults.sumOf { it.ag }.toDouble() / n else 1.15
    eloRatings = computeElo()
    allTeams = sortedResults
      .flatMap { listOf(it.home, it.away) }
      .distinct()
      .sorted()
  }

  private fun computeElo(k: Double = 20.0, homeAdv: Double = 60.0): Map<String, Double> {
    val elo = mutableMapOf<String, Double>()
    for (r in sortedResults) {
      val eh = elo[r.home] ?: 1500.0
      val ea = elo[r.away] ?: 1500.0
      val expH = 1.0 / (1.0 + 10.0.pow(-(eh + homeAdv - ea) / 400.0))
      val act = when {
        r.hg > r.ag -> 1.0
        r.hg == r.ag -> 0.5
        else -> 0.0
      }
      val g = 1.0 + ln(1.0 + abs(r.hg - r.ag).toDouble())
      elo[r.home] = eh + k * g * (act - expH)
      elo[r.away] = ea - k * g * (act - expH)
    }
    return elo
  }

  fun games(team: String, last: Int = 10): List<MatchInput> {
    val all = sortedResults.filter { it.home == team || it.away == team }
    return if (all.size <= last) all else all.subList(all.size - last, all.size)
  }

  fun strengths(team: String, last: Int = 10): Triple<Double, Double, Int> {
    val gs = games(team, last)
    var scored = 0.0
    var conceded = 0.0
    var expS = 0.0
    var expC = 0.0
    for (r in gs) {
      val isHome = r.home == team
      scored += if (isHome) r.hg.toDouble() else r.ag.toDouble()
      conceded += if (isHome) r.ag.toDouble() else r.hg.toDouble()
      expS += if (isHome) avgHomeGoals else avgAwayGoals
      expC += if (isHome) avgAwayGoals else avgHomeGoals
    }
    val w = gs.size.toDouble() / (gs.size + FootballIqConstants.PRIOR_GAMES)
    val att = if (expS > 0.0) 1.0 + w * ((scored / expS) - 1.0) else 1.0
    val dfn = if (expC > 0.0) 1.0 + w * ((conceded / expC) - 1.0) else 1.0
    return Triple(att, dfn, gs.size)
  }

  fun teamLeaderboard(recentWindow: Int = 10): List<TeamRatingSnapshot> {
    return allTeams.map { team ->
      val (att, dfn, sample) = strengths(team, last = recentWindow)
      val allTeamMatches = sortedResults.filter { it.home == team || it.away == team }
      val recent = allTeamMatches.takeLast(minOf(5, recentWindow)).map { m ->
        val gf = if (m.home == team) m.hg else m.ag
        val ga = if (m.home == team) m.ag else m.hg
        when {
          gf > ga -> "W"
          gf == ga -> "D"
          else -> "L"
        }
      }
      TeamRatingSnapshot(
        team = team,
        elo = eloRatings[team] ?: 1500.0,
        attackStrength = att,
        defenseStrength = dfn,
        sampleGames = sample,
        totalMatches = allTeamMatches.size,
        recentForm = recent,
      )
    }.sortedByDescending { it.elo }
  }

  fun analyzeRecentTeam(team: String, last: Int = 10): TeamRecentAnalysis {
    val gs = games(team, last = last)
    val details = gs.asReversed().map { m ->
      val isHome = m.home == team
      val gf = if (isHome) m.hg else m.ag
      val ga = if (isHome) m.ag else m.hg
      val res = when {
        gf > ga -> "W"
        gf == ga -> "D"
        else -> "L"
      }
      RecentMatchDetail(
        date = m.date,
        opponent = if (isHome) m.away else m.home,
        isHome = isHome,
        goalsFor = gf,
        goalsAgainst = ga,
        result = res,
      )
    }
    val w = details.count { it.result == "W" }
    val d = details.count { it.result == "D" }
    val l = details.count { it.result == "L" }
    val gfSum = details.sumOf { it.goalsFor }
    val gaSum = details.sumOf { it.goalsAgainst }
    val count = details.size.toDouble().coerceAtLeast(1.0)
    return TeamRecentAnalysis(
      team = team,
      recentGames = details,
      wins = w,
      draws = d,
      losses = l,
      goalsFor = gfSum,
      goalsAgainst = gaSum,
      pointsPerGame = if (details.isEmpty()) 0.0 else (w * 3 + d) / count,
      goalsForPerGame = if (details.isEmpty()) 0.0 else gfSum / count,
      goalsAgainstPerGame = if (details.isEmpty()) 0.0 else gaSum / count,
    )
  }

  fun headToHead(home: String, away: String, last: Int = 5): List<MatchInput> {
    return sortedResults
      .filter { (it.home == home && it.away == away) || (it.home == away && it.away == home) }
      .takeLast(last)
      .asReversed()
  }

  fun predict(home: String, away: String, recentWindow: Int = 10): PredictionOutcome {
    if (home.isBlank() || away.isBlank() || home == away) {
      return PredictionOutcome.InsufficientData(
        home = home,
        away = away,
        homeGames = 0,
        awayGames = 0,
        message = "Select two distinct teams with match history.",
      )
    }

    val (ah, dh, nh) = strengths(home, last = recentWindow)
    val (aa, da, na) = strengths(away, last = recentWindow)
    val minSample = minOf(nh, na)
    if (minSample < FootballIqConstants.MIN_MATCHES) {
      return PredictionOutcome.InsufficientData(
        home = home,
        away = away,
        homeGames = nh,
        awayGames = na,
        message = "Not enough information for a reliable prediction.",
      )
    }

    val homeElo = eloRatings[home] ?: 1500.0
    val awayElo = eloRatings[away] ?: 1500.0
    val f = 10.0.pow((homeElo - awayElo) / 1600.0)
    val lh = avgHomeGoals * ah * da * sqrt(f)
    val la = avgAwayGoals * aa * dh / sqrt(f)

    val m = MathUtils.scoreMatrix(lh, la, FootballIqConstants.MAX_GOALS)
    val n = m.size
    var pH = 0.0
    var pD = 0.0
    var btts = 0.0
    var over25 = 0.0
    var bestI = 0
    var bestJ = 0
    var bestP = -1.0

    for (i in 0 until n) {
      for (j in 0 until n) {
        val p = m[i][j]
        if (i > j) pH += p
        if (i == j) pD += p
        if (i >= 1 && j >= 1) btts += p
        if (i + j > 2) over25 += p
        if (p > bestP) {
          bestP = p
          bestI = i
          bestJ = j
        }
      }
    }
    val pA = (1.0 - pH - pD).coerceIn(0.0, 1.0)

    val ht = MathUtils.scoreMatrix(
      lh * FootballIqConstants.HT_SHARE,
      la * FootballIqConstants.HT_SHARE,
      6,
    )
    var htH = 0.0
    var htD = 0.0
    for (i in ht.indices) {
      for (j in ht.indices) {
        if (i > j) htH += ht[i][j]
        if (i == j) htD += ht[i][j]
      }
    }
    val htA = (1.0 - htH - htD).coerceIn(0.0, 1.0)
    val htProbs = linkedMapOf("Home" to htH, "Draw" to htD, "Away" to htA)

    val dc = linkedMapOf("1X" to (pH + pD), "X2" to (pD + pA), "12" to (pH + pA))
    val k1 = dc.maxBy { it.value }.key
    val p1 = dc[k1] ?: 0.0

    val mk = linkedMapOf(
      "$home win" to pH,
      "Draw" to pD,
      "$away win" to pA,
      "BTTS Yes" to btts,
      "BTTS No" to (1.0 - btts),
      "Over 2.5" to over25,
      "Under 2.5" to (1.0 - over25),
    )
    val k2 = mk.maxBy { it.value }.key
    val p2 = mk[k2] ?: 0.0

    val htBest = htProbs.maxBy { it.value }

    val opt1 = SafeOption(
      pick = k1,
      probability = MathUtils.roundTo(p1, 4),
      confidence = confidence(p1, minSample),
      why = "Highest of the three double-chance markets from a Poisson score matrix (xG ${MathUtils.formatFixed(lh, 2)}-${MathUtils.formatFixed(la, 2)}), adjusted by Elo.",
    )
    val opt2 = SmartOption(
      pick = k2,
      probability = MathUtils.roundTo(p2, 4),
      confidence = confidence(p2, minSample),
      why = "Most probable among 1X2, BTTS and Over/Under 2.5 for this fixture.",
    )
    val opt3 = AdvancedOption(
      correctScore = "$bestI-$bestJ",
      probability = MathUtils.roundTo(bestP, 4),
      halfTime = htBest.key,
      halfTimeProbability = MathUtils.roundTo(htBest.value, 4),
      expectedTotalGoals = MathUtils.roundTo(lh + la, 2),
      confidence = ConfidenceLevel.LOW,
      why = "Modal cell of the score matrix; exact scores carry low probability by nature.",
    )

    val canonicalJson = buildCanonicalPayloadJson(
      lh = lh,
      la = la,
      pH = pH,
      pD = pD,
      pA = pA,
      opt1 = opt1,
      opt2 = opt2,
      opt3 = opt3,
    )

    return PredictionOutcome.Ready(
      home = home,
      away = away,
      homeSample = nh,
      awaySample = na,
      homeElo = homeElo,
      awayElo = awayElo,
      homeAttack = ah,
      homeDefense = dh,
      awayAttack = aa,
      awayDefense = da,
      expectedHomeGoals = MathUtils.roundTo(lh, 2),
      expectedAwayGoals = MathUtils.roundTo(la, 2),
      probHome = MathUtils.roundTo(pH, 4),
      probDraw = MathUtils.roundTo(pD, 4),
      probAway = MathUtils.roundTo(pA, 4),
      bttsYes = btts,
      over25 = over25,
      scoreMatrix = m,
      htProbs = htProbs,
      option1Safe = opt1,
      option2Smart = opt2,
      option3Advanced = opt3,
      canonicalPayloadJson = canonicalJson,
    )
  }

  private fun escapeJson(value: String): String {
    val sb = StringBuilder(value.length + 2)
    sb.append('"')
    for (ch in value) {
      when (ch) {
        '"' -> sb.append("\\\"")
        '\\' -> sb.append("\\\\")
        '\b' -> sb.append("\\b")
        '\u000C' -> sb.append("\\f")
        '\n' -> sb.append("\\n")
        '\r' -> sb.append("\\r")
        '\t' -> sb.append("\\t")
        else -> if (ch < ' ') {
          sb.append("\\u%04x".format(ch.code))
        } else {
          sb.append(ch)
        }
      }
    }
    sb.append('"')
    return sb.toString()
  }

  private fun buildCanonicalPayloadJson(
    lh: Double,
    la: Double,
    pH: Double,
    pD: Double,
    pA: Double,
    opt1: SafeOption,
    opt2: SmartOption,
    opt3: AdvancedOption,
  ): String {
    // Keys sorted alphabetically to match Python json.dumps(..., sort_keys=True)
    val egAway = MathUtils.roundTo(la, 2)
    val egHome = MathUtils.roundTo(lh, 2)
    val rPh = MathUtils.roundTo(pH, 4)
    val rPd = MathUtils.roundTo(pD, 4)
    val rPa = MathUtils.roundTo(pA, 4)

    return buildString {
      append('{')
      append("\"expected_goals\":{\"away\":$egAway,\"home\":$egHome},")
      append("\"option_1_safe\":{")
      append("\"confidence\":${escapeJson(opt1.confidence.label)},")
      append("\"pick\":${escapeJson(opt1.pick)},")
      append("\"probability\":${opt1.probability},")
      append("\"why\":${escapeJson(opt1.why)}")
      append("},")
      append("\"option_2_smart\":{")
      append("\"confidence\":${escapeJson(opt2.confidence.label)},")
      append("\"pick\":${escapeJson(opt2.pick)},")
      append("\"probability\":${opt2.probability},")
      append("\"why\":${escapeJson(opt2.why)}")
      append("},")
      append("\"option_3_advanced\":{")
      append("\"confidence\":${escapeJson(opt3.confidence.label)},")
      append("\"correct_score\":${escapeJson(opt3.correctScore)},")
      append("\"expected_total_goals\":${opt3.expectedTotalGoals},")
      append("\"half_time\":${escapeJson(opt3.halfTime)},")
      append("\"half_time_probability\":${opt3.halfTimeProbability},")
      append("\"probability\":${opt3.probability},")
      append("\"why\":${escapeJson(opt3.why)}")
      append("},")
      append("\"probabilities\":{\"away\":$rPa,\"draw\":$rPd,\"home\":$rPh},")
      append("\"status\":\"ok\"")
      append('}')
    }
  }

  companion object {
    fun confidence(p: Double, sample: Int): ConfidenceLevel {
      if (sample >= 10 && p >= 0.75) return ConfidenceLevel.HIGH
      return if (p >= 0.55 && sample >= 7) ConfidenceLevel.MEDIUM else ConfidenceLevel.LOW
    }
  }
}

object CalibrationEngine {
  fun brier(probs: List<Triple<Double, Double, Double>>, outcomes: List<Int>): Double {
    if (probs.isEmpty()) return 0.0
    var tot = 0.0
    for (idx in probs.indices) {
      val p = probs[idx]
      val o = outcomes[idx]
      tot += (p.first - if (o == 0) 1.0 else 0.0).pow(2)
      tot += (p.second - if (o == 1) 1.0 else 0.0).pow(2)
      tot += (p.third - if (o == 2) 1.0 else 0.0).pow(2)
    }
    return tot / probs.size
  }

  fun logLoss(
    probs: List<Triple<Double, Double, Double>>,
    outcomes: List<Int>,
    eps: Double = 1e-15,
  ): Double {
    if (probs.isEmpty()) return 0.0
    var tot = 0.0
    for (idx in probs.indices) {
      val p = probs[idx]
      val pOutcome = when (outcomes[idx]) {
        0 -> p.first
        1 -> p.second
        else -> p.third
      }
      tot += -ln(maxOf(pOutcome, eps))
    }
    return tot / probs.size
  }

  fun backtest(results: List<MatchInput>, warmup: Int = 100): BacktestSummary? {
    val sorted = results.sortedWith(compareBy({ it.date }, { it.home }, { it.away }))
    if (warmup >= sorted.size) return null

    val probs = mutableListOf<Triple<Double, Double, Double>>()
    val outcomes = mutableListOf<Int>()
    val timeline = mutableListOf<BacktestPoint>()
    var hits = 0

    for (i in warmup until sorted.size) {
      val r = sorted[i]
      val pred = FootballIqModel(sorted.subList(0, i)).predict(r.home, r.away)
      if (pred !is PredictionOutcome.Ready) continue

      val pTriple = Triple(pred.probHome, pred.probDraw, pred.probAway)
      val actual = when {
        r.hg > r.ag -> 0
        r.hg == r.ag -> 1
        else -> 2
      }
      val predicted = listOf(pTriple.first, pTriple.second, pTriple.third)
        .withIndex()
        .maxBy { it.value }
        .index

      if (predicted == actual) hits++
      probs.add(pTriple)
      outcomes.add(actual)

      timeline.add(
        BacktestPoint(
          index = probs.size,
          date = r.date,
          fixtureLabel = "${r.home} vs ${r.away} (${r.hg}-${r.ag})",
          cumulativeAccuracy = hits.toDouble() / probs.size,
          cumulativeBrier = brier(probs, outcomes),
          predictedOutcome = predicted,
          actualOutcome = actual,
        )
      )
    }

    if (probs.isEmpty()) return null

    val n = probs.size.toDouble()
    return BacktestSummary(
      evaluatedCount = probs.size,
      warmupUsed = warmup,
      accuracy1x2 = hits / n,
      brier = brier(probs, outcomes),
      logLoss = logLoss(probs, outcomes),
      homeWinPredictedRate = probs.sumOf { it.first } / n,
      homeWinActualRate = outcomes.count { it == 0 } / n,
      drawPredictedRate = probs.sumOf { it.second } / n,
      drawActualRate = outcomes.count { it == 1 } / n,
      awayWinPredictedRate = probs.sumOf { it.third } / n,
      awayWinActualRate = outcomes.count { it == 2 } / n,
      timeline = timeline,
    )
  }
}

object PredictionRecordBuilder {
  private val isoUtcPatterns = listOf(
    "yyyy-MM-dd'T'HH:mm:ss'Z'",
    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
    "yyyy-MM-dd'T'HH:mm'Z'",
  )

  fun formatUtcIso(millis: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
      timeZone = TimeZone.getTimeZone("UTC")
    }
    return fmt.format(Date(millis))
  }

  fun parseUtcIsoMillis(isoUtc: String): Long? {
    val clean = isoUtc.trim()
    for (pattern in isoUtcPatterns) {
      try {
        val fmt = SimpleDateFormat(pattern, Locale.US).apply {
          timeZone = TimeZone.getTimeZone("UTC")
          isLenient = false
        }
        val parsed = fmt.parse(clean)
        if (parsed != null) return parsed.time
      } catch (_: Exception) {
        // try next pattern
      }
    }
    return null
  }

  fun create(
    fixtureId: String,
    kickoffUtc: String,
    prediction: PredictionOutcome.Ready,
    modelVersion: String = FootballIqConstants.MODEL_VERSION,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<ImmutablePredictionPayload> {
    return try {
      val cleanFixture = fixtureId.trim()
      require(cleanFixture.isNotBlank()) { "Fixture ID cannot be blank." }
      val cleanKickoff = kickoffUtc.trim()
      val kickoffMillis = parseUtcIsoMillis(cleanKickoff)
        ?: return Result.failure(
          IllegalArgumentException("Kickoff must be ISO-8601 UTC (e.g. 2026-11-21T15:00:00Z).")
        )
      if (nowMillis >= kickoffMillis) {
        return Result.failure(IllegalStateException("Cannot save a pre-match prediction after kickoff."))
      }
      val createdUtc = formatUtcIso(nowMillis)
      val body = prediction.canonicalPayloadJson
      val digest = MathUtils.sha256Hex("$cleanFixture|$cleanKickoff|$modelVersion|$body")
      Result.success(
        ImmutablePredictionPayload(
          fixtureId = cleanFixture,
          homeTeam = prediction.home,
          awayTeam = prediction.away,
          kickoffUtc = cleanKickoff,
          modelVersion = modelVersion,
          createdUtc = createdUtc,
          payloadJson = body,
          sha256 = digest,
          safePick = prediction.option1Safe.pick,
          smartPick = prediction.option2Smart.pick,
          exactScore = prediction.option3Advanced.correctScore,
        )
      )
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  fun verifyDigest(
    fixtureId: String,
    kickoffUtc: String,
    modelVersion: String,
    payloadJson: String,
    storedSha256: String,
  ): Boolean {
    val expected = MathUtils.sha256Hex("$fixtureId|$kickoffUtc|$modelVersion|$payloadJson")
    return expected == storedSha256
  }
}
