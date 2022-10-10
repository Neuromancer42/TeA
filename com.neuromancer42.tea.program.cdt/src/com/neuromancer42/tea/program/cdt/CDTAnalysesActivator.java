package com.neuromancer42.tea.program.cdt;

import com.neuromancer42.tea.core.analyses.IAnalysisBuilder;
import com.neuromancer42.tea.core.analyses.IDlogAnalysisBuilder;
import com.neuromancer42.tea.core.analyses.JavaAnalysis;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.project.OsgiProject;
import com.neuromancer42.tea.core.util.Timer;
import com.neuromancer42.tea.program.cdt.dataflow.InputMarker;
import com.neuromancer42.tea.program.cdt.dataflow.PreDataflowAnalysis;
import com.neuromancer42.tea.program.cdt.dataflow.PreIntervalAnalysis;
import com.neuromancer42.tea.program.cdt.memmodel.CMemoryModel;
import com.neuromancer42.tea.program.cdt.parser.CParserAnalysis;
import com.neuromancer42.tea.souffle.SouffleRuntime;
import org.osgi.framework.*;
import java.io.InputStream;
import java.util.*;

public class CDTAnalysesActivator implements BundleActivator {
    private CDTAnalysesBuilder cdtBuilder = null;

    @Override
    public void start(BundleContext bundleContext) throws Exception {
        cdtBuilder = new CDTAnalysesBuilder(bundleContext, SouffleRuntime.g());
        OsgiProject.registerAnalysisBuilder(bundleContext, cdtBuilder);
        Messages.log("CDTAnalysis: started to register analyses");
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        StringBuilder msg = new StringBuilder();
        msg.append("registered analyses:");
        for (var entry : cdtBuilder.registeredAnalyses.entrySet()) {
            msg.append(' ').append(entry.getKey()).append("(").append(entry.getValue().getName()).append(")");
        }
        Messages.log("CDTAnalysis: " + msg);
    }

    public static class CDTAnalysesBuilder implements IAnalysisBuilder {

        private static final String prePTName = "PrePointer";
        private static final String cipaName = "ciPointerAnalysis";
        private static final String intervalName = "interval";

        protected final Map<String, JavaAnalysis> registeredAnalyses;
        private final IDlogAnalysisBuilder souffleBuilder;
        private final BundleContext context;
        private boolean built;

        public CDTAnalysesBuilder(BundleContext context, IDlogAnalysisBuilder souffleBuilder) {
            this.context = context;
            this.souffleBuilder = souffleBuilder;
            this.registeredAnalyses = new LinkedHashMap<>();
            this.built = false;
        }


        @Override
        public String getName() {
            return "CDT";
        }

        @Override
        public synchronized void buildAnalyses(String ... analysisNames) {
            if (built) {
                Messages.warn("CDTAnalysesBuilder: duplicate call of building analyses");
            }
            Timer timer = new Timer("CDTAnalysis");
            Messages.log("ENTER: CDT Analyses Initialization started at " + (new Date()));
            timer.init();

            Set<String> analysisNameSet = new HashSet<>(List.of(analysisNames));
            analysisNameSet.removeAll(registeredAnalyses.keySet());
            if (analysisNameSet.contains(intervalName)) {
                registerIntervalAnalyses();
            } else if (analysisNameSet.contains(InputMarker.analysisName) || analysisNameSet.contains(PreDataflowAnalysis.analysisName)) {
                registerDataflowAnalyses();
            } else if (analysisNameSet.contains(cipaName) || analysisNameSet.contains(CMemoryModel.analysisName)) {
                registerMemModel();
            } else if (analysisNameSet.contains(CParserAnalysis.analysisName)) {
                registerParser();
            }
            timer.done();
            Messages.log("LEAVE: CDT Analyses Initialization finished");
            Timer.printTimer(timer);
            built = true;
        }

        @Override
        public JavaAnalysis getAnalysis(String analysisName) {
            return registeredAnalyses.get(analysisName);
        }

        @Override
        public String[] availableAnalyses() {
            return new String[]{CParserAnalysis.analysisName,
                    CMemoryModel.analysisName, cipaName,
                    PreDataflowAnalysis.analysisName, InputMarker.analysisName,
                    intervalName
            };
        }

        private void registerParser() {
            Messages.log("CDTAnalyses: Registering CParser");
            CParserAnalysis cparser = new CParserAnalysis();
            OsgiProject.registerAnalysis(context, cparser);
            registeredAnalyses.put(CParserAnalysis.analysisName, cparser);
        }


        private void registerMemModel() {
            registerParser();

            Messages.log("CDTAnalyses: Registering CMemModel");
            CMemoryModel cMemModel = new CMemoryModel();
            OsgiProject.registerAnalysis(context, cMemModel);
            registeredAnalyses.put(CMemoryModel.analysisName, cMemModel);

            Messages.log("CDTAnalyses: Registering pre_pt.dl");
            InputStream prePTstream = CDTAnalysesActivator.class.getResourceAsStream("memmodel/pre_pt.dl");
            JavaAnalysis prePT = souffleBuilder.createAnalysisFromStream(prePTName, "pre_pt", prePTstream);
            OsgiProject.registerAnalysis(context, prePT);
            registeredAnalyses.put(prePTName, prePT);

            Messages.log("CDTAnalyses: Registering cipa_cg.dl");
            InputStream cipaCGstream = CDTAnalysesActivator.class.getResourceAsStream("memmodel/cipa_cg.dl");
            JavaAnalysis cipa = souffleBuilder.createAnalysisFromStream(cipaName, "cipa_cg", cipaCGstream);
            OsgiProject.registerAnalysis(context, cipa);
            registeredAnalyses.put(cipaName, cipa);
        }


        private void registerDataflowAnalyses() {
            registerMemModel();

            Messages.log("CDTAnalyses: Registering PreDataflow");
            PreDataflowAnalysis preDataflow = new PreDataflowAnalysis();
            OsgiProject.registerAnalysis(context, preDataflow);
            registeredAnalyses.put(PreDataflowAnalysis.analysisName, preDataflow);

            Messages.log("CDTAnalyses: Registering InputMarker");
            InputMarker marker = new InputMarker();
            OsgiProject.registerAnalysis(context, marker);
            registeredAnalyses.put(InputMarker.analysisName, marker);
        }

        private void registerIntervalAnalyses() {
            registerDataflowAnalyses();

            Messages.log("CDTAnalyses: Registering PreInterval");
            PreIntervalAnalysis preInterval = new PreIntervalAnalysis();
            OsgiProject.registerAnalysis(context, preInterval);
            registeredAnalyses.put(PreIntervalAnalysis.analysisName, preInterval);

            Messages.log("CDTAnalyses: Registering interval.dl");
            InputStream intervalStream = CDTAnalysesActivator.class.getResourceAsStream("dataflow/interval.dl");
            JavaAnalysis interval = souffleBuilder.createAnalysisFromStream(intervalName, "interval", intervalStream);
            OsgiProject.registerAnalysis(context, interval);
            registeredAnalyses.put(intervalName, interval);
        }
    }
}
