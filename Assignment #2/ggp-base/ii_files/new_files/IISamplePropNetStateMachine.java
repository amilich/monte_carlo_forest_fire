package org.ggp.base.util.statemachine.iistatemachine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.IIPropNet;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.architecture.components.And;
import org.ggp.base.util.propnet.architecture.components.Constant;
import org.ggp.base.util.propnet.architecture.components.Not;
import org.ggp.base.util.propnet.architecture.components.Or;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.propnet.architecture.components.Transition;
import org.ggp.base.util.propnet.factory.OptimizingPropNetFactory;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.See;
import org.ggp.base.util.statemachine.SeesState;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

/**
 * Sample implementation of a propnet state machine to handle incomplete information
 * games. This state machine is capable of computing a SeesState from MachineState and
 * partially computing a MachineState from a SeesState. In this way, a SeesState acts
 * as a partial characteristic of a MachineState, so this state machine computes legal
 * moves and next states with this partial information. It also "refines" legal moves for
 * an opponent (i.e., eliminates moves that are impossible given a SeesState).
 * @author hardiecate
 *
 */

public class IISamplePropNetStateMachine extends IIStateMachine {

	private IIPropNet iipropNet;
	private List<Role> roles;
	private List<Proposition> ordering;
	private StateMachine normalStateMachine;
	private List<Proposition> seesPropagationProps;
	private Map<Proposition, Double> knownProps;
	private Set<Proposition> trueProps;

	@Override
	public void initialize(StateMachine stateMachine, List<Gdl> description) throws InterruptedException {
		normalStateMachine = stateMachine;
		PropNet originalPropNet = OptimizingPropNetFactory.create(description);
		iipropNet = new IIPropNet(originalPropNet);
		roles = iipropNet.getRoles();
		seesPropagationProps = new ArrayList<Proposition>();
	}

	@Override
	public IIPropNet getIIPropNet() {
		return iipropNet;
	}

	@Override
	public int getGoal(MachineState state, Role role) throws GoalDefinitionException {
		return normalStateMachine.getGoal(state, role);
	}

	@Override
	public List<Integer> getGoals(SeesState seesState) throws GoalDefinitionException {
		List<Integer> goals = new ArrayList<Integer>();
		for (int i = 0; i < roles.size(); i++) {
			Role role = roles.get(i);
			Set<Proposition> goalProps = iipropNet.getGoalPropositions().get(role);
			boolean goalFound = false;
			for (Proposition prop : goalProps) {
				if ((propMark(prop) == 1) && !goalFound) {
					goals.add(new Integer(Integer.parseInt(prop.getName().getBody().get(1).toString()))); // could be issue with index of 2
					goalFound = true;
				} else if (goalFound) {
					goals.add(-1);
					break;
				}
			}
			if (!goalFound) goals.add(-1);
		}
		return goals;
	}

	// presupposes prop is a sees proposition
	private See getSeeFromProp(Proposition prop, Role role) {
		return new See(role, prop.getName().getBody().get(1));
	}

	@Override
	public boolean isTerminal(MachineState state) {
		return normalStateMachine.isTerminal(state);
	}

	@Override
	public List<Role> getRoles() {
		return roles;
	}

	@Override
	public List<Move> getLegalMoves(MachineState state, Role role) throws MoveDefinitionException {
		setPropNetState(state);
		return normalStateMachine.getLegalMoves(state, role);
	}


	@Override
	// could be adjusted to return moves which have unknown II value, but strictly speaking this should
	// not be necessary
	public List<Move> getLegalMoves(SeesState seesState, Move move) throws MoveDefinitionException {

		setPropNetState(seesState, move);
		List<Move> moves = new ArrayList<Move>();

		for (Proposition prop : iipropNet.getLegalPropositions().get(seesState.getRole())) {
			if (propMark(prop) == 1) {
				Move m = new Move(prop.getName().getBody().get(1));
				moves.add(m);
			}
		}
		return moves;
	}

	@Override
	public List<Move> getLegalMoves(SeesState seesState) throws MoveDefinitionException {

		setPropNetState(seesState);
		List<Move> moves = new ArrayList<Move>();
		for (Proposition prop : iipropNet.getLegalPropositions().get(seesState.getRole())) {
			if (propMark(prop) == 1) {
				Move m = new Move(prop.getName().getBody().get(1));
				moves.add(m);
			}
		}
		return moves;
	}

	// could be done more efficiently by specifying whether the propnet needs to be reset.
	// As it is now, the propnet is reset every time legal moves are queried.
	@Override
	public List<Move> refineLegalMoves(SeesState seesState, Move move, Role role) {
		setPropNetState(seesState, move);
		List<Move> moves = new ArrayList<Move>();
		for (Proposition prop : iipropNet.getLegalPropositions().get(role)) {
			if (!(propMark(prop) == 0)) {
				moves.add(getMoveFromProposition(prop));
			}
		}
		return moves;
	}

