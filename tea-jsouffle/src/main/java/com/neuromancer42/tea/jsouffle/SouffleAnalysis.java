package com.neuromancer42.tea.jsouffle;

import com.google.protobuf.TextFormat;
import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.core.analysis.Trgt;
import com.neuromancer42.tea.jsouffle.swig.SWIGSouffleProgram;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class SouffleAnalysis {
    private final String name;
    private final Path proofDir;
    private SWIGSouffleProgram souffleProgram;
    private SWIGSouffleProgram proverProgram;

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
        proverProgram = provProgram;
        Path tmpProofDir = null;
        if (provProgram != null) {
            try {
                tmpProofDir = Files.createDirectories(analysisPath.resolve("provenance"));
            } catch (IOException e) {
                Messages.error("SouffleAnalysis %s: failed to create provenance directory", name);
                Messages.fatal(e);
            }
        }
        proofDir = tmpProofDir;
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

    public Path getAnalysisPath() {
        return analysisPath;
    }

    public Path getFactDir() {
        return factDir;
    }

    public Path getOutDir() {
        return outDir;
    }

    public Path getProofDir() {
        return proofDir;
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

    public List<Trgt.Constraint> prove(Collection<Trgt.Tuple> targets) {
        // 0. filter targets and activate prover program
        List<Trgt.Tuple> outputs = new ArrayList<>();
        {
            List<String> lines = new ArrayList<>();
            for (Trgt.Tuple target : targets) {
                String relName = target.getRelName();
                if (outputRelNames.contains(relName)) {
                    outputs.add(target);
                    StringBuilder lb = new StringBuilder();
                    lb.append(relName);
                    int[] intTuple = encodeIndices(relName, target.getAttributeList().toArray(new String[0]));
                    assert intTuple != null;
                    for (int idx : intTuple) {
                        lb.append("\t").append(idx);
                    }
                    lines.add(lb.toString());
                }
            }
            try {
                Files.write(proofDir.resolve("targets.list"), lines, StandardCharsets.UTF_8);
            } catch (IOException ioException) {
                Messages.error("SouffleAnalysis %s: failed to dump target tuples: %s", name, ioException.getMessage());
            }
        }
        Messages.log("SouffleAnalysis %s: activate provenance program to prove %d targets", name, outputs.size());
        activateProver(proofDir);

        // 3. fetch ruleInfos
        List<String> ruleInfos = new ArrayList<>(proverProgram.getInfoRelNames());

        // 4. fetch constraintItems
        Path consFilePath = proofDir.resolve("cons_all.txt");
        List<String> consLines = null;
        try {
            consLines = Files.readAllLines(consFilePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Messages.error("SouffleAnalysis %s: failed to read constraint items from provenance file %s", name, consFilePath.toString());
            Messages.fatal(e);
        }
        assert consLines != null;
        Map<Trgt.Tuple, List<Trgt.Constraint>> clauseMap = new LinkedHashMap<>();
        for (String line : consLines) {
            Trgt.Constraint constraint = decodeConstraint(line);
            clauseMap.computeIfAbsent(constraint.getHeadTuple(), k -> new ArrayList<>()).add(constraint);
        }

        ProvenanceBuilder provBuilder = new ProvenanceBuilder(clauseMap, ruleInfos);
        Messages.log("SouffleAnalysis %s: provenance finished, freeing prover program", name);
        proverProgram = null;

        return provBuilder.prove(targets);
    }

    public void activateProver(Path proofPath) {
        if (!activated) {
            Messages.fatal("SouffleAnalysis %s: the analysis should be activated before building provenance", name);
            assert false;
        }
        if (proverProgram == null) {
            Messages.fatal("SouffleAnalysis %s: provenance program has not been built for this analysis", name);
            assert false;
        }
        proverProgram.loadAll(factDir.toString());
        proverProgram.run();
        proverProgram.printProvenance(proofPath.toString());
    }

    public String[] decodeIndices(String relName, int[] indices) {
        assert activated;
        String[] domKinds = relSignMap.get(relName);
        if (domKinds == null) return null;
        String[] attributes = new String[domKinds.length];
        for (int i = 0; i < attributes.length; ++i) {
            ProgramDom dom = doms.get(domKinds[i]);
            if (indices[i] >= dom.size()) {
                Messages.fatal("SouffleAnalysis %s: index %d out of bound of dom %s", name, indices[i], domKinds[i]);
                assert false;
            }
            attributes[i] = dom.get(indices[i]);
        }
        return attributes;
    }

    private int[] encodeIndices(String relName, String[] attributes) {
        assert activated;
        String[] domKinds = relSignMap.get(relName);
        if (domKinds == null) return null;
        int[] domIndices = new int[domKinds.length];
        for (int i = 0; i < domKinds.length; ++i) {
            ProgramDom dom = doms.get(domKinds[i]);
            domIndices[i] = dom.indexOf(attributes[i]);
            if (domIndices[i] < 0) {
                Messages.fatal("SouffleAnalysis %s: dom %s contains no element %s", getName(), domKinds[i], attributes[i]);
                assert false;
            }
        }
        return domIndices;
    }

    private Trgt.Constraint decodeConstraint(String line) {
        Trgt.Constraint.Builder constrBuilder = Trgt.Constraint.newBuilder();
        String[] atoms = line.split("\t");
        String headAtom = atoms[0];
        boolean headSign = true;
        if (headAtom.charAt(0) == '!' && !headAtom.substring(0, headAtom.indexOf('(')).equals("!=")) {
            headAtom = headAtom.substring(1);
            headSign = false;
        }
        Trgt.Tuple headTuple = decodeTuple(headAtom);
        constrBuilder.setHeadTuple(headTuple);

        for (int i = 1; i < atoms.length - 1; ++i) {
            String bodyAtom = atoms[i];
            boolean bodySign = true;
            if (bodyAtom.charAt(0) == '!' && !bodyAtom.substring(0, bodyAtom.indexOf('(')).equals("!=")) {
                bodyAtom = bodyAtom.substring(1);
                bodySign = false;
            }
            constrBuilder.addBodyTuple(decodeTuple(bodyAtom));
        }

        String ruleInfo = atoms[atoms.length - 1];
        if (ruleInfo.charAt(0) != '#') {
            Messages.fatal("SouffleAnalysis %s: wrong rule info recorded - %s", name, ruleInfo);
        }
        ruleInfo = ruleInfo.substring(1);
        constrBuilder.setRuleInfo(ruleInfo);

        return constrBuilder.build();
    }

    private Trgt.Tuple decodeTuple(String s) {
        String[] splits1 = s.split("\\(");
        String relName = splits1[0];
        String indexString = splits1[1].replace(")", "");
        String[] splits2 = indexString.split(",");
        int[] domIndices = new int[splits2.length];
        for (int i = 0; i < splits2.length; i++) {
            domIndices[i] = Integer.parseInt(splits2[i]);
        }
        String[] attributes = decodeIndices(relName, domIndices);
        if (attributes == null) {
            Messages.fatal("SouffleAnalysis %s: tuple %s cannot be parsed", s);
            assert false;
        }
        return Trgt.Tuple.newBuilder().setRelName(relName).addAllAttribute(List.of(attributes)).build();
    }

    private class ProvenanceBuilder {
        private final Map<Trgt.Tuple, List<Trgt.Constraint>> clauseMap;
//        private final List<Trgt.Tuple> inputs;
//        private final List<Trgt.Tuple> outputs;
        private final List<String> ruleInfos;

        public ProvenanceBuilder(Map<Trgt.Tuple, List<Trgt.Constraint>> clauseMap, List<String> ruleInfos) {
            this.clauseMap = clauseMap;
//            this.inputs = inputs;
//            this.outputs = outputs;
            this.ruleInfos = ruleInfos;
        }

        private List<Trgt.Constraint> prove(Collection<Trgt.Tuple> targets) {
            List<Trgt.Constraint> constrs = new ArrayList<>();
            Set<Trgt.Tuple> workList = new LinkedHashSet<>();
            Set<Trgt.Tuple> unsolvedTargets = new LinkedHashSet<>();
            for (Trgt.Tuple target : targets) {
                String relName = target.getRelName();
                if (outputRelNames.contains(relName)) {
                    workList.add(target);
                } else {
                    Messages.debug("ProvenanceBuilder %s: leaving unseen tuple %s to other analyses", name, TextFormat.shortDebugString(target), relName);
                    unsolvedTargets.add(target);
                }
            }
            while (!workList.isEmpty()) {
                Set<Trgt.Tuple> newWorkList = new LinkedHashSet<>();
                for (Trgt.Tuple head : workList) {
                    String headRelName = head.getRelName();
                    if (clauseMap.containsKey(head)) {
                        for (Trgt.Constraint constraint : clauseMap.get(head)) {
                            assert constraint.getHeadTuple().equals(head);
                            constrs.add(constraint);
                            newWorkList.addAll(constraint.getBodyTupleList());
                            constrs.add(constraint);
                        }
                    } else {
                        if (inputRelNames.contains(headRelName)) {
                            Messages.debug("ProvenanceBuilder %s: leaving input tuple %s to other analyses", name, TextFormat.shortDebugString(head));
                            unsolvedTargets.add(head);
                        } else {
                            Messages.debug("ProvenanceBuilder %s: axiomatic tuple %s in analysis", name, TextFormat.shortDebugString(head));
                        }
                    }
                }
                workList = newWorkList;
            }

            targets.clear();
            targets.addAll(unsolvedTargets);
            return constrs;
        }
    }
//
//    private class SouffleProvenanceBuilder extends ProvenanceBuilder {
//        private SWIGSouffleProgram provenanceProgram;
//        private final Path provenanceDir;
//
//        private boolean provActivated;
//
//        private List<String> ruleInfos;
//        private List<ConstraintItem> constraintItems;
//        private List<RawTuple> inputTuples;
//        private List<RawTuple> outputTuples;
//
//        public SouffleProvenanceBuilder(SWIGSouffleProgram provProgram) {
//            // Souffle's provenance has been pruned, not need to use prune in ProvenanceBuilder again
//            super(SouffleAnalysis.this.name, false);
//            provenanceProgram = provProgram;
//            Path tmpProvDir = null;
//            try {
//                tmpProvDir = Files.createDirectories(analysisPath.resolve("provenance"));
//            } catch(IOException e){
//                Messages.error("SouffleProvenanceBuilder %s: failed to create provenance directory", name);
//                Messages.fatal(e);
//            }
//            provenanceDir = tmpProvDir;
//        }
//
//        private void activate() {
//            if (!activated) {
//                Messages.fatal("SouffleProvenanceBuilder %s: the analysis should be activated before building provenance", name);
//            }
//            // 1. fetch inputTuples
//            inputTuples = new ArrayList<>();
//            for (String relName : inputRelNames) {
//                Path factPath = factDir.resolve(relName + ".facts");
//                List<int[]> table = loadTableFromFile(factPath);
//                for (int[] row : table) {
//                    inputTuples.add(new RawTuple(relName, row));
//                }
//            }
//
//            // 2. fetch outputTuples
//            outputTuples = new ArrayList<>();
//            for (String relName : outputRelNames) {
//                Path outPath = outDir.resolve(relName + ".csv");
//                List<int[]> table = loadTableFromFile(outPath);
//                for (int[] row : table) {
//                    outputTuples.add(new RawTuple(relName, row));
//                }
//            }
//
//            // 3. fetch ruleInfos
//            ruleInfos = new ArrayList<>();
//            ruleInfos.addAll(provenanceProgram.getInfoRelNames());
//
//            if (provActivated) {
//                Messages.warn("SouffleProvenanceBuilder %s: the provenance has been activated before, are you sure to re-run?", name);
//            }
//            provenanceProgram.loadAll(factDir.toString());
//            provenanceProgram.run();
//            provenanceProgram.printProvenance(provenanceDir.toString());
//            // 4. fetch constraintItems
//            Path consFilePath = provenanceDir.resolve("cons_all.txt");
//            List<String> consLines = null;
//            try {
//                consLines = Files.readAllLines(consFilePath, StandardCharsets.UTF_8);
//            } catch (IOException e) {
//                Messages.error("SouffleProvenanceBuilder %s: failed to read constraint items from file %s", name, consFilePath.toString());
//                Messages.fatal(e);
//            }
//            constraintItems = new ArrayList<>();
//            assert consLines != null;
//            for (String line : consLines) {
//                ConstraintItem constraintItem = decodeConstraintItem(line);
//                constraintItems.add(constraintItem);
//            }
//            provActivated = true;
//
//            Messages.debug("SouffleProvenanceBuilder %s: freeing souffle program %s", name, provenanceProgram);
//            provenanceProgram = null;
//        }
//
//        private ConstraintItem decodeConstraintItem(String line) {
//            String[] atoms = line.split("\t");
//            String headAtom = atoms[0];
//            boolean headSign = true;
//            if (headAtom.charAt(0) == '!' && !headAtom.substring(0, headAtom.indexOf('(')).equals("!=")) {
//                headAtom = headAtom.substring(1);
//                headSign = false;
//            }
//            RawTuple headTuple = new RawTuple(headAtom);
//
//            List<RawTuple> bodyTuples = new ArrayList<>();
//            List<Boolean> bodySigns = new ArrayList<>();
//            for (int i = 1; i < atoms.length - 1; ++i) {
//                String bodyAtom = atoms[i];
//                boolean bodySign = true;
//                if (bodyAtom.charAt(0) == '!' && !bodyAtom.substring(0, bodyAtom.indexOf('(')).equals("!=")) {
//                    bodyAtom = bodyAtom.substring(1);
//                    bodySign = false;
//                }
//                bodyTuples.add(new RawTuple(bodyAtom));
//                bodySigns.add(bodySign);
//            }
//
//            String ruleInfo = atoms[atoms.length - 1];
//            if (ruleInfo.charAt(0) != '#') {
//                Messages.fatal("SouffleProvenanceBuilder %s: wrong rule info recorded - %s", name, ruleInfo);
//            }
//            ruleInfo = ruleInfo.substring(1);
//            int ruleId = ruleInfos.indexOf(ruleInfo);
//            return new ConstraintItem(ruleId, headTuple, bodyTuples, headSign, bodySigns);
//        }
//
//        public List<String> getRuleInfos() {
//            if (!provActivated) {
//                activate();
//            }
//            return ruleInfos;
//        }
//
//        public Collection<ConstraintItem> getAllConstraintItems() {
//            if (!provActivated) {
//                activate();
//            }
//            return constraintItems;
//        }
//
//        public Collection<RawTuple> getInputTuples() {
//            if (!provActivated) {
//                activate();
//            }
//            return inputTuples;
//        }
//
//        public Collection<RawTuple> getOutputTuples() {
//            if (!provActivated) {
//                activate();
//            }
//            return outputTuples;
//        }
//    }
}