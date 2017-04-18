package org.ggp.base.player.gamer;

import java.util.List;
import java.util.Random;

import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

/**
 * Plays a random move in the list
 */
public class IISampleRandomGamer extends IISampleGamer {

	private final Random RAND = new Random();

	@Override
	protected void runMetaGame() {
		// do nothing
	}

	@Override
	protected Move getBestMove(int turnNumber) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		while (!IISampleGamer.hasTimedOut()) {}
		List<Move> moves;
		if (turnNumber == 1) {
			moves = getMoves(getIIStateMachine().getInitialState());
		} else {
			moves = getMoves();
		}
		return moves.get(RAND.nextInt(moves.size()));
	}
}
