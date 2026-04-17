# PROJECT CHECKLIST

Add tasks here as we progress. Add a checkmark to the empty boxes as we clear 'em:

## 🟢 Phase 1: Data & Core Model (In Progress)
- [ ] **PROVINCE MAPPING:** Complete the `province.json` adjacency list for all 81 provinces. Make sure to give isolated islands adjacencies as well so they're not useless.

- [X] **Serialization:** Create the `GameState` POJO and test saving/loading the current state to a JSON file using Jackson.

- [X] **Owner-Based Styling:** Modify `SvgMapLoader` or `Main` to color provinces based on `ownerId` (e.g., Player 1 = Red, Player 2 = Blue).

## 🟡 Phase 2: Gameplay UI & Interaction
- [X] **Troop Count Labels:** 

  - [X] Create a `uiLayer` in `InteractiveMapPane`.
  - [X] Add `Text` nodes that follow the center of each province to show current army size.

- [ ] **Move Validation Enhancements:**
        
  - [ ] Prevent players from selecting/moving from a province they don't own.
  - [X] ~~Add a "Cancel Move" button or right-click to clear the `arrowLayer`. Maybe a drag animation from one province to another? idk whichever's easiest to implement~~ Update: when the user moves army count from one province to another to zero, the arrow just disappears
- [X] **Troop Allocation UI:** Create a small JavaFX `HBox` or `Slider` that pops up when a move is queued to select the number of troops.

- [X] **Turn Timer:** Implement a `Timeline` or `AnimationTimer` for the 60-second countdown.
- [X] **Switch Players:** Button that switches player, down the line we'll refactor this to just indicate that the player is done moving
- [X] **Resolution Engine:** handles what happens when armies move, has three phases
  - [X] **Phase 1 DEPARTURES:** Decreases army count for all departed provinces
  - [X] **Phase 2 CROSSFIRES:** What happens when Army 1 from Province A goes to Province B and Army 2 from Province B goes to Province A and they meet head on
  - [X] **Phase 3 CONVERGENCE & CLASHES:** Has three possible scenarios - Peaceful Transfer, Standard Invasion, Multi-way Bloodbath. Check code itself for more info
  - [X] **Bonus Phase 4 TIE-BREAKER:** If army is an exact match, defender has advantage, else if two attackers are tied while invading an empty or third party province, they annihilate each other, nobody gets the province.

## 🔴 MoSCoW

### Must Haves

### Should Haves
- Redesign Arrow: it looks terrible
- Resolution Animation: Show all the moves made by the players, maybe make it province by province or something, to make it easier to see.
### Could Haves
- Music and Mute button on top right
- Color Picker/Wheel - let the players decide on their own colors
- Music during resolution phase, music during animation of resolution phase
- Cards: When you win a province, you get part of a card, complete a set and you can get extra armies.
- Combat Modifier: Add a lil bit of randomness to the resolution phase by giving armies +/- 10% combat efficiency roll, letting smaller armies potentially beat larger ones.
### Won't Haves