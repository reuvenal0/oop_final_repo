# 🚀 LatentSpace Explorer

LatentSpace Explorer is an interactive JavaFX application for exploring
high-dimensional embedding spaces.

The system enables visual and mathematical investigation of semantic
relationships between entities (e.g., words) while maintaining strict
architectural separation between:

-   🧠 Mathematical computation engine
-   📦 Data model
-   🔧 Application (Use Cases) layer
-   🎨 View transformation layer
-   🖥 Graphical UI (JavaFX)

Although demonstrated using word embeddings, the system is fully generic
and can represent **any entity that can be embedded into a numeric
vector space** (images, DNA sequences, documents, etc.).

The project emphasizes clean architecture, immutability, extensibility,
and separation of concerns.

------------------------------------------------------------------------

# ✨ Core Features

-   2D and 3D visualization of embedding spaces
-   Runtime selection of distance metric (Cosine / Euclidean)
-   K-Nearest Neighbors search
-   Custom semantic projection axes
-   Vector arithmetic (e.g., `king - man + woman`)
-   Subspace Grouping (centroid analysis of multiple entities)
-   Overlay visualization of vector computation paths
-   Support for multiple representations (e.g., FULL + PCA)

------------------------------------------------------------------------

# 🏗 Architecture Overview

The system follows a layered architecture:

    IO Layer
    Model Layer
    Metrics & Projection
    Application Layer (Use Cases)
    View Transformation Layer
    JavaFX UI

Each layer has a clearly defined responsibility and does not violate
boundaries.

The UI never accesses model or metric classes directly --- it
communicates only through the `ExplorerUseCases` interface.

------------------------------------------------------------------------

# 📂 IO Layer

Responsible for loading external data and transforming it into model objects.
Completely isolated from mathematical logic and UI.

## Main Components

### Representation

Flyweight object representing a representation type (e.g., `FULL`, `PCA`).

### RepresentationSource`<T>`

Interface defining a source of embeddings for a given representation.

### JsonSource`<T>

Loads embeddings from JSON files using Java streams, validates
identifiers and dimensions, and returns immutable mappings.

### JsonFormat

Defines field names used in JSON input (e.g., `"word"` vs `"id"`).

### PythonScriptRunner

Executes external Python scripts via `ProcessBuilder`.

### EmbeddingsAssembler

Factory that builds an `EmbeddingStorage` from multiple
`RepresentationSource`s while enforcing identical ID sets across
representations.

------------------------------------------------------------------------

# 🧩 Model Layer

The mathematical and structural core of the system.

Defines vector behavior and embedding structures independently from UI and IO.

## Vector (Immutable)

Represents a numeric vector (`double[]`) and supports:

-   Addition
-   Subtraction
-   Scaling
-   Normalization
-   Dot product
-   Centroid computation

All vector operations are pure and return new immutable instances.

## Embedding Structure

### EmbeddingSingle`<T>

Represents a single entity with multiple vector representations.

### EmbeddingGroup`<T>`

Represents a collection of embeddings with centralized access.

### EmbeddingItem`<T>`

Immutable implementation of `EmbeddingSingle`.

### EmbeddingStorage`<T>

Immutable in-memory implementation of `EmbeddingGroup`.

The model layer contains no UI logic.

------------------------------------------------------------------------

# 📏 Distance Metrics (Strategy Pattern)

Distance calculations use the `DistanceMetric` interface.

Supported metrics:

-   Cosine Distance
-   Euclidean Distance

New metrics can be added without modifying existing logic.

------------------------------------------------------------------------

# 🔎 K-Nearest Neighbors

The `NearestNeighbors` class computes the top-K closest entities to:

-   A given entity ID
-   An arbitrary vector

The search is performed in the full vector space (not the projected
display space).

------------------------------------------------------------------------

# 🧭 Projection System

## CustomProjectionService

Builds a semantic axis from two anchor entities and projects all embeddings onto that axis.

Example:

    "poor" ↔ "rich"

## ProjectionAxis

Represents a 1D axis in vector space and computes:

-   Coordinate on axis
-   Orthogonal distance
-   Purity score

## ProjectionScore

Immutable record representing projection results.

------------------------------------------------------------------------

