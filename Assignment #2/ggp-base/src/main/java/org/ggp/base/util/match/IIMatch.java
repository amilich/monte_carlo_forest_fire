package org.ggp.base.util.match;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ggp.base.util.crypto.SignableJSON;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.gdl.factory.GdlFactory;
import org.ggp.base.util.gdl.factory.exceptions.GdlFormatException;
import org.ggp.base.util.gdl.grammar.GdlTerm;
import org.ggp.base.util.symbol.factory.exceptions.SymbolFormatException;

import external.JSON.JSONArray;
import external.JSON.JSONException;
import external.JSON.JSONObject;

public class IIMatch extends Match {

	private final List<Set<GdlTerm>> seesHistory;

	public IIMatch(Match match) {
		super(match.getMatchId(), match.getPreviewClock(), match.getStartClock(), match.getPlayClock(), match.getGame(), match.getTournamentNameFromHost());
		seesHistory = new ArrayList<Set<GdlTerm>>();
	}

	public IIMatch(String matchId, int previewClock, int startClock, int playClock, Game theGame, String tournamentNameFromHost) {
		super(matchId, previewClock, startClock, playClock, theGame, tournamentNameFromHost);
		seesHistory = new ArrayList<Set<GdlTerm>>();
	}


	public IIMatch(String theJSON, Game theGame, String authToken)
			throws JSONException, SymbolFormatException, GdlFormatException {
		super(theJSON, theGame, authToken);
		JSONObject theMatchObject = new JSONObject(theJSON);
		this.seesHistory = new ArrayList<Set<GdlTerm>>();
		JSONArray theSees = theMatchObject.getJSONArray("sees");
        for (int i = 0; i < theSees.length(); i++) {
            Set<GdlTerm> seesTerms = new HashSet<GdlTerm>();
            JSONArray sees = theSees.getJSONArray(i);
            for (int j = 0; j < sees.length(); j++) {
                seesTerms.add(GdlFactory.createTerm(sees.getString(j)));
            }
            seesHistory.add(seesTerms);
        }
	}

	public void appendSees(Set<GdlTerm> sees) {
		seesHistory.add(sees);
	}

	@Override
	public String toJSON() {
        JSONObject theJSON = new JSONObject();

        try {
            theJSON.put("matchId", getMatchId());
            theJSON.put("randomToken", getRandomToken());
            theJSON.put("startTime", getStartTime().getTime());
            theJSON.put("gameMetaURL", getGameRepositoryURL());
            theJSON.put("isCompleted", isCompleted());
            theJSON.put("isAborted", isAborted());
            theJSON.put("sees", new JSONArray(renderArrayAsJSON(renderSeesHistory(seesHistory), true)));
            theJSON.put("states", new JSONArray(renderArrayAsJSON(renderStateHistory(getStateHistory()), true)));
            theJSON.put("moves", new JSONArray(renderArrayAsJSON(renderMoveHistory(getMoveHistory()), false)));
            theJSON.put("stateTimes", new JSONArray(renderArrayAsJSON(getStateTimeHistory(), false)));
            if (getErrorHistory().size() > 0) {
                theJSON.put("errors", new JSONArray(renderArrayAsJSON(renderErrorHistory(getErrorHistory()), false)));
            }
            if (getGoalValues().size() > 0) {
                theJSON.put("goalValues", getGoalValues());
            }
            theJSON.put("previewClock", getPreviewClock());
            theJSON.put("startClock", getStartClock());
            theJSON.put("playClock", getPlayClock());
            if (getPlayerNamesFromHost() != null) {
                theJSON.put("playerNamesFromHost", getPlayerNamesFromHost());
            }
            if (getIsPlayerHuman() != null) {
            	theJSON.put("isPlayerHuman", getIsPlayerHuman());
            }
            theJSON.put("scrambled", getGdlScrambler() != null ? getGdlScrambler().scrambles() : false);
        } catch (JSONException e) {
            return null;
        }

        if (getCryptographicKeys() != null) {
            try {
                SignableJSON.signJSON(theJSON, getCryptographicKeys().thePublicKey, getCryptographicKeys().thePrivateKey);
                if (!SignableJSON.isSignedJSON(theJSON)) {
                    throw new Exception("Could not recognize signed match: " + theJSON);
                }
                if (!SignableJSON.verifySignedJSON(theJSON)) {
                    throw new Exception("Could not verify signed match: " + theJSON);
                }
            } catch (Exception e) {
                System.err.println(e);
                theJSON.remove("matchHostPK");
                theJSON.remove("matchHostSignature");
            }
        }

        return theJSON.toString();
    }

	@Override
	public String toXML() {
    	try {
    		JSONObject theJSON = new JSONObject(toJSON());

    		StringBuilder theXML = new StringBuilder();
    		theXML.append("<match>");
    		for (String key : JSONObject.getNames(theJSON)) {
    			Object value = theJSON.get(key);
    			if (value instanceof JSONObject) {
    				throw new RuntimeException("Unexpected embedded JSONObject in match JSON with tag " + key + "; could not convert to XML.");
    			} else if (!(value instanceof JSONArray)) {
    				theXML.append(renderLeafXML(key, theJSON.get(key)));
    			} else if (key.equals("sees")) {
    				theXML.append(renderSeesHistoryXML(seesHistory));
    			} else if (key.equals("states")) {
    				theXML.append(renderStateHistoryXML(getStateHistory()));
    			} else if (key.equals("moves")) {
    				theXML.append(renderMoveHistoryXML(getMoveHistory()));
    			} else if (key.equals("errors")) {
    				theXML.append(renderErrorHistoryXML(getErrorHistory()));
    			} else {
    				theXML.append(renderArrayXML(key, (JSONArray)value));
    			}
    		}
    		theXML.append("</match>");

    		return theXML.toString();
    	} catch (JSONException je) {
    		return null;
    	}
    }

	public Set<GdlTerm> getMostRecentSees() {
		if (seesHistory.size() == 0)
			return null;
		return seesHistory.get(seesHistory.size()-1);
	}

	public List<Set<GdlTerm>> getSeesHistory() {
		return seesHistory;
	}

	private static final List<String> renderSeesHistory(List<Set<GdlTerm>> seesHistory) {
		List<String> renderedSees = new ArrayList<String>();
        for (Set<GdlTerm> aSees : seesHistory) {
            renderedSees.add(renderSeesAsSymbolList(aSees));
        }
        return renderedSees;
	}

	private static final String renderSeesAsSymbolList(Set<GdlTerm> theSees) {
		String s = "( ";
		for (GdlTerm term : theSees) {
			s += term.toString().trim() + " ";
		}
		return s + ")";
	}

	/* Include legacy XML rendering methods here if necessary */

	private static final String renderSeesHistoryXML(List<Set<GdlTerm>> seesHistory) {
		StringBuilder theXML = new StringBuilder();
		theXML.append("<seesHistory>");
		for (Set<GdlTerm> seesState : seesHistory) {
			theXML.append(renderSeesXML(seesState));
		}
		theXML.append("</seesHistory>");
		return theXML.toString();
	}

	private static final String renderSeesXML(Set<GdlTerm> seesState) {
		StringBuilder theXML = new StringBuilder();
		theXML.append("<sees>");
		for (GdlTerm sees : seesState) {
			theXML.append(renderGdlToXML(sees));
		}
		theXML.append("</sees>");
		return theXML.toString();
	}

}
