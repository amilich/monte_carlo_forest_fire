package org.ggp.base.player.gamer;

import java.util.List;

import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.gdl.grammar.GdlTerm;
import org.ggp.base.util.match.IIMatch;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.SeesState;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.iistatemachine.NotABotIIPropNetStateMachine;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

public abstract class IISampleGamer extends IIGamer {

	// time at which computation must stop
	private static long timeout;
	// time left that computation of a move or metagame must stop
	private static final long TIME_CUSHION = 1000;
	//protected NotABotIIPropNetStateMachine iistateMachine;
	private int turnNumber = 1;

	public IISampleGamer() {
		iistateMachine = new NotABotIIPropNetStateMachine();
		normalStateMachine = new ProverStateMachine();
	}

	protected abstract void runMetaGame() throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException;

	/**
	 * @return the best move for the current state
	 */
	protected abstract Move getBestMove(int turnNumber)
		throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException;


	@Override
	public void iimetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException, InterruptedException {

		iimatch = new IIMatch(getMatch());
		normalStateMachine.initialize(iimatch.getGame().getRules());
		iistateMachine.initialize(normalStateMachine, iimatch.getGame().getRules());

		currentState = iistateMachine.getInitialState();
		currentSees = null;

		role = iistateMachine.getRoleFromConstant(getRoleName());
		iimatch.appendState(currentState.getContents());


		// update time at which timeout will occur
		this.timeout = timeout - TIME_CUSHION;

		// inheriting subclass will run metagame
		runMetaGame();
	}

	@Override
	public Move iiselectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {

		// update time at which timeout will occur
		this.timeout = timeout - TIME_CUSHION;
		// get best possible move
		Move move = getBestMove(turnNumber);
		turnNumber++;
		System.out.println(getRole().toString() + ": Move submitted: "+ move + "\n");
		return move;
	}

	/**
	 * @return true if the timer has sufficiently run out
	 */
	public static boolean hasTimedOut(){
		return IISampleGamer.timeout < System.currentTimeMillis();
	}

	public static long timeLeft(){
		return Math.max(0, IISampleGamer.timeout-System.currentTimeMillis());
	}

	protected Move getMostRecentMove() {
		int roleIndex = getRoleIndex();
		List<GdlTerm> lastMoves = getIIMatch().getMostRecentMoves();
		if (lastMoves != null) {
			return new Move(lastMoves.get(roleIndex));
		}
		return null;
	}

	/**
	 * Quick helper methods that return a list of moves using specific MachineStates
	 * By default, uses current state and this role
	 */
	protected List<Move> getMoves() throws MoveDefinitionException{
		return getMoves(getCurrentSees());
	}

	protected List<Move> getMoves(MachineState state) throws MoveDefinitionException{
		return getMoves(state, getRole());
	}

	protected List<Move> getMoves(Role role) throws MoveDefinitionException{
		return getMoves(getCurrentSees(), role);
	}

	protected List<Move> getMoves(MachineState state, Role role) throws MoveDefinitionException{
		return getIIStateMachine().getLegalMoves(state, role);
	}

	/**
	 * Helpers for returning list of moves using SeesState
	 * @throws MoveDefinitionException
	 */
	protected List<Move> getMoves(SeesState seesState) throws MoveDefinitionException {
		Move move = getMostRecentMove();
		if (move != null) {
			return getIIStateMachine().getLegalMoves(seesState, getMostRecentMove());
		}
		return getIIStateMachine().getLegalMoves(seesState);
	}

	protected List<Move> getMoves(SeesState seesState, Role role) {
		Move move = getMostRecentMove();
		if (move != null) {
			return getIIStateMachine().refineLegalMoves(seesState, getMostRecentMove(), role);
		}
		return getIIStateMachine().refineLegalMoves(seesState, role);
	}


	@Override
	public void iistop() {
		turnNumber = 1;
	}

	@Override
	public void iiabort() {
		// TODO Auto-generated method stub
	}

	@Override
	public void preview(Game g, long timeout) throws GamePreviewException {
		// TODO Auto-generated method stub
	}

	@Override
	public String getName() {
		// gets the name of the inheriting subclass
		return getClass().getSimpleName();
	}
}
