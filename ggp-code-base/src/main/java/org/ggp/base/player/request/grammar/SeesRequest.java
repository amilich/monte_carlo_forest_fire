package org.ggp.base.player.request.grammar;

import java.util.Set;

import org.ggp.base.player.event.PlayerTimeEvent;
import org.ggp.base.player.gamer.IIGamer;
import org.ggp.base.player.gamer.event.GamerUnrecognizedMatchEvent;
import org.ggp.base.player.gamer.exception.MoveSelectionException;
import org.ggp.base.util.gdl.grammar.GdlTerm;
import org.ggp.base.util.logging.GamerLogger;

public class SeesRequest extends Request {

	private final IIGamer iigamer;
	private final String matchId;
	private final Set<GdlTerm> sees;

	public SeesRequest(IIGamer gamer, String matchId, Set<GdlTerm> sees)
	{
		this.iigamer = gamer;
		this.matchId = matchId;
		this.sees = sees;
	}

	@Override
	public String process(long receptionTime) {
	    // First, check to ensure that this play request is for the match
	    // we're currently playing. If we're not playing a match, or we're
	    // playing a different match, send back "busy".
		if (iigamer.getIIMatch() == null || !iigamer.getIIMatch().getMatchId().equals(matchId)) {
			iigamer.notifyObservers(new GamerUnrecognizedMatchEvent(matchId));
			GamerLogger.logError("GamePlayer", "Got play message not intended for current game: ignoring.");
			return "busy";
		}

		if (sees != null) {
			iigamer.getIIMatch().appendSees(sees);
		}

		try {
			iigamer.notifyObservers(new PlayerTimeEvent(iigamer.getIIMatch().getPlayClock() * 1000));
			return iigamer.selectMove(iigamer.getIIMatch().getPlayClock() * 1000 + receptionTime).toString();
		} catch (MoveSelectionException e) {
		    GamerLogger.logStackTrace("GamePlayer", e);
			return "nil";
		}
	}

	@Override
	public String getMatchId() {
		return matchId;
	}

	@Override
	public String toString()
	{
		return "sees";
	}

}
