import java.util.ArrayList;
import java.util.List;

import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

public class MCTSGraphPlayer extends StateMachineGamer {
	ThreadedGraphNode root = null;

	@Override
	public StateMachine getInitialStateMachine() {
		return new CachedStateMachine(new ProverStateMachine());
	}

	List<StateMachine> machines = new ArrayList<StateMachine>();
	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		root = null;
		ThreadedGraphNode.numCharges = 0;
		moveNum = 0;
		initRoot();
		expandTree(timeout); // TODO
		System.out.println("[GRAPH] METAGAME charges = " + ThreadedGraphNode.numCharges);
	}

	public void expandTree(long timeout) {
		int numLoops = 0;
		while (!MyHeuristics.checkTime(timeout)) {
			ArrayList<ThreadedGraphNode> path = new ArrayList<ThreadedGraphNode>();
			numLoops ++;
			try {
				ThreadedGraphNode selected = root.selectAndExpand(path);
				double score = selected.simulate();
				selected.backpropagate(path, score); // sqrt 2 for c
			} catch(Exception e) {
				e.printStackTrace();
			}
			if (numLoops > 1000000) break; // TODO
		}
		System.out.println(numLoops);
	}


	private void createMachines() {
		machines.clear();
		for (int ii = 0; ii < ThreadedGraphNode.NUM_THREADS; ii ++) {
			StateMachine m = getInitialStateMachine();
			m.initialize(getMatch().getGame().getRules());
			machines.add(m);
		}
	}

	private void initRoot() throws MoveDefinitionException {
		ThreadedGraphNode.setRole(getRole());
		ThreadedGraphNode.setRole(getRole());
		ThreadedGraphNode.setStateMachine(getStateMachine());
		createMachines();
		ThreadedGraphNode.setStateMachines(machines);
		root = new ThreadedGraphNode(getCurrentState());
	}

	int moveNum = 0;
	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
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

		expandTree(timeout);
		System.out.println("[GRAPH] Num charges = " + ThreadedGraphNode.numCharges);
		moveNum ++;
		Move m = root.getBestMove();
		return m;
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
