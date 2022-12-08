package com.neuromancer42.tea.codemanager.cdt;

import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;
import com.neuromancer42.tea.commons.configs.Constants;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.core.analysis.Analysis;
import com.neuromancer42.tea.core.analysis.ProviderGrpc;
import com.neuromancer42.tea.core.analysis.Trgt;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CDTProviderImpl extends ProviderGrpc.ProviderImplBase {
    private CDTCManager cmanager;
    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void getFeature(Analysis.Configs request, StreamObserver<Analysis.ProviderInfo> responseObserver) {
        Analysis.ProviderInfo.Builder infoBuilder = Analysis.ProviderInfo.newBuilder();
        infoBuilder.setName("cdt-codemanager");
        Analysis.AnalysisInfo.Builder analysisBuilder = Analysis.AnalysisInfo.newBuilder();
        analysisBuilder.setName("cmanager");
        for (String domInfo : CDTCManager.producedDoms) {
            String[] entry = domInfo.split(":");
            assert entry.length == 2;
            analysisBuilder.addProducingDom(
                    Trgt.DomInfo.newBuilder()
                            .setName(entry[0])
                            .setDescription(entry[1])
                            .build()
            );
        }
        for (String relInfo : CDTCManager.producedRels) {
            String relName = relInfo.substring(0, relInfo.indexOf("("));
            String[] relDoms = relInfo.substring(relInfo.indexOf("(")+1, relInfo.indexOf(")")).split(",");
            String relDesc = relInfo.substring(relInfo.indexOf(")")+2);
            analysisBuilder.addProducingRel(
                    Trgt.RelInfo.newBuilder()
                            .setName(relName)
                            .addAllDom(List.of(relDoms))
                            .setDescription(relDesc)
                            .build()
            );
        }
        Analysis.AnalysisInfo cmanagerInfo = analysisBuilder.build();
        infoBuilder.addAnalysis(cmanagerInfo);
        for (String relInfo : CDTCManager.observableRels) {
            String relName = relInfo.substring(0, relInfo.indexOf("("));
            String[] relAttrs = relInfo.substring(relInfo.indexOf("(")+1, relInfo.indexOf(")")).split(",");

            List<String> attrNames = new ArrayList<>();
            List<String> attrDoms = new ArrayList<>();
            for (String attr : relAttrs) {
                attrNames.add(attr.split(":")[0]);
                attrDoms.add(attr.split(":")[1]);
            }
            String relDesc = relName + "(" + StringUtils.join(attrNames, ",") + ")" + relInfo.substring(relInfo.indexOf(")")+1);
            infoBuilder.addObservableRel(
                    Trgt.RelInfo.newBuilder()
                            .setName(relName)
                            .addAllDom(attrDoms)
                            .setDescription(relDesc)
                            .build()
            );
        }
        Analysis.ProviderInfo cdtInfo = infoBuilder.build();
        responseObserver.onNext(cdtInfo);
        responseObserver.onCompleted();
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void runAnalysis(Analysis.RunRequest request, StreamObserver<Analysis.RunResults> responseObserver) {
        Analysis.RunResults.Builder respBuilder = Analysis.RunResults.newBuilder();
        Map<String, String> option = request.getOption().getPropertyMap();
        if (!option.containsKey(Constants.OPT_SRC)) {
            respBuilder.setMsg(Constants.MSG_FAIL + ": No source file specified");
        } else {
            String sourceFile = option.get(Constants.OPT_SRC);
            String[] flags = option.getOrDefault(Constants.OPT_SRC_FLAGS, "").split(" ");
            Path workPath = CDTProvider.g().getWorkPath();
            List<String> includePaths = new ArrayList<>();
            Map<String, String> definedSymbols = new LinkedHashMap<>();
            for (String flag : flags) {
                if (flag.startsWith("-I")) {
                    for (String includePath : flag.substring(2).split(File.pathSeparator))  {
                        Messages.log("CParser: add include path %s", includePath);
                        includePaths.add(includePath);
                    }
                } else if (flag.startsWith("-D")) {
                    String[] pair = flag.substring(2).split("=");
                    String symbol = pair[0];
                    String value = "";
                    if (pair.length > 1)
                        value = pair[1];
                    Messages.log("CParser: add defined symbol %s=%s", symbol, value);
                    definedSymbols.put(symbol, value);
                }
            }
            cmanager = new CDTCManager(workPath, sourceFile, definedSymbols, includePaths);
            cmanager.run();
            for (ProgramDom dom : cmanager.getProducedDoms()) {
                Trgt.DomInfo domInfo = Trgt.DomInfo.newBuilder()
                        .setName(dom.getName())
                        .setDescription("generated by CDT-C manager")
                        .build();
                Trgt.DomTrgt domTrgt = Trgt.DomTrgt.newBuilder()
                        .setInfo(domInfo)
                        .setLocation(dom.getLocation())
                        .build();
                respBuilder.addDomOutput(domTrgt);
            }
            for (ProgramRel rel : cmanager.getProducedRels()) {
                Trgt.RelInfo relInfo = Trgt.RelInfo.newBuilder()
                        .setName(rel.getName())
                        .addAllDom(List.of(rel.getSign().getDomKinds()))
                        .setDescription("generated by CDT-C manager")
                        .build();
                Trgt.RelTrgt relTrgt = Trgt.RelTrgt.newBuilder()
                        .setInfo(relInfo)
                        .setLocation(rel.getLocation())
                        .build();
                respBuilder.addRelOutput(relTrgt);
            }
        }
        responseObserver.onNext(respBuilder.build());
        responseObserver.onCompleted();
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void prove(Analysis.ProveRequest request, StreamObserver<Analysis.ProveResponse> responseObserver) {
        responseObserver.onNext(Analysis.ProveResponse.newBuilder().build());
        responseObserver.onCompleted();
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void instrument(Analysis.InstrumentRequest request, StreamObserver<Analysis.InstrumentResponse> responseObserver) {
        // TODO integrate CInstrument
        super.instrument(request, responseObserver);
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void test(Analysis.TestRequest request, StreamObserver<Analysis.TestResponse> responseObserver) {
        // TODO integrate compile & run
        super.test(request, responseObserver);
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void shutdown(Analysis.ShutdownRequest request, StreamObserver<Analysis.ShutdownResponse> responseObserver) {
        Messages.log("*** shutting down cdt server due to core request");
        responseObserver.onNext(Analysis.ShutdownResponse.getDefaultInstance());
        responseObserver.onCompleted();
        System.exit(0);
    }
}
