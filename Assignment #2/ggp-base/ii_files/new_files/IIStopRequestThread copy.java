package org.ggp.base.server.threads;

import java.util.Set;

import org.ggp.base.server.IIGameServer;
import org.ggp.base.server.request.RequestBuilder;
import org.ggp.base.util.match.IIMatch;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.See;


public final class IIStopRequestThread extends RequestThread
{
	public IIStopRequestThread(IIGameServer iigameServer, IIMatch iimatch, Set<See> sees, Role role, String host, int port, String playerName)
	{
		super(iigameServer, role, host, port, playerName, iimatch.getPlayClock() * 1000, RequestBuilder.getIIStopRequest(iimatch.getMatchId(), sees, iimatch.getGdlScrambler()));
	}

	@Override
	protected void handleResponse(String response) {
		;
	}
}
