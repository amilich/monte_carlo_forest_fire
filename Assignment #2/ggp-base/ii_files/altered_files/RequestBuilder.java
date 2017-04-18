package org.ggp.base.server.request;

import java.util.List;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.scrambler.GdlScrambler;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.See;


public final class RequestBuilder
{
	public static String getPlayRequest(String matchId, List<Move> moves, GdlScrambler scrambler)
	{
		if (moves == null) {
			return "( PLAY " + matchId + " NIL )";
		} else {
			StringBuilder sb = new StringBuilder();

			sb.append("( PLAY " + matchId + " (");
			for (Move move : moves)
			{
				sb.append(scrambler.scramble(move.getContents()) + " ");
			}
			sb.append(") )");

			return sb.toString();
		}
	}

	public static String getSeesRequest(String matchId, Set<See> sees, GdlScrambler scrambler) {
		 if (sees == null) {
			 return "( SEES " + matchId + " NIL )";
		 } else {
			 StringBuilder sb = new StringBuilder();

			 sb.append("( SEES " + matchId + " (");
			 for (See see : sees)
			 {
				 System.out.println(see.getContents().toString());
				 sb.append(scrambler.scramble(see.getContents()) + " ");
			 }
			 sb.append(") )");

			 return sb.toString();
		 }
	}

	public static String getIIStopRequest(String matchId, Set<See> sees, GdlScrambler scrambler) {
		 if (sees == null) {
			 return "( STOP " + matchId + " NIL )";
		 } else {
			 StringBuilder sb = new StringBuilder();

			 sb.append("( STOP " + matchId + " (");
			 for (See see : sees)
			 {
				 sb.append(scrambler.scramble(see.getContents()) + " ");
			 }
			 sb.append(") )");

			 return sb.toString();
		 }
	}

	public static String getStartRequest(String matchId, Role role, List<Gdl> description, int startClock, int playClock, GdlScrambler scrambler)
	{
		StringBuilder sb = new StringBuilder();

		sb.append("( START " + matchId + " " + scrambler.scramble(role.getName()) + " (");
		for (Gdl gdl : description)
		{
			sb.append(scrambler.scramble(gdl) + " ");
		}
		sb.append(") " + startClock + " " + playClock + ")");

		return sb.toString();
	}

	public static String getPreviewRequest(List<Gdl> description, int previewClock, GdlScrambler scrambler)
	{
		StringBuilder sb = new StringBuilder();

		sb.append("( PREVIEW (");
		for (Gdl gdl : description)
		{
			sb.append(scrambler.scramble(gdl) + " ");
		}
		sb.append(") " + previewClock + " )");

		return sb.toString();
	}

	public static String getStopRequest(String matchId, List<Move> moves, GdlScrambler scrambler)
	{
		if (moves == null) {
			return "( STOP " + matchId + " NIL )";
		} else {
			StringBuilder sb = new StringBuilder();

			sb.append("( STOP " + matchId + " (");
			for (Move move : moves)
			{
				sb.append(scrambler.scramble(move.getContents()) + " ");
			}
			sb.append(") )");

			return sb.toString();
		}
	}

	public static String getAbortRequest(String matchId)
	{
		return "( ABORT " + matchId + " )";
	}

	public static String getInfoRequest()
	{
		return "( INFO )";
	}
}