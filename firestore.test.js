const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const VALID_SHA = "a".repeat(64);

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write matches or predictionRecords", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("matches").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("matches").doc("m_1").set({
      userId: ALICE_UID,
      home: "Arsenal",
      away: "Chelsea",
      hg: 2,
      ag: 1,
      matchDate: "2026-11-10",
      source: "manual",
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("predictionRecords").get());
});

test("Owner can create, list, update, and delete matches with valid Kotlin payloads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 5000);
  const matchRef = aliceDb.collection("users").doc(ALICE_UID).collection("matches").doc("match_1");

  await assertSucceeds(
    matchRef.set({
      userId: ALICE_UID,
      home: "Arsenal",
      away: "Chelsea",
      hg: 2,
      ag: 1,
      matchDate: "2026-11-10",
      source: "manual",
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("matches").get());

  await assertSucceeds(
    matchRef.update({
      hg: 3,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject invalid match payloads (same team, ghost field, future timestamp, @DocumentId id key)
  await assertFails(
    matchRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("matches").doc("match_2").set({
      id: "match_2",
      userId: ALICE_UID,
      home: "Arsenal",
      away: "Arsenal",
      hg: 1,
      ag: 1,
      matchDate: "2026-11-10",
      source: "manual",
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(matchRef.delete());
});

test("PredictionRecord is strictly append-only (INSERT allowed, UPDATE & DELETE forbidden)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 2000);
  const recRef = aliceDb.collection("users").doc(ALICE_UID).collection("predictionRecords").doc("rec_1");

  await assertSucceeds(
    recRef.set({
      userId: ALICE_UID,
      fixtureId: "EPL_ARS_CHE_20261121",
      homeTeam: "Arsenal",
      awayTeam: "Chelsea",
      kickoffUtc: "2026-11-21T15:00:00Z",
      modelVersion: "football-iq-poisson-elo-v1",
      createdUtc: "2026-11-19T11:30:00Z",
      payloadJson: "{\"status\":\"ok\"}",
      sha256: VALID_SHA,
      safePick: "1X",
      smartPick: "Arsenal win",
      exactScore: "2-1",
      createdAt: past,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("predictionRecords").get());

  // Immutability check: UPDATE and DELETE must fail even for owner!
  await assertFails(recRef.update({ safePick: "X2" }));
  await assertFails(recRef.delete());

  // Invalid sha256 or future createdAt must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("predictionRecords").doc("rec_bad").set({
      userId: ALICE_UID,
      fixtureId: "EPL_ARS_CHE",
      homeTeam: "Arsenal",
      awayTeam: "Chelsea",
      kickoffUtc: "2026-11-21T15:00:00Z",
      modelVersion: "football-iq-poisson-elo-v1",
      createdUtc: "2026-11-19T11:30:00Z",
      payloadJson: "{\"status\":\"ok\"}",
      sha256: "not-a-valid-sha",
      safePick: "1X",
      smartPick: "Arsenal win",
      exactScore: "2-1",
      createdAt: past,
    })
  );
});

test("Cross-user isolation prevents Bob from reading or writing Alice data", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 2000);

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("matches").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("predictionRecords").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("matches").doc("hijack").set({
      userId: BOB_UID,
      home: "Team A",
      away: "Team B",
      hg: 1,
      ag: 0,
      matchDate: "2026-11-10",
      source: "manual",
      createdAt: past,
      updatedAt: past,
    })
  );
});
