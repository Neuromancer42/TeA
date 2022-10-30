package com.neuromancer42.tea.core.provenance;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;


/**
 * Represents a constraint in provenance, which has the following form:
 * headTuple = subTuple1 * subTuple2...subTupleN
 * @author xin
 *
 */
public class ConstraintItem {
  // TODO(rg): Why are signs not in the tuples?
	private final Tuple headTuple;
	private final Tuple[] subTuples;
	private final Boolean headTupleSign;
	private final Boolean[] subTuplesSign;
	private final int ruleId;

	public ConstraintItem(
            int ruleId,
            Tuple headTuple, List<Tuple> subTuples,
            Boolean headTupleSign, List<Boolean> subTuplesSign
	) {
    	Preconditions.checkNotNull(headTuple);
    	Preconditions.checkArgument(subTuples.size() == subTuplesSign.size());
		subTuples.removeAll(nullSingleton);
		this.ruleId = ruleId;
		this.headTuple = headTuple;
		this.subTuples = subTuples.toArray(new Tuple[0]);
		this.headTupleSign = headTupleSign;
		this.subTuplesSign = subTuplesSign.toArray(new Boolean[0]);
	}

	public int getRuleId() { return ruleId; }

	public Tuple getHeadTuple() {
		return headTuple;
	}

	public List<Tuple> getSubTuples() {
		return List.of(subTuples);
	}

	public Boolean getHeadTupleSign() {
		return headTupleSign;
	}

	public List<Boolean> getSubTuplesSign() {
		return List.of(subTuplesSign);
	}

	public String toString(){
		StringBuilder sb = new StringBuilder();
		if(!headTupleSign) sb.append("!");
		sb.append(headTuple.toString());
		sb.append(":=");
		for(int i = 0; i < subTuples.length; i ++){
			if(i!=0)
				sb.append("*");
			if(!subTuplesSign[i]) sb.append("!");
			sb.append(subTuples[i]);
		}
		return sb.toString();
	}

  // TODO(rg): Simplify, given that these things aren't null.
	@Override
	public int hashCode() {
		return Objects.hash(ruleId, headTuple, Arrays.hashCode(subTuples), headTupleSign, Arrays.hashCode(subTuplesSign));
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
		return Objects.equals(ruleId, other.ruleId) &&
				Objects.equals(headTuple, other.headTuple) && Arrays.equals(subTuples, other.subTuples) &&
				Objects.equals(headTupleSign, other.headTupleSign) && Arrays.equals(subTuplesSign, other.subTuplesSign);
	}

  static private final Collection<Object> nullSingleton = Lists.newArrayList();
  static { nullSingleton.add(null); }
}
