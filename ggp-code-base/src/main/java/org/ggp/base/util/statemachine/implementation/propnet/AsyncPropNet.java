package org.ggp.base.util.statemachine.implementation.propnet;

import java.util.BitSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;


/**
 * AsyncPropNet
 *
 * Encapsulates two state machines: the Prover and our IntPropNet.
 * This class will use the Prover internally until the IntPropNet is initialized,
 * at which point it will switch over.
 */
public class AsyncPropNet extends StateMachine {

	private CachedStateMachine csm;
	private IntPropNet ip;

	private StateMachine getSM() {
		if (ip == null) {
			return csm;
		}
		return ip;
	}

	@Override
	public MachineState internalDC(MachineState start, int tid)
			throws MoveDefinitionException, TransitionDefinitionException {
		if (ip == null) {
			Random r = new Random();
			while (!csm.isTerminal(start)) {
				List<List<Move>> jmoves = csm.getLegalJointMoves(start);
				List<Move> selected =jmoves.get(r.nextInt(jmoves.size()));
				start = csm.getNextState(start, selected);
			}
			return start;
		} else {
			return ip.internalDC(start, tid);
		}
	}

	@Override
	public MachineState preInternalDC(MachineState start, MachineState finalS, int tid)
			throws MoveDefinitionException, TransitionDefinitionException {
		if (ip == null) {
			Random r = new Random();
			MachineState next = null;
			while (true) {
				List<List<Move>> jmoves = csm.getLegalJointMoves(start);
				List<Move> selected = jmoves.get(r.nextInt(jmoves.size()));
				next = csm.getNextState(start, selected);
				if (!csm.isTerminal(next)) {
					start = next;
				} else {
					break;
				}
			}
			if (next.props != null) {
				finalS.props = (BitSet) next.props.clone();
			}
			return start;
		} else {
			return ip.preInternalDC(start, finalS, tid);
		}
	}

	@Override
	public double cheapMobility(MachineState s, Role r, int tid) throws MoveDefinitionException {
		if (ip == null) {
			double numActions = csm.findActions(r).size();
			double numMoves = csm.getLegalMoves(s, r).size();
			return 100.0 * numMoves / numActions;
		} else {
			return ip.cheapMobility(s, r, tid);
		}
	}

	@Override
	public List<Move> findActions(Role role) throws MoveDefinitionException {
		return getSM().findActions(role);
	}

	@Override
	/**
	 * Don't take long here; this is called before the player even gets
	 * to start running stateMachineMetaGame.
	 */
	public void initialize(List<Gdl> description, Role role) {
		ExecutorService executor = Executors.newFixedThreadPool(1); // Threadpool cause we might want to async init more stuff later
		final List<Gdl> finalDesc = description;
		final Role finalRole = role;

		ip = null;
		executor.submit(new Runnable() {
			@Override
			public void run() {
				try {
					IntPropNet propnet = new IntPropNet();
					propnet.initialize(finalDesc, finalRole);
					ip = propnet;
					System.out.println("[AsyncPropnet] Done initializing IntPropNet.");
				} catch(Exception e) {
					System.out.println("[AsyncPropNet] Error while initializing IntPropNet.");
				}
			}
		});

		csm = new CachedStateMachine(new ProverStateMachine());
		csm.initialize(description, role);
	}

	@Override
	public int getGoal(MachineState state, Role role) throws GoalDefinitionException {
		return getSM().getGoal(state, role);
	}

	@Override
    public int getGoal(MachineState state, Role role, int tid) throws GoalDefinitionException {
    	if (ip == null) {
    		return csm.getGoal(state, role);
    	}
    	return ip.getGoal(state, role, tid);
    }

	@Override
	public boolean isTerminal(MachineState state) {
		return getSM().isTerminal(state);
	}

	@Override
	public List<Role> getRoles() {
		return getSM().getRoles();
	}

	@Override
	public MachineState getInitialState() {
		return getSM().getInitialState();
	}

	@Override
	public List<Move> getLegalMoves(MachineState state, Role role) throws MoveDefinitionException {
		return getSM().getLegalMoves(state, role);
	}

	@Override
	public MachineState getNextState(MachineState state, List<Move> moves) throws TransitionDefinitionException {
		return getSM().getNextState(state, moves);
	}

}
