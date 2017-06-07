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

import MCFFplayers.ThreadedGraphNode;

public class MCTSGraphPlayer extends StateMachineGamer {
	ThreadedGraphNode root = null;
	List<Gdl> prevRules = null;

	final static long PROPNET_MIN_BUFFER_TIME_AFTER_INIT_MS = 7000;

	@Override
	public StateMachine getAndInitializeStateMachine(long timeout, Role role) {
		ExecutorService executor = Executors.newFixedThreadPool(1); // Threadpool cause we might want to async init more stuff later
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

		StateMachine csm = new CachedStateMachine(new ProverStateMachine());
		csm.initialize(getMatch().getGame().getRules(), role);
		long nowMs = System.currentTimeMillis();
		long timeRem = timeout - nowMs;
		long timeToBuild = timeRem - PROPNET_MIN_BUFFER_TIME_AFTER_INIT_MS;

		IntPropNet initializedNet;
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

	@Override
	public StateMachine getInitialStateMachine() {
		System.out.println("[GRAPH] getInitialStateMachine SHOULD NEVER BE CALLED");
		return new IntPropNet();
	}

	// http://stackoverflow.com/questions/28428365/how-to-find-correlation-between-two-integer-arrays-in-java
	public double Correlation(List<Double> xs, List<Double> ys) {
		double sx = 0.0;
		double sy = 0.0;
		double sxx = 0.0;
		double syy = 0.0;
		double sxy = 0.0;
		int n = xs.size();
		//	double maxX = Collections.max(xs);
		//	double maxY = Collections.max(ys);
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

	private double CORR_THRESHOLD = 0.2;
	static final int TIME_REM = 15000;
	static final int TIME_CORR = 15000;
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
		if (Math.abs(corr) > CORR_THRESHOLD) { // we want the abs value of corr because in some games, it might be beneficial to restrict our own moves
			System.out.println("ENABLING MOBILITY HEURISTIC [corr=" + corr + "]");
			ThreadedGraphNode.heuristicEnable = true;
			ThreadedGraphNode.mobilityCorr = corr;
			// we want to store corr because a higher correlation should entail a higher c value in the select fn
		}
	}

	// List of machines used for depth charges
	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// getStateMachine().initialize(getMatch().getGame().getRules(), getRole());
		ThreadedGraphNode.stateMap.clear();
		resetGraphNode();
		moveNum = 0;
		try {
			if (getStateMachine().getRoles().size() > 1) {
				// mobilityHeuristic(timeout);
			}
		} catch (Exception e) {
			System.out.println("[GRAPH] Error while computing mobility heuristic:");
			e.printStackTrace();
		}
		expandTree(timeout);
		System.out.println("[GRAPH] METAGAME charges = " + ThreadedGraphNode.numCharges);
		moveNum = 0;
	}

	// Must be called in order to reset static information regarding the game.
	private void resetGraphNode() throws MoveDefinitionException, GoalDefinitionException {
		ThreadedGraphNode.setRole(getRole());
		ThreadedGraphNode.setStateMachine(getStateMachine());
		ThreadedGraphNode.roleIndex = -1; // Otherwise it's OK to keep! TODO
		ThreadedGraphNode.stateMap.clear();
		ThreadedGraphNode.numCharges = 0;
		initRoot();
	}

	private double CSP_UPDATE_COEFF = 1.3;
	int num_update = 0;
	private int MAX_ITERATIONS = 3000000; // Unnecessary to explore
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
				selected.backpropagate(path, score); // sqrt 2 for c
			} catch(Exception e) {
				e.printStackTrace();
			}
			if (numLoops > MAX_ITERATIONS) {
				break; // TODO
			}
		}
		if (numLoops > MAX_ITERATIONS || ThreadedGraphNode.numCharges < 10) {
			if (moveNum > 1 && root.getBestUtility() < 97.0) { // TODO threshold
				if (getStateMachine().getRoles().size() == 1 && num_update < 2) {
					System.out.println("Updating Csp");
					ThreadedGraphNode.Csp *= CSP_UPDATE_COEFF;
					num_update ++;
				}
			}
		}
		System.out.println(numLoops + ", " + moveNum);
		System.out.println("[GRAPH] Charges/sec = " + (ThreadedGraphNode.numCharges / timeDiff));
	}

	private void initRoot() throws MoveDefinitionException, GoalDefinitionException {
		root = new ThreadedGraphNode(getCurrentState());
	}

	int moveNum = 0;
	boolean failed = false;
	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		try {
			if (root == null) {
				initRoot();
			} else if (moveNum != 0){
				ThreadedGraphNode matchingChild = root.findMatchingState(getCurrentState());
				root = matchingChild; // may be null
				if (root != null) {
					System.out.println("*** [GRAPH] ADVANCED TREE ***");
				} else {
					initRoot();
					System.out.println("*** [GRAPH] FAILED TO ADVANCE TREE ***");
				}
			} else {
				System.out.println("[GRAPH] First move: advanced tree.");
			}

			//			if (moveNum != 0) {
			//				root = new ThreadedGraphNode(getCurrentState());
			//			}

			expandTree(timeout);
			System.out.println("[GRAPH] Num charges = " + ThreadedGraphNode.numCharges);
			moveNum ++;
			Move m = root.getBestMove();
			return m;
		} catch (Exception e) {
			System.out.println("[GRAPH] Exception in stateMachineSelectMove. Falling back to any legal move.");
			failed = true;
			this.stateMachine = new CachedStateMachine(new ProverStateMachine());
			this.stateMachine.initialize(getMatch().getGame().getRules(), getRole());
			resetGraphNode();
			return this.stateMachine.findLegalx(getRole(), getCurrentState());
		}
	}

	@Override
	public void stateMachineStop() {
		// TODO Auto-generated method stub

	}

	@Override
	public void stateMachineAbort() {
		// TODO Auto-generated method stub

	}

	@Override
	public void preview(Game g, long timeout) throws GamePreviewException {
		// TODO Auto-generated method stub

	}

	@Override
	public String getName() {
		return "GraphMCTSPlayer";
	}
}