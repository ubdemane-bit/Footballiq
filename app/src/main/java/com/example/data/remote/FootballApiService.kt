package com.example.data.remote

import com.example.engine.MatchInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class LeaguePreset(
  val id: String,
  val name: String,
  val country: String,
  val sportsDbLeagueId: String,
)

object FootballLeagues {
  val presets = listOf(
    LeaguePreset("epl", "Premier League", "England", "4328"),
    LeaguePreset("laliga", "La Liga", "Spain", "4335"),
    LeaguePreset("bundesliga", "Bundesliga", "Germany", "4331"),
    LeaguePreset("seriea", "Serie A", "Italy", "4332"),
    LeaguePreset("ligue1", "Ligue 1", "France", "4334"),
  )
}

class FootballApiService(
  private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build(),
) {
  /**
   * Fetches completed 2026/27 season fixtures (`eventsseason.php?id=...&s=2026-2027`)
   * merged with `eventspastleague.php` from TheSportsDB public API.
   */
  suspend fun fetchLeagueFromSportsDb(
    leagueId: String,
    season: String = "2026-2027",
  ): Result<List<MatchInput>> =
    withContext(Dispatchers.IO) {
      try {
        val cleanId = leagueId.trim()
        val urls = listOf(
          "https://www.thesportsdb.com/api/v1/json/3/eventsseason.php?id=$cleanId&s=${season.trim()}",
          "https://www.thesportsdb.com/api/v1/json/3/eventspastleague.php?id=$cleanId",
        )
        val merged = linkedMapOf<String, MatchInput>()
        var lastHttpError: String? = null

        for (url in urls) {
          val request = Request.Builder().url(url).get().build()
          runCatching {
            client.newCall(request).execute().use { response ->
              if (!response.isSuccessful) {
                lastHttpError = "API error ${response.code}: ${response.message}"
              } else {
                val body = response.body?.string().orEmpty()
                parseMatchesJson(body, defaultSource = "api").forEach { m ->
                  val key = "${m.date}|${m.home.lowercase()}|${m.away.lowercase()}"
                  merged.putIfAbsent(key, m)
                }
              }
            }
          }.onFailure { err ->
            lastHttpError = err.localizedMessage
          }
        }

        val allMatches = merged.values.sortedBy { it.date }
        val season2627Matches = allMatches.filter {
          it.date >= "2026-07-01" && it.date <= "2027-06-30"
        }
        val finalResult = if (season2627Matches.isNotEmpty()) season2627Matches else allMatches
        if (finalResult.isEmpty()) {
          Result.failure(
            IllegalStateException(lastHttpError ?: "No completed match scores returned from API.")
          )
        } else {
          Result.success(finalResult)
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  /**
   * Fetches matches from any user-supplied JSON REST endpoint (supports optional X-Auth-Token header).
   */
  suspend fun fetchCustomEndpoint(url: String, apiTokenHeader: String? = null): Result<List<MatchInput>> =
    withContext(Dispatchers.IO) {
      try {
        val cleanUrl = url.trim()
        require(cleanUrl.startsWith("https://") || cleanUrl.startsWith("http://")) {
          "Endpoint URL must start with https:// or http://"
        }
        val reqBuilder = Request.Builder().url(cleanUrl).get()
        if (!apiTokenHeader.isNullOrBlank()) {
          reqBuilder.addHeader("X-Auth-Token", apiTokenHeader.trim())
        }
        client.newCall(reqBuilder.build()).execute().use { response ->
          if (!response.isSuccessful) {
            return@withContext Result.failure(IllegalStateException("HTTP ${response.code}: ${response.message}"))
          }
          val body = response.body?.string().orEmpty()
          val parsed = parseMatchesJson(body, defaultSource = "api")
          if (parsed.isEmpty()) {
            Result.failure(IllegalStateException("No valid Match objects found in JSON response."))
          } else {
            Result.success(parsed)
          }
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  /**
   * Parses multiple standard Football API JSON shapes into MatchInput objects:
   * 1. Simple array or {"matches": [...]} with {home, away, hg, ag, date}
   * 2. football-data.org v4 {"matches": [{homeTeam:{name}, awayTeam:{name}, score:{fullTime:{home, away}}, utcDate}]}
   * 3. TheSportsDB {"events": [{strHomeTeam, strAwayTeam, intHomeScore, intAwayScore, dateEvent}]}
   */
  fun parseMatchesJson(rawJson: String, defaultSource: String = "api"): List<MatchInput> {
    val trimmed = rawJson.trim()
    if (trimmed.isBlank()) return emptyList()

    val array: JSONArray = when {
      trimmed.startsWith("[") -> JSONArray(trimmed)
      trimmed.startsWith("{") -> {
        val root = JSONObject(trimmed)
        when {
          root.has("events") && !root.isNull("events") -> root.getJSONArray("events")
          root.has("matches") && !root.isNull("matches") -> root.getJSONArray("matches")
          root.has("response") && !root.isNull("response") -> root.getJSONArray("response")
          root.has("results") && !root.isNull("results") -> root.getJSONArray("results")
          else -> JSONArray()
        }
      }
      else -> return emptyList()
    }

    val out = mutableListOf<MatchInput>()
    val isoDateRegex = Regex("""^\d{4}-\d{2}-\d{2}$""")

    for (i in 0 until array.length()) {
      val obj = array.optJSONObject(i) ?: continue
      val match = parseSingleMatchObject(obj, defaultSource, isoDateRegex)
      if (match != null) out.add(match)
    }
    return out
  }

  private fun parseSingleMatchObject(
    obj: JSONObject,
    defaultSource: String,
    isoDateRegex: Regex,
  ): MatchInput? {
    // Shape A: Direct Match object {home, away, hg, ag, date}
    if (obj.has("home") && obj.has("away") && obj.has("hg") && obj.has("ag")) {
      val home = obj.optString("home").trim()
      val away = obj.optString("away").trim()
      val hg = obj.optInt("hg", -1)
      val ag = obj.optInt("ag", -1)
      val date = obj.optString("date").take(10)
      if (home.isNotEmpty() && away.isNotEmpty() && home != away && hg in 0..30 && ag in 0..30 && isoDateRegex.matches(date)) {
        return MatchInput(home = home.take(60), away = away.take(60), hg = hg, ag = ag, date = date, source = defaultSource)
      }
    }

    // Shape B: TheSportsDB {strHomeTeam, strAwayTeam, intHomeScore, intAwayScore, dateEvent}
    if (obj.has("strHomeTeam") && obj.has("strAwayTeam")) {
      val home = obj.optString("strHomeTeam").trim()
      val away = obj.optString("strAwayTeam").trim()
      val hgStr = obj.optString("intHomeScore").trim()
      val agStr = obj.optString("intAwayScore").trim()
      val hg = hgStr.toIntOrNull() ?: -1
      val ag = agStr.toIntOrNull() ?: -1
      val date = obj.optString("dateEvent").take(10)
      if (home.isNotEmpty() && away.isNotEmpty() && home != away && hg in 0..30 && ag in 0..30 && isoDateRegex.matches(date)) {
        return MatchInput(home = home.take(60), away = away.take(60), hg = hg, ag = ag, date = date, source = defaultSource)
      }
    }

    // Shape C: football-data.org v4 {homeTeam:{name}, awayTeam:{name}, score:{fullTime:{home, away}}, utcDate}
    val homeTeamObj = obj.optJSONObject("homeTeam")
    val awayTeamObj = obj.optJSONObject("awayTeam")
    val fullTimeObj = obj.optJSONObject("score")?.optJSONObject("fullTime")
    if (homeTeamObj != null && awayTeamObj != null && fullTimeObj != null) {
      val home = (homeTeamObj.optString("shortName").ifBlank { homeTeamObj.optString("name") }).trim()
      val away = (awayTeamObj.optString("shortName").ifBlank { awayTeamObj.optString("name") }).trim()
      if (!fullTimeObj.isNull("home") && !fullTimeObj.isNull("away")) {
        val hg = fullTimeObj.optInt("home", -1)
        val ag = fullTimeObj.optInt("away", -1)
        val date = obj.optString("utcDate").take(10)
        if (home.isNotEmpty() && away.isNotEmpty() && home != away && hg in 0..30 && ag in 0..30 && isoDateRegex.matches(date)) {
          return MatchInput(home = home.take(60), away = away.take(60), hg = hg, ag = ag, date = date, source = defaultSource)
        }
      }
    }

    return null
  }
}

object SampleLeagueDataset {
  /**
   * Deterministic 132-match double round-robin season across 12 clubs (no randomness).
   * Enables immediate MIN_MATCHES >= 5 predictions and warmup = 100 walk-forward backtests.
   */
  fun generate132Matches(): List<MatchInput> {
    val teams = listOf(
      "Arsenal", "Liverpool", "Man City", "Chelsea",
      "Aston Villa", "Newcastle", "Tottenham", "Brighton",
      "West Ham", "Man United", "Fulham", "Brentford",
    )
    val attackTier = mapOf(
      "Arsenal" to 2.0, "Liverpool" to 2.1, "Man City" to 2.2, "Chelsea" to 1.7,
      "Aston Villa" to 1.6, "Newcastle" to 1.6, "Tottenham" to 1.7, "Brighton" to 1.5,
      "West Ham" to 1.2, "Man United" to 1.3, "Fulham" to 1.2, "Brentford" to 1.3,
    )
    val defenseLeak = mapOf(
      "Arsenal" to 0.7, "Liverpool" to 0.8, "Man City" to 0.8, "Chelsea" to 1.0,
      "Aston Villa" to 1.1, "Newcastle" to 1.0, "Tottenham" to 1.2, "Brighton" to 1.1,
      "West Ham" to 1.4, "Man United" to 1.3, "Fulham" to 1.2, "Brentford" to 1.3,
    )

    val out = mutableListOf<MatchInput>()
    var dayOffset = 0
    val n = teams.size
    val rotating = teams.drop(1).toMutableList()

    // Full double round-robin (22 rounds * 6 fixtures = 132 deterministic fixtures)
    for (half in 0..1) {
      val circle = rotating.toMutableList()
      for (rnd in 0 until (n - 1)) {
        val roundPairs = mutableListOf<Pair<String, String>>()
        val t0 = teams[0]
        val t1 = circle[0]
        roundPairs.add(if ((rnd + half) % 2 == 0) t0 to t1 else t1 to t0)
        for (i in 1 until n / 2) {
          val a = circle[i]
          val b = circle[circle.size - i]
          roundPairs.add(if ((i + rnd + half) % 2 == 0) a to b else b to a)
        }

        val dateStr = formatDayOffset(dayOffset)
        for ((idx, pair) in roundPairs.withIndex()) {
          val (h, a) = pair
          val hPot = (attackTier[h] ?: 1.3) * (defenseLeak[a] ?: 1.1)
          val aPot = (attackTier[a] ?: 1.3) * (defenseLeak[h] ?: 1.1) * 0.82
          val hMod = ((rnd * 3 + idx + half) % 3) - 1 // -1, 0, +1 deterministic variation
          val aMod = ((rnd * 2 + idx * 2) % 3) - 1
          val hg = (hPot + hMod * 0.55).toInt().coerceIn(0, 5)
          val ag = (aPot + aMod * 0.50).toInt().coerceIn(0, 4)
          out.add(MatchInput(home = h, away = a, hg = hg, ag = ag, date = dateStr, source = "seed"))
        }
        dayOffset += 4
        val last = circle.removeAt(circle.lastIndex)
        circle.add(0, last)
      }
    }
    return out
  }

  private fun formatDayOffset(offsetDays: Int): String {
    // Starts 2026-08-08 -> walks forward by days cleanly
    val monthLengths = listOf(31, 30, 31, 30, 31) // Aug, Sep, Oct, Nov, Dec
    val months = listOf(8, 9, 10, 11, 12)
    var day = 8 + offsetDays
    var mIdx = 0
    while (mIdx < monthLengths.size - 1 && day > monthLengths[mIdx]) {
      day -= monthLengths[mIdx]
      mIdx++
    }
    val month = months[mIdx].toString().padStart(2, '0')
    val dayStr = day.coerceIn(1, 31).toString().padStart(2, '0')
    return "2026-$month-$dayStr"
  }
}
