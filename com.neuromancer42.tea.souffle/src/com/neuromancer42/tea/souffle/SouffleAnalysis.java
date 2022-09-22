package com.neuromancer42.tea.souffle;

import com.neuromancer42.tea.core.analyses.*;
import com.neuromancer42.tea.core.bddbddb.Dom;
import com.neuromancer42.tea.core.bddbddb.RelSign;
import com.neuromancer42.tea.core.project.*;
import com.neuromancer42.tea.core.provenance.AbstractProvenanceBuilder;
import com.neuromancer42.tea.core.provenance.ConstraintItem;
import com.neuromancer42.tea.core.provenance.Provenance;
import com.neuromancer42.tea.core.provenance.Tuple;
import com.neuromancer42.tea.core.util.Utils;
import com.neuromancer42.tea.souffle.swig.SWIGSouffleProgram;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public final class SouffleAnalysis extends JavaAnalysis {
    private SWIGSouffleProgram souffleProgram;

    private final SouffleProvenanceBuilder provBuilder;

    private final String analysis; // field for debug; different analysis instance may refer to the same analysis program
    private final Path analysisDir;
    private final Path factDir;
    private final Path outDir;
    private final List<String> inputRelNames;
    private final List<String> outputRelNames;
    private final Map<String, RelSign> relSignMap;
    private final Map<String, SouffleRelTrgt> inputRelTrgts;
    private final Map<String, SouffleRelTrgt> outputRelTrgts;
    private final Map<String, Trgt<ProgramDom<Object>>> domTrgtMap;

    private boolean activated = false;

    SouffleAnalysis(String name, String analysis, SWIGSouffleProgram program, SWIGSouffleProgram provProgram) {
        this.name = name;
        this.analysis = analysis;
        souffleProgram = program;
        // TODO: change analysis directory?
        analysisDir = Paths.get(Config.v().souffleWorkDirName).resolve(name);
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
        domTrgtMap = new HashMap<>();
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
                String domName = relAttrs[i];
                Trgt<ProgramDom<Object>> domTrgt = AnalysesUtil.createDomTrgt(name, domName, Object.class);
                domTrgtMap.put(domName, domTrgt);
                registerConsumer(domTrgt);
            }
            RelSign relSign = ProgramRel.genDefaultRelSign(relAttrs);
            relSignMap.put(relName, relSign);
        }
        inputRelTrgts = new HashMap<>();
        for (String relName: inputRelNames) {
            RelSign relSign = relSignMap.get(relName);
            SouffleRelTrgt relTrgt = createSouffleRelTrgt(relName, relSign, name);
            inputRelTrgts.put(relName, relTrgt);
            registerConsumer(relTrgt);
        }
        outputRelTrgts = new HashMap<>();
        for (String relName: outputRelNames) {
            RelSign relSign = relSignMap.get(relName);
            SouffleRelTrgt relTrgt = createSouffleRelTrgt(relName, relSign, name);
            outputRelTrgts.put(relName, relTrgt);
            registerProducer(relTrgt);
        }

        if (provProgram != null) {
            provBuilder = new SouffleProvenanceBuilder(provProgram);
        } else {
            provBuilder = null;
        }
    }

    private SouffleRelTrgt createSouffleRelTrgt(String relName, RelSign relSign, String location) {
        RelInfo relInfo = new RelInfo(location, relSign);
        return new SouffleRelTrgt(relName, relInfo);
    }

    public ProgramRel getOutputRel(String relName) {
        SouffleRelTrgt relTrgt = outputRelTrgts.get(relName);
        return relTrgt.get();
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
        activate();
        close();
    }

    public void activate() {
        if (activated) {
            Messages.warn("SouffleAnalysis %s: the analysis has been activated before, are you sure to re-run?", name);
        }
        if (souffleProgram == null) {
            Messages.fatal("SouffleAnalysis %s: souffle analysis has been closed", name);
        }
        souffleProgram.loadAll(factDir.toString());
        souffleProgram.run();
        souffleProgram.printAll(outDir.toString());

        activated = true;
    }

    public void close() {
        if (!activated) {
            Messages.warn("SouffleAnalysis %s: close souffle analysis before running it", name);
        }
        if (souffleProgram == null) {
            Messages.warn("SouffleAnalysis %s: re-close the souffle analysis", name);
        } else {
            Messages.debug("SouffleAnalyusis %s: freeing souffle program %s", name, souffleProgram);
            souffleProgram.delete();
            souffleProgram = null;
        }
    }

    public Provenance getProvenance() {
        if (provBuilder == null) {
            Messages.fatal("SouffeProvenanceBuilder %s: provenance program has not been built for this analysis", name);
            assert false;
        }
        return provBuilder.getProvenance();
    }

    private class SouffleProvenanceBuilder extends AbstractProvenanceBuilder {
        private SWIGSouffleProgram provenanceProgram;
        private final Path provenanceDir;

        private boolean provActivated;

        private List<String> ruleInfos;
        private List<ConstraintItem> constraintItems;
        private List<Tuple> inputTuples;
        private List<Tuple> outputTuples;

        public SouffleProvenanceBuilder(SWIGSouffleProgram provProgram) {
            // Souffle's provenance has been pruned, not need to use prune in AbstractProvenanceBuilder again
            super(SouffleAnalysis.this.name, false);
            provenanceProgram = provProgram;
            Path tmpProvDir = null;
            try {
                tmpProvDir = Files.createDirectories(analysisDir.resolve("provenance"));
            } catch(IOException e){
                Messages.error("SouffleProvenanceBuilder %s: failed to create provenance directory", name);
                Messages.fatal(e);
            }
            provenanceDir = tmpProvDir;
        }

        private void activate() {
            if (!activated) {
                Messages.fatal("SouffleProvenanceBuilder %s: the analysis should be activated before building provenance", name);
            }
            // 1. fetch inputTuples
            inputTuples = new ArrayList<>();
            for (String relName : inputRelNames) {
                Path factPath = factDir.resolve(relName + ".facts");
                List<int[]> table = SouffleRuntime.loadTableFromFile(factPath);
                for (int[] row : table) {
                    inputTuples.add(new Tuple(relName, row));
                }
            }

            // 2. fetch outputTuples
            outputTuples = new ArrayList<>();
            for (String relName : outputRelNames) {
                Path outPath = outDir.resolve(relName + ".csv");
                List<int[]> table = SouffleRuntime.loadTableFromFile(outPath);
                for (int[] row : table) {
                    outputTuples.add(new Tuple(relName, row));
                }
            }

            // 3. fetch ruleInfos
            ruleInfos = new ArrayList<>();
            ruleInfos.addAll(provenanceProgram.getInfoRelNames());

            if (provActivated) {
                Messages.warn("SouffleProvenanceBuilder %s: the provenance has been activated before, are you sure to re-run?", name);
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
                Messages.error("SouffleProvenanceBuilder %s: failed to read constraint items from file %s", name, consFilePath.toString());
                Messages.fatal(e);
            }
            constraintItems = new ArrayList<>();
            assert consLines != null;
            for (String line : consLines) {
                ConstraintItem constraintItem = decodeConstraintItem(line);
                constraintItems.add(constraintItem);
            }
            provActivated = true;

            Messages.debug("SouffleProvenanceBuilder %s: freeing souffle program %s", name, provenanceProgram);
            provenanceProgram.delete();
            provenanceProgram = null;
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
                Messages.fatal("SouffleProvenanceBuilder %s: wrong rule info recorded - %s", name, ruleInfo);
            }
            ruleInfo = ruleInfo.substring(1);
            int ruleId = ruleInfos.indexOf(ruleInfo);
            return new ConstraintItem(ruleId, headTuple, bodyTuples, headSign, bodySigns);
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

    private class SouffleRelTrgt extends Trgt<ProgramRel> {
        private SouffleRelTrgt(String name, TrgtInfo info) {
            super(name, info);
        }

        @Override
        public ProgramRel get() {
            if (val == null) {
                loadRel();
            }
            return super.get();
        }

        @Override
        public <SubT extends ProgramRel> void accept(SubT v) {
            super.accept(v);
            dumpRel();
        }

        private void dumpRel() {
            Path factPath = factDir.resolve(val.getName()+".facts");
            Messages.debug("SouffleAnalysis: dumping facts to path %s", factPath.toAbsolutePath());
            try {
                List<String> lines = new ArrayList<>();
                val.load();
                Iterable<int[]> tuples = val.getIntTuples();
                int domNum = val.getDoms().length;
                for (int[] tuple: tuples) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < domNum; ++i) {
                        int id = tuple[i];
                        //s += rel.getDoms()[i].toUniqueString(element);
                        sb.append(id);
                        if (i < domNum - 1) {
                            sb.append("\t");
                        }
                    }
                    lines.add(sb.toString());
                }
                Files.write(factPath, lines, StandardCharsets.UTF_8);
                val.close();
            } catch (IOException e) {
                Messages.error("SouffleAnalysis %s: failed to dump relation %s", name, val.getName());
                Messages.fatal(e);
            }
        }

        private void loadRel() {
            String relName = super.getName();

            if (!activated) {
                Messages.fatal("SouffleAnalysis %s: souffle program has not been activated before loading <rel %s>", name, relName);
            }

            RelSign relSign = relSignMap.get(relName);


            String[] domNames = relSign.getDomNames();
            int domNum = domNames.length;
            Dom<?>[] doms = new Dom[domNum];
            for (int i = 0; i < doms.length; ++i) {
                String domKind = Utils.trimNumSuffix(domNames[i]);
                doms[i] = domTrgtMap.get(domKind).get();
            }

            val = new ProgramRel(relName, doms, relSign);

            val.init();
            Path outPath = outDir.resolve(relName+".csv");
            Messages.debug("SouffleAnalysis: loading facts from path %s", outPath.toAbsolutePath());
            List<int[]> table = SouffleRuntime.loadTableFromFile(outPath);
            for (int[] row: table) {
                val.add(row);
            }
            val.save();
            val.close();
        }

    }
}
