import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.propnet.IntPropNet;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

/**
 * MCTSGraphPlayer
 *
 * Graph-based MCTS player. Builds propnet, searches for heuristics,
 * and then plays game.
 *
 * @author monte_carlo_forest_fire
 */
public class MCTSGraphPlayer extends StateMachineGamer {
	final static long PROPNET_FAIL_TIME = 7000; // Minimum buffer time to leave before giving up on propnet in metagame
	private ThreadedGraphNode root = null; // Root of the MCTS tree
	private StateMachine csm = null; // Fallback prover state machine
	private int moveNum = 0; // The current move we are on

	/**
	 * getAndInitializeStateMachine
	 *
	 * Combines getInitialStateMachine and state machine initialize.
	 * Creates a callable to build propnet in separate thread, then creates
	 * prover state machine.
	 *
	 * If propnet times out, prover state machine is returned; otherwise
	 * propent is used for game representation.
	 */
	@Override
	public StateMachine getAndInitializeStateMachine(long timeout, Role role) {
		ExecutorService executor = Executors.newFixedThreadPool(1);
		final List<Gdl> finalDesc = getMatch().getGame().getRules();
		final Role finalRole = role;

		Future<IntPropNet> fut = executor.submit(new Callable<IntPropNet>() {
			@Override
			public IntPropNet call() {
				try {
					IntPropNet propnet = new IntPropNet();
					propnet.initialize(finalDesc, finalRole);
					System.out.println("Callable returning");
					return propnet;
				} catch (Exception e) {
					System.out.println("[GRAPH] Error in thread building IntPropNet.");
					System.out.println(e);
				}
				return null;
			}
		});

		// While propnet is building, create our prover state machine
		csm = new CachedStateMachine(new ProverStateMachine());
		csm.initialize(getMatch().getGame().getRules(), role);
		long nowMs = System.currentTimeMillis();
		long timeRem = timeout - nowMs;
		long timeToBuild = timeRem - PROPNET_FAIL_TIME;

		IntPropNet initializedNet = null;
		boolean propnetFinished = false;
		try {
			System.out.println("[GRAPH] Awaiting propNet termination");
			executor.shutdown();
			propnetFinished = executor.awaitTermination(timeToBuild, TimeUnit.MILLISECONDS);
		} catch (Exception e) {
			System.out.println("[GRAPH] Executor threw an exception calling awaitTermination");
		}

		if (propnetFinished) {
			System.out.println("[GRAPH] PropNet finished in time :)");
			try {
				initializedNet = fut.get();
				if (initializedNet != null) {
					return initializedNet;
				}
			} catch (Exception ex) {
				System.out.println("[GRAPH] Exception calling fut.get().");
			}
		}
		System.out.println("[GRAPH] Using prover :|");
		fut.cancel(true);
		ThreadedGraphNode.usingProver = true;
		List<StateMachine> machines = new ArrayList<StateMachine>();
		for (int ii = 0; ii < IntPropNet.NUM_THREADS; ii ++) {
			StateMachine m = new CachedStateMachine(new ProverStateMachine());
			m.initialize(this.getMatch().getGame().getRules(), role);
			machines.add(m);
		}
		ThreadedGraphNode.machines = machines;
		System.out.println("[GRAPH] Built provers");
		return csm;
	}

