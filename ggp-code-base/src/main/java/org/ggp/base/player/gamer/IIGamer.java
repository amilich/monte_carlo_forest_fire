package org.ggp.base.player.gamer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ggp.base.player.gamer.exception.AbortingException;
import org.ggp.base.player.gamer.exception.MetaGamingException;
import org.ggp.base.player.gamer.exception.MoveSelectionException;
import org.ggp.base.player.gamer.exception.StoppingException;
import org.ggp.base.util.gdl.grammar.GdlTerm;
import org.ggp.base.util.logging.GamerLogger;
import org.ggp.base.util.match.IIMatch;
import org.ggp.base.util.observer.Subject;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.See;
import org.ggp.base.util.statemachine.SeesState;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.iistatemachine.IISamplePropNetStateMachine;
import org.ggp.base.util.statemachine.iistatemachine.IIStateMachine;

public abstract class IIGamer extends Gamer implements Subject {

	protected IIMatch iimatch;
	protected Role role;
	protected SeesState currentSees;
	protected MachineState currentState;
	protected IISamplePropNetStateMachine iistateMachine;
	protected StateMachine normalStateMachine;

	/* Not necessary for now */
    // public abstract IIStateMachine getInitialIIStateMachine();

    public abstract Move iiselectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException;

    public abstract void iimetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException, InterruptedException;

	public abstract void iistop();

	public abstract void iiabort();

	@Override
	public void metaGame(long timeout) throws MetaGamingException {
		try
		{
			iimetaGame(timeout);
		}
		catch (Exception e)
		{
		    GamerLogger.logStackTrace("IIGamePlayer", e);
			throw new MetaGamingException(e);
		}
	}

	@Override
	public GdlTerm selectMove(long timeout) throws MoveSelectionException {
		try {
			Set<GdlTerm> lastSees = iimatch.getMostRecentSees();

			if (lastSees != null) {
				Set<See> sees = new HashSet<See>();
				for (GdlTerm term : lastSees) {
					sees.add(new See(role, term));
				}
				currentSees = new SeesState(role, sees);
			}

			GdlTerm move = iiselectMove(timeout).getContents();
			List<GdlTerm> moveSet = new ArrayList<GdlTerm>();
			for (int i = 0; i < iistateMachine.getRoles().size(); i++) {
				moveSet.add(null);
			}
			moveSet.set(getRoleIndex(), move);
			iimatch.appendMoves(moveSet);
			return move;
		}
		catch (Exception e)
		{
			GamerLogger.logStackTrace("IIGamePlayer", e);
			throw new MoveSelectionException(e);
		}
	}

	protected int getRoleIndex() {
		List<Role> roles = iistateMachine.getRoles();
		for (int i = 0; i < roles.size(); i++) {
			if (roles.get(i).equals(role)) {
				return i;
			}
		}
		System.out.println("IIGamer: role not found in getRoles()");
		return -1;
	}

	@Override
	public void stop() throws StoppingException {
		try {
			Set<GdlTerm> lastSees = iimatch.getMostRecentSees();

			if (lastSees != null) {
				iimatch.appendSees(lastSees);
				Set<See> sees = new HashSet<See>();
				for (GdlTerm term : lastSees) {
					sees.add(new See(role, term));
				}
				currentSees = new SeesState(role, sees);
				iimatch.markCompleted(iistateMachine.getGoals(currentSees));
			}

			iistop();
		}
		catch (Exception e)
		{
			GamerLogger.logStackTrace("IIGamePlayer", e);
			throw new StoppingException(e);
		}
	}

	@Override
	public void abort() throws AbortingException {
		try {
			iiabort();
		}
		catch (Exception e)
		{
			GamerLogger.logStackTrace("GamePlayer", e);
			throw new AbortingException(e);
		}
	}

	public IIMatch getIIMatch() {
		return iimatch;
	}

	public final void setIIMatch(IIMatch match) {
		iimatch = match;
	}

	// =====================================================================
    // Next, methods which can be used by subclasses to get information about
    // the current state of the game, and tweak the state machine on the fly.

	/**
	 * Returns the current state of the game.
	 */
	public final SeesState getCurrentSees()
	{
		return currentSees;
	}

	/**
	 * Returns the role that this gamer is playing as in the game.
	 */
	public final Role getRole()
	{
		return role;
	}

	/**
	 * Returns the state machine.  This is used for calculating the next state and other operations, such as computing
	 * the legal moves for all players, whether states are terminal, and the goal values of terminal states.
	 */
	public final IIStateMachine getIIStateMachine()
	{
		return iistateMachine;
	}

	public final StateMachine getNormalStateMachine() {
		return normalStateMachine;
	}

	// intended for use only when there is only one possible state, e.g., at beginning of game
	public final MachineState getCurrentMachineState() {
		return currentState;
	}
}