	@Override
	public List<Move> refineLegalMoves(SeesState seesState, Role role) {
		setPropNetState(seesState);
		List<Move> moves = new ArrayList<Move>();
		for (Proposition prop : iipropNet.getLegalPropositions().get(role)) {
			if (!(propMark(prop) == 0)) {
				moves.add(getMoveFromProposition(prop));
			}
		}
		return moves;
	}

	@Override
	public MachineState getNextState(MachineState state, List<Move> moves) throws TransitionDefinitionException {
		return normalStateMachine.getNextState(state, moves);
	}

	@Override
	public MachineState getInitialState() {
		return normalStateMachine.getInitialState();
	}



	/**
	 * Takes in a Legal Proposition and returns the appropriate corresponding Move
	 * @param p
	 * @return a PropNetMove
	 */
	public static Move getMoveFromProposition(Proposition legalProp)
	{
		return new Move(legalProp.getName().get(1));
	}

	@Override
	public SeesState getSeesState(MachineState state, Role role) {

		setPropNetState(state);
		Set<See> sees = new HashSet<See>();
		if (iipropNet.getSeesPropositionsByRole().get(role) == null) {
			return new SeesState(role, null);
		}
		for (Proposition prop : iipropNet.getSeesPropositionsByRole().get(role)) {
			if (normalStatePropMark(prop)) {
				See see = new See(role, prop.getName().toTerm());
				sees.add(see);
			}
		}
		return new SeesState(role, sees);
	}

	private void setPropNetState(SeesState seesState) {
		resetPropNet(seesState.getRole());
		markSees(seesState);
		propagateSees();
	}

	private void setPropNetState(SeesState seesState, Move move) {
		markAction(move, seesState.getRole());
		resetPropNet(seesState.getRole());
		markSees(seesState);
		propagateSees();
	}

	private void setPropNetState(MachineState state) {
		clearPropNet();
		markBases(state.getContents());
	}

	private void resetPropNet(Role role) {

		iipropNet.setIIValue(iipropNet.getInitProposition(), 0);
		knownProps = new HashMap<Proposition, Double>();
		trueProps = new HashSet<Proposition>();
		for (Transition t : iipropNet.getNonDoesTransitions()) { // get iivalues of base propositions connected to transition
			double iivalue = propMark(t.getSingleInput());
			if (t.getOutputs().size() > 1) System.out.println("NABIIPNSM: Transition found with more than one base proposition.");
			knownProps.put((Proposition)t.getSingleOutput(), iivalue);
		}
		for (Transition t : iipropNet.getDoesTransitions().get(role)) {
			double iivalue = propMark(t.getSingleInput());
			knownProps.put((Proposition)t.getSingleOutput(), iivalue);
		}
		for (Proposition p : knownProps.keySet()) { // set iivalues of these base props
			iipropNet.setIIValue(p, knownProps.get(p));
			if (knownProps.get(p) == 1) {
				trueProps.add(p);
			}
		}

		for (Proposition p : iipropNet.getPropositions()) { // set all other propositions to unknown
			if (!knownProps.containsKey(p) && !iipropNet.getInputPropositions().values().contains(p)) {
				iipropNet.setIIValue(p, 0.5);
			}
		}

	}

	public Map<Proposition, Double> getKnownProps() {
		return knownProps;
	}

	public Set<Proposition> getTrueProps() {
		return trueProps;
	}

	private void markAction(Move move, Role role) {
		if (move != null){
			for (Proposition legal : iipropNet.getLegalPropositions().get(role)) {
				if (legal.getName().get(1).equals(move.getContents())) {
					iipropNet.setIIValue(iipropNet.getLegalInputMap().get(legal), 1);
				} else {
					iipropNet.setIIValue(iipropNet.getLegalInputMap().get(legal), 0);
				}
			}
		}
	}

	private void markSees(SeesState seesState) {
		Role role = seesState.getRole();
		if (iipropNet.getSeesPropositionsByRole().get(role) != null) {
			for (Proposition prop : iipropNet.getSeesPropositionsByRole().get(seesState.getRole())) {
				if (seesState.containsSee(prop.getName())) {
					iipropNet.setIIValue(prop, 1);
				} else {
					iipropNet.setIIValue(prop, 0);
				}
			}
		}
	}

	private void propagateSees() {
		List<Proposition> trueSees = new ArrayList<Proposition>();
		List<Proposition> falseSees = new ArrayList<Proposition>();
		for (Proposition prop : iipropNet.getSeesPropositionsByName().values()) {
			seesPropagationProps.add(prop);
			if (prop.getValue()) trueSees.add(prop);
			else falseSees.add(prop);
		}
		// propagate true values
		for (Proposition prop : trueSees) {
			for (Component c : prop.getInputs()) {
				propagateSeesHelper(c, true);
			}
		}
		// propagate false values
		for (Proposition prop : falseSees) {
			for (Component c : prop.getInputs()) {
				propagateSeesHelper(c, false);
			}
		}
	}

	private void propagateSeesHelper(Component c, boolean value) {

		double iivalue;
		if (value) iivalue = 1;
		else iivalue = 0;
		if (c instanceof Proposition) {
			Proposition prop = (Proposition) c;
			iipropNet.setIIValue(prop, iivalue);

			if (!seesPropagationProps.contains(prop)) seesPropagationProps.add(prop);

			if (prop.getInputs().size() > 1 || ((prop.getInputs().size() == 1) && !(prop.getSingleInput() instanceof Transition))) {
				for (Component comp : prop.getInputs()) propagateSeesHelper(comp, value);
			}

		} else if ((c instanceof And) && value) {
			for (Component comp : c.getInputs()) propagateSeesHelper(comp, value);

		} else if ((c instanceof Or) && !value) {
			for (Component comp : c.getInputs()) propagateSeesHelper(comp, value);

		} else if (c instanceof Not) {
			propagateSeesHelper(c.getSingleInput(), !value);
		}

	}

	// Clears propnet by setting the II value of every single component to unknown. In theory we only need to clear
	// the values from propositions that were affected by the downward sees propagation, so this could potentially
	// be done more efficiently by keeping track of these propositions during propagation.
	private void clearPropNet() {
		if (seesPropagationProps == null) {
			for(Proposition prop : iipropNet.getPropositions()) {
				iipropNet.setIIValue(prop, 0.5);
			}
		} else {
			for (Proposition prop : seesPropagationProps) {
				iipropNet.setIIValue(prop, 0.5);
			}
		}
		iipropNet.setIIValue(iipropNet.getInitProposition(), 0);
	}

	private void markBases(Set<GdlSentence> sentences){
		for (GdlSentence sent: iipropNet.getBasePropositions().keySet()){
			if (sentences.contains(sent)) {
				iipropNet.setIIValue(iipropNet.getBasePropositions().get(sent),1);
			} else {
				iipropNet.setIIValue(iipropNet.getBasePropositions().get(sent), 0);
			}
		}
	}

	private double propMark(Component c){
		if (c instanceof Proposition){

			Proposition iiprop = (Proposition) c;
			double iivalue = iipropNet.getIIValue(iiprop);
			if ((iivalue == 0) || (iivalue == 1)) {
				return iivalue;
			}
			// view proposition
			if (iiprop.getInputs().size() == 1 && !(iiprop.getSingleInput() instanceof Transition)){
				return propMark(iiprop.getSingleInput());
			}
			// base or input proposition
			else{
				return iivalue;
			}
		}
		else if (c instanceof And){
			double propValue = 1;
			for (Component c2 : c.getInputs()){
				if (propMark(c2) == 0) return 0;
				if (propMark(c2) == 0.5) propValue = 0.5;
			}
			return propValue;
		}
		else if (c instanceof Or){
			double propValue = 0;
			for (Component c2 : c.getInputs()){
				if (propMark(c2) == 1) return 1;
				if (propMark(c2) == 0.5) propValue = 0.5;
			}
			return propValue;
		}
		else if (c instanceof Constant){
			if(c.getValue()) return 1;
			else return 0;
		}
		else if (c instanceof Not){
			if (propMark(c.getSingleInput()) == 0) return 1;
			else if (propMark(c.getSingleInput()) == 1) return 0;
			else return propMark(c.getSingleInput());
		}
		else if (c instanceof Transition){
			return propMark(c.getSingleInput());
		}

		return 0;
	}

	private boolean normalStatePropMark(Component c){
		if (c instanceof Proposition){

			// view proposition
			if (!(c.getSingleInput() instanceof Transition)){
				return normalStatePropMark(c.getSingleInput());
			}
			// base or input proposition
			else{
				return c.getValue();
			}
		}
		else if (c instanceof And){
			for (Component c2:c.getInputs()){
				if (!normalStatePropMark(c2)) return false;
			}
			return true;
		}
		else if (c instanceof Or){
			for (Component c2:c.getInputs()){
				if (normalStatePropMark(c2)) return true;
			}
			return false;
		}
		else if (c instanceof Constant){
			return c.getValue();
		}
		else if (c instanceof Not){
			return !normalStatePropMark(c.getSingleInput());
		}
		else if (c instanceof Transition){
			return normalStatePropMark(c.getSingleInput());
		}

		System.out.println("NotABotIIPropNetStateMachine found unknown Component during normalStatePropMark");
		return false;
	}
}