	/**
	 * stateMachineMetaGame
	 *
	 * Search MCTS tree in metagame.
	 */
	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		ThreadedGraphNode.stateMap.clear();
		resetGraphNode();
		moveNum = 0;
		try {
			if (getStateMachine().getRoles().size() > 1) {
				mobilityHeuristic(timeout);
				goalHeuristic(timeout);
			}
		} catch (Exception e) {
			System.out.println("[GRAPH] Error while computing mobility heuristic:");
			e.printStackTrace();
		}
		expandTree(timeout);
		System.out.println("[GRAPH] METAGAME charges = " + ThreadedGraphNode.numCharges);
		moveNum = 0;
	}

	/**
	 * stateMachineSelectMove
	 *
	 * Expands MCTS tree and returns highest mobility move.
	 */
	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		try {
			updateCachedTree();
			expandTree(timeout);
			System.out.println("[GRAPH] Num charges = " + ThreadedGraphNode.numCharges);
			moveNum ++;
			Move m = root.getBestMove();
			return m;
		} catch (Exception e) {
			System.out.println("[GRAPH] Exception in stateMachineSelectMove. Falling back to any legal move.");
			this.switchStateMachine(csm);
			resetGraphNode();
			return this.stateMachine.findLegalx(getRole(), getCurrentState());
		}
	}

	/**
	 * updateCachedTree
	 *
	 * We cache the game tree between moves; this moves the root along.
	 */
	private void updateCachedTree() throws MoveDefinitionException, GoalDefinitionException {
		if (root == null) {
			initRoot();
		} else if (moveNum != 0){
			ThreadedGraphNode matchingChild = root.findMatchingState(getCurrentState());
			root = matchingChild;
			if (root != null) {
				System.out.println("*** [GRAPH] ADVANCED TREE ***");
			} else {
				initRoot();
				System.out.println("*** [GRAPH] FAILED TO ADVANCE TREE ***");
			}
		} else {
			System.out.println("[GRAPH] First move: advanced tree.");
		}
	}


	/**
	 * AlphaBetaThread
	 *
	 * Thread to run alpha beta search on game tree. See MyBoundedMobilityPlayer.java
	 * and MyAlphaBetaPlayer.java for search implementations.
	 */
	class AlphaBetaThread implements Runnable {
		Move action = null;
		MachineState s = null;
		StateMachine m = null;
		Role r = null;
		boolean finished = false;

		public AlphaBetaThread(StateMachine m, MachineState s, Role r) {
			this.s = s; this.r = r; this.m = m;
		}

		@Override
		public void run() {
			finished = false;
			if (getStateMachine().getRoles().size() == 1) {
				try {
					action = MyBoundedMobilityPlayer.singlePlayerBestMove(r, s, m);
				} catch (Exception e) {
					e.printStackTrace();
					return;
				}
			} else {
				try {
					action = MyAlphaBetaPlayer.staticBest(m, s, r);
				} catch (Exception e) {
					e.printStackTrace();
					return;
				}
			}
			finished = true;
		}
	}

	/**
	 * resetGraphNode
	 *
	 * Resets static information regarding the game.
	 */
	private void resetGraphNode() throws MoveDefinitionException, GoalDefinitionException {
		ThreadedGraphNode.setRole(getRole());
		ThreadedGraphNode.setStateMachine(getStateMachine());
		ThreadedGraphNode.roleIndex = -1; // Otherwise it's OK to keep
		ThreadedGraphNode.stateMap.clear();
		ThreadedGraphNode.numCharges = 0;
		initRoot();
	}

	@Override
	public StateMachine getInitialStateMachine() {
		System.out.println("[GRAPH] getInitialStateMachine SHOULD NEVER BE CALLED");
		return new IntPropNet();
	}

	private double CORR_THRESHOLD = 0.2; // Threshold for enabling correlation heuristic
	static final int TIME_REM = 15000; // Time remaining before we should give up on heuristic search
	static final int TIME_CORR = 8000; // Time to spend on heuristic search

	/**
	 * mobilityHeuristic
	 *
	 * Search for and enable mobility heuristic. Does not just compare mobility in terminal state;
	 * computes a mobility score for an entire depth charge. See preInternalDCMobility in IntPropNet.java.
	 */
	public void mobilityHeuristic(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		if (!(getStateMachine() instanceof IntPropNet)) {
			System.out.println("Skipping mobility heuristic because not using propNet.");
			return;
		}
		List<Double> ourScore = new ArrayList<Double>();
		List<Double> heuristic = new ArrayList<Double>();
		long newTimeout = System.currentTimeMillis() + TIME_CORR;
		System.out.println("Starting correlation");
		while (!MyHeuristics.checkTime(timeout - TIME_REM) && !MyHeuristics.checkTime(newTimeout)) {
			MachineState finalState = new MachineState();
			double[] weightedMobility = new double[1]; // for returning the value only
			getStateMachine().preInternalDCMobility(getCurrentState(), finalState, 0, weightedMobility, getRole());
			ourScore.add((double)getStateMachine().getGoal(finalState, getRole()));
			heuristic.add(weightedMobility[0]);
		}
		double corr = Correlation(ourScore, heuristic);
		System.out.println("Corr = " + corr);
		System.out.println("Num charges = " + heuristic.size());
		if (Math.abs(corr) > CORR_THRESHOLD) {
			System.out.println("ENABLING MOBILITY HEURISTIC [corr=" + corr + "]");
			ThreadedGraphNode.heuristicEnable = true;
			ThreadedGraphNode.mobilityCorr = corr;
			// Higher correlation entails higher c value in the select fn
		}
	}

	/**
	 * goalHeuristic
	 *
	 * Search for and enable goal heuristic. Computes an intermediate goal
	 * score for an entire depth charge. See preInternalDCGoal in IntPropNet.java.
	 */
	public void goalHeuristic(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		if (!(getStateMachine() instanceof IntPropNet)) {
			System.out.println("Skipping goal heuristic because not using propNet.");
			return;
		}
		List<Double> ourScore = new ArrayList<Double>();
		List<Double> heuristic = new ArrayList<Double>();
		long newTimeout = System.currentTimeMillis() + TIME_CORR;
		System.out.println("Starting goal correlation");
		while (!MyHeuristics.checkTime(timeout - TIME_REM) && !MyHeuristics.checkTime(newTimeout)) {
			MachineState finalState = new MachineState();
			double[] weightedGoal = new double[1]; // For returning the value of our mobility throughout the depth charge
			getStateMachine().preInternalDCGoal(getCurrentState(), finalState, 0, weightedGoal, getRole());
			ourScore.add((double) getStateMachine().getGoal(finalState, getRole()));
			heuristic.add(weightedGoal[0]);
		}
		double corr = Correlation(ourScore, heuristic);
		System.out.println("Corr = " + corr);
		System.out.println("Num charges = " + heuristic.size());
		if (Math.abs(corr) > CORR_THRESHOLD) {
			System.out.println("ENABLING GOAL HEURISTIC [corr=" + corr + "]");
			ThreadedGraphNode.heuristicEnable = true;
			ThreadedGraphNode.goalCorr = corr;
			// As in mobility, higher correlation entails higher c value in select fn
		}
	}

	private int num_update = 0; // Number of times we have updated the c value
	private double CSP_UPDATE_COEFF = 1.3; // Coefficient for updating our single player c value
	private int MAX_NUM_UPDATE = 2; // Number of times we will update c value
	private int MAX_ITERATIONS = 3000000; // Unnecessary to explore
	private int MIN_NUM_CHARGES = 10; // If we do fewer than this, we should update c or return a move

	/**
	 * expandTree
	 *
	 * Expand the MCTS tree prior to the timeout expiring.
	 */
	public void expandTree(long timeout) throws MoveDefinitionException {
		long startT = System.currentTimeMillis();
		double timeDiff = (timeout - startT) / 1000.0 - MyHeuristics.MAX_DELIB_THRESHOLD / 1000.0;
		ThreadedGraphNode.numCharges = 0;

		int numLoops = 0;
		ArrayList<ThreadedGraphNode> path = new ArrayList<ThreadedGraphNode>();
		while (!MyHeuristics.checkTime(timeout)) {
			path.clear();
			numLoops ++;
			try {
				ThreadedGraphNode selected = root.selectAndExpand(path);
				double score = selected.simulate();
				selected.backpropagate(path, score);
			} catch(Exception e) {
				e.printStackTrace();
			}
			if (numLoops > MAX_ITERATIONS) {
				break; // We could also update the c-value here (as opposed to below)
			}
		}
		if (numLoops > MAX_ITERATIONS || ThreadedGraphNode.numCharges < MIN_NUM_CHARGES) {
			if (moveNum > 1 && root.getBestUtility() < 97.0) { // TODO threshold
				if (getStateMachine().getRoles().size() == 1 && num_update < MAX_NUM_UPDATE) {
					System.out.println("[GRAPH] Updating Csp");
					ThreadedGraphNode.Csp *= CSP_UPDATE_COEFF;
					num_update ++;
				}
			}
		}
		System.out.println(numLoops + ", " + moveNum);
		System.out.println("[GRAPH] Charges/sec = " + (ThreadedGraphNode.numCharges / timeDiff));
	}

	/**
	 * Initialize the root of our MCTS tree with the current state.
	 */
	private void initRoot() throws MoveDefinitionException, GoalDefinitionException {
		root = new ThreadedGraphNode(getCurrentState());
	}

	@Override
	public void stateMachineStop() {	}

	@Override
	public void stateMachineAbort() {	}

	@Override
	public void preview(Game g, long timeout) throws GamePreviewException {	}

	@Override
	public String getName() {
		return "GraphMCTSPlayer";
	}

	/**
	 * Correlation
	 *
	 * Computes correlation of two arraylists of doubles.
	 * See http://stackoverflow.com/questions/28428365/how-to-find-correlation-between-two-integer-arrays-in-java
	 */
	public double Correlation(List<Double> xs, List<Double> ys) {
		double sx = 0.0;
		double sy = 0.0;
		double sxx = 0.0;
		double syy = 0.0;
		double sxy = 0.0;
		int n = xs.size();
		for (int i = 0; i < n; i ++) {
			double x = xs.get(i); ///maxX;
			double y = ys.get(i); ///maxY;
			sx += x;
			sy += y;
			sxx += x * x;
			syy += y * y;
			sxy += x * y;
		}
		double numerator = n * sxy - sx * sy;
		if (Math.abs(numerator) < 0.001) {
			return 0;
		}
		double denominator = Math.sqrt(n * sxx - sx * sx) * Math.sqrt(n * syy - sy * sy);
		return numerator / denominator;
	}
}