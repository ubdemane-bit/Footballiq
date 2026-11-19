package com.example

import com.example.data.remote.SampleLeagueDataset
import com.example.engine.CalibrationEngine
import com.example.engine.FootballIqModel
import com.example.engine.MatchInput
import com.example.engine.PredictionOutcome
import com.example.engine.PredictionRecordBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FootballIqEngineTest {
  @Test
  fun insufficientDataWhenFewerThanFiveMatches() {
    val tiny = listOf(
      MatchInput(home = "A", away = "B", hg = 2, ag = 1, date = "2026-01-01"),
      MatchInput(home = "B", away = "A", hg = 0, ag = 1, date = "2026-01-05"),
    )
    val res = FootballIqModel(tiny).predict("A", "B")
    assertTrue(res is PredictionOutcome.InsufficientData)
  }

  @Test
  fun deterministicPredictionAndWalkForwardBacktestOn132Matches() {
    val matches = SampleLeagueDataset.generate132Matches()
    assertEquals(132, matches.size)

    val model = FootballIqModel(matches)
    val pred = model.predict("Arsenal", "Liverpool")
    assertTrue(pred is PredictionOutcome.Ready)
    val ready = pred as PredictionOutcome.Ready
    val sumProbs = ready.probHome + ready.probDraw + ready.probAway
    assertEquals(1.0, sumProbs, 0.005)
    assertTrue(ready.option1Safe.probability >= 0.5)

    val bt = CalibrationEngine.backtest(matches, warmup = 100)
    assertTrue(bt != null)
    assertEquals(32, bt!!.evaluatedCount)
    assertTrue(bt.brier in 0.0..2.0)
    assertTrue(bt.logLoss > 0.0)
  }

  @Test
  fun preKickoffRecordRejectsPastKickoffAndVerifiesSha256() {
    val matches = SampleLeagueDataset.generate132Matches()
    val ready = FootballIqModel(matches).predict("Arsenal", "Chelsea") as PredictionOutcome.Ready
    val now = 1_795_000_000_000L

    val pastRes = PredictionRecordBuilder.create(
      fixtureId = "FIX_1",
      kickoffUtc = PredictionRecordBuilder.formatUtcIso(now - 60_000L),
      prediction = ready,
      nowMillis = now,
    )
    assertTrue(pastRes.isFailure)

    val futureRes = PredictionRecordBuilder.create(
      fixtureId = "FIX_1",
      kickoffUtc = PredictionRecordBuilder.formatUtcIso(now + 3600_000L),
      prediction = ready,
      nowMillis = now,
    )
    assertTrue(futureRes.isSuccess)
    val rec = futureRes.getOrThrow()
    assertTrue(
      PredictionRecordBuilder.verifyDigest(
        fixtureId = rec.fixtureId,
        kickoffUtc = rec.kickoffUtc,
        modelVersion = rec.modelVersion,
        payloadJson = rec.payloadJson,
        storedSha256 = rec.sha256,
      )
    )
  }

  @Test
  fun ocrParserExtractsKnownTeamsAndAliases() {
    val teams = listOf("Arsenal", "Chelsea", "Man City", "Liverpool")

    val broadcast = "PREMIER LEAGUE\nARS 15:00 CHE\nEmirates Stadium"
    val parsedAlias = com.example.engine.FixtureOcrParser.parse(broadcast, teams)
    assertTrue(parsedAlias != null)
    assertEquals("Arsenal", parsedAlias!!.homeTeam)
    assertEquals("Chelsea", parsedAlias.awayTeam)

    val poster = "Manchester City vs Liverpool"
    val parsedPoster = com.example.engine.FixtureOcrParser.parse(poster, teams)
    assertTrue(parsedPoster != null)
    assertEquals("Man City", parsedPoster!!.homeTeam)
    assertEquals("Liverpool", parsedPoster.awayTeam)
  }

  @Test
  fun payloadParserRecoversOriginalProbabilitiesAndOptions() {
    val matches = SampleLeagueDataset.generate132Matches()
    val ready = FootballIqModel(matches).predict("Arsenal", "Liverpool") as PredictionOutcome.Ready

    val parsed = com.example.engine.PredictionPayloadParser.parse(ready.canonicalPayloadJson)
    assertTrue(parsed != null)
    val payload = parsed!!
    assertEquals(ready.expectedHomeGoals, payload.expectedGoals.home, 0.001)
    assertEquals(ready.expectedAwayGoals, payload.expectedGoals.away, 0.001)
    assertEquals(ready.probHome, payload.probabilities.home, 0.0001)
    assertEquals(ready.probDraw, payload.probabilities.draw, 0.0001)
    assertEquals(ready.probAway, payload.probabilities.away, 0.0001)
    assertEquals(ready.option1Safe.pick, payload.option1Safe.pick)
    assertEquals(ready.option1Safe.probability, payload.option1Safe.probability, 0.0001)
    assertEquals(ready.option2Smart.pick, payload.option2Smart.pick)
    assertEquals(ready.option2Smart.probability, payload.option2Smart.probability, 0.0001)
    assertEquals(ready.option3Advanced.correctScore, payload.option3Advanced.correctScore)
    assertEquals(ready.option3Advanced.probability, payload.option3Advanced.probability, 0.0001)
  }

  @Test
  fun season2627FilterAndRecentGamesBreakdownWorkDeterministically() {
    val mixed = listOf(
      MatchInput(home = "Arsenal", away = "Chelsea", hg = 3, ag = 0, date = "2025-12-10"),
      MatchInput(home = "Chelsea", away = "Arsenal", hg = 2, ag = 0, date = "2026-03-14"),
    ) + SampleLeagueDataset.generate132Matches()

    val season2627 = com.example.engine.SeasonFilter.filterSeason2627(mixed)
    assertEquals(132, season2627.size)
    assertTrue(season2627.all { it.date >= "2026-07-01" && it.date <= "2027-06-30" })

    val model = FootballIqModel(season2627)
    val recent5 = model.analyzeRecentTeam("Arsenal", last = 5)
    assertEquals(5, recent5.recentGames.size)
    assertEquals(recent5.wins + recent5.draws + recent5.losses, 5)
    assertTrue(recent5.recentGames.first().date >= recent5.recentGames.last().date)

    val pred5 = model.predict("Arsenal", "Liverpool", recentWindow = 5)
    assertTrue(pred5 is PredictionOutcome.Ready)
  }
}
