package com.neuromancer42.tea.core.analyses;

import com.neuromancer42.tea.core.bddbddb.RelSign;
import com.neuromancer42.tea.core.project.Config;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.project.ITask;

import com.neuromancer42.tea.core.bddbddb.Rel;

import java.util.HashMap;
import java.util.Map;


/**
 * Generic implementation of a program relation (a specialized kind of Java task).
 * <p>
 * A program relation is a relation over one or more program domains.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 * @author Saswat Anand
 */
public class ProgramRel extends Rel implements ITask {
    private static final String SKIP_TUPLE =
        "WARN: Skipping a tuple from relation '%s' as element '%s' was not found in domain '%s'.";
    protected Object[] consumes;

    public static ProgramRel createRel(String relName, ProgramDom<?>[] doms, String[] domNames, String domOrder) {
        ProgramRel rel = new ProgramRel();
        rel.setName(relName);
        rel.setSign(domNames, domOrder);
        rel.setDoms(doms);
        return rel;
    }

    public static ProgramRel createRel(String relName, ProgramDom<?>[] doms) {
        String[] rawDomNames = new String[doms.length];
        for (int i = 0; i < doms.length; ++i) {
            rawDomNames[i] = doms[i].getName();
        }
        RelSign defaultRelSign = genDefaultRelSign(rawDomNames);

        ProgramRel rel = new ProgramRel();
        rel.setName(relName);
        rel.setSign(defaultRelSign);
        rel.setDoms(doms);
        return rel;
    }

    // generate default RelSign
    // example: Souffle relation: Reach(a1:A,b:B,a2:A) ==> RelSign: [A0,B0,A1]:"A0xB0xA1"
    public static RelSign genDefaultRelSign(String[] rawDomNames) {
        assert(rawDomNames.length > 0);
        String[] domNames = new String[rawDomNames.length];
        Map<String, Integer> domCount = new HashMap<>();
        for (int i = 0; i < rawDomNames.length; ++i) {
            String rawDomName = rawDomNames[i];
            boolean allLetters = true;
            for (int j = 0; j < rawDomName.length(); ++j) {
                char c = rawDomName.charAt(j);
                if ((c < 'a' || c > 'z') && (c < 'A' || c > 'Z')) {
                    allLetters = false;
                    break;
                }
            }
            if (!allLetters) {
                Messages.fatal("AnalysesUtil: raw domain name (%s) should be restricted to english letters only", rawDomName);
            } else {
                int idx = domCount.getOrDefault(rawDomName, 0);
                domNames[i] = rawDomName + idx;
                domCount.put(rawDomName, idx + 1);
            }
        }
        StringBuilder domOrder = new StringBuilder(domNames[0]);
        for (int i = 1; i < domNames.length; ++i) {
            domOrder.append('x').append(domNames[i]);
        }
        return new RelSign(domNames, domOrder.toString());
    }

    @Override
    public void run() {
        init();
        fill();
        save();
        close();
    }
    public void init() {
        zero();
    }

    public void save() {
        if (Config.v().verbose >= 1)
            System.out.println("SAVING rel " + name + " size: " + size());
        super.save(Config.v().bddbddbWorkDirName);
    }

    public void load() {
        super.load(Config.v().bddbddbWorkDirName);
    }

    public void fill()
	{
		throw new RuntimeException("implement");
	}

    public void print() {
        super.print(Config.v().outDirName);
    }
    public String toString() {
        return name;
    }
    public void skip(Object elem, ProgramDom<?> dom) {
        Messages.log(SKIP_TUPLE, getClass().getName(), elem, dom.getClass().getName());
    }
}
