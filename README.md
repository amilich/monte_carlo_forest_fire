# monte_carlo_forest_fire
CS227B 
Spring 2017

Codebase for our game player; note that MCTS = Monte Carlo Tree Search. See our main player files below:
* [MCTS Node Class](https://github.com/amilich/monte_carlo_forest_fire/blob/cca5852dab06916282b39826040db51c1f422786/ggp-code-base/src/main/java/ThreadedGraphNode.java)
* [MCTS Player Class](https://github.com/amilich/monte_carlo_forest_fire/blob/84646a18cd3f2bb64a35be35e95efacf9bba4f4d/ggp-code-base/src/main/java/MCTSGraphPlayer.java)
* [Integer Based Propnet](https://github.com/amilich/monte_carlo_forest_fire/blob/84646a18cd3f2bb64a35be35e95efacf9bba4f4d/ggp-code-base/src/main/java/org/ggp/base/util/statemachine/implementation/propnet/IntPropNet.java)

Our [MCTS Player Class](https://github.com/amilich/monte_carlo_forest_fire/blob/84646a18cd3f2bb64a35be35e95efacf9bba4f4d/ggp-code-base/src/main/java/MCTSGraphPlayer.java) is tasked with searching for a move given a game state. To do so, it performed Monte Carlo Tree Search (MCTS) with our [MCTS Node Class](https://github.com/amilich/monte_carlo_forest_fire/blob/cca5852dab06916282b39826040db51c1f422786/ggp-code-base/src/main/java/ThreadedGraphNode.java). This requires thousands (or millions) of game simulations, which are performed by our Integer Based Propnet. The propnet - propositional net - optimizes data structures for cache usage and determining game states.
