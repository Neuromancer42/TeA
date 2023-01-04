package com.neuromancer42.tea.commons.provenance.tests;

import com.google.protobuf.TextFormat;
import com.neuromancer42.tea.commons.provenance.ProvenanceBuilder;
import com.neuromancer42.tea.core.analysis.Trgt;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProvenanceTest {
    private static final Trgt.Tuple i1 = Trgt.Tuple.newBuilder().setRelName("I").addAttribute("1").build();
    private static final Trgt.Tuple i2 = Trgt.Tuple.newBuilder().setRelName("I").addAttribute("2").build();
    private static final Trgt.Tuple h2 = Trgt.Tuple.newBuilder().setRelName("H").addAttribute("2").build();
    private static final Trgt.Tuple o0 = Trgt.Tuple.newBuilder().setRelName("O").addAttribute("0").build();
    private static final Trgt.Tuple o1 = Trgt.Tuple.newBuilder().setRelName("O").addAttribute("1").build();
    private static final Trgt.Constraint cons1 = Trgt.Constraint.newBuilder().setHeadTuple(o0).addBodyTuple(i1).setRuleInfo("rule1").build();
    private static final Trgt.Constraint cons21 = Trgt.Constraint.newBuilder().setHeadTuple(h2).addBodyTuple(i2).setRuleInfo("rule2").build();
    private static final Trgt.Constraint cons22 = Trgt.Constraint.newBuilder().setHeadTuple(o0).addBodyTuple(h2).setRuleInfo("rule3").build();
    private static final Trgt.Constraint cons20 = Trgt.Constraint.newBuilder().setHeadTuple(o0).addBodyTuple(i2).setRuleInfo("rule4").build();
    private static final Trgt.Constraint loop0 = Trgt.Constraint.newBuilder().setHeadTuple(o1).addBodyTuple(o0).setRuleInfo("rule5").build();
    private static final Trgt.Constraint loop1 = Trgt.Constraint.newBuilder().setHeadTuple(o0).addBodyTuple(o1).setRuleInfo("rule5").build();

    @Order(0)
    @Test
    @DisplayName("Generate unpruned provenance")
    public void testSimpleBuilder() {
        ProvenanceBuilder provBuilder = new ProvenanceBuilder("simple", new HashMap<>());
        provBuilder.addConstraints(List.of(cons1));
        provBuilder.addInputTuples(List.of(i1));
        provBuilder.addOutputTuples(List.of(o0));
        provBuilder.computeProvenance();
        Trgt.Provenance prov = provBuilder.getProvenance();
        Assertions.assertEquals(1, prov.getConstraintCount());
        Assertions.assertEquals(1, prov.getInputCount());
        Assertions.assertEquals(1, prov.getOutputCount());
        Assertions.assertEquals(cons1, prov.getConstraint(0));
    }

    @Order(0)
    @Test
    @DisplayName("Generate unpruned provenance with loop")
    public void testSimpleBuilderWithLoop() {
        ProvenanceBuilder provBuilder = new ProvenanceBuilder("simpleloop", new HashMap<>());
        provBuilder.addConstraints(List.of(cons1, loop0, loop1));
        provBuilder.addInputTuples(List.of(i1));
        provBuilder.addOutputTuples(List.of(o0));
        provBuilder.computeProvenance();
        Trgt.Provenance prov = provBuilder.getProvenance();
        Assertions.assertEquals(3, prov.getConstraintCount());
        Assertions.assertEquals(1, prov.getInputCount());
        Assertions.assertEquals(1, prov.getOutputCount());
    }

    @Order(1)
    @Test
    @DisplayName("Dump provenance correctly")
    public void testDumpProvenance() throws IOException {
        ProvenanceBuilder provBuilder = new ProvenanceBuilder("dump", new HashMap<>());
        provBuilder.addConstraints(List.of(cons1));
        provBuilder.addInputTuples(List.of(i1));
        provBuilder.addOutputTuples(List.of(o0));
        provBuilder.computeProvenance();
        Path workPath = Paths.get("test-out");
        boolean res = provBuilder.dumpProvenance(workPath);
        Assertions.assertTrue(res);
        List<String> lines = Files.readAllLines(workPath.resolve("cons_pruned.txt"));
        Assertions.assertEquals(1, lines.size());
        System.err.println(lines.get(0));
    }

    @Order(2)
    @Test
    @DisplayName("DOBSolver truncates longer proof before augment")
    public void testDOBPruneNoAug() {
        Map<String, String> option = new HashMap<>();
        option.put("tea.provenance.prune", "true");
        option.put("tea.provenance.augment", "false");
        ProvenanceBuilder provBuilder = new ProvenanceBuilder("nolongpath", option);
        provBuilder.addConstraints(List.of(cons1, cons21, cons22));
        provBuilder.addInputTuples(List.of(i1, i2));
        provBuilder.addOutputTuples(List.of(o0));
        provBuilder.computeProvenance();
        Trgt.Provenance prov = provBuilder.getProvenance();
        for (Trgt.Constraint cons : prov.getConstraintList()) {
            System.err.println(TextFormat.shortDebugString(cons));
        }
        Assertions.assertEquals(1, prov.getConstraintCount());
        Assertions.assertEquals(1, prov.getInputCount());
        Assertions.assertEquals(1, prov.getOutputCount());
    }

    @Order(3)
    @Test
    @DisplayName("DOBSolver preserves longer proof after augment")
    public void testDOBPruneWithAug() {
        Map<String, String> option = new HashMap<>();
        option.put("tea.provenance.prune", "true");
        option.put("tea.provenance.augment", "true");
        ProvenanceBuilder provBuilder = new ProvenanceBuilder("longpath", option);
        provBuilder.addConstraints(List.of(cons1, cons21, cons22));
        provBuilder.addInputTuples(List.of(i1, i2));
        provBuilder.addOutputTuples(List.of(o0));
        provBuilder.computeProvenance();
        Trgt.Provenance prov = provBuilder.getProvenance();
        for (Trgt.Constraint cons : prov.getConstraintList()) {
            System.err.println(TextFormat.shortDebugString(cons));
        }
        Assertions.assertEquals(3, prov.getConstraintCount());
        Assertions.assertEquals(2, prov.getInputCount());
        Assertions.assertEquals(1, prov.getOutputCount());
    }

    @Order(4)
    @Test
    @DisplayName("DOBSolver preserves longer proof after augment")
    public void testDOBPruneSamePath() {
        Map<String, String> option = new HashMap<>();
        option.put("tea.provenance.prune", "true");
        option.put("tea.provenance.augment", "false");
        ProvenanceBuilder provBuilder = new ProvenanceBuilder("longpath", option);
        provBuilder.addConstraints(List.of(cons1, cons21, cons22, cons20));
        provBuilder.addInputTuples(List.of(i1, i2));
        provBuilder.addOutputTuples(List.of(o0));
        provBuilder.computeProvenance();
        Trgt.Provenance prov = provBuilder.getProvenance();
        for (Trgt.Constraint cons : prov.getConstraintList()) {
            System.err.println(TextFormat.shortDebugString(cons));
        }
        Assertions.assertEquals(2, prov.getConstraintCount());
        Assertions.assertEquals(2, prov.getInputCount());
        Assertions.assertEquals(1, prov.getOutputCount());
    }

    @Order(5)
    @Test
    @DisplayName("DOBSolver truncates loop")
    public void testDOBPruneLoop() {
        Map<String, String> option = new HashMap<>();
        option.put("tea.provenance.prune", "true");
        option.put("tea.provenance.augment", "true");
        ProvenanceBuilder provBuilder = new ProvenanceBuilder("loop", option);
        provBuilder.addConstraints(List.of(cons1, loop0, loop1));
        provBuilder.addInputTuples(List.of(i1));
        provBuilder.addOutputTuples(List.of(o0, o1));
        provBuilder.computeProvenance();
        Trgt.Provenance prov = provBuilder.getProvenance();
        for (Trgt.Constraint cons : prov.getConstraintList()) {
            System.err.println(TextFormat.shortDebugString(cons));
        }
        Assertions.assertEquals(2, prov.getConstraintCount());
        Assertions.assertEquals(1, prov.getInputCount());
        Assertions.assertEquals(2, prov.getOutputCount());
    }
}
