# PROJECT CHECKLIST

Add tasks here as we progress. Add a checkmark to the empty boxes as we clear 'em:

## 🟢 Phase 1: Data & Core Model
- [ ] **PROVINCE MAPPING:** Complete the `province.json` adjacency list for all 81 provinces. Make sure to give isolated islands adjacencies as well so they're not useless.

- [ ] **REGION JSON** Complete the `region.json` list for all 18 regions, grouping the provinces by regions, and giving them bonus armies

- [X] **Serialization:** Create the `GameState` POJO and test saving/loading the current state to a JSON file using Jackson.

- [X] **Owner-Based Styling:** Modify `SvgMapLoader` or `Main` to color provinces based on `ownerId` (e.g., Player 1 = Red, Player 2 = Blue).

## 🟡 Phase 2: Gameplay UI & Interaction
- [X] **Troop Count Labels:** 

  - [X] Create a `uiLayer` in `InteractiveMapPane`.
  - [X] Add `Text` nodes that follow the center of each province to show current army size.

- [X] **Move Validation Enhancements:**
        
  - [X] Prevent players from selecting/moving from a province they don't own.
  - [X] ~~Add a "Cancel Move" button or right-click to clear the `arrowLayer`. Maybe a drag animation from one province to another? IDK whichever's easiest to implement~~ Update: when the user moves army count from one province to another to zero, the arrow just disappears
- [X] **Troop Allocation UI:** Create a small JavaFX `HBox` or `Slider` that pops up when a move is queued to select the number of troops.

- [X] **Turn Timer:** Implement a `Timeline` or `AnimationTimer` for the 60-second countdown.
- [X] **Switch Players:** Button that switches player, down the line we'll refactor this to just indicate that the player is done moving
- [X] **Resolution Engine:** handles what happens when armies move, has three phases
  - [X] **Phase 1 DEPARTURES:** Decreases army count for all departed provinces
  - [X] **Phase 2 CROSSFIRES:** What happens when Army 1 from Province A goes to Province B and Army 2 from Province B goes to Province A, and they meet head on
  - [X] **Phase 3 CONVERGENCE & CLASHES:** Has three possible scenarios - Peaceful Transfer, Standard Invasion, Multi-way Bloodbath. Check code itself for more info
  - [X] **Bonus Phase 4 TIE-BREAKER:** If army is an exact match, defender has advantage, else if two attackers are tied while invading an empty or third party province, they annihilate each other, nobody gets the province.

## 🔵 Phase 3: idk
- [X] **Initial drafting:** Create an initial phase where players can pick a region to place their initial armies
- [X] **Drafting Phase:** Allow players to allocate their armies
  - [X] **Number of province army allocation:** Depending on how many provinces they control, give them a certain amount of armies
- [X] **Region Bonus:** Give 'em more armies if they control regions
- [X] **Menu Scene:** Scene before the actual gameplay,
  - [X] **Color Wheel:** Allow the player to change their color
  - [X] **Initial Province:** Allow the player to choose their initial province
  - [X] **Add Players:** Allow them to add and remove players
- [X] **Win & Lose Condition:** Player loses and is not iterated upon anymore when he has no territories left and a player wins when he's the only player left with territory
- [ ] **AI Enemy:** Implement an AI
  - [X] **AI Toggle:** Make a button in the lobby allowing players to be AI
  - [X] **AI Drafting:** Make AI know how to draft

## 🔴 MoSCoW

### Must Haves

### Should Haves
- Redesign Arrow: it looks terrible
- Resolution Animation: Show all the moves made by the players, maybe make it province by province or something, to make it easier to see.
- Redesign Scenes: Make it not look like a 2000s flash game.
- Menu Stage: join the host server by inputting port. Color wheel should appear after joining host and we should be able to see what colors other players chose/are choosing.
- Button or something to hover over in-game that highlights the regions available
- Resolution Animation: shows how the armies fought
- When moving armies in planning phase, it should decrement the current province's numbers for better visibility.

### Could Haves
- Music and Mute button on top right
- Music during resolution phase, music during animation of resolution phase
- Cards: When you win a province, you get part of a card, complete a set, and you can get extra armies.
- Combat Modifier: Add a lil bit of randomness to the resolution phase by giving armies +/- 10% combat efficiency roll, letting smaller armies potentially beat larger ones.
- In the initial phase, allow players to pick more than 1 province, 
- Change draft allocation to use a slider instead of tapping
- Rule scene, showing how the game works
### Won't Haves