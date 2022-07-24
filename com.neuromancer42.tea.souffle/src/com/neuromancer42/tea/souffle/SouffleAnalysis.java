package com.neuromancer42.tea.souffle;

import com.neuromancer42.tea.core.analyses.*;
import com.neuromancer42.tea.core.bddbddb.Dom;
import com.neuromancer42.tea.core.bddbddb.Rel;
import com.neuromancer42.tea.core.bddbddb.RelSign;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.provenance.AbstractProvenanceBuilder;
import com.neuromancer42.tea.core.provenance.ConstraintItem;
import com.neuromancer42.tea.core.provenance.Provenance;
import com.neuromancer42.tea.core.provenance.Tuple;
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

    private final Path analysisDir;
    private final Path factDir;
    private final Path outDir;
    private final List<String> inputRelNames;
    private final List<String> outputRelNames;
    private final Map<String, RelSign> relSignMap;
    private final Map<String, Dom> domMap;
    private final Set<String> domNames;

    private boolean activated = false;

    public SouffleAnalysis(String name, String filename) {
        this.name = name;
        dlogFile = new File(filename);
        SouffleRuntime.g().loadDlog(name, dlogFile, false);
        souffleProgram = SwigInterface.newInstance(name);
        if (souffleProgram == null ) {
            Messages.fatal("SouffleAnalysis %s: failed to load souffle program", name);
        }
        // TODO: change analysis directory?
        analysisDir = SouffleRuntime.g().getWorkDir().resolve(name);
        Path tmpFactDir = null;
        Path tmpOutDir = null;
        try {
            tmpFactDir = Files.createDirectories(analysisDir.resolve("fact"));
            tmpOutDir = Files.createDirectories(analysisDir.resolve("out"));
        } catch (IOException e) {
            Messages.error("SouffleAnalysis %s: failed to create I/O directory", name);
            Messages.fatal(e);
        }
        factDir = tmpFactDir;
        outDir = tmpOutDir;
        inputRelNames = new ArrayList<>();
        inputRelNames.addAll(souffleProgram.getInputRelNames());
        outputRelNames = new ArrayList<>();
        outputRelNames.addAll(souffleProgram.getOutputRelNames());
        relSignMap = new HashMap<>();
        domNames = new HashSet<>();
        for (String souffleSign : souffleProgram.getRelSigns()) {
            int idx = souffleSign.indexOf('<');
            String relName = souffleSign.substring(0, idx);
            String[] relAttrs = souffleSign.substring(idx + 1, souffleSign.length() - 1).split((","));
            for (int i = 0; i < relAttrs.length; ++i) {
                int colonIdx = relAttrs[i].indexOf(':');
                // assume all domains are encoded as `unsigned`
                if (!relAttrs[i].substring(0, colonIdx).equals("u")) {
                    Messages.warn("SouffleAnalysis %s: Non-symbol type attribute in <rel: %s>%d: %s", name, relName, i, relAttrs[i]);
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

    public Path getAnalysisDir() {
        return analysisDir;
    }

    public Path getFactDir() {
        return factDir;
    }

    public Path getOutDir() {
        return outDir;
    }


    public void run() {
        if (souffleProgram == null) {
            Messages.fatal("SouffleAnalysis %s: souffle analysis should be loaded before running", name);
        }
        if (activated) {
            Messages.warn("SouffleAnalysis %s: the analysis has been activated before, are you sure to re-run?", name);
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
        for (String relName : inputRelNames) {
            RelSign relSign = relSignMap.get(relName);
            TrgtInfo relInfo = new TrgtInfo(ProgramRel.class, name, relSign);
            consumerMap.put(relName, new Pair<>(relInfo, rel -> dumpRel((ProgramRel) rel)));
        }
    }

    protected void setProducerMap() {
        for (String relName : outputRelNames) {
            RelSign relSign = relSignMap.get(relName);
            TrgtInfo relInfo = new TrgtInfo(ProgramRel.class, name, relSign);
            producerMap.put(relName, new Pair<>(relInfo, () -> loadRel(relName)));
        }
    }

    private ProgramRel loadRel(String relName) {
        if (!activated) {
            Messages.fatal("SouffleAnalysis %s: souffle program has not been activated before loading <rel %s>", name, relName);
        }
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
        List<int[]> table = loadTableFromFile(outPath);
        for (int[] row: table) {
            rel.add(row);
        }
        rel.close();
        return rel;
    }

    private static List<int[]> loadTableFromFile(Path outPath) {
        List<int[]> table = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(outPath);
            for (String line : lines) {
                String[] tuple = line.split("\t");
                int width = tuple.length;
                int[] indexes = new int[width];
                for (int i = 0; i < width; ++i) {
                    indexes[i] = Integer.parseInt(tuple[i]);
                }
                table.add(indexes);
            }
        } catch (IOException e) {
            Messages.error("SouffleAnalysis: failed to read table from %s", outPath.toString());
        }
        return table;
    }

    private void dumpRel(ProgramRel rel) {
        Path factPath = factDir.resolve(rel.getName()+".facts");
        try {
            List<String> lines = new ArrayList<>();
            Rel.AryNIterable tuples = rel.getAryNValTuples();
            int domNum = rel.getDoms().length;
            for (Object[] tuple: tuples) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < domNum; ++i) {
                    Object element = tuple[i];
                    int id = rel.getDoms()[i].indexOf(element);
                    //s += rel.getDoms()[i].toUniqueString(element);
                    sb.append(id);
                    if (i < domNum - 1) {
                        sb.append("\t");
                    }
                }
                lines.add(sb.toString());
            }
            Files.write(factPath, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Messages.error("SouffleAnalysis %s: failed to dump relation %s", name, rel.getName());
            Messages.fatal(e);
        }
    }

    private SouffleProvenanceBuilder provBuilder;

    private Path provenanceDir;

    public Path getProvenanceDir() {
        return provenanceDir;
    }

    public Provenance getProvenance() {
        if (provBuilder == null) {
            try {
                provenanceDir = Files.createDirectories(analysisDir.resolve("provenance"));
            } catch(IOException e){
                Messages.error("SouffleAnalysis %s: failed to create provenance directory", name);
                Messages.fatal(e);
            }
            provBuilder = new SouffleProvenanceBuilder();
        }
        return provBuilder.getProvenance();
    }

    private class SouffleProvenanceBuilder extends AbstractProvenanceBuilder {
        private final SWIGSouffleProgram provenanceProgram;
        private final String provenanceProcess;

        private boolean provActivated;

        private List<String> ruleInfos;
        private List<ConstraintItem> constraintItems;
        private List<Tuple> inputTuples;
        private List<Tuple> outputTuples;

        private void activate() {
            if (!activated) {
                Messages.fatal("SouffleProvenanceBuilder %s: the analysis %s should be activated before building provenance", provenanceProcess, SouffleAnalysis.this.name);
            }
            // 1. fetch inputTuples
            inputTuples = new ArrayList<>();
            for (String relName : inputRelNames) {
                Path factPath = factDir.resolve(relName + ".facts");
                List<int[]> table = loadTableFromFile(factPath);
                for (int[] row : table) {
                    inputTuples.add(new Tuple(relName, row));
                }
            }

            // 2. fetch outputTuples
            outputTuples = new ArrayList<>();
            for (String relName : outputRelNames) {
                Path outPath = outDir.resolve(relName + ".csv");
                List<int[]> table = loadTableFromFile(outPath);
                for (int[] row : table) {
                    outputTuples.add(new Tuple(relName, row));
                }
            }

            // 3. fetch ruleInfos
            ruleInfos = new ArrayList<>();
            ruleInfos.addAll(provenanceProgram.getInfoRelNames());

            if (provActivated) {
                Messages.warn("SouffleProvenanceBuilder %s: the provenance has been activated before, are you sure to re-run?", provenanceProcess);
            }
            provenanceProgram.loadAll(factDir.toString());
            provenanceProgram.run();
            provenanceProgram.printProvenance(provenanceDir.toString());
            // 4. fetch constraintItems
            Path consFilePath = provenanceDir.resolve("cons_all.txt");
            List<String> consLines = null;
            try {
                consLines = Files.readAllLines(consFilePath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                Messages.error("SouffleProvenanceBuilder %s: failed to read constraint items from file %s", provenanceProcess, consFilePath.toString());
                Messages.fatal(e);
            }
            constraintItems = new ArrayList<>();
            for (String line : consLines) {
                ConstraintItem constraintItem = decodeConstraintItem(line);
                constraintItems.add(constraintItem);
            }
            provActivated = true;
        }

        private ConstraintItem decodeConstraintItem(String line) {
            String[] atoms = line.split("\t");
            String headAtom = atoms[0];
            boolean headSign = true;
            if (headAtom.charAt(0) == '!' && !headAtom.substring(0, headAtom.indexOf('(')).equals("!=")) {
                headAtom = headAtom.substring(1);
                headSign = false;
            }
            Tuple headTuple = new Tuple(headAtom);

            List<Tuple> bodyTuples = new ArrayList<>();
            List<Boolean> bodySigns = new ArrayList<>();
            for (int i = 1; i < atoms.length - 1; ++i) {
                String bodyAtom = atoms[i];
                boolean bodySign = true;
                if (bodyAtom.charAt(0) == '!' && !bodyAtom.substring(0, bodyAtom.indexOf('(')).equals("!=")) {
                    bodyAtom = bodyAtom.substring(1);
                    bodySign = false;
                }
                bodyTuples.add(new Tuple(bodyAtom));
                bodySigns.add(bodySign);
            }

            String ruleInfo = atoms[atoms.length - 1];
            if (ruleInfo.charAt(0) != '#') {
                Messages.fatal("SouffleProvenanceBuilder %s: wrong rule info recorded - %s", provenanceProcess, ruleInfo);
            }
            ruleInfo = ruleInfo.substring(1);
            int ruleId = ruleInfos.indexOf(ruleInfo);
            return new ConstraintItem(ruleId, headTuple, bodyTuples, headSign, bodySigns);
        }

        public SouffleProvenanceBuilder() {
            // Souffle's provenance has been pruned, not need to use prune in AbstractProvenanceBuilder again
            super(SouffleAnalysis.this.name, false);
            provenanceProcess = SouffleAnalysis.this.name + "_w_P";
            SouffleRuntime.g().loadDlog(provenanceProcess, dlogFile, true);
            provenanceProgram = SwigInterface.newInstance(provenanceProcess);
        }


        @Override
        public List<String> getRuleInfos() {
            if (!provActivated) {
                activate();
            }
            return ruleInfos;
        }

        @Override
        public Collection<ConstraintItem> getAllConstraintItems() {
            if (!provActivated) {
                activate();
            }
            return constraintItems;
        }

        @Override
        public Collection<Tuple> getInputTuples() {
            if (!provActivated) {
                activate();
            }
            return inputTuples;
        }

        @Override
        public Collection<Tuple> getOutputTuples() {
            if (!provActivated) {
                activate();
            }
            return outputTuples;
        }
    }
}
