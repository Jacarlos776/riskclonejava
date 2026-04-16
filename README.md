# 🚩 RISK GAME !!!!!!!

## 🛠️ Environment Setup (Prerequisites)

### 1. Java Development Kit (JDK)
Must have **JDK 21** or higher installed. 
* Check your version: `java -version`
* If missing, download it ://

### 2. Maven (The Build Tool)
If you're using **VS Code** and in windows, run this in the directory:

.\mvnw clean install

---

## 🚀 Installation & Launch

### 1. Clone the repository
```bash
git clone https://github.com/Jacarlos776/riskclonejava.git
cd riskclonejava
```

### 2. Run the application
To run the application:
```bash
.\mvnw javafx:run
```


### Note: I used IntelliJ IDEA, but I tested the git clone by running it in VSCode and it worked naman. Just needed to setup env variables for the java


# PROJECT GUIDE
You're only going to be looking at two folders really: java folder and resources folder

## Resources Folder
- In this folder you will find our **map.svg** that represents the provinces for our game. If you're using VSCode its gonna default as a map preview, so you're just gonna see the map. You want to right click the map.svg then Open with... and open with Text Editor. Inside you will see 81 <path> elements each with an id like PH-CEB and a title like "Cebu". (I got the map from wikipedia)

- Another file you will find in here is the **province.json**. Its an adjacency list. On the left is the province itself, and on the right is a list of provinces that are 'adjacent' to that province. Right now I only did three provinces, we need to do all 81 (lmaoo). To help with that, I added debugging in the code so when you click a province you can see their ID.

## Java Folder
- In this folder you will see an **engine folder**, **view folder**, and **Launcher.java** and **Main.java**


### Engine Folder
- Where we will store game state and logic. Currently only has one file, **adjancencyService**: The "Brain" of the map logic, responsible for validating connections between provinces.
    JSON Integration: Uses Jackson Databind to parse province.json into a Java Map. This translates the raw text data into a searchable memory structure.

    Adjacency Logic (areAdjacent): Performs a high-performance $O(1)$ lookup to check if two province IDs are neighbors. This prevents players from "teleporting" armies across the map and ensures moves only occur between valid borders.

    Resource Handling: Designed to load data directly from the JAR resources, making the game portable and easy to distribute.

### View Folder
- **SvgMapLoader.java**:
    loadMap(...): Parses an SVG file via DocumentBuilder. Iterates through <path> elements to create SVGPath nodes, assigning IDs and injecting mouse listeners for hover/click events.

    setNodeSelected(...): A utility method to switch a province's visual state. It updates the internal selected property and applies the corresponding CSS styling (Amber/Gold).

    Hover Logic: Inline listeners that swap styles between NORMAL and HOVER. Includes a toFront() call to ensure the active province's borders are never obscured by neighbors.

- **InteractiveMapPane.java**: 
    Constructor: Initializes the two layers (provinceLayer, arrowLayer). Order is hardcoded so arrows always render on top of provinces.

    handleProvinceClick(...): The primary state machine for player input. Handles three states: Select (first click), Deselect (click same province), or Move (click adjacent province).

    drawArrow(...): Calculates the geometric center of two provinces using getBoundsInParent() and renders a dashed DARKRED tactical arrow on the top layer.

    handleScroll(...): Implements anchored zooming. It calculates a "pivot shift" to ensure the map expands or contracts relative to the cursor position rather than the pane's center.

    handleMouseDragged(...): Manages panning by tracking the delta between the current mouse position and the lastMouse coordinates, applying it to the pane's translation.

### Main and Launcher
- Launcher.java: Does nothing really.
- Main.java: assembles the Game World and initializes the JavaFX Stages, does five things:
  - Step 1:	Initialize Services - Loads the AdjacencyService first to ensure the game rules are ready before the UI exists.
  - Step 2:	Component Dependency - Creates the InteractiveMapPane and injects the AdjacencyService into it.
  - Step 3:	Event Binding - Uses a Method Reference (gameBoard::handleProvinceClick) to link the SVG parser to the map controller. This ensures that when an SVG path is clicked, the InteractiveMapPane knows exactly which province was triggered.
  - Step 4:	Scene Graph Construction - Wraps the interactive map in a StackPane (used here as the "Ocean" background) and sets the initial window resolution to 1280x720.
  - Step 5:	Stage Launch - Sets the window title and makes the application visible to the user.

