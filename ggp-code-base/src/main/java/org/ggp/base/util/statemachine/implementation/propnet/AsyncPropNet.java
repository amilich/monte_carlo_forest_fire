package org.ggp.base.util.statemachine.implementation.propnet;

import java.util.List;
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
 * Encapsulates two state machines: the Prover and our IntPropNet.
 *
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

	// TODO(andrew): Are there other StateMachine methods that DepthCharger/ThreadedGraphNode/MCTSGraphPlayer
	// rely on, besides the abstract methods in StateMachine? If so you need to add them here and curry the
	// function call in the same pattern that the below methods do. Things that I found are missing so far:
	// -

	// You need to make sure that CachedStateMachine implements these methods also if you want AsyncPropNet to work.

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
