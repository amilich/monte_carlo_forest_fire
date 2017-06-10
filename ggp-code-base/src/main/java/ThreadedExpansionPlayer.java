import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;

import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.propnet.BitSetPropNet;

/**
 * ThreadedExpansionPlayer
 *
 * This class is currently unused.
 * It was initially used to allow multiple threads to perform the entire MCTS sequence -
 * selection, expansion, simulation, and backpropagation - at once; we decided this
 * was too complicated to be useful (it did not yield huge performance benefits).
 *
 * See TreeExpander.java for the thread that performed these expansions.
 */
public class ThreadedExpansionPlayer extends StateMachineGamer {
	MachineLessNode root = null;
	List<Gdl> prevRules = null;

	@Override
	public StateMachine getInitialStateMachine() {
		return new BitSetPropNet();
	}

	// List of machines used for depth charges
	List<StateMachine> machines = new ArrayList<StateMachine>();
	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		resetGraphNode();
		moveNum = 0;
		expandTree(timeout);
		System.out.println("[GRAPH] METAGAME charges = " + MachineLessNode.numCharges);
	}

	static ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();

	// Must be called in order to reset static information regarding the game.
	private void resetGraphNode() throws MoveDefinitionException {
		MachineLessNode.setRole(getRole());
		MachineLessNode.roleIndex = -1;
		MachineLessNode.stateMap.clear();
		MachineLessNode.numCharges = 0;
		createMachines(); // Clears and adds new machines
		initRoot();
	}

	int num_mach = 2;
	public void expandTree(long timeout) {
		Semaphore backprop = new Semaphore(1);
		TreeExpander t1 = null ;// new TreeExpander(root, backprop, timeout, getStateMachine());
		TreeExpander t2 = null ;//new TreeExpander(root, backprop, timeout, machines.get(1));
		// TreeExpander t3 = new TreeExpander(root, backprop, timeout, machines.get(2));
		// TreeExpander t3 = new TreeExpander(root, backprop, timeout);
		Collection<Future<?>> futures = new LinkedList<Future<?>>();
		futures.add(executor.submit(t1));
		futures.add(executor.submit(t2));
		// futures.add(executor.submit(t3));
		// futures.add(executor.submit(t3));

		for (Future<?> future : futures) {
			try {
				future.get();
			} catch (Exception e) { e.printStackTrace(); }
		}
	}


	private void createMachines() {
		machines.clear();
		machines.add(getStateMachine());
		for (int ii = 1; ii < num_mach; ii ++) {
			StateMachine m = getInitialStateMachine();
			m.initialize(getMatch().getGame().getRules(), getRole());
			machines.add(m);
		}
		System.out.println("[GRAPH] Created machines.");
	}

	private void initRoot() throws MoveDefinitionException {
		root = new MachineLessNode(getCurrentState(), getStateMachine());
	}

	int moveNum = 0;
	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		if (root == null) {
			initRoot();
		} else if (moveNum != 0){
			MachineLessNode matchingChild = root.findMatchingState(getCurrentState());
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
		System.out.println("[GRAPH] Num charges = " + MachineLessNode.numCharges);
		moveNum ++;
		Move m = root.getBestMove(getStateMachine());
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
		return "ThreadedExpansionPlayer";
	}
}
