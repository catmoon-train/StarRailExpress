# Fourth Room

## Package Structure

```text
org.agmas.noellesroles.game.modes.fourthroom
├── block
│   ├── FourthRoomTableBlock
│   └── FourthRoomTableBlockEntity
├── card
│   ├── Card
│   ├── CardCategory
│   ├── CardInstance
│   ├── CardRegistry
│   ├── BasicCard
│   └── SkillCard
├── config
│   └── FourthRoomConfig
├── duel
│   └── FourthRoomDuelManager
├── effect
│   ├── EffectEvent
│   ├── EffectQueue
│   ├── ActionReport
│   └── TableEffectEvents
├── game
│   ├── FourthRoomGameMode
│   ├── FourthRoomGameManager
│   ├── FourthRoomSavedData
│   ├── FourthRoomPhase
│   ├── FourthRoomTeam
│   ├── FourthRoomPlayerState
│   ├── FourthRoomRoomState
│   └── FourthRoomStickyNoteState
├── network
│   ├── FourthRoomStatePayload
│   ├── FourthRoomTableEffectsPayload
│   ├── CardPlayPayload
│   ├── BuyFourthRoomItemPayload
│   ├── RevealIdentityPayload
│   ├── CompleteFourthRoomTaskPayload
│   ├── EndTurnPayload
│   └── UseAssassinationItemPayload
├── room
│   ├── RoomDefinition
│   └── RoomManager
├── shop
│   ├── FourthRoomShopItem
│   └── FourthRoomShopService
├── scene
│   ├── FourthRoomSceneGenerator
│   └── FourthRoomSceneLayout
└── task
    ├── FourthRoomTaskType
    └── FourthRoomTaskScheduler
```

## Core Classes

### FourthRoomGameMode

- `initializeGame(ServerLevel, SREGameWorldComponent, List<ServerPlayer>)`
- `tickServerGameLoop(ServerLevel, SREGameWorldComponent)`
- `finalizeGame(ServerLevel, SREGameWorldComponent)`

Role: entry point registered into the existing SRE game-mode framework.

### FourthRoomGameManager

- `initializeMatch(List<ServerPlayer>)`
- `tickServer()`
- `playCard(UUID, String, UUID)`
- `endTurn(UUID)`
- `revealOwnIdentity(UUID)`
- `drawCards(UUID, int, boolean)`
- `shuffleDiscardIntoDeck(UUID)`
- `stealRandomCard(UUID, UUID)`
- `dismantleRandomCard(UUID)`
- `interrogateOpponent(UUID, UUID)`
- `armDecoy(UUID)`
- `restoreOneIdentity(UUID)`
- `addMarkedKill(UUID, int)`
- `addSkipTurns(UUID, int)`
- `addLifeShield(UUID, int)`
- `peekTopCards(UUID, int)`
- `inflictCardDamage(UUID, UUID, String)`
- `eliminatePlayer(UUID, String)`
- `buyItem(UUID, FourthRoomShopItem)`
- `useAssassinationItem(UUID, UUID, FourthRoomShopItem)`
- `placeStickyNote(UUID, BlockPos, Direction, String)`
- `searchNotes(UUID)`
- `grantCoins(UUID, int, String)`
- `buildSnapshot(ServerPlayer)`
- `syncMatchState()`

Role: server-authoritative coordinator. All card resolution, room rotation, economy changes, snapshot sync, and elimination flow are routed through this class.

Additional responsibility in the current implementation: it also mirrors each room's public battle state onto the in-world table block entity and emits timed card / pulse / banner / camera events for nearby clients.

### FourthRoomTableBlock / FourthRoomTableBlockEntity

- `placeStructure(LevelAccessor, BlockPos, Direction)`
- `applyRoomVisualState(...)`
- `broadcastEffects(List<EffectEvent>)`
- `startCardAnimation(...)`
- `startPulse(...)`
- `showBanner(...)`

Role: 3x3 in-world battle table. The block itself is now a multiblock shell centered on the room anchor, while the center block entity acts as the synced host for draw/discard counts, seat ownership, recent public actions, flying cards, banner text, and local animation state.

Current interaction model:

- card battle no longer depends on the old screen as the primary input path
- the client renders an in-world hand HUD while the player is looking at their own room table
- scroll the hotbar to choose the current hand card
- right-click the table center to play a non-target card
- right-click a seat identity area to play a target card at that player
- right-click the draw pile to end turn
- sneak and right-click your own identity area to reveal one hidden identity block
- the table surface no longer prints full `xxx 对 xxx 使用了什么` style battle text; recent played cards are rendered as a loose random stack on the felt instead
- identity cards are rendered near each seat, with hidden cards kept face-down for other players while the local player can still see their own full identity set client-side

