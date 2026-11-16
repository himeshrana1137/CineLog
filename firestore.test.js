const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validMoviePayload(uid, overrides = {}) {
  const pastTime = new Date(Date.now() - 2000);
  return {
    userId: uid,
    movieId: 550,
    mediaType: "movie",
    title: "Fight Club",
    posterPath: "/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg",
    releaseDate: "1999-10-15",
    rating: 8.4,
    userRating: 5,
    note: "Masterpiece rewatch.",
    overview: "An insomniac office worker and a devil-may-care soap maker form an underground fight club.",
    genres: ["Drama", "Thriller"],
    runtime: 139,
    numberOfSeasons: 0,
    numberOfEpisodes: 0,
    watchedSeason: 0,
    watchedAt: pastTime,
    updatedAt: pastTime,
    ...overrides,
  };
}

function validTvPayload(uid, overrides = {}) {
  const pastTime = new Date(Date.now() - 2000);
  return {
    userId: uid,
    movieId: 1396,
    mediaType: "tv",
    title: "Breaking Bad",
    posterPath: "/ggFHVNu6YYI5L9pCfOacjizRGt.jpg",
    releaseDate: "2008-01-20",
    rating: 8.9,
    userRating: 5,
    note: "Season 4 finale is unmatched.",
    overview: "A chemistry teacher diagnosed with terminal lung cancer turns to manufacturing and selling meth.",
    genres: ["Drama", "Crime"],
    runtime: 47,
    numberOfSeasons: 5,
    numberOfEpisodes: 62,
    watchedSeason: 4,
    watchedAt: pastTime,
    updatedAt: pastTime,
    ...overrides,
  };
}

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

test("Unauthenticated user: cannot list or read watched media", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("watched").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("watched").doc("550").get());
});

test("Unauthenticated user: cannot write watched media", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("watched").doc("tv_1396").set(
      validTvPayload(ALICE_UID)
    )
  );
});

test("Authenticated owner: can create, read, list, update, and delete both movies and TV shows", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const movieDoc = aliceDb.collection("users").doc(ALICE_UID).collection("watched").doc("550");
  const tvDoc = aliceDb.collection("users").doc(ALICE_UID).collection("watched").doc("tv_1396");

  await assertSucceeds(movieDoc.set(validMoviePayload(ALICE_UID)));
  await assertSucceeds(tvDoc.set(validTvPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("watched").get());

  await assertSucceeds(
    tvDoc.update({
      userRating: 5,
      watchedSeason: 5,
      note: "Finished the whole series!",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(movieDoc.delete());
  await assertSucceeds(tvDoc.delete());
});

test("Cross-user isolation: Bob cannot access or modify Alice's watched TV shows", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("watched")
      .doc("tv_1396")
      .set(validTvPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceTvRef = bobDb.collection("users").doc(ALICE_UID).collection("watched").doc("tv_1396");

  await assertFails(aliceTvRef.get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("watched").get());
  await assertFails(aliceTvRef.set(validTvPayload(BOB_UID)));
  await assertFails(aliceTvRef.update({ watchedSeason: 1, updatedAt: new Date(Date.now() - 1000) }));
  await assertFails(aliceTvRef.delete());
});

test("Schema validation: rejects invalid mediaType, ghost fields, and mutating mediaType", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const tvDoc = aliceDb.collection("users").doc(ALICE_UID).collection("watched").doc("tv_1396");

  await assertFails(tvDoc.set(validTvPayload(ALICE_UID, { mediaType: "anime" })));
  await assertFails(tvDoc.set(validTvPayload(ALICE_UID, { isAdmin: true })));
  await assertFails(tvDoc.set(validTvPayload(ALICE_UID, { watchedSeason: 999 })));

  await assertSucceeds(tvDoc.set(validTvPayload(ALICE_UID)));
  await assertFails(
    tvDoc.update({
      mediaType: "movie",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
