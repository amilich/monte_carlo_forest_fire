package org.ggp.base.server;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ggp.base.server.event.ServerCompletedMatchEvent;
import org.ggp.base.server.event.ServerMatchUpdatedEvent;
import org.ggp.base.server.event.ServerNewGameStateEvent;
import org.ggp.base.server.event.ServerNewMatchEvent;
import org.ggp.base.server.event.ServerNewMovesEvent;
import org.ggp.base.server.event.ServerTimeEvent;
import org.ggp.base.server.threads.IIStopRequestThread;
import org.ggp.base.server.threads.RandomSeesRequestThread;
import org.ggp.base.server.threads.SeesRequestThread;
import org.ggp.base.util.match.IIMatch;
import org.ggp.base.util.observer.Subject;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.See;
import org.ggp.base.util.statemachine.SeesState;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.iistatemachine.NotABotIIPropNetStateMachine;

public class IIGameServer extends GameServer implements Subject {

	private final IIMatch iimatch;
	private final List<Set<See>> currentSees;
	private final NotABotIIPropNetStateMachine iistateMachine;

	public IIGameServer(IIMatch match, List<String> hosts, List<Integer> ports) throws InterruptedException {
		super(match, hosts, ports);
		iimatch = match;
		currentSees = new ArrayList<Set<See>>();
		for (int i = 0; i < this.hosts.size(); i++) {
			currentSees.add(new HashSet<See>());
		}
		iistateMachine = new NotABotIIPropNetStateMachine();
		iistateMachine.initialize(stateMachine, iimatch.getGame().getRules());
	}

	@Override
	public void run()
	{
		try {

        	if (iimatch.getPreviewClock() >= 0) {
        		sendPreviewRequests();
        	}

            notifyObservers(new ServerNewMatchEvent(stateMachine.getRoles(), currentState));
            notifyObservers(new ServerTimeEvent(iimatch.getStartClock() * 1000));
            sendStartRequests();
            appendErrorsToMatchDescription();

            while (!stateMachine.isTerminal(getCurrentState())) {
                publishWhenNecessary();
                saveWhenNecessary();
                notifyObservers(new ServerNewGameStateEvent(currentState));
                notifyObservers(new ServerTimeEvent(iimatch.getPlayClock() * 1000));
                notifyObservers(new ServerMatchUpdatedEvent(iimatch, getSpectatorServerKey(), getSaveToFilename()));
                previousMoves = sendSeesRequests();

                notifyObservers(new ServerNewMovesEvent(previousMoves));
                currentState = stateMachine.getNextState(currentState, previousMoves);

                iimatch.appendMoves2(previousMoves);
                iimatch.appendState(currentState.getContents());
                appendErrorsToMatchDescription();

                for (int i = 0; i < hosts.size(); i++) {
                	SeesState seesState = iistateMachine.getSeesState(currentState, stateMachine.getRoles().get(i));
                	currentSees.set(i, seesState.getSees());
                }

                if (iimatch.isAborted()) {
                	return;
                }
            }
            iimatch.markCompleted(stateMachine.getGoals(currentState));
            publishWhenNecessary();
            saveWhenNecessary();
            notifyObservers(new ServerNewGameStateEvent(currentState));
            notifyObservers(new ServerCompletedMatchEvent(getGoals()));
            notifyObservers(new ServerMatchUpdatedEvent(iimatch, getSpectatorServerKey(), getSaveToFilename()));
            sendIIStopRequests(previousMoves);
        } catch (InterruptedException ie) {
        	if (iimatch.isAborted()) {
        		return;
        	} else {
        		ie.printStackTrace();
        	}
        } catch (Exception e) {
        	e.printStackTrace();
        }
	}

	private synchronized void sendIIStopRequests(List<Move> previousMoves) throws InterruptedException {
        List<IIStopRequestThread> threads = new ArrayList<IIStopRequestThread>(hosts.size());
        for (int i = 0; i < hosts.size(); i++) {
        	if (!getPlayerPlaysRandomly(i)) {
        		threads.add(new IIStopRequestThread(this, iimatch, currentSees.get(i), stateMachine.getRoles().get(i), hosts.get(i), ports.get(i), getPlayerNameFromMatchForRequest(i)));
        	}
        }
        for (IIStopRequestThread thread : threads) {
            thread.start();
        }
        for (IIStopRequestThread thread : threads) {
            thread.join();
        }
    }

	protected synchronized List<Move> sendSeesRequests() throws InterruptedException, MoveDefinitionException {
		List<SeesRequestThread> threads = new ArrayList<SeesRequestThread>(hosts.size());
		for (int i = 0; i < hosts.size(); i++) {
            List<Move> legalMoves = stateMachine.getLegalMoves(currentState, stateMachine.getRoles().get(i));
            if (getPlayerPlaysRandomly(i)) {
            	threads.add(new RandomSeesRequestThread(iimatch, legalMoves));
            } else {
                threads.add(new SeesRequestThread(this, iimatch, currentSees.get(i), legalMoves, stateMachine.getRoles().get(i), hosts.get(i), ports.get(i), getPlayerNameFromMatchForRequest(i), getPlayerGetsUnlimitedTime(i)));
            }
        }
		for (SeesRequestThread thread : threads) {
			thread.start();
		}

		if (forceUsingEntireClock) {
            Thread.sleep(iimatch.getPlayClock() * 1000);
        }

		List<Move> moves = new ArrayList<Move>();
        for (SeesRequestThread thread : threads) {
            thread.join();
            moves.add(thread.getMove());
        }

        return moves;
	}

	private String getPlayerNameFromMatchForRequest(int i) {
    	if (iimatch.getPlayerNamesFromHost() != null) {
    		return iimatch.getPlayerNamesFromHost().get(i);
    	} else {
    		return "";
    	}
    }
}