# 🧪 Vector Arithmetic Lab

Allows building and evaluating vector expressions such as:

    king - man + woman

## Workflow

1.  Compute resulting vector.
2.  Retrieve nearest neighbors to the result.
3.  Optionally display the visual computation path.

## Components

-   VectorExpression
-   Term
-   VectorExpressionEvaluator
-   VectorArithmeticLab
-   LabResult

All computation is executed in the Application layer.

------------------------------------------------------------------------

# 🧮 Subspace Grouping (Centroid Analysis)

This module enables semantic analysis of a group of entities instead of
a single entity.

The system:

1.  Retrieves full vectors for selected IDs.
2.  Computes their centroid (vector average).
3.  Finds the K nearest neighbors to that centroid using the selected metric.
4.  Returns results as DTOs to the UI.

Example use case:

    government, military, official, authority

The UI does not compute the centroid itself --- it delegates to
`ExplorerUseCases`.

------------------------------------------------------------------------

# 🎨 View Transformation Layer

Responsible only for transforming vectors into display coordinates.

Contains no semantic logic.

## Main Classes

-   ViewPoint
-   Point2D / Point3D
-   LabeledPoint
-   PointCloud
-   ViewMode
-   ViewMode2D
-   ViewMode3D
-   ViewSpace

Switching between 2D and 3D does not affect the mathematical core.

------------------------------------------------------------------------

# 🔌 Application Layer (Use Cases Boundary)

Acts as the boundary between UI and core logic.

## ExplorerUseCases

Defines all operations available to the UI:

-   Load dataset
-   Select metric
-   Retrieve neighbors
-   Projection
-   Vector arithmetic
-   Subspace grouping
-   View configuration

## ExplorerApplicationService

Concrete implementation that:

-   Loads data
-   Instantiates KNN, projection, and lab modules
-   Manages selected metric and representation
-   Returns immutable DTOs to UI

## DTOs

-   NeighborView
-   ProjectionScoreView
-   LabResultView
-   MetricOption
-   AppliedViewConfig

DTOs prevent UI dependency on internal model classes.

------------------------------------------------------------------------

# 🖥 JavaFX UI

The UI provides:

-   Point cloud visualization (2D & 3D)
-   Axis selection
-   Entity search
-   Nearest neighbors tab
-   Projection tab
-   Vector Arithmetic Lab tab
-   Subspace Grouping tab

## Main Components

### FxApp

Application entry point.

### MainView

Main UI container managing state and interaction.

### Scatter2DView

Canvas-based 2D rendering with zoom, pan, and selection.

### Cloud3DView

JavaFX 3D visualization (basic interaction supported).

### LabTabController

Handles vector arithmetic UI interactions.

### GroupTabController

Handles Subspace Grouping UI logic.

### NeighborDisplayFormatter

Formats neighbor results for display.

The UI performs no semantic computation.

------------------------------------------------------------------------

# 🧪 Testing

The project includes extensive unit tests covering:

-   Model contracts
-   Metrics correctness
-   KNN behavior
-   Projection logic
-   Vector arithmetic
-   Subspace grouping
-   Application layer contracts
-   Acceptance tests

All core logic is testable independently of the UI.

------------------------------------------------------------------------

# ⚙ Build & Run

## Requirements

-   Java 21+
-   Maven 3.9+

## Run tests

``` bash
mvn test
```

## Run application

``` bash
mvn javafx:run
```

------------------------------------------------------------------------

# 🧱 Design Principles

-   Immutability where possible
-   Strict separation of concerns
-   UI depends only on use cases
-   Strategy pattern for extensibility
-   Factory pattern for assembly
-   DTO boundary between UI and core
-   No circular dependencies

------------------------------------------------------------------------

# 🔮 Future Improvements

-   Improved 3D interaction and rendering
-   Visual centroid rendering in Subspace Grouping
-   Additional distance metrics
-   Performance optimizations for very large datasets

------------------------------------------------------------------------

# 🎯 Project Goal

The goal of this project is not merely to visualize embeddings, but to
provide a clean, extensible infrastructure for research and exploration
of vector spaces, independent of data type.

The system is designed as a foundation for experimentation in
representation learning and semantic analysis.
