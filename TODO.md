# PROJECT CHECKLIST

Add tasks here as we progress. Add a checkmark to the empty boxes as we clear 'em:

## 🟢 Phase 1: Data & Core Model (In Progress)
- [ ] **PROVINCE MAPPING:** Complete the `province.json` adjacency list for all 81 provinces. Make sure to give isolated islands adjacencies as well so they're not useless.

- [X] **Serialization:** Create the `GameState` POJO and test saving/loading the current state to a JSON file using Jackson.

- [X] **Owner-Based Styling:** Modify `SvgMapLoader` or `Main` to color provinces based on `ownerId` (e.g., Player 1 = Red, Player 2 = Blue).

## 🟡 Phase 2: Gameplay UI & Interaction
- [ ] **Troop Count Labels:** 

  - [X] Create a `uiLayer` in `InteractiveMapPane`.
  - [X] Add `Text` nodes that follow the center of each province to show current army size.

- [ ] **Move Validation Enhancements:**
        
  - [ ] Prevent players from selecting/moving from a province they don't own.
  - [ ] Add a "Cancel Move" button or right-click to clear the `arrowLayer`. Maybe a drag animation from one province to another? idk whichever's easiest to implement
- [ ] **Troop Allocation UI:** Create a small JavaFX `HBox` or `Slider` that pops up when a move is queued to select the number of troops.

- [ ] **Turn Timer:** Implement a `Timeline` or `AnimationTimer` for the 60-second countdown.

## MoSCoW

### Must Haves

### Should Haves
- Redesign Arrow
### Could Haves
- Music and Mute button on top right
- Color Picker/Wheel - let the players decide on their own colors
### Won't Haves