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
    reaction: "mind_blown",
    originalLanguage: "en",
    overview: "An insomniac office worker and a devil-may-care soap maker form an underground fight club.",
    genres: ["Drama", "Thriller"],
    cast: ["Brad Pitt", "Edward Norton", "Helena Bonham Carter"],
    directors: ["David Fincher"],
    runtime: 139,
    numberOfSeasons: 0,
    numberOfEpisodes: 0,
    watchedSeason: 0,
    watchLog: ["2026-11-10", "2026-11-16"],
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
    reaction: "masterpiece",
    originalLanguage: "en",
    overview: "A chemistry teacher diagnosed with terminal lung cancer turns to manufacturing and selling meth.",
    genres: ["Drama", "Crime"],
    cast: ["Bryan Cranston", "Aaron Paul", "Anna Gunn"],
    directors: ["Vince Gilligan"],
    runtime: 47,
    numberOfSeasons: 5,
    numberOfEpisodes: 62,
    watchedSeason: 4,
    watchLog: ["2026-11-12"],
    watchedAt: pastTime,
    updatedAt: pastTime,
    ...overrides,
  };
}

function validWatchlistPayload(uid, overrides = {}) {
  const pastTime = new Date(Date.now() - 2000);
  return {
    userId: uid,
    movieId: 27205,
    mediaType: "movie",
    title: "Inception",
    posterPath: "/9gk7adHYeDvHkCSEqAvQNLV5Uge.jpg",
    releaseDate: "2010-07-16",
    rating: 8.4,
    overview: "A thief who steals corporate secrets through dream-sharing technology.",
    originalLanguage: "en",
    genres: ["Action", "Science Fiction"],
    runtime: 148,
    numberOfSeasons: 0,
    numberOfEpisodes: 0,
    addedAt: pastTime,
    updatedAt: pastTime,
    ...overrides,
  };
}

function validCustomListPayload(uid, overrides = {}) {
  const pastTime = new Date(Date.now() - 2000);
  return {
    userId: uid,
    name: "Weekend Sci-Fi",
    description: "Mind-bending films to rewatch",
    items: [
      {
        movieId: 550,
        mediaType: "movie",
        title: "Fight Club",
        posterPath: "/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg",
        releaseDate: "1999-10-15",
        rating: 8.4,
        originalLanguage: "en",
      },
    ],
    createdAt: pastTime,
    updatedAt: pastTime,
    ...overrides,
  };
}

function validSettingsPayload(uid, overrides = {}) {
  const pastTime = new Date(Date.now() - 2000);
  return {
    userId: uid,
    accentPreset: "yellow",
    accentHex: "#F5C518",
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

test("Unauthenticated user: cannot access watched, watchlist, lists, or settings", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("watched").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("watchlist").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("lists").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("settings").doc("preferences").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("watchlist").doc("27205").set(validWatchlistPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("lists").doc("list_1").set(validCustomListPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("settings").doc("preferences").set(validSettingsPayload(ALICE_UID))
  );
});

test("Authenticated owner: CRUD on watched (with watchLog), watchlist, custom lists, and settings", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const movieDoc = aliceDb.collection("users").doc(ALICE_UID).collection("watched").doc("550");
  const tvDoc = aliceDb.collection("users").doc(ALICE_UID).collection("watched").doc("tv_1396");
  const watchlistDoc = aliceDb.collection("users").doc(ALICE_UID).collection("watchlist").doc("27205");
  const listDoc = aliceDb.collection("users").doc(ALICE_UID).collection("lists").doc("scifi_list");
  const settingsDoc = aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("preferences");

  await assertSucceeds(movieDoc.set(validMoviePayload(ALICE_UID)));
  await assertSucceeds(tvDoc.set(validTvPayload(ALICE_UID)));
  await assertSucceeds(watchlistDoc.set(validWatchlistPayload(ALICE_UID)));
  await assertSucceeds(listDoc.set(validCustomListPayload(ALICE_UID)));
  await assertSucceeds(settingsDoc.set(validSettingsPayload(ALICE_UID)));

  await assertSucceeds(
    movieDoc.update({
      reaction: "emotional",
      originalLanguage: "hi",
      cast: ["Shah Rukh Khan", "Kajol"],
      directors: ["Aditya Chopra"],
      runtime: 181,
      watchLog: ["2026-11-10", "2026-11-16", "2026-11-16"],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    listDoc.update({
      name: "Top Classics",
      items: [],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    settingsDoc.set(validSettingsPayload(ALICE_UID, { accentPreset: "custom", accentHex: "#00FFAA" }))
  );

  await assertSucceeds(listDoc.delete());
  await assertSucceeds(watchlistDoc.delete());
  await assertSucceeds(movieDoc.delete());
  await assertSucceeds(tvDoc.delete());
});

test("Cross-user isolation: Bob cannot read or write Alice's watched, watchlist, lists, or settings", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const aliceUser = context.firestore().collection("users").doc(ALICE_UID);
    await aliceUser.collection("watched").doc("tv_1396").set(validTvPayload(ALICE_UID));
    await aliceUser.collection("watchlist").doc("27205").set(validWatchlistPayload(ALICE_UID));
    await aliceUser.collection("lists").doc("scifi_list").set(validCustomListPayload(ALICE_UID));
    await aliceUser.collection("settings").doc("preferences").set(validSettingsPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceWatched = bobDb.collection("users").doc(ALICE_UID).collection("watched").doc("tv_1396");
  const aliceWatchlist = bobDb.collection("users").doc(ALICE_UID).collection("watchlist").doc("27205");
  const aliceList = bobDb.collection("users").doc(ALICE_UID).collection("lists").doc("scifi_list");
  const aliceSettings = bobDb.collection("users").doc(ALICE_UID).collection("settings").doc("preferences");

  await assertFails(aliceWatched.get());
  await assertFails(aliceWatchlist.get());
  await assertFails(aliceList.get());
  await assertFails(aliceSettings.get());
  await assertFails(aliceWatchlist.set(validWatchlistPayload(BOB_UID)));
  await assertFails(aliceList.set(validCustomListPayload(BOB_UID)));
  await assertFails(aliceSettings.set(validSettingsPayload(BOB_UID)));
  await assertFails(aliceWatched.delete());
});

test("Schema validation: rejects invalid reaction, list name, accentHex, settingId, and ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const movieDoc = aliceDb.collection("users").doc(ALICE_UID).collection("watched").doc("550");
  const listDoc = aliceDb.collection("users").doc(ALICE_UID).collection("lists").doc("bad_list");
  const settingsDoc = aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("preferences");
  const wrongSettingsDoc = aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("other");

  await assertFails(movieDoc.set(validMoviePayload(ALICE_UID, { reaction: "invalid_emoji" })));
  await assertFails(movieDoc.set(validMoviePayload(ALICE_UID, { extraField: true })));
  await assertFails(listDoc.set(validCustomListPayload(ALICE_UID, { name: "" })));
  await assertFails(listDoc.set(validCustomListPayload(ALICE_UID, { extraField: "nope" })));
  await assertFails(settingsDoc.set(validSettingsPayload(ALICE_UID, { accentHex: "not-a-hex" })));
  await assertFails(settingsDoc.set(validSettingsPayload(ALICE_UID, { accentPreset: "orange" })));
  await assertFails(wrongSettingsDoc.set(validSettingsPayload(ALICE_UID)));
});
