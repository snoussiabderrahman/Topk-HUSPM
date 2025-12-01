# Top-K High Utility Sequential Pattern Mining (TKUSP)

Implementation of two variants of the TKUSP algorithm for mining top-k high utility sequential patterns using Cross-Entropy optimization.

## Table of Contents

- [Overview](#overview)
- [Algorithms](#algorithms)
  - [TKUSP (Base Algorithm)](#tkusp-base-algorithm)
  - [TKUSP_V1 (Enhanced Algorithm)](#tkusp_v1-enhanced-algorithm)
- [Key Features](#key-features)
- [Data Structures](#data-structures)
- [Compilation and Execution](#compilation-and-execution)
- [Configuration Parameters](#configuration-parameters)
- [Project Structure](#project-structure)

---

## Overview

This project implements Cross-Entropy based algorithms for discovering the top-k sequential patterns with highest utility from transaction databases. The algorithms iteratively learn probability distributions to generate candidate patterns and converge towards optimal solutions.

**Problem**: Given a sequence database with utility information, find the k sequential patterns with highest utility values.

---

## Algorithms

### TKUSP (Base Algorithm)

The baseline Cross-Entropy optimization algorithm for Top-K Utility Sequential Pattern mining.

#### Key Components

1. **Initialization**

   - Compute singleton utilities (1-item patterns)
   - Initialize top-k with k highest utility singletons
   - Set initial minUtil threshold from k-th singleton
   - Initialize Probability Matrix (PM) uniformly at 0.5

2. **Iterative Learning Loop**

   - **Generate Sample**: Create N sequences using PM
   - **Evaluate**: Calculate utility for each sequence
   - **Select Elite**: Keep top ρ×N sequences (elite)
   - **Update Top-K**: Merge elite with current top-k
   - **Update PM**: Learn from elite distributions
   - **Prune**: Update promising items based on new minUtil

3. **Termination Conditions**
   - Maximum iterations reached
   - PM becomes binary (converged)

#### Probability Matrix (PM)

- **Dimensions**: `[num_items × max_sequence_length]`
- **Semantics**: `PM[i][j]` = probability that item `i` appears at position `j`
- **Initialization**: All values set to 0.5 (maximum uncertainty)
- **Update Formula**:
  ```
  PM[item][position] = count(item at position in elite) / count(sequences with position in elite)
  ```
- **Preservation**: When items are pruned, PM values for remaining items are preserved

#### Pseudocode

```
Algorithm: TKUSP
Input: Dataset D, k (number of patterns), N (sample size),
       ρ (elite ratio), maxLength, maxIterations
Output: Top-K patterns with highest utilities

1: Compute singleton utilities
2: Initialize topK ← k highest utility singletons
3: minUtil ← utility of k-th singleton
4: Prune items with utility < minUtil
5: Initialize PM[items × maxLength] ← 0.5

6: for iteration = 1 to maxIterations do
7:     // Generate sample
8:     sample ← []
9:     for i = 1 to N do
10:        seqLength ← random(1, maxLength)
11:        seq ← []
12:        for pos = 0 to seqLength-1 do
13:            itemset ← generateItemset(PM, pos)
14:            seq.append(itemset)
15:        sample.append(seq)
16:
17:    // Evaluate and sort
18:    for each seq in sample do
19:        seq.utility ← calculateUtility(seq, D)
20:    sort sample by utility (descending)
21:
22:    // Select elite
23:    eliteSize ← ceil(ρ × N)
24:    elite ← sample[0:eliteSize]
25:
26:    // Update top-k
27:    topK ← merge and keep k best from (topK ∪ sample)
28:
29:    // Update threshold and prune
30:    if topK has k patterns:
31:        newMinUtil ← topK[k-1].utility
32:        if newMinUtil > minUtil:
33:            minUtil ← newMinUtil
34:            prune items and sequences
35:            rebuild PM preserving remaining items
36:
37:    // Update probability matrix
38:    for each item i in items do
39:        for each position j in [0, maxLength) do
40:            count ← 0, total ← 0
41:            for each seq in elite do
42:                if j < seq.length:
43:                    total ← total + 1
44:                    if item i in seq[j]:
45:                        count ← count + 1
46:            PM[i][j] ← count / total  (or 0 if total = 0)
47:
48:    if PM is binary: break  // Converged
49:
50: return topK
```

---

### TKUSP_V1 (Enhanced Algorithm)

An improved version with three major enhancements over the base algorithm.

#### Enhancement 1: Early Stopping Mechanisms

Two stopping conditions prevent unnecessary iterations:

**1. Top-K Stagnation**

- **Detection**: k-th utility unchanged for X consecutive iterations
- **Formula**: If `utility(topK[k-1])` doesn't improve for X iterations → stop
- **Benefit**: Stops when no further improvement is possible

**2. PM Stabilization**

- **Detection**: PM variation below threshold ε for Y consecutive iterations
- **Formula**: If `max|PM_new[i][j] - PM_old[i][j]| < ε` for Y iterations → stop
- **Benefit**: Stops when probability distribution has converged

#### Enhancement 2: Adaptive Sequence Length Distribution

Instead of uniform random length selection, learns optimal lengths from elite.

**Initialization**:

```
p(i) = 1 / max_length_seq  ∀i ∈ [1..max_length_seq]
```

**Update (after each iteration)**:

```
occ(i) = number of sequences of length i in elite
p_new(i) = (occ(i) + 1) / (elite_size + max_length_seq)
```

**Sampling**:

- Draw length from learned distribution p(i) using CDF
- Sequences concentrate around successful lengths

#### Enhancement 3: Smoothing Mutation

Adaptive balance between exploration (random) and exploitation (PM-based).

**Smooth Factor**:

```
α = [(u_best - u_quantile) / u_best] × ρ

where:
  u_best = elite[0].utility  (best sequence)
  u_quantile = elite[⌊elite_size × ρ⌋].utility
  ρ = elite ratio
```

**Sample Generation**:

- **Exploration** (⌊N × α⌋ sequences):
  - Random length (uniform [1..maxLength])
  - Random items from promising items
  - Random itemset sizes from dataset distribution
- **Exploitation** (N - ⌊N × α⌋ sequences):
  - Length from learned distribution p(i)
  - Items selected using PM probabilities
  - Itemset sizes from dataset distribution

**Adaptive Behavior**:

- **Early iterations**: High elite diversity → high α → more exploration
- **Late iterations**: Elite converges → low α → more exploitation
- **Convergence**: α → 0 → pure PM-based generation

#### Pseudocode Additions (TKUSP_V1)

```
Algorithm: TKUSP_V1 (additions to TKUSP)

// After line 5 (initialization):
5a: Initialize lengthProbs[maxLength] ← 1/maxLength uniformly
5b: elite ← null
5c: stagnationCounter ← 0, stabilizationCounter ← 0
5d: lastKthUtility ← 0, previousPM ← null

// Modified loop condition (line 6):
6: while iteration ≤ maxIterations AND not binary(PM)
         AND not hasStagnated AND not hasStabilized do

// After line 6 (start of loop):
6a:    // Calculate smooth factor
6b:    if iteration = 1:
6c:        smoothFactor ← ρ
6d:    else:
6e:        quantileIdx ← floor(elite.size × ρ)
6f:        uBest ← elite[0].utility
6g:        uQuantile ← elite[quantileIdx].utility
6h:        smoothFactor ← ((uBest - uQuantile) / uBest) × ρ
6i:        smoothFactor ← clamp(smoothFactor, 0, 1)

// Modified sample generation (lines 8-15):
8:     numRandom ← floor(N × smoothFactor)
9:     numPMBased ← N - numRandom
10:    sample ← []
11:
12:    // Generate random sequences (exploration)
13:    for i = 1 to numRandom do
14:        seqLength ← random(1, maxLength)  // uniform
15:        seq ← generateRandomSequence(seqLength)
16:        sample.append(seq)
17:
18:    // Generate PM-based sequences (exploitation)
19:    for i = 1 to numPMBased do
20:        seqLength ← sampleFromDistribution(lengthProbs)  // learned
21:        seq ← generatePMBasedSequence(seqLength, PM)
22:        sample.append(seq)

// After line 46 (PM update):
46a:   // Update length distribution
46b:   for i = 1 to maxLength do
46c:       occ[i] ← count sequences of length i in elite
46d:   for i = 1 to maxLength do
46e:       lengthProbs[i] ← (occ[i] + 1) / (elite.size + maxLength)

// After line 46e (stagnation detection):
46f:   currentKthUtility ← topK[k-1].utility
46g:   if currentKthUtility > lastKthUtility:
46h:       stagnationCounter ← 0
46i:   else:
46j:       stagnationCounter ← stagnationCounter + 1
46k:       if stagnationCounter ≥ X:
46l:           hasStagnated ← true
46m:   lastKthUtility ← currentKthUtility

// After line 46m (stabilization detection):
46n:   if previousPM is not null:
46o:       pmVariation ← max|PM - previousPM|
46p:       if pmVariation < ε:
46q:           stabilizationCounter ← stabilizationCounter + 1
46r:           if stabilizationCounter ≥ Y:
46s:               hasStabilized ← true
46t:       else:
46u:           stabilizationCounter ← 0
46v:   previousPM ← copy(PM)
```

---

## Key Features

### Common Features (Both Algorithms)

1. **Optimized Data Structures**

   - `OptimizedDataStructures`: Efficient storage and retrieval
   - `UtilityCalculator`: Cached utility computations
   - `DatasetStatistics`: Pre-computed dataset metrics

2. **Promising Item Pruning**

   - Dynamic pruning based on current minUtil threshold
   - Reduces search space and computation

3. **Utility Caching**

   - Cache utility calculations to avoid redundant computations
   - Invalidate cache when items are pruned

4. **Elite-Based Learning**
   - Learn from top ρ×N sequences each iteration
   - Probability updates based on elite distributions

### TKUSP_V1 Exclusive Features

1. **Early Stopping**

   - Stagnation detection (k-th utility)
   - Stabilization detection (PM variation)

2. **Adaptive Length Distribution**

   - Learn length probabilities from elite
   - Concentrate on successful pattern lengths

3. **Smoothing Mutation**

   - Dynamic exploration-exploitation balance
   - Prevents premature convergence

4. **Enhanced Convergence**
   - Typically requires fewer iterations
   - Better quality patterns

---

## Data Structures

### Core Data Structures

#### 1. OptimizedDataStructures

```java
class OptimizedDataStructures {
    - sequenceItemMap: Map<Integer, Set<Integer>>  // sequence -> items
    - invertedIndex: Map<Integer, List<SequencePosition>>  // item -> positions
    - promisingItems: List<Integer>  // items with utility >= minUtil
    - currentMinUtil: long

    + computeSingletonUtilities(items): Map<Integer, Long>
    + updatePromisingItems(minUtil): void
    + releaseNonPromisingDistinctIds(): void
}
```

**Purpose**: Efficient storage and retrieval of sequence-item relationships

#### 2. UtilityCalculator (Static Cache)

```java
class UtilityCalculator {
    - static cache: Map<String, Entry>  // pattern signature -> utility

    + calculateSequenceUtility(seq, dataStructures): long
    + clearCache(): void
    + invalidateCacheForRemovedItems(removedItems): void
    + printCacheStatistics(): void
}
```

**Purpose**: Cache utility calculations to avoid redundant computations

#### 3. DatasetStatistics

```java
class DatasetStatistics {
    - itemsetSizeDistribution: Map<Integer, Integer>
    - cumulativeProbabilities: double[]

    + sampleItemsetSize(random): int  // Sample from distribution
    + printStatistics(): void
}
```

**Purpose**: Pre-compute and sample from dataset distributions

#### 4. Probability Matrix (PM)

```
Type: double[numItems][maxSequenceLength]
Range: [0.0, 1.0]
Semantics: PM[i][j] = probability item i appears at position j
```

#### 5. Length Probability Distribution (TKUSP_V1 only)

```
Type: double[maxSequenceLength]
Range: [0.0, 1.0]
Semantics: lengthProbs[i-1] = probability of generating sequence of length i
Constraint: Σ lengthProbs = 1.0
```

### Model Classes

```java
class Sequence {
    - itemsets: List<Itemset>
    - utility: int
    + length(): int
    + getSignature(): String  // For deduplication
}

class Itemset {
    - items: List<Item>
    + isEmpty(): boolean
}

class Item {
    - id: int
}
```

---

## Compilation and Execution

### Prerequisites

- Java 8 or higher
- All required libraries in `lib/` directory

### Compilation

```bash
# Compile all sources
javac -d bin -cp "src:lib/*" src/**/*.java

# Or compile specific algorithm
javac -cp "src:lib/*" src/algorithms/TKUSP.java
javac -cp "src:lib/*" src/algorithms/TKUSP_V1.java
```

### Execution

#### Basic Example

```bash
java -cp "bin:lib/*" Main <algorithm> <dataset> <k> <parameters>
```

#### Run TKUSP

```bash
java -cp "bin:lib/*" Main TKUSP data/SIGN_sequence_utility.txt 10 \
    --sample-size 100 \
    --rho 0.2 \
    --max-iterations 100 \
    --max-length 10
```

#### Run TKUSP_V1

```bash
java -cp "bin:lib/*" Main TKUSP_V1 data/SIGN_sequence_utility.txt 10 \
    --sample-size 100 \
    --rho 0.2 \
    --max-iterations 100 \
    --max-length 10 \
    --stagnation-threshold 10 \
    --stabilization-threshold 5 \
    --pm-epsilon 0.001
```

### Example with SPMF Library

```bash
# Using SPMF for comparison with exact algorithm (USpan)
java -jar lib/spmf.jar run USpan data/SIGN_sequence_utility.txt output.txt 350 10
```

---

## Configuration Parameters

### Common Parameters (Both Algorithms)

| Parameter           | Type   | Description                              | Typical Value |
| ------------------- | ------ | ---------------------------------------- | ------------- |
| `k`                 | int    | Number of top patterns to find           | 10-100        |
| `sampleSize` (N)    | int    | Number of sequences per iteration        | 50-500        |
| `rho` (ρ)           | double | Elite ratio (fraction kept for learning) | 0.1-0.3       |
| `maxIterations`     | int    | Maximum iterations                       | 50-200        |
| `maxSequenceLength` | int    | Maximum pattern length                   | 5-15          |

### TKUSP_V1 Additional Parameters

| Parameter                    | Type   | Description                            | Typical Value |
| ---------------------------- | ------ | -------------------------------------- | ------------- |
| `stagnationThreshold` (X)    | int    | Iterations for stagnation detection    | 5-15          |
| `stabilizationThreshold` (Y) | int    | Iterations for stabilization detection | 3-10          |
| `pmVariationEpsilon` (ε)     | double | PM variation threshold                 | 0.001-0.01    |

### Parameter Guidelines

**Sample Size (N)**:

- Smaller: Faster iterations, less accurate
- Larger: Slower iterations, more accurate
- Rule of thumb: N ≥ 10 × k

**Elite Ratio (ρ)**:

- Smaller (0.1-0.15): Aggressive learning, faster convergence, risk of local optima
- Larger (0.25-0.3): Conservative learning, better exploration
- Recommended: 0.2

**Stopping Thresholds (TKUSP_V1)**:

- Stagnation: 5-10% of maxIterations
- Stabilization: 2-5% of maxIterations

---

## Project Structure

```
Topk-HUSPM/
├── src/
│   ├── algorithms/
│   │   ├── Algorithm.java           # Interface
│   │   ├── TKUSP.java               # Base algorithm
│   │   └── TKUSP_V1.java            # Enhanced algorithm
│   ├── config/
│   │   └── AlgorithmConfig.java     # Configuration holder
│   ├── model/
│   │   ├── Dataset.java
│   │   ├── Sequence.java
│   │   ├── Itemset.java
│   │   └── Item.java
│   ├── utils/
│   │   ├── OptimizedDataStructures.java
│   │   ├── UtilityCalculator.java
│   │   └── DatasetStatistics.java
│   └── Main.java                    # Entry point
├── data/                            # Input datasets
├── lib/                             # External libraries
├── output/                          # TKUSP results
├── output_v1/                       # TKUSP_V1 results
├── output_exacts/                   # Exact algorithm results
└── README.md                        # This file
```

---

## Algorithm Comparison

| Feature                      | TKUSP  | TKUSP_V1 |
| ---------------------------- | ------ | -------- |
| Basic Cross-Entropy          | ✓      | ✓        |
| Probability Matrix Learning  | ✓      | ✓        |
| Dynamic Pruning              | ✓      | ✓        |
| Utility Caching              | ✓      | ✓        |
| Early Stopping               | ✗      | ✓        |
| Adaptive Length Distribution | ✗      | ✓        |
| Smoothing Mutation           | ✗      | ✓        |
| Typical Iterations           | 50-100 | 20-50    |
| Convergence Quality          | Good   | Better   |

---

## Performance Optimizations

### Implemented Optimizations

1. **Utility Caching**: Avoid recalculating utilities for duplicate patterns
2. **Incremental Pruning**: Only update structures when threshold increases
3. **PM Preservation**: Preserve learned probabilities when pruning items
4. **Lazy Evaluation**: Only compute utilities for generated samples
5. **Early Termination**: Stop when converged (TKUSP_V1)
6. **Smart Sampling**: Use learned distributions (TKUSP_V1)

### Memory Management

- Explicit garbage collection before/after runs
- Release non-promising item data structures
- Clear caches when items are pruned
- Efficient sequence deduplication using signatures

---

## References

Based on Cross-Entropy Method for optimization and High Utility Sequential Pattern Mining.

**Key Concepts**:

- Cross-Entropy Optimization
- High Utility Pattern Mining
- Sequential Pattern Mining
- Adaptive Sampling Strategies

---

## License

This project is part of academic research on high utility sequential pattern mining.
