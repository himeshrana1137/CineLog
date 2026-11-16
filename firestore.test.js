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
    title: "Fight Club",
    posterPath: "/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg",
    releaseDate: "1999-10-15",
    rating: 8.4,
    userRating: 5,
    note: "Masterpiece rewatch.",
    overview: "An insomniac office worker and a devil-may-care soap maker form an underground fight club.",
    genres: ["Drama", "Thriller"],
    runtime: 139,
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

test("Unauthenticated user: cannot list or read watched movies", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("watched").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("watched").doc("550").get());
});

test("Unauthenticated user: cannot write watched movie", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("watched").doc("550").set(
      validMoviePayload(ALICE_UID)
    )
  );
});

test("Authenticated owner: can create, read, list, update rating/note, and delete watched movie", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("watched").doc("550");

  await assertSucceeds(docRef.set(validMoviePayload(ALICE_UID)));
  await assertSucceeds(docRef.get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("watched").get());

  await assertSucceeds(
    docRef.update({
      userRating: 4,
      note: "Updated thoughts after second viewing.",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(docRef.delete());
});

test("Cross-user isolation: Bob cannot read, list, create, update, or delete Alice's watched movies", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("watched")
      .doc("550")
      .set(validMoviePayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceDocRef = bobDb.collection("users").doc(ALICE_UID).collection("watched").doc("550");

  await assertFails(aliceDocRef.get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("watched").get());
  await assertFails(aliceDocRef.set(validMoviePayload(BOB_UID)));
  await assertFails(aliceDocRef.update({ userRating: 1, updatedAt: new Date(Date.now() - 1000) }));
  await assertFails(aliceDocRef.delete());
});

test("Schema validation: rejects ghost fields, invalid userRating, spoofed userId, and mutated watchedAt", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("watched").doc("550");

  // Ghost field rejected
  await assertFails(docRef.set(validMoviePayload(ALICE_UID, { isAdmin: true })));

  // Out-of-bounds userRating (> 5) rejected
  await assertFails(docRef.set(validMoviePayload(ALICE_UID, { userRating: 6 })));

  // Spoofed userId rejected
  await assertFails(docRef.set(validMoviePayload(BOB_UID)));

  // Valid create, then attempt to mutate immutable watchedAt
  await assertSucceeds(docRef.set(validMoviePayload(ALICE_UID)));
  await assertFails(
    docRef.update({
      watchedAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
