package com.neuromancer42.tea.core.provenance;

import com.neuromancer42.tea.core.analyses.ProgramRel;
import com.neuromancer42.tea.core.bddbddb.Rel.RelView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

import com.google.common.collect.*;

/**
 * A rule generated from a single rule in the datalog. It represents a set of
 * constraints generated from the rule.
 *
 * @author xin
 *
 */
public class LookUpRule {
    private String instrName;
    private Term headTerm;
    private List<Term> subGoalTerm = new ArrayList<Term>();
    private ProgramRel instRelation;
    private boolean ifNeg = false;
    private boolean isEqGTLT = false;

    public LookUpRule(String line) {
        Scanner sc = new Scanner(line);
        instrName = sc.next();
        headTerm = readTerm(sc);
        while (sc.hasNext()) {
            subGoalTerm.add(readTerm(sc));
        }
        instRelation = RelUtil.pRel(instrName);
        instRelation.load();
    }

    public void update() {
        if (false) System.out.printf("DBG: update rule %s\n",instrName);
        instRelation.load();
    }

    public String getHeadRelName() {
        return headTerm.name;
    }

    public List<String> getSubRelNames() {
    	List<String> subRelNames = Lists.newArrayList();
    	for (Term t : subGoalTerm) {
            if (ignoredName(t.name)) continue;
    		subRelNames.add(t.name);
    	}

        return subRelNames;
    }

    public int getHeadDomCnt() {
        return headTerm.attrIdx.size();
    }

    public List<Integer> getSubDomCnts() {
    	List<Integer> subDomCnts = Lists.newArrayList();
    	for (Term t : subGoalTerm)
    		subDomCnts.add(t.attrIdx.size());

        return subDomCnts;
    }

    /**
     * Whether t is on the lhs of the rule
     *
     * @param t
     * @return
     */
    public boolean match(Tuple t) {
        return t.getRelName().equals(headTerm.name);
    }

    public boolean match(ConstraintItem c) {
        if (!c.getHeadTuple().getRelName().equals(headTerm.name))
            return false;
        if (c.getHeadTupleSign().equals(headTerm.isNeg))
            return false;

        Iterator<Tuple> tupItr = c.getSubTuples().iterator();
        Iterator<Boolean> signItr = c.getSubTuplesSign().iterator();
        for (Term term : subGoalTerm) {
            if (ignoredName(term.name))
                continue;
        	if (!tupItr.hasNext())
        		return false;
        	if (!tupItr.next().getRelName().equals(term.name))
                return false;
            if (signItr.next().equals(term.isNeg))
                return false;
        }
        if (tupItr.hasNext())
        	return false;
        return true;
    }
    
    public int viewSize(Tuple t){
    	RelView view = this.getMatchView(t);
    	if(view == null)
    		return 0;
        return view.size();
    }

    private RelView getMatchView(Tuple t){
        RelView view = instRelation.getView();
        int[] indices = t.getIndices();
        for (int i = 0; i < headTerm.attrIdx.size(); i++) {
            if (!headTerm.isConstant.get(i))
                view.selectAndDelete(headTerm.attrIdx.get(i).intValue(), indices[i]);
            else{
            	int value = headTerm.attrIdx.get(i);
            	if(value < 0)
            		continue;
            	else
            		if(value != indices[i])
            			return null;
            }
        }
        return view;
    }
    
    // Like getConstrIterForTuple, but you can use a foreach loop.
    public Iterable<ConstraintItem> view(final Tuple t) {
        return new Iterable<ConstraintItem>() {
            @Override public Iterator<ConstraintItem> iterator() {
                return t == null? getAllConstrIterator() : getConstrIterForTuple(t);
            }
        };
    }
    public Iterable<ConstraintItem> viewAll() { return view(null); }

    public Iterator<ConstraintItem> getConstrIterForTuple(Tuple t) {
        if (!match(t)) throw new RuntimeException(t + " does not match the head term of current rule.");
        return new ConstraintItemIterator(t);
    }
    
    public int getNumConstrsWithTupleAsHead(Tuple t){
    	if(!this.match(t))
    		return 0;
    	RelView view = instRelation.getView();
    	int[] indices = t.getIndices();
    	for (int i = 0; i < headTerm.attrIdx.size(); i++) {
    		if (!headTerm.isConstant.get(i))
    			view.selectAndDelete(headTerm.attrIdx.get(i).intValue(), indices[i]);
    	}
    	return view.size();
    }
    
    /**
	 * To get all the constraints related to t in current rule
	 * 
	 * @param t
	 * @return
	 */
	public List<ConstraintItem> lookUp(Tuple t) {
		List<ConstraintItem> ret = new ArrayList<ConstraintItem>();
		Iterator<ConstraintItem> iter = this.getConstrIterForTuple(t);
		while (iter.hasNext()) {
			ret.add(iter.next());
		}
		return ret;
	}

    public Iterator<ConstraintItem> getAllConstrIterator() {
        return new ConstraintItemIterator(null);
    }

    /**
     * Generate the tuple from a view with selectAndDelete on
     * @param headTuple
     * @param parInstRel
     * @param t
     * @return
     */
    private Tuple getTuple(Tuple headTuple, int[] parInstRel, Term t) {
        int[] instrRel = new int[instRelation.getDoms().length];
        int c = 0;
        int tupleIdx[] = headTuple.getIndices();
OUT:    for (int i = 0; i < instrRel.length; i++) {
            for (int j = 0; j < headTerm.attrIdx.size(); j++)
                if (!headTerm.isConstant.get(j))
                    if (headTerm.attrIdx.get(j) == i) {
                        instrRel[i] = tupleIdx[j];
                        continue OUT;
                    }
            instrRel[i] = parInstRel[c];
            c++;
        }
        return getTuple(instrRel, t);
    }

    private Tuple getTuple(int[] instrRel, Term t) {
        ProgramRel tRel = RelUtil.pRel(t.name);
        int indices[] = new int[t.attrIdx.size()];
        for (int i = 0; i < t.attrIdx.size(); i++)
            if (t.isConstant.get(i))
                indices[i] = t.attrIdx.get(i);
            else
                indices[i] = instrRel[t.attrIdx.get(i)];
        Tuple ret = new Tuple(tRel, indices);
        return ret;
    }

    private Term readTerm(Scanner sc) {
        Term ret = new Term();
        ret.name = sc.next();
        if (ret.name.startsWith("!")){
        	ret.name = ret.name.substring(1);
        	ret.isNeg = true;
        }

        int attrNum = Integer.parseInt(sc.next());
        for (int i = 0; i < attrNum; i++) {
            String item = sc.next();
            if (item.startsWith("_")) {
                item = item.substring(1);
                ret.attrIdx.add(Integer.parseInt(item));
                ret.isConstant.add(true);
            } else if (item.equals("*")) {
                ret.attrIdx.add(-1);
                ret.isConstant.add(true);
            } else {
                ret.attrIdx.add(Integer.parseInt(item));
                ret.isConstant.add(false);
            }
        }
        return ret;
    }

    @Override
    public String toString() {
        return "LookUpRule [instrName=" + instrName
            + ", headTerm=" + headTerm
            + ", subGoalTerm=" + subGoalTerm
            + ", instRelation=" + instRelation + "]";
    }

    class ConstraintItemIterator implements Iterator<ConstraintItem> {
        Iterator<int[]> iter;
        Tuple t;

        /**
         * Create the iterator to get ConstraintItems related to t, if t ==
         * null, return all the ConstraintItems
         *
         * @param t
         */
        ConstraintItemIterator(Tuple t) {
            if (t == null) {
                ProfileUtil.start("CII", "<init>(null)");
                iter = instRelation.getAryNIntTuples().iterator();
                ProfileUtil.stop("CII");
            } else {
                ProfileUtil.start("CII", "<init>(nonnull)");
                this.t = t;
                RelView view = getMatchView(t);
                iter = view.getAryNIntTuples().iterator();
                ProfileUtil.stop("CII");
            }
        }

        @Override
        public boolean hasNext() {
            ProfileUtil.start("CII", "hasNext");
            boolean r = iter.hasNext();
            ProfileUtil.stop("CII");
            return r;
        }

        public ConstraintItem nextO() {
            int[] instRel = iter.next();
            Tuple headTuple;
            List<Tuple> subTuples = Lists.newArrayList();
            List<Boolean> subTupleSigns = Lists.newArrayList();

            headTuple = t != null? t : getTuple(instRel, headTerm);
            for (Term term : subGoalTerm) {
                if (ignoredName(term.name)) {
            		if(!isEqGTLT)
            			System.out.println("Equality or greater than relation detected in the datalog rules. Ignoring the corresponding tuples.");
            		isEqGTLT = true;
            		continue;
            	}
            	//if (term.name.startsWith("!")) {
            	if (term.isNeg) {
                    if (!ifNeg)
                        System.out.println("Negation detected in the datalog rules. Pay attention for unwanted errors.");
                    ifNeg = true;
                    subTupleSigns.add(false);
                   // continue;
                }else
                	subTupleSigns.add(true);

                subTuples.add(
                    t != null ?
                    getTuple(headTuple, instRel, term)
                    : getTuple(instRel, term));
            }
            return new ConstraintItem(LookUpRule.this, headTuple, subTuples, true, subTupleSigns);
        }

        @Override public ConstraintItem next() {
            ProfileUtil.start("CII", "next");
            ConstraintItem r = nextO();
            ProfileUtil.stop("CII");
            return r;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        LookUpRule other = (LookUpRule) obj;
        if (headTerm == null) {
            if (other.headTerm != null)
                return false;
        } else if (!headTerm.equals(other.headTerm))
            return false;
        if (subGoalTerm == null) {
            if (other.subGoalTerm != null)
                return false;
        } else if (!subGoalTerm.equals(other.subGoalTerm))
            return false;
        if (instrName == null) {
            if (other.instrName != null)
                return false;
        } else if (!instrName.equals(other.instrName))
            return false;
        return true;
    }

    // TODO check with it
    private static final Pattern ignoredNameRe = Pattern.compile(".*_(gt|eq|lt)_.*");
    private static boolean ignoredName(String s) {
        return ignoredNameRe.matcher(s).matches();
    }
}

class Term {
    public String name;
    public List<Integer> attrIdx = new ArrayList<Integer>();
    public List<Boolean> isConstant = new ArrayList<Boolean>();
    public Boolean isNeg = false;

    public String toString() {
        StringBuilder sb = new StringBuilder();
        if(isNeg) sb.append("!");
        sb.append(name);
        sb.append("(");
        for (int i = 0; i < attrIdx.size(); i++) {
            if (i != 0)
                sb.append(",");
            if (isConstant.get(i))
                sb.append("_");
            sb.append(attrIdx.get(i));
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((attrIdx == null) ? 0 : attrIdx.hashCode());
        result = prime * result + ((isConstant == null) ? 0 : isConstant.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + isNeg.hashCode();
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
        Term other = (Term) obj;
        if (attrIdx == null) {
            if (other.attrIdx != null)
                return false;
        } else if (!attrIdx.equals(other.attrIdx))
            return false;
        if (isConstant == null) {
            if (other.isConstant != null)
                return false;
        } else if (!isConstant.equals(other.isConstant))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if(!(this.isNeg.equals(other.isNeg)))
        	return false;
        return true;
    }
}

// vim:ts=4:sw=4:
