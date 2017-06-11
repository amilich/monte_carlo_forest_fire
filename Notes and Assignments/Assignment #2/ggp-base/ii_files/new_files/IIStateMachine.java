package org.ggp.base.util.statemachine.iistatemachine;

import java.util.List;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.gdl.grammar.GdlTerm;
import org.ggp.base.util.propnet.architecture.IIPropNet;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.See;
import org.ggp.base.util.statemachine.SeesState;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public abstract class IIStateMachine {

	private StateMachine normalStateMachine;

	// ============================================
	//          Stubs for implementations
	// ============================================
	//  The following methods are required for a valid
	// state machine implementation.
	/**
	 * Initializes the StateMachine to describe the given game rules.
	 * <p>
	 * This method should only be called once, and it should be called before any
	 * other methods on the StateMachine.
	 */
	public abstract void initialize(StateMachine stateMachine, List<Gdl> description) throws InterruptedException;
	/**
	 * Returns the goal value for the given role in the given state. Goal values
	 * are always between 0 and 100.
	 *
	 * @throws GoalDefinitionException if there is no goal value or more than one
	 * goal value for the given role in the given state. If this occurs when this
	 * is called on a terminal state, this indicates an error in either the game
	 * description or the StateMachine implementation.
	 */
	public abstract int getGoal(MachineState state, Role role) throws GoalDefinitionException;

	public abstract List<Integer> getGoals(SeesState seesState) throws GoalDefinitionException;

	/**
	 * Returns true if and only if the given state is a terminal state (i.e. the
	 * game is over).
	 */
	public abstract boolean isTerminal(MachineState state);

	/**
	 * Returns a list of the roles in the game, in the same order as they
	 * were defined in the game description.
	 * <p>
	 * The result will be the same as calling {@link Role#computeRoles(List)}
	 * on the game rules used to initialize this state machine.
	 */
	public abstract List<Role> getRoles();
	/**
	 * Returns the initial state of the game.
	 */
	public abstract MachineState getInitialState();

	/**
	 * Returns the initial state of the game as a SeesState.
	 */
	// likely not necessary
	// public abstract SeesState getInitialSeesState();

	/**
	 * Returns a list containing every move that is legal for the given role in the
	 * given state.
	 *
	 * @throws MoveDefinitionException if the role has no legal moves. This indicates
	 * an error in either the game description or the StateMachine implementation.
	 */
	// TODO: There are philosophical reasons for this to return Set<Move> rather than List<Move>.
	public abstract List<Move> getLegalMoves(MachineState state, Role role) throws MoveDefinitionException;

	/**
	 * Returns a list containing every move that can be determined to be legal for the
	 * role in the given SeesState.
	 */
	public abstract List<Move> getLegalMoves(SeesState seesState, Move move) throws MoveDefinitionException;

	public abstract List<Move> getLegalMoves(SeesState seesState) throws MoveDefinitionException;

	/**
	 * In general, one cannot determine anything about the legal moves of other players. However, sometimes
	 * we may be able to determine whether some moves are not legal, in which case we can refine the list
	 * of legal moves for this player. As such, the Role parameter should be different than the role contained
	 * in the SeesState parameter.
	 */
	public abstract List<Move> refineLegalMoves(SeesState seesState, Move move, Role role);

	public abstract List<Move> refineLegalMoves(SeesState seesState, Role role);

	/**
	 * Returns the next state of the game given the current state and a joint move
	 * list containing one move per role.
	 *
	 * @param moves A list containing one move per role. The moves should be
	 * listed in the same order as roles are listed by {@link #getRoles()}.
	 * @throws TransitionDefinitionException indicates an error in either the
	 * game description or the StateMachine implementation.
	 */
	public abstract MachineState getNextState(MachineState state, List<Move> moves) throws TransitionDefinitionException;

	public abstract SeesState getSeesState(MachineState state, Role role);

	// This really shouldn't need to be done in IIStateMachine since it technically doesn't need to be done with a propnet
	public abstract IIPropNet getIIPropNet();

	// The following methods are included in the abstract StateMachine base so
	// implementations which use alternative Role/Move/State representations
	// can look up/compute what some Gdl corresponds to in their representation.
	// They are implemented for convenience, using the default ways of generating
	// these objects, but they can be overridden to support machine-specific objects.
	public MachineState getMachineStateFromSentenceList(Set<GdlSentence> sentenceList) {
		return new MachineState(sentenceList);
	}
	public Role getRoleFromConstant(GdlConstant constant) {
		return new Role(constant);
	}
	public Move getMoveFromTerm(GdlTerm term) {
		return new Move(term);
	}

	public See getSeeFromTerm(Role role, GdlTerm term) {
		return new See(role, term);
	}

	public StateMachine getNormalStateMachine(){
		return normalStateMachine;
	}
}
