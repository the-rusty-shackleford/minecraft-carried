# Carried — repository notes

- Consumers and Backpacks+ compile against this artifact from mavenLocal: after any change run
  `./gradlew publishToMavenLocal` here before building them, or they build against the old one.
- The gametest mod (`carried_gametest`) registers a fake provider in its constructor; a test that
  needs a player to carry stores calls `FakeStores.carry(player, ...)`, and a player it never
  handed stores carries none.
