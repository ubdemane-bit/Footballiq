package com.example.engine

import java.util.Locale

data class ScannedFixture(
  val homeTeam: String,
  val awayTeam: String,
  val rawLines: List<String>,
  val matchedKnownTeams: Boolean,
)

object FixtureOcrParser {
  private val clubAliases: Map<String, List<String>> = mapOf(
    "Arsenal" to listOf("arsenal", "ars", "gunners"),
    "Liverpool" to listOf("liverpool", "liv", "lfc"),
    "Man City" to listOf("man city", "manchester city", "mci", "mcfc"),
    "Chelsea" to listOf("chelsea", "che", "cfc"),
    "Aston Villa" to listOf("aston villa", "avl", "villa"),
    "Newcastle" to listOf("newcastle", "newcastle united", "new", "nufc"),
    "Tottenham" to listOf("tottenham", "tottenham hotspur", "spurs", "tot", "thfc"),
    "Brighton" to listOf("brighton", "brighton hove", "bha"),
    "West Ham" to listOf("west ham", "west ham united", "whu"),
    "Man United" to listOf("man united", "man utd", "manchester united", "manchester utd", "mun", "mufc"),
    "Fulham" to listOf("fulham", "ful", "ffc"),
    "Brentford" to listOf("brentford", "bre", "bfc"),
  )

  private val vsLineRegex = Regex(
    """^([A-Za-z0-9 .&'\-]{2,40}?)\s+(?:vs\.?|v\.?|-|–|—|@)\s+([A-Za-z0-9 .&'\-]{2,40})$""",
    RegexOption.IGNORE_CASE,
  )

  fun parse(rawText: String, knownTeams: List<String>): ScannedFixture? {
    val lines = rawText
      .lines()
      .map { it.trim() }
      .filter { it.length >= 2 }

    if (lines.isEmpty()) return null

    val normalizedText = normalize(rawText)
    if (normalizedText.isBlank()) return null

    // 1. Match against known teams in the user's dataset (by earliest appearance)
    val hits = mutableListOf<Pair<Int, String>>()
    for (team in knownTeams) {
      val candidates = buildCandidatesForTeam(team)
      var earliestPos = Int.MAX_VALUE
      for (cand in candidates) {
        val pos = findWholePhraseIndex(normalizedText, cand)
        if (pos >= 0 && pos < earliestPos) {
          earliestPos = pos
        }
      }
      if (earliestPos == Int.MAX_VALUE && team.length >= 5) {
        val fuzzyPos = findFuzzyWordMatch(normalizedText, normalize(team))
        if (fuzzyPos >= 0) {
          earliestPos = fuzzyPos
        }
      }
      if (earliestPos != Int.MAX_VALUE) {
        hits.add(earliestPos to team)
      }
    }

    val orderedUniqueTeams = hits
      .sortedBy { it.first }
      .map { it.second }
      .distinct()

    if (orderedUniqueTeams.size >= 2) {
      return ScannedFixture(
        homeTeam = orderedUniqueTeams[0],
        awayTeam = orderedUniqueTeams[1],
        rawLines = lines.take(8),
        matchedKnownTeams = true,
      )
    }

    // 2. Fallback: parse "Team A vs Team B" / "Team A - Team B" lines
    for (line in lines) {
      val cleanLine = line.replace(Regex("""\d+\s*[-:]\s*\d+"""), " - ").trim()
      val match = vsLineRegex.find(cleanLine)
      if (match != null) {
        val rawHome = match.groupValues[1].trim()
        val rawAway = match.groupValues[2].trim()
        if (rawHome.length >= 2 && rawAway.length >= 2 && !rawHome.equals(rawAway, ignoreCase = true)) {
          val resolvedHome = resolveKnownOrRaw(rawHome, knownTeams)
          val resolvedAway = resolveKnownOrRaw(rawAway, knownTeams)
          if (!resolvedHome.equals(resolvedAway, ignoreCase = true)) {
            return ScannedFixture(
              homeTeam = resolvedHome,
              awayTeam = resolvedAway,
              rawLines = lines.take(8),
              matchedKnownTeams = resolvedHome in knownTeams && resolvedAway in knownTeams,
            )
          }
        }
      }
    }

    // 3. Fallback: two consecutive lines separated by a standalone "vs" / "v" line
    for (i in 0 until lines.size - 2) {
      val mid = lines[i + 1].lowercase(Locale.US).trim()
      if (mid == "vs" || mid == "vs." || mid == "v" || mid == "v." || mid == "-") {
        val rawHome = lines[i].trim()
        val rawAway = lines[i + 2].trim()
        if (rawHome.length in 2..40 && rawAway.length in 2..40 && !rawHome.equals(rawAway, ignoreCase = true)) {
          val resolvedHome = resolveKnownOrRaw(rawHome, knownTeams)
          val resolvedAway = resolveKnownOrRaw(rawAway, knownTeams)
          return ScannedFixture(
            homeTeam = resolvedHome,
            awayTeam = resolvedAway,
            rawLines = lines.take(8),
            matchedKnownTeams = resolvedHome in knownTeams && resolvedAway in knownTeams,
          )
        }
      }
    }

    return null
  }

  private fun resolveKnownOrRaw(raw: String, knownTeams: List<String>): String {
    val norm = normalize(raw)
    for (team in knownTeams) {
      if (buildCandidatesForTeam(team).any { it == norm || norm.contains(it) }) {
        return team
      }
    }
    return raw.take(60)
  }

  private fun buildCandidatesForTeam(team: String): List<String> {
    val normTeam = normalize(team)
    val aliases = clubAliases[team].orEmpty().map { normalize(it) }
    return (listOf(normTeam) + aliases).distinct().filter { it.length >= 3 }
  }

  private fun normalize(text: String): String {
    return text
      .lowercase(Locale.US)
      .replace('1', 'l')
      .replace('0', 'o')
      .replace(Regex("[^a-z0-9\\s]"), " ")
      .replace(Regex("\\s+"), " ")
      .trim()
  }

  private fun findWholePhraseIndex(haystack: String, needle: String): Int {
    val paddedHaystack = " $haystack "
    val idx = paddedHaystack.indexOf(" $needle ")
    return if (idx >= 0) idx else -1
  }

  private fun findFuzzyWordMatch(haystack: String, target: String): Int {
    val words = haystack.split(' ')
    var offset = 0
    for (word in words) {
      if (word.length >= 4 && absLengthDiff(word, target) <= 2) {
        val maxDist = if (target.length >= 7) 2 else 1
        if (levenshtein(word, target) <= maxDist) {
          return offset
        }
      }
      offset += word.length + 1
    }
    return -1
  }

  private fun absLengthDiff(a: String, b: String): Int = kotlin.math.abs(a.length - b.length)

  private fun levenshtein(a: String, b: String): Int {
    val dp = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
      var prev = dp[0]
      dp[0] = i
      for (j in 1..b.length) {
        val temp = dp[j]
        dp[j] = if (a[i - 1] == b[j - 1]) prev else 1 + minOf(prev, dp[j], dp[j - 1])
        prev = temp
      }
    }
    return dp[b.length]
  }
}
