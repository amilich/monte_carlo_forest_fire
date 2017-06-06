import java.util.ArrayList;
import java.util.List;

import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
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

	public class propNetBuilder implements Runnable {
		@Override
		public void run() {
		}
	}

@Override
public StateMachine getInitialStateMachine() {
	//		return new BitSetPropNet();
	//		return new ExpPropNet();
	return new IntPropNet();
	//		return new ExpFactorPropNet();
	// 		return new BitSetNet();
	//		return new BasicFactorPropNet();
	//		return new StateLessPropNet();
//	return new AsyncPropNet();
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
static final int TIME_REM = 15000;
static final int TIME_CORR = 15000;
public void mobilityHeuristic(long timeout)
		throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
	List<Integer> ourScore = new ArrayList<Integer>();
	List<Integer> heuristic = new ArrayList<Integer>();
	long newTimeout = System.currentTimeMillis() + TIME_CORR;
	System.out.println("Starting correlation");
	while (!MyHeuristics.checkTime(timeout - TIME_REM) && !MyHeuristics.checkTime(newTimeout)) {
		// TODO(andrew): This code needs to be updated to be compatible with AsyncPropNet
		// You either need to write an implementation of preInternalDC for cachedstatemachine
		// and a wrapper preInternalDC method in AsyncPropNet, give up do async initialization,
		// or not call getStateMachine().preInternalDC here.

		// You also need to update AsyncPropNet for cheapMobility support. I would have but this
		// code below is unclear to me -- why does it assume tid=0??
		MachineState finalState = new MachineState();
		MachineState next = getStateMachine().preInternalDC(getCurrentState(), finalState, 0);
		ourScore.add(getStateMachine().getGoal(finalState, getRole()));
		heuristic.add(getStateMachine().cheapMobility(next, getRole(), 0));
	}
	double corr = Correlation(ourScore, heuristic);
	System.out.println("Corr = " + corr);
	System.out.println("Num charges = " + heuristic.size());
	if (corr > CORR_THRESHOLD) {
		System.out.println("ENABLING MOBILITY HEURISTIC [corr=" + corr + "]");
		ThreadedGraphNode.heuristicEnable = true;
	}
}
private double CORR_THRESHOLD = 0.25;

// List of machines used for depth charges
@Override
public void stateMachineMetaGame(long timeout)
		throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
	// getStateMachine().initialize(getMatch().getGame().getRules(), getRole());
	resetGraphNode();
	moveNum = 0;
	try {
		mobilityHeuristic(timeout);
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

private double CSP_UPDATE_COEFF = 3.0;
private final int MAX_ITERATIONS = 3000000; // Unnecessary to explore
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
		// if (numLoops > temp_max) break; // TODO
		if (numLoops > MAX_ITERATIONS) {
			if (getStateMachine().getRoles().size() == 1) {
				System.out.println("Updating Csp");
				ThreadedGraphNode.Csp *= CSP_UPDATE_COEFF;
			}
			break; // TODO
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