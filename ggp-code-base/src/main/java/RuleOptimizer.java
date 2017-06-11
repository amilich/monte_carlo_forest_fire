import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlLiteral;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlRule;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.gdl.grammar.GdlTerm;
import org.ggp.base.util.gdl.grammar.GdlVariable;

import com.google.common.collect.ImmutableList;

/**
 * Reorder subgoals, remove redundant subgoals.
 *
 * @author monte_carlo_forest_fire
 */
public class RuleOptimizer {
	public static List<Gdl> optimizeRules(List<Gdl> description){
		List<Gdl> optmizedSubgoals = optimizeSubgoals(description);
		return optmizedSubgoals;
	}

	private static GdlRule removeRedundantSubgaols(GdlRule rule){
		String beforeStr = rule.toString();
		GdlSentence head = rule.getHead();
		ArrayList<GdlLiteral> newBody = new ArrayList<GdlLiteral>();
		Set<GdlVariable> seenVars = new HashSet<GdlVariable>();
		ArrayList<GdlLiteral> subgoals = new ArrayList<GdlLiteral>(rule.getBody());

		while (subgoals.size() > 0){
			GdlLiteral bestSubgoal = getBest(subgoals, seenVars);
			newBody.add(bestSubgoal);
		}
		GdlRule newRule = new GdlRule(head, ImmutableList.copyOf(newBody));
		String afterStr = newRule.toString();
		if (!beforeStr.equals(afterStr)){
			System.out.println("before " + beforeStr);
			System.out.println("after  " + afterStr);
		}
		return newRule;
	}

	private static List<Gdl> optimizeSubgoals(List<Gdl> description){
		List<Gdl> result = new ArrayList<Gdl>();
		for (Gdl gdl : description) {
			if (gdl instanceof GdlRule) {
				GdlRule reorderedRule = reorder((GdlRule) gdl);
				result.add(removeRedundantSubgaols(reorderedRule));
			}
		}
		return result;
	}

	private static GdlRule reorder(GdlRule rule){
		String beforeStr = rule.toString();
		GdlSentence head = rule.getHead();
		ArrayList<GdlLiteral> newBody = new ArrayList<GdlLiteral>();
		Set<GdlVariable> seenVars = new HashSet<GdlVariable>();
		ArrayList<GdlLiteral> subgoals = new ArrayList<GdlLiteral>(rule.getBody());
		while (subgoals.size() > 0){
			GdlLiteral bestSubgoal = getBest(subgoals, seenVars);
			newBody.add(bestSubgoal);
		}
		GdlRule newRule = new GdlRule(head, ImmutableList.copyOf(newBody));
		String afterStr = newRule.toString();
		if (!beforeStr.equals(afterStr)){
			System.out.println("before " + beforeStr);
			System.out.println("after  " + afterStr);
		}
		return newRule;
	}

	private static GdlLiteral getBest(List<GdlLiteral> subgoals, Set<GdlVariable> seenVars){
		int varNum = 100000;
		int bestIdx = 0;
		HashSet<GdlVariable> newVars = null;
		for (int i = 0; i < subgoals.size(); i++){
			if (subgoals.get(i) instanceof GdlRelation){
				HashSet<GdlVariable> candidateVars = new HashSet<GdlVariable>();
				int dum = unboundvarnum((GdlRelation)subgoals.get(i), seenVars, candidateVars);
				if (dum < varNum){
					varNum = dum;
					bestIdx = i;
					newVars = candidateVars;
				}
			}
		}
		if (!(newVars == null)){
			// means we found a GdlDistinct or something that is not a relation
			seenVars.addAll(newVars);
		}
		GdlLiteral best = subgoals.get(bestIdx);
		subgoals.remove(bestIdx);
		return best;
	}

	private static int unboundvarnum(GdlRelation subgoal, Set<GdlVariable> seenVars, Set<GdlVariable> candidateVars){
		 // cast because we want to be in the domain of Variable/Constant/Function, but subgoals are GdlRelations, which is a different, but equivalent, hierarchy
		Set<GdlVariable> unboundVars = getUnboundVars(subgoal.toTerm(), new HashSet<GdlVariable>(), seenVars);
		candidateVars.addAll(unboundVars);
		return unboundVars.size();
	}

	private static Set<GdlVariable> getUnboundVars(GdlTerm term, Set<GdlVariable> unboundVars, Set<GdlVariable> seenVars){
		if (term instanceof GdlVariable){
			GdlVariable var = (GdlVariable) term;
			if(!seenVars.contains(var)){
				unboundVars.add(var);
			}
			return unboundVars;
		}
		if (term instanceof GdlConstant){ //because we have reached the bottom of the recursion with not a GdlVariable
			return unboundVars;
		}
		// else, we continue the recursion
		List<GdlTerm> body = term.toSentence().getBody();
		for(int i = 0; i < body.size(); i++){
			unboundVars = getUnboundVars(body.get(i), unboundVars, seenVars);
		}
		return unboundVars;
	}
}