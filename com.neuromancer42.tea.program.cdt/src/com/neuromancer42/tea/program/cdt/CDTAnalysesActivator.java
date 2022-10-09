package com.neuromancer42.tea.program.cdt;

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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CDTAnalysesActivator implements BundleActivator {
    private final List<JavaAnalysis> registeredAnalyses = new ArrayList<>();

    @Override
    public void start(BundleContext bundleContext) throws Exception {
        CompletableFuture.runAsync(() -> registerAnalyses(bundleContext));
        Messages.log("CDTAnalysis: started to register analyses");
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        StringBuilder msg = new StringBuilder();
        msg.append("registered analyses:");
        for (JavaAnalysis analysis : registeredAnalyses) {
            msg.append(' ').append(analysis.getName());
        }
        Messages.log("CDTAnalysis: " + msg);
    }

    public void registerAnalyses(BundleContext bundleContext) {
        Timer timer = new Timer("CDTAnalysis");
        Messages.log("ENTER: CDT Analyses Initialization started at " + (new Date()));
        timer.init();
        IDlogAnalysisBuilder souffleBuilder = SouffleRuntime.g();
        registerParser(bundleContext);
        registerMemModel(bundleContext, souffleBuilder);
        registerDataflowAnalyses(bundleContext, souffleBuilder);
        timer.done();
        Messages.log("LEAVE: CDT Analyses Initialization finished");
        Timer.printTimer(timer);
    }

    private void registerParser(BundleContext bundleContext) {
        Messages.log("CDTAnalyses: Registering CParser");
        CParserAnalysis cparser = new CParserAnalysis();
        OsgiProject.registerAnalysis(bundleContext, cparser);
        registeredAnalyses.add(cparser);
    }


    private void registerMemModel(BundleContext bundleContext, IDlogAnalysisBuilder souffleBuilder) {
        Messages.log("CDTAnalyses: Registering CMemModel");
        CMemoryModel cMemModel = new CMemoryModel();
        OsgiProject.registerAnalysis(bundleContext, cMemModel);
        registeredAnalyses.add(cMemModel);

        Messages.log("CDTAnalyses: Registering pre_pt.dl");
        InputStream prePTstream = CDTAnalysesActivator.class.getResourceAsStream("memmodel/pre_pt.dl");
        JavaAnalysis prePT = souffleBuilder.createAnalysisFromStream("PrePointer", "pre_pt", prePTstream);
        OsgiProject.registerAnalysis(bundleContext, prePT);
        registeredAnalyses.add(prePT);

        Messages.log("CDTAnalyses: Registering cipa_cg.dl");
        InputStream cipaCGstream = CDTAnalysesActivator.class.getResourceAsStream("memmodel/cipa_cg.dl");
        JavaAnalysis cipa = souffleBuilder.createAnalysisFromStream("ciPointerAnalysis", "cipa_cg", cipaCGstream);
        OsgiProject.registerAnalysis(bundleContext, cipa);
        registeredAnalyses.add(cipa);
    }


    private void registerDataflowAnalyses(BundleContext bundleContext, IDlogAnalysisBuilder souffleBuilder) {
        Messages.log("CDTAnalyses: Registering PreDataflow");
        PreDataflowAnalysis preDataflow = new PreDataflowAnalysis();
        OsgiProject.registerAnalysis(bundleContext, preDataflow);
        registeredAnalyses.add(preDataflow);

        Messages.log("CDTAnalyses: Registering InputMarker");
        InputMarker marker = new InputMarker();
        OsgiProject.registerAnalysis(bundleContext, marker);
        registeredAnalyses.add(marker);

        Messages.log("CDTAnalyses: Registering PreInterval");
        PreIntervalAnalysis preInterval = new PreIntervalAnalysis();
        OsgiProject.registerAnalysis(bundleContext, preInterval);
        registeredAnalyses.add(preInterval);

        Messages.log("CDTAnalyses: Registering interval.dl");
        InputStream intervalStream = CDTAnalysesActivator.class.getResourceAsStream("dataflow/interval.dl");
        JavaAnalysis interval = souffleBuilder.createAnalysisFromStream("interval", "interval", intervalStream);
        OsgiProject.registerAnalysis(bundleContext, interval);
        registeredAnalyses.add(interval);
    }
}
