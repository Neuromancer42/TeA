package com.neuromancer42.tea.jsouffle;

import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.commons.provenance.AbstractProvenanceBuilder;
import com.neuromancer42.tea.commons.provenance.ConstraintItem;
import com.neuromancer42.tea.commons.provenance.Provenance;
import com.neuromancer42.tea.commons.provenance.RawTuple;
import com.neuromancer42.tea.jsouffle.swig.SWIGSouffleProgram;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class SouffleAnalysis {
    private final String name;
    private SWIGSouffleProgram souffleProgram;

    private final SouffleProvenanceBuilder provBuilder;

    private final String analysis; // field for debug; different analysis instance may refer to the same analysis program
    private final Path analysisPath;
    private final Path factDir;
    private final Path outDir;
    private final Set<String> domNames;
    private final List<String> inputRelNames;
    private final List<String> outputRelNames;
    private final Map<String, String[]> relSignMap;

    private boolean activated = false;

    SouffleAnalysis(String name, String analysis, Path path, SWIGSouffleProgram program, SWIGSouffleProgram provProgram) {
        this.name = name;
        this.analysis = analysis;
        souffleProgram = program;
        // TODO: change analysis directory?
        analysisPath = path;
        Path tmpFactDir = null;
        Path tmpOutDir = null;
        try {
            tmpFactDir = Files.createDirectories(analysisPath.resolve("fact"));
            tmpOutDir = Files.createDirectories(analysisPath.resolve("out"));
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
        domNames = new LinkedHashSet<>();
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
                domNames.add(domName);
            }
            relSignMap.put(relName, relAttrs);
        }

        if (provProgram != null) {
            provBuilder = new SouffleProvenanceBuilder(provProgram);
        } else {
            provBuilder = null;
        }
    }

    public String getName() {
        return name;
    }

    public String[] getAllDomKinds() {
        return domNames.toArray(new String[0]);
    }

    public Map<String, String[]> getInputRels() {
        Map<String, String[]> inputRelSignMap = new LinkedHashMap<>();
        for (String inputRel : inputRelNames) {
            inputRelSignMap.put(inputRel, relSignMap.get(inputRel));
        }
        return inputRelSignMap;
    }

    public Map<String, String[]> getOutputRels() {
        Map<String, String[]> outputRelSignMap = new LinkedHashMap<>();
        for (String outputRel : outputRelNames) {
            outputRelSignMap.put(outputRel, relSignMap.get(outputRel));
        }
        return outputRelSignMap;
    }


    public String[] translateTuple(String relName, int[] indices) {
        assert activated;
        String[] domKinds = relSignMap.get(relName);
        String[] translated = new String[domKinds.length];
        for (int i = 0; i < translated.length; ++i) {
            translated[i] = doms.get(domKinds[i]).get(indices[i]);
        }
        return translated;
    }

    public Path getAnalysisPath() {
        return analysisPath;
    }

    public Path getFactDir() {
        return factDir;
    }

    public Path getOutDir() {
        return outDir;
    }

    private final Map<String, ProgramDom> doms = new LinkedHashMap<>();
    private final Map<String, ProgramRel> producedRels = new LinkedHashMap<>();

    public Collection<ProgramRel> run(Map<String, ProgramDom> domMap, Map<String, ProgramRel> inputRelMap) {
        for (String domName : domNames) {
            doms.put(domName, domMap.get(domName));
        }
        for (String relName : inputRelNames) {
            dumpRel(relName, inputRelMap.get(relName));
        }
        activate();
        close();
        for (String relName : outputRelNames) {
            producedRels.put(relName, loadRel(relName));
        }
        return producedRels.values();
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

    private void dumpRel(String relName, ProgramRel rel) {
        assert relName.equals(rel.getName());
        Path factPath = factDir.resolve(relName+".facts");
        Messages.debug("SouffleAnalysis: dumping facts to path %s", factPath.toAbsolutePath());
        try {
            rel.load();
            List<String> lines = new ArrayList<>();
            Iterable<int[]> tuples = rel.getIntTuples();
            int domNum = rel.getDoms().length;
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
            rel.close();
        } catch (IOException e) {
            Messages.error("SouffleAnalysis %s: failed to dump relation %s", name, relName);
            Messages.fatal(e);
        }
    }

    private ProgramRel loadRel(String relName) {
        if (!activated) {
            Messages.fatal("SouffleAnalysis %s: souffle program has not been activated before loading <rel %s>", name, relName);
        }

        String[] domKinds = relSignMap.get(relName);
        int domNum = domKinds.length;
        ProgramDom[] relDoms = new ProgramDom[domNum];
        for (int i = 0; i < domKinds.length; ++i) {
            relDoms[i] = doms.get(domKinds[i]);
        }

        ProgramRel rel = new ProgramRel(relName, relDoms);

        rel.init();
        Path outPath = outDir.resolve(relName+".csv");
        Messages.debug("SouffleAnalysis: loading facts from path %s", outPath.toAbsolutePath());
        List<int[]> table = loadTableFromFile(outPath);
        for (int[] row: table) {
            rel.add(row);
        }

        rel.save(analysisPath.toString());
        rel.close();

        return rel;
    }

    static List<int[]> loadTableFromFile(Path outPath) {
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
            Messages.fatal(e);
        }
        return table;
    }

    private class SouffleProvenanceBuilder extends AbstractProvenanceBuilder {
        private SWIGSouffleProgram provenanceProgram;
        private final Path provenanceDir;

        private boolean provActivated;

        private List<String> ruleInfos;
        private List<ConstraintItem> constraintItems;
        private List<RawTuple> inputTuples;
        private List<RawTuple> outputTuples;

        public SouffleProvenanceBuilder(SWIGSouffleProgram provProgram) {
            // Souffle's provenance has been pruned, not need to use prune in AbstractProvenanceBuilder again
            super(SouffleAnalysis.this.name, false);
            provenanceProgram = provProgram;
            Path tmpProvDir = null;
            try {
                tmpProvDir = Files.createDirectories(analysisPath.resolve("provenance"));
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
                List<int[]> table = loadTableFromFile(factPath);
                for (int[] row : table) {
                    inputTuples.add(new RawTuple(relName, row));
                }
            }

            // 2. fetch outputTuples
            outputTuples = new ArrayList<>();
            for (String relName : outputRelNames) {
                Path outPath = outDir.resolve(relName + ".csv");
                List<int[]> table = loadTableFromFile(outPath);
                for (int[] row : table) {
                    outputTuples.add(new RawTuple(relName, row));
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
            RawTuple headTuple = new RawTuple(headAtom);

            List<RawTuple> bodyTuples = new ArrayList<>();
            List<Boolean> bodySigns = new ArrayList<>();
            for (int i = 1; i < atoms.length - 1; ++i) {
                String bodyAtom = atoms[i];
                boolean bodySign = true;
                if (bodyAtom.charAt(0) == '!' && !bodyAtom.substring(0, bodyAtom.indexOf('(')).equals("!=")) {
                    bodyAtom = bodyAtom.substring(1);
                    bodySign = false;
                }
                bodyTuples.add(new RawTuple(bodyAtom));
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

        public List<String> getRuleInfos() {
            if (!provActivated) {
                activate();
            }
            return ruleInfos;
        }

        public Collection<ConstraintItem> getAllConstraintItems() {
            if (!provActivated) {
                activate();
            }
            return constraintItems;
        }

        public Collection<RawTuple> getInputTuples() {
            if (!provActivated) {
                activate();
            }
            return inputTuples;
        }

        public Collection<RawTuple> getOutputTuples() {
            if (!provActivated) {
                activate();
            }
            return outputTuples;
        }
    }
}