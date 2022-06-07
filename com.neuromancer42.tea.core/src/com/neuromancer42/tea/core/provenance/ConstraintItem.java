package com.neuromancer42.tea.core.provenance;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import java.util.Collection;
import java.util.List;


/**
 * Represents a constraint in provenance, which has the following form:
 * headTuple = subTuple1 * subTuple2...subTupleN
 * @author xin
 *
 */
public class ConstraintItem {
  // TODO(rg): Why are signs not in the tuples?
	private final  Tuple headTuple;
	private final  List<Tuple> subTuples;
	private final Boolean headTupleSign;
	private final List<Boolean> subTuplesSign;
	private final LookUpRule rule;

	public ConstraintItem(
			LookUpRule rule,
			Tuple headTuple, List<Tuple> subTuples,
			Boolean headTupleSign, List<Boolean> subTuplesSign
	) {
    	Preconditions.checkNotNull(headTuple);
    	Preconditions.checkArgument(subTuples.size() == subTuplesSign.size());
		subTuples.removeAll(nullSingleton);
		this.rule = rule;
		this.headTuple = headTuple;
		this.subTuples = subTuples;
		this.headTupleSign = headTupleSign;
		this.subTuplesSign = Lists.newArrayList(subTuplesSign);
	}

	public LookUpRule getRule() { return rule; }

	public Tuple getHeadTuple() {
		return headTuple;
	}

	public List<Tuple> getSubTuples() {
		return subTuples;
	}

	public Boolean getHeadTupleSign() {
		return headTupleSign;
	}

	public List<Boolean> getSubTuplesSign() {
		return subTuplesSign;
	}

	public String toString(){
		StringBuilder sb = new StringBuilder();
		if(!headTupleSign) sb.append("!");
		sb.append(headTuple.toString());
		sb.append(":=");
		for(int i = 0; i < subTuples.size(); i ++){
			if(i!=0)
				sb.append("*");
			if(!subTuplesSign.get(i)) sb.append("!");
			sb.append(subTuples.get(i));
		}
		return sb.toString();
	}

  // TODO(rg): Simplify, given that these things aren't null.
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((rule == null) ? 0 : rule.hashCode());
		result = prime * result
				+ ((headTuple == null) ? 0 : headTuple.hashCode());
		result = prime * result
				+ ((subTuples == null) ? 0 : subTuples.hashCode());
		result = prime * result
				+ ((headTupleSign == null) ? 0 : headTupleSign.hashCode());
		result = prime * result
				+ ((subTuplesSign == null) ? 0 : subTuplesSign.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ConstraintItem other = (ConstraintItem) obj;
		if (rule == null) {
			if (other.rule != null)
				return false;
		} else if (!rule.equals(other.rule))
			return false;

		if (headTuple == null) {
			if (other.headTuple != null)
				return false;
		} else if (!headTuple.equals(other.headTuple))
			return false;
		if (subTuples == null) {
			if (other.subTuples != null)
				return false;
		} else if (!subTuples.equals(other.subTuples))
			return false;
		
		if (headTupleSign == null) {
			if (other.headTupleSign != null)
				return false;
		} else if (!headTupleSign.equals(other.headTupleSign))
			return false;
		if (subTuplesSign == null) {
			if (other.subTuplesSign != null)
				return false;
		} else if (!subTuplesSign.equals(other.subTuplesSign))
			return false;
		return true;
	}

  static private Collection<Object> nullSingleton = Lists.newArrayList();
  static { nullSingleton.add(null); }
}