### TableEffectEvents / FourthRoomTableEffectsPayload

- `CardMotion`
- `Pulse`
- `Banner`
- `CameraFocus`

Role: dedicated client animation transport. Unlike `FourthRoomStatePayload`, this path carries short-lived cinematic events with millisecond offsets, so card motion and camera focus are not forced through the coarse JSON snapshot path.

### RoomManager

- `buildRoomDefinitions()`
- `assignRooms(List<FourthRoomPlayerState>)`
- `teleportPlayersToRooms()`
- `teleportAlivePlayersToLobby()`
- `getOpponent(UUID)`
- `advanceTurn(int)`

Role: virtual room generation and pairing. It currently derives room positions from the world spawn plus config offsets.

Current behavior: seat anchors are now derived perpendicular to each room door so the two players face the 3x3 table instead of clipping into the doorway.

### FourthRoomSceneGenerator

- `generate(BlockPos)`

Role: code-generated test scene builder. It creates a lobby, connected test rooms, seat blocks, corridors, duel arena, and writes the generated anchors into Fourth Room saved data plus the existing area component.

Current room centerpiece: `FOURTH_ROOM_TABLE` is now placed as a 3x3 multiblock in each room center instead of the old coffee-table placeholder.

### FourthRoomTaskScheduler

- `tick()`
- `scheduleNextTask(long)`
- `completeTask(UUID)`
- `hasActiveTask()`
- `clearActiveTask()`
- `startRandomTask()`

Role: global off-table task window controller.

### FourthRoomShopService

- `buy(UUID, FourthRoomShopItem)`
- `useAssassinationItem(UUID, UUID, FourthRoomShopItem)`
- `placeStickyNote(UUID, BlockPos, Direction, String)`
- `searchNotes(UUID, int)`

Role: gold economy, assassination item consumption, sticky-note placement and discovery.

### FourthRoomDuelManager

- `tick()`
- `maybeResolveWinCondition()`
- `startFinalDuel()`
- `finishMatch(FourthRoomTeam, String)`

Role: end-of-match resolution and final duel fallback.

## Card Implementation Notes

### Base Cards

- `death`: instant on draw, deals one damage to the drawer, never enters hand.
- `cleanse`: shuffles discard back into deck.
- `bottom_draw`: draws one card from deck bottom.
- `seize`: steals one random eligible card from opponent hand.
- `skip`: stores one future skipped turn on self.
- `point_kill`: adds one delayed damage stack to target's next turn.
- `dismantle`: moves one random eligible opponent card to deck bottom.
- `peek`: stores top three card ids in the player peek cache.
- `life`: grants one damage shield.

### Skill Cards

- `rebuild`: recycle discard and draw two.
- `interrogate`: inspect opponent hand server-side and push one eligible card to deck bottom.
- `decoy`: arm a one-time redirect flag.
- `first_aid`: restore one revealed identity card.
- `fate_shift`: remove one point-kill stack and peek top two.

### Point-Kill Stack Logic

The game manager stores stacked delayed hits in `FourthRoomPlayerState.markedForKill`.

Resolution order:

1. turn changes to the target
2. all stored stacks are read once
3. the counter is reset to zero before damage is applied
4. each stack deals one separate damage instance
5. each damage instance can be blocked independently by `life` or redirected by `decoy`

This avoids double-resolution when turn changes and keeps stack behavior deterministic.

### Random Seize / Dismantle Logic

Eligible pool:

1. collect only cards whose definition returns `canBeStolenOrDismantled() == true`
2. skill cards are excluded by design
3. choose one uniformly from the remaining list
4. move it directly to attacker hand or target deck bottom

This keeps random behavior simple and verifiable on the server.

## Network Payloads

- `FourthRoomStatePayload`: server to client snapshot json.
- `FourthRoomTableEffectsPayload`: server to nearby clients timed table animation batch.
- `CardPlayPayload`: client to server card play request.
- `BuyFourthRoomItemPayload`: client to server shop purchase.
- `RevealIdentityPayload`: client to server manual reveal.
- `CompleteFourthRoomTaskPayload`: client to server task completion hook.
- `EndTurnPayload`: client to server end-turn request.
- `UseAssassinationItemPayload`: client to server assassination item request.

All resolution still occurs on the server. The client only sends intent.

`FourthRoomStatePayload` is now the coarse tactical snapshot, while `FourthRoomTableEffectsPayload` carries the immersive layer: flying cards, banner pulses, and event camera focus.

## World Table Battle

The main battle surface is no longer the old fullscreen card screen. The current flow is:

