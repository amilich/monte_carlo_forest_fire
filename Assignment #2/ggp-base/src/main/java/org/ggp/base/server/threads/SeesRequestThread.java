package org.ggp.base.server.threads;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.ggp.base.server.IIGameServer;
import org.ggp.base.server.event.ServerIllegalMoveEvent;
import org.ggp.base.server.request.RequestBuilder;
import org.ggp.base.util.gdl.factory.GdlFactory;
import org.ggp.base.util.gdl.factory.exceptions.GdlFormatException;
import org.ggp.base.util.match.IIMatch;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.See;
import org.ggp.base.util.symbol.factory.exceptions.SymbolFormatException;

public class SeesRequestThread extends RequestThread {

	private final IIGameServer iigameServer;
	private final List<Move> legalMoves;
	private final IIMatch iimatch;
	private final Role role;

	private Move move;

	public SeesRequestThread(IIGameServer iigameServer, IIMatch iimatch, Set<See> sees, List<Move> legalMoves, Role role, String host, int port, String playerName, boolean unlimitedTime)
	{
		super(iigameServer, role, host, port, playerName, unlimitedTime ? -1 : (iimatch.getPlayClock() * 1000 + 1000), RequestBuilder.getSeesRequest(iimatch.getMatchId(), sees, iimatch.getGdlScrambler()));
		this.iigameServer = iigameServer;
		this.legalMoves = legalMoves;
		this.iimatch = iimatch;
		this.role = role;

		move = legalMoves.get(new Random().nextInt(legalMoves.size()));
	}

	public Move getMove()
	{
		return move;
	}

	@Override
	protected void handleResponse(String response) {
		try {
			Move candidateMove = iigameServer.getStateMachine().getMoveFromTerm(GdlFactory.createTerm(iimatch.getGdlScrambler().unscramble(response).toString()));
			if (new HashSet<Move>(legalMoves).contains(candidateMove)) {
				move = candidateMove;
			} else {
				iigameServer.notifyObservers(new ServerIllegalMoveEvent(role, candidateMove));
			}
		} catch (GdlFormatException e) {
			iigameServer.notifyObservers(new ServerIllegalMoveEvent(role, null));
		} catch (SymbolFormatException e) {
			iigameServer.notifyObservers(new ServerIllegalMoveEvent(role, null));
		}
	}
}
