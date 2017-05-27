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


public class RuleOptimizer {
	public static List<Gdl> optimizeRules(List<Gdl> description){
		List<Gdl> reorderedSubgoals  = reorderSubgoals(description);

		return reorderedSubgoals;
	}

	private static List<Gdl> reorderSubgoals(List<Gdl> description){
		List<Gdl> result = new ArrayList<Gdl>();
		for (Gdl gdl : description) {
			if (gdl instanceof GdlRule) {
				result.add(reorder((GdlRule) gdl));
			}
		}

	}

	private static GdlRule reorder(GdlRule rule){
		GdlSentence head = rule.getHead();
		ArrayList<GdlLiteral> newBody = new ArrayList<GdlLiteral>();
		Set<GdlVariable> seenVars = new HashSet<GdlVariable>();
		List<GdlLiteral> subgoals = rule.getBody();

		while (subgoals.size() > 0){
			GdlLiteral bestSubgoal = getBest(subgoals, seenVars);
			newBody.add(bestSubgoal);
			List<GdlVariable> vars = findVariables(bestSubgoal);
			seenVars.addAll(vars);
		}

		return new GdlRule(head, ImmutableList.copyOf(newBody));
	}

	private static GdlLiteral getBest(List<GdlLiteral> subgoals, Set<GdlVariable> seenVars){
		int varNum = 10000;
		int bestIdx = 0;
		for (int i = 0; i < subgoals.size(); i++){
			int dum = unboundvarnum(subgoals.get(i), seenVars);
			if (dum < varNum){
				varNum = dum;
				bestIdx = i;
			}
		}
		GdlLiteral best = subgoals.get(bestIdx);
		subgoals.remove(bestIdx);
		return best;
	}

	private static int unboundvarnum(GdlLiteral subgoal, Set<GdlVariable> seenVars){
		 // cast because we want to be in the domain of Variable/Constant/Function, but subgoals are GdlRelations, which is a different, but equivalent, hierarchy
		List<GdlVariable> unboundVars = getUnboundVars(((GdlRelation) subgoal).toTerm(), new ArrayList<GdlVariable>(), seenVars);
		return unboundVars.size();
	}

	private static List<GdlVariable> getUnboundVars(GdlTerm term, List<GdlVariable> unboundVars, Set<GdlVariable> seenVars){
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

	private static List<GdlVariable> findVariables(Gdl subgoal){
		List<GdlVariable> vars = new ArrayList<GdlVariable>();
		for(GdlLiteral g : subgoal.getBody()){
			if (g instanceof GdlRelation){

			}
		}

		return vars;
	}
}
