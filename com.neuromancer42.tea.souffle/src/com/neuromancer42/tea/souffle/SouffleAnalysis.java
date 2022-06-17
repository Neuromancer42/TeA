package com.neuromancer42.tea.souffle;

import com.neuromancer42.tea.core.analyses.*;
import com.neuromancer42.tea.core.bddbddb.Dom;
import com.neuromancer42.tea.core.bddbddb.Rel;
import com.neuromancer42.tea.core.bddbddb.RelSign;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.util.Utils;
import com.neuromancer42.tea.core.util.tuple.object.Pair;
import com.neuromancer42.tea.souffle.swig.SWIGSouffleProgram;
import com.neuromancer42.tea.souffle.swig.SwigInterface;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class SouffleAnalysis extends JavaAnalysis {
    private final File dlogFile;
    protected final SWIGSouffleProgram souffleProgram;
    public final Path factDir;
    public final Path outDir;
    private final Map<String, RelSign> relSignMap;
    private final Map<String, Dom> domMap;
    private final Set<String> domNames;

    private boolean activated = false;

    public SouffleAnalysis(String name, String filename) throws IOException {
        this.name = name;
        dlogFile = new File(filename);
        Path analysisDir = SouffleRuntime.g().loadDlog(name, dlogFile, false);
        souffleProgram = SwigInterface.newInstance(name);
        factDir = Files.createDirectories(analysisDir.resolve("fact"));
        outDir = Files.createDirectories(analysisDir.resolve("out"));
        relSignMap = new HashMap<>();
        domNames = new HashSet<>();
        for (String souffleSign : souffleProgram.getRelSigns()) {
            int idx = souffleSign.indexOf('<');
            String relName = souffleSign.substring(0, idx);
            String[] relAttrs = souffleSign.substring(idx + 1, souffleSign.length() - 1).split((","));
            for (int i = 0; i < relAttrs.length; ++i) {
                int colonIdx = relAttrs[i].indexOf(':');
                if (!relAttrs[i].substring(0, colonIdx).equals("s")) {
                    Messages.warn("SouffleAnalysis: Non-symbol type attribute in <rel: %s>%d: %s", relName, i, relAttrs[i]);
                }
                // keep subtype name only
                relAttrs[i] = relAttrs[i].substring(colonIdx + 1);
                domNames.add(relAttrs[i]);
            }
            RelSign relSign = AnalysesUtil.genDefaultRelSign(relAttrs);
            relSignMap.put(relName, relSign);
        }
        domMap = new HashMap<>();
    }

    public void run() {
        if (souffleProgram == null) {
            Messages.fatal("SouffleAnalysis: souffle analysis should be loaded before running");
        }
        souffleProgram.loadAll(factDir.toString());
        souffleProgram.run();
        souffleProgram.printAll(outDir.toString());
        activated = true;
    }

    protected void setConsumerMap() {
        for (String domName: domNames) {
            TrgtInfo domInfo = new TrgtInfo(ProgramDom.class, name, null);
            consumerMap.put(domName, new Pair<>(domInfo, dom -> domMap.put(domName, (ProgramDom) dom)));
        }
        for (String relName : souffleProgram.getInputRelNames()) {
            RelSign relSign = relSignMap.get(relName);
            TrgtInfo relInfo = new TrgtInfo(ProgramRel.class, name, relSign);
            consumerMap.put(relName, new Pair<>(relInfo, rel -> dumpRel((ProgramRel) rel)));
        }
    }

    protected void setProducerMap() {
        for (String relName : souffleProgram.getOutputRelNames()) {
            RelSign relSign = relSignMap.get(relName);
            TrgtInfo relInfo = new TrgtInfo(ProgramRel.class, name, relSign);
            producerMap.put(relName, new Pair<>(relInfo, () -> loadRel(relName)));
        }
    }

    private ProgramRel loadRel(String relName) {
        ProgramRel rel = new ProgramRel();
        rel.setName(name);

        RelSign relSign = relSignMap.get(relName);
        rel.setSign(relSign);

        String[] domNames = relSign.getDomNames();
        int domNum = domNames.length;
        Dom[] doms = new Dom[domNum];
        for (int i = 0; i < doms.length; ++i) {
            String domKind = Utils.trimNumSuffix(domNames[i]);
            doms[i] = domMap.get(domKind);
        }
        rel.setDoms(doms);

        rel.zero();
        Path outPath = outDir.resolve(relName+".csv");
        try {
            List<String> lines = Files.readAllLines(outPath);
            for (String line : lines) {
                String[] tuple = line.split("\t");
                int[] indexes = new int[domNum];
                for (int i = 0; i < domNum; ++i) {
                    indexes[i] = Integer.parseInt(tuple[i]);
                }
                rel.add(indexes);
            }
        } catch (IOException e) {
            Messages.error("SouffleAnalysis %s: failed to load relation %s", name, relName);
        }
        rel.close();
        return rel;
    }

    private void dumpRel(ProgramRel rel) {
        Path factPath = factDir.resolve(rel.getName()+".facts");
        try {
            List<String> lines = new ArrayList<>();
            Rel.AryNIterable tuples = rel.getAryNValTuples();
            int domNum = rel.getDoms().length;
            for (Object[] tuple: tuples) {
                String s = "";
                for (int i = 0; i < domNum; ++i) {
                    Object element = tuple[i];
                    int id = rel.getDoms()[i].indexOf(element);
                    //s += rel.getDoms()[i].toUniqueString(element);
                    s += id;
                    if (i < domNum - 1) {
                        s += "\t";
                    }
                }
                lines.add(s);
            }
            Files.write(factPath, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Messages.error("SouffleAnalysis %s: failed to dump relation %s", name, rel.getName());
            Messages.fatal(e);
        }
    }

    // TODO: 3/ provenance() repeat step 1.1~1.3 with provenance option
    // TODO:    3+/ swig/SwigInterface.h has c++ implementation of provenance generation, but it does nothing without provenance option
}
