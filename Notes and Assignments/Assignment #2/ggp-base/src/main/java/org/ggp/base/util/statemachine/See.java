package org.ggp.base.util.statemachine;

import java.io.Serializable;

import org.ggp.base.util.gdl.grammar.GdlTerm;

/**
 *
 * @author hardiecate
 *
 */

@SuppressWarnings("serial")
public class See implements Serializable {

	private final GdlTerm contents;
	private final Role role;

	public See(Role role) {
		this.role = role;
		contents = null;
	}

	public See(Role role, GdlTerm contents) {
		this.role = role;
		this.contents = contents;
	}

	@Override
	public boolean equals(Object o) {
		if (o != null && (o instanceof See)) {
			See s = (See) o;
			return (s.getContents().equals(contents) && s.getRole().equals(role));
		}
		return false;
	}

	public Role getRole() {
		return role;
	}

	public GdlTerm getContents() {
		return contents;
	}

	@Override
    public int hashCode()
    {
        return contents.hashCode();
    }

    @Override
    public String toString()
    {
        return contents.toString();
    }
}