1. each generated room contains a 3x3 physical card table
2. players are teleported to stools aligned to the sides of that table
3. `FourthRoomGameManager` mirrors each room's public state onto the center table block entity
4. nearby clients render draw pile, discard pile, public action cards, seat markers, turn highlight, banner text, and animated flying cards via a block-entity renderer
5. every logged public room event triggers a timed effect batch with banner, pulse, and optional camera focus

The old `FourthRoomBattleScreen` still exists, but it is now treated as a tactical side panel rather than the primary battlefield.

Current client interaction:

- look at your own room table
- when an inactive scene table reaches `2/2`, the table now auto-starts a two-player Fourth Room match using exactly those two seated players
- press `H` to toggle the tactical panel
- watch the real battle state update on the table itself

This preserves the mature card-selection UI while moving the actual presentation into the world.

## Immersion Layer

Implemented immersive elements:

- card-motion animation from player seat to table center and then to discard pile
- draw animation from draw pile to the acting seat
- table pulse highlight for damage, reveal, defense, and turn changes
- cinematic banner text above the table for every public action
- short camera-director events that briefly pull the player's view toward the acting seat, target seat, or current turn holder
- HUD prompt near the table reminding the player that `H` opens the tactical panel for the looked-at room table

## Commands

### Start

- `/tmm:start sre:fourth_room`
- `/tmm:start sre:fouth_room`
- `/tmm:start sre:fouth_room 8`

Notes:

- the typo form `sre:fouth_room` is intentionally kept as an alias for compatibility with your requested command.
- for Fourth Room, the optional integer parameter is interpreted as requested player count instead of countdown minutes.

### Runtime Control

- `/tmm:fourthroom status`
- `/tmm:fourthroom generate_test_scene`
- `/tmm:fourthroom generate_test_scene <origin>`
- `/tmm:fourthroom reveal`
- `/tmm:fourthroom play <cardId> [target]`
- `/tmm:fourthroom endturn`
- `/tmm:fourthroom buy <itemId>`
- `/tmm:fourthroom use_item <itemId> <target>`
- `/tmm:fourthroom task_complete`
- `/tmm:fourthroom search_notes`

These commands currently act as the fallback interaction layer until a dedicated GUI is added.

Updated positioning:

- world-table interaction is now the primary presentation path
- these commands remain the fastest debug / test hooks for local iteration
- `H` is no longer a global battle screen toggle; it opens the tactical panel only while looking at your own room table

Recommended local test flow:

1. run `/tmm:fourthroom generate_test_scene` where you want the lobby floor center
2. gather players inside the generated lobby / ready area
3. run `/tmm:start sre:fouth_room 8`
4. use `/tmm:fourthroom status` during testing to inspect phase, scene anchor and hand state

## Config

Generated file:

- `config/starrailexpress-fourth-room.json`

Key fields:

- `defaultPlayerCount`
- `roomCount`
- `playersPerRoom` (runtime is fixed to `2`, one table = one duel)
- `maxRotations`
- `rotationIntervalSeconds`
- `taskMinIntervalSeconds`
- `taskMaxIntervalSeconds`
- `taskDurationSeconds`
- `goldMultiplier`
- `itemPrices`
- `duelMapName`

## Build And Run

Requirements:

- Java 21
- Fabric Loader / Fabric API matching Minecraft 1.21.1
- Mojang mappings are already enabled in `build.gradle`

Commands:

- `./gradlew compileJava`
- `./gradlew runClient`
- `./gradlew build`

## Current Limitations And Recommended Next Steps

### Seat Blocks And Right-Click Seating

Current state:

- rooms are virtual and players are teleported to generated seat positions.

Recommended production upgrade:

- add a dedicated mountable room-seat block or bind to pre-placed seat markers in your map structure.

### GUI / Screen / HandledScreen

Current state:

- state sync payloads and command fallbacks are implemented.
- a dedicated card table GUI has not been added yet.

Recommended production upgrade:

- add a client screen that renders `FourthRoomStatePayload` and emits the existing C2S payloads.

### Off-Table Task Validation

Current state:

- scheduler, rewards, and completion entry points exist.
- real-world flavored tasks such as drinking water or restroom behavior still need map/block/entity hooks.

Recommended production upgrade:

- connect task completion to concrete Fabric events, block interactions, or scoreboard triggers.

### Final Duel Trigger

Original design issue:

- if one team has zero survivors, there is no true two-team duel left to resolve.

Current server behavior:

- immediate victory is declared when one team is fully eliminated.
- forced duel fallback starts when max rotations are reached and both teams still have survivors.

Recommended production upgrade:

- if you want a real final arena, use a different trigger such as max rotations reached, sudden-death timer, or both teams reduced below a survivor threshold.
