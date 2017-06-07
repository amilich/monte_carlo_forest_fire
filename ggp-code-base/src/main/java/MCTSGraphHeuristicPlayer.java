import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
import org.ggp.base.util.statemachine.implementation.propnet.BitSetPropNet;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

import MCFFplayers.ThreadedGraphNode;

public class MCTSGraphHeuristicPlayer extends StateMachineGamer {
	ThreadedGraphNode root = null;
	List<Gdl> prevRules = null;

	@Override
	public StateMachine getInitialStateMachine() {
		if (failed) {
			return new CachedStateMachine(new ProverStateMachine());
		}
		return new BitSetPropNet();
		//	return new StateLessPropNet();
	}

	public MachineState customdc(MachineState state, StateMachine machine) throws TransitionDefinitionException, MoveDefinitionException {
		Random r = new Random();
		while (true) {
			List<List<Move>> jmoves = machine.getLegalJointMoves(state);
			MachineState nextState = machine.getNextState(state, jmoves.get(r.nextInt(jmoves.size())));
			if (machine.isTerminal(nextState)) {
				return state;
			} else {
				state = nextState;
			}
		}
	}

	public int myMobility(Role role, MachineState state, StateMachine machine)
			throws MoveDefinitionException, TransitionDefinitionException {
		double numLegalActions = machine.getLegalMoves(state, role).size();
		double numActions = machine.findActions(role).size(); // TODO TODO
		return (int) (100 * numLegalActions / numActions);
	}

	// http://stackoverflow.com/questions/28428365/how-to-find-correlation-between-two-integer-arrays-in-java
	public double Correlation(List<Integer> xs, List<Integer> ys) {
		double sx = 0.0;
		double sy = 0.0;
		double sxx = 0.0;
		double syy = 0.0;
		double sxy = 0.0;
		int n = xs.size();
		for (int i = 0; i < n; i ++) {
			double x = xs.get(i).doubleValue();
			double y = ys.get(i).doubleValue();
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

	static final double corr_threshold = 0.5;
	public void mobilityHeuristic(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		List<Integer> ourScore = new ArrayList<Integer>();
		List<Integer> heuristic = new ArrayList<Integer>();
		long newTimeout = System.currentTimeMillis() + 15000;
		System.out.println("Starting correlation");
		while (!MyHeuristics.checkTime(timeout - 20000) && !MyHeuristics.checkTime(newTimeout)) {
			MachineState next = customdc(getCurrentState(), getStateMachine());
			ourScore.add(getStateMachine().getGoal(next, getRole()));
			heuristic.add(myMobility(getRole(), next, getStateMachine()));
		}
		double corr = Correlation(ourScore, heuristic);
		System.out.println("Corr = " + corr);
		System.out.println("Num charges = " + heuristic.size());
	}

	// List of machines used for depth charges
	List<StateMachine> machines = new ArrayList<StateMachine>();
	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		resetGraphNode();
		moveNum = 0;
		mobilityHeuristic(timeout);
		expandTree(timeout); // TODO
		System.out.println("[GRAPH] METAGAME charges = " + ThreadedGraphNode.numCharges);
	}

	// Must be called in order to reset static information regarding the game.
	private void resetGraphNode() throws MoveDefinitionException, GoalDefinitionException {
		ThreadedGraphNode.setRole(getRole());
		ThreadedGraphNode.setStateMachine(getStateMachine());
		ThreadedGraphNode.roleIndex = -1; // Otherwise it's OK to keep! TODO
		ThreadedGraphNode.stateMap.clear();
		ThreadedGraphNode.numCharges = 0;
		createMachines(); // Clears and adds new machines
		initRoot();
	}

	private final int MAX_ITERATIONS = 5000000; // Unnecessary to explore
	public void expandTree(long timeout) {
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
			if (numLoops > MAX_ITERATIONS) break; // TODO
		}
		System.out.println(numLoops + ", " + moveNum);
		System.out.println("[GRAPH] Charges/sec = " + (ThreadedGraphNode.numCharges / timeDiff));
	}


	private void createMachines() {
		machines.clear();
		machines.add(getStateMachine());
		for (int ii = 1; ii < ThreadedGraphNode.NUM_THREADS; ii ++) {
			StateMachine m = getInitialStateMachine();
			m.initialize(getMatch().getGame().getRules(), getRole());
			machines.add(m);
		}
		System.out.println("[GRAPH] Created machines.");
	}

	private void initRoot() throws MoveDefinitionException, GoalDefinitionException {
		// ThreadedGraphNode.setStateMachines(machines); // TODO
		root = new ThreadedGraphNode(getCurrentState());
	}

	int moveNum = 0;
	boolean failed = false;
	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		try {
			/* if (root == null) {
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
			}*/
			if (moveNum != 0) {
				root = new ThreadedGraphNode(getCurrentState());
			}

			expandTree(timeout);
			System.out.println("[GRAPH] Num charges = " + ThreadedGraphNode.numCharges);
			moveNum ++;
			Move m = root.getBestMove();
			return m;
		} catch (Exception e) {
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
		return "Graph_Heuristic";
	}
}
