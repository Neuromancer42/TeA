package com.neuromancer42.tea.libdai.tests;

import com.neuromancer42.tea.commons.inference.Categorical01;
import com.neuromancer42.tea.commons.inference.CausalGraph;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.libdai.DAIRuntime;
import com.neuromancer42.tea.libdai.IteratingCausalDriver;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class FactorGraphTest {

    private static Categorical01 unknownCoin;
    private static CausalGraph causalGraph;
    private static Categorical01 confidence;

    private static final String workdir = "test-out";
    private static final Path workPath = Paths.get(workdir);


    @BeforeAll
    public static void setup() throws IOException {
        Files.createDirectories(workPath);
        DAIRuntime.init(Paths.get(workdir, "build"), 1);
        List<Object> nodes = new ArrayList<>();
        nodes.add("Coin1");  nodes.add("Coin2");
        nodes.add("Or"); nodes.add("And");
        List<Object> singletons = new ArrayList<>();
        singletons.add("Coin1"); singletons.add("Coin2");
        Map<Object, List<Object>> sums = new HashMap<>();
        Map<Object, List<Object>> prods = new HashMap<>();
        sums.put("Or", singletons);
        prods.put("And", singletons);
        causalGraph = CausalGraph.createCausalGraph("two_coins", nodes, singletons, sums, prods);
        unknownCoin = new Categorical01(0.1D, 0.5D, 0.9D);
        confidence = new Categorical01(0.01D, 0.99D);
        Messages.log("Created causal graph object");
    }

    @Test
    @Order(1)
    @DisplayName("One coin, flip twice")
    public void oneCoinTwiceTest() {
        try {
            causalGraph.resetStochNodes();
            causalGraph.setStochNode("Coin1", unknownCoin);
            causalGraph.setStochNode("Coin2", unknownCoin);
            causalGraph.setStochNode("Or", new Categorical01(confidence));
            causalGraph.setStochNode("And", new Categorical01(confidence));
            causalGraph.dumpDot(Paths.get(workdir, "cg1_2.dot"), Object::toString, Categorical01::toString);
            PrintWriter pw = new PrintWriter(Files.newBufferedWriter(Paths.get(workdir, "pln1_2.fg")));
            DAIRuntime.dumpRepeatedFactorGraph(pw, causalGraph, 1, true);
        } catch (IOException e) {
            Assertions.fail(e);
        }
    }

    @Test
    @Order(2)
    @DisplayName("Two coins, flip once")
    public void twoCoinsOnceTest() {
        try {
            causalGraph.resetStochNodes();
            causalGraph.setStochNode("Coin1", new Categorical01(unknownCoin));
            causalGraph.setStochNode("Coin2", new Categorical01(unknownCoin));
            causalGraph.setStochNode("Or", new Categorical01(confidence));
            causalGraph.setStochNode("And", new Categorical01(confidence));
            causalGraph.dumpDot(Paths.get(workdir, "cg2_1.dot"), Object::toString, Categorical01::toString);
            PrintWriter pw = new PrintWriter(Files.newBufferedWriter(Paths.get(workdir, "pln2_1.fg")));
            DAIRuntime.dumpRepeatedFactorGraph(pw, causalGraph, 1, true);
        } catch (IOException e) {
            Assertions.fail(e);
        }
    }

    @Test
    @Order(3)
    @DisplayName("Testing iterating inferer")
    public void iteratingInfererTest() {
        causalGraph.resetStochNodes();
        causalGraph.setStochNode("Coin1", new Categorical01(unknownCoin));
        causalGraph.setStochNode("Coin2", new Categorical01(unknownCoin));
        IteratingCausalDriver inferer = new IteratingCausalDriver("coin2", workPath, causalGraph);
        int distId1 = causalGraph.getNodesDistId(causalGraph.getNodeId("Coin1"));
        int distId2 = causalGraph.getNodesDistId(causalGraph.getNodeId("Coin1"));
        Messages.log("Prior: \t" +
                Arrays.toString(inferer.queryFactorById(distId1)) + ", " +
                Arrays.toString(inferer.queryFactorById(distId2)));
        double p_both0 = inferer.queryPossibilityById(causalGraph.getNodeId("And"));
        double p_either0 = inferer.queryPossibilityById(causalGraph.getNodeId("Or"));
        Messages.log("Predict: \tboth - " + p_both0 + "\teither - " + p_either0);

        Map<Object, Boolean> obs = new HashMap<>();
        obs.put("And", true); obs.put("Or", true);
        inferer.appendObservation(obs);
        Messages.log("Posterior: \t" +
                Arrays.toString(inferer.queryFactorById(distId1)) + ", " +
                Arrays.toString(inferer.queryFactorById(distId2)));
        Double p_both1 = inferer.queryPossibilityById(causalGraph.getNodeId("And"));
        Double p_either1 = inferer.queryPossibilityById(causalGraph.getNodeId("Or"));
        Messages.log("Predict: \tboth - " + p_both1 + "\teither - " + p_either1);
        Assertions.assertTrue(p_both1 > p_both0 && p_either1 > p_either0);
    }
}
