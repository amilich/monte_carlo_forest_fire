package org.ggp.base.util.statemachine;

import java.util.Set;

import org.ggp.base.util.gdl.grammar.GdlSentence;

public class SeesState {

	private Role role;
	private Set<See> sees;

	public SeesState(Role role, Set<See> sees) {
		this.role = role;
		this.sees = sees;
	}

	public Role getRole() {
		return role;
	}

	public void setRole(Role role) {
		this.role = role;
	}

	public Set<See> getSees() {
		return sees;
	}

	public boolean containsSee(GdlSentence sent) {
		for (See see : sees) {
			if (see.getContents().toSentence().equals(sent)) {
				return true;
			}
		}
		return false;
	}
}
