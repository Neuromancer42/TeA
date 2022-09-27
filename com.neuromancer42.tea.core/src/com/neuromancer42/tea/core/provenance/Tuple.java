package com.neuromancer42.tea.core.provenance;

import com.neuromancer42.tea.core.analyses.ProgramRel;
import com.neuromancer42.tea.core.bddbddb.Dom;
import com.neuromancer42.tea.core.project.Messages;

import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a tuple in the program relation
 * 
 * @author xin
 *
 * This tuple is uninterpreted; instantiating it needs a ProgramRel object
 *
 * @author yifan
 */
public class Tuple {
	private String relName;
	private int[] domIndices;

	public Tuple(String relName, int ... indices) {
		this.relName = relName;
		this.domIndices = indices;
	}

	/**
	 * Assume s has the following form: VH(2,3)
	 * 
	 * @param s
	 */
	public Tuple(String s) {
		String splits1[] = s.split("\\(");
		relName = splits1[0];
		String indexString = splits1[1].replace(")", "");
		String splits2[] = indexString.split(",");
		domIndices = new int[splits2.length];
		for (int i = 0; i < splits2.length; i++) {
			domIndices[i] = Integer.parseInt(splits2[i]);
		}
	}

	public String getRelName() {
		return relName;
	}

	public int[] getIndices() {
		return domIndices;
	}

	@Override
	public int hashCode() {
		return Objects.hash(Arrays.hashCode(domIndices), relName);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Tuple other = (Tuple) obj;
		return Objects.equals(relName, other.relName) && Arrays.equals(domIndices, other.domIndices);
	}

	public String toString() {
		StringBuilder sb = new StringBuilder("");
		sb.append(relName);
		sb.append("(");
		for (int i = 0; i < domIndices.length; i++) {
			if (i != 0)
				sb.append(',');
			sb.append(domIndices[i]);
		}
		sb.append(")");
		return sb.toString();
	}

	public String toInterpretedString(ProgramRel rel, String sep) {
		if (isSpurious(rel)) {
			Messages.fatal("Tuple %s: the provided rel %s does not match this tuple", relName, rel.getName());
		}
		StringBuilder sb = new StringBuilder("");
		sb.append(rel.getName());
		sb.append("(");
		for (int i = 0; i < domIndices.length; i++) {
			if (i != 0) sb.append(sep);
			sb.append(rel.getDoms()[i].toUniqueString(domIndices[i]));
		}
		sb.append(")");
		return sb.toString();
	}

	public boolean isSpurious(ProgramRel rel){
		if (!relName.equals(rel.getName())) {
			Messages.warn("Tuple %s: <rel %s> has different name", rel.toString(), rel.getName());
			return true;
		}
		if (rel.getDoms().length != domIndices.length) {
			Messages.warn("Tuple %s: <rel %s> has %d domains instead of %d", rel.toString(), rel.getName(), rel.getDoms().length, domIndices.length);
			return true;
		}
		for(int i = 0 ; i < rel.getDoms().length; i++) {
			if (domIndices[i] < 0 || domIndices[i] >= rel.getDoms()[i].size()) {
				Messages.warn("Tuple %s: domain %d is supurious for <dom %s>@<rel %s>", rel.toString(), i, rel.getDoms()[i].getName(), rel.getName());
				return true;
			}
		}
		return false;
	}
}
