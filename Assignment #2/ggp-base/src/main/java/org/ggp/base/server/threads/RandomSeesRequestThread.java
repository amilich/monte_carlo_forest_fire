package org.ggp.base.server.threads;

import java.util.List;
import java.util.Random;

import org.ggp.base.util.match.IIMatch;
import org.ggp.base.util.statemachine.Move;


public final class RandomSeesRequestThread extends SeesRequestThread
{
	private Move move;

	public RandomSeesRequestThread(IIMatch iimatch, List<Move> legalMoves)
	{
		super(null, iimatch, null, legalMoves, null, null, 0, null, true);
		move = legalMoves.get(new Random().nextInt(legalMoves.size()));
	}

	@Override
	public Move getMove()
	{
		return move;
	}

	@Override
	public void run()
	{
		;
	}
}