package com.neuromancer42.tea.applications.callgraph;

import com.neuromancer42.tea.core.analyses.ProgramRel;
import com.neuromancer42.tea.core.application.AbstractApplication;
import com.neuromancer42.tea.core.project.ITask;
import com.neuromancer42.tea.core.project.OsgiProject;
import com.neuromancer42.tea.core.project.Trgt;
import com.neuromancer42.tea.core.provenance.IProvable;
import com.neuromancer42.tea.core.provenance.Provenance;
import com.neuromancer42.tea.program.cdt.parser.CParser;
import com.neuromancer42.tea.program.cdt.parser.CParserAnalysis;
import org.osgi.framework.BundleContext;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class CallGraphAnalysis extends AbstractApplication {
    @Override
    protected String getName() {
        return "CallGraph";
    }

    @Override
    protected String[] requiredAnalyses() {
        return new String[]{"ciPointerAnalysis"};
    }

    @Override
    protected void runApplication(BundleContext context, OsgiProject project) {
        project.requireTasks("ciPointerAnalysis");
        project.run("ciPointerAnalysis");
        project.printRels(new String[]{"ci_IM"});

        ProgramRel ciIM = (ProgramRel) project.getTrgt("ci_IM");
        ciIM.load();
        Map<Integer, Set<Integer>> imMap = new LinkedHashMap<>();
        for (int[] tuple : ciIM.getIntTuples()) {
            int iId = tuple[0];
            int mId = tuple[1];
            imMap.computeIfAbsent(iId, k -> new LinkedHashSet<>()).add(mId);
        }
        Set<Integer> instrIIds = new LinkedHashSet<>();
        Set<Integer> instrMIds = new LinkedHashSet<>();
        for (var entry : imMap.entrySet()) {
            Integer iId = entry.getKey();
            Set<Integer> mIds = entry.getValue();
            if (mIds.size() > 1) {
                instrIIds.add(iId);
                instrMIds.addAll(mIds);
            }
        }
        CParser cParser = ((CParserAnalysis) project.getTask("CParser")).getParser();
        CParser.CInstrument cInstr = cParser.getInstrument();
        for (int iId : instrIIds) {
            cInstr.instrumentBeforeInvoke(iId);
        }
        for (int mId : instrMIds) {
            cInstr.instrumentEnterMethod(mId);
        }
        cInstr.dumpInstrumented();

        ITask task = project.getTask("ciPointerAnalysis");
        Provenance provenance = ((IProvable) task).getProvenance();
//        CausalGraph<String> causalGraph = CausalGraph.buildCausalGraph(provenance,
//                cons -> new Categorical01(0.1, 0.5, 1.0),
//                input -> new Categorical01(0.1, 0.5, 1.0));
//        causalGraph.dump(Path.of(Config.v().outDirName));
    }
}
