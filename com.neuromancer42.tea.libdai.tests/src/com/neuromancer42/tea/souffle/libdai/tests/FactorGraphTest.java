package com.neuromancer42.tea.souffle.libdai.tests;

import com.neuromancer42.tea.core.inference.Categorical01;
import com.neuromancer42.tea.core.inference.CausalGraph;
import com.neuromancer42.tea.core.project.Config;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.libdai.DAIRuntime;
import com.neuromancer42.tea.libdai.IteratingCausalDriver;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class FactorGraphTest {

    private static Categorical01 unknownCoin;
    private static CausalGraph<String> causalGraph;
    private static Categorical01 confidence;

    @BeforeAll
    public static void setup() {
        List<String> nodes = new ArrayList<>();
        nodes.add("Coin1");  nodes.add("Coin2");
        nodes.add("Or"); nodes.add("And");
        List<String> singletons = new ArrayList<>();
        singletons.add("Coin1"); singletons.add("Coin2");
        Map<String, List<String>> sums = new HashMap<>();
        Map<String, List<String>> prods = new HashMap<>();
        sums.put("Or", singletons);
        prods.put("And", singletons);
        causalGraph = CausalGraph.createCausalGraph("two_coins", nodes, singletons, sums, prods);
        unknownCoin = new Categorical01(new double[]{0.1D, 0.5D, 0.9D});
        confidence = new Categorical01(new double[]{0.01D, 0.99D});
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
            causalGraph.dumpDot(Paths.get("cg1_2.dot"), String::toString, Categorical01::toString);
            PrintWriter pw = new PrintWriter(Files.newBufferedWriter(Paths.get("pln1_2.fg")));
            DAIRuntime.dumpRepeatedFactorGraph(pw, causalGraph, 1);
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
            causalGraph.dumpDot(Paths.get("cg2_1.dot"), String::toString, Categorical01::toString);
            PrintWriter pw = new PrintWriter(Files.newBufferedWriter(Paths.get("pln2_1.fg")));
            DAIRuntime.dumpRepeatedFactorGraph(pw, causalGraph, 1);
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
        IteratingCausalDriver inferer = new IteratingCausalDriver("coin2", causalGraph);
        int distId1 = causalGraph.getNodesDistId(causalGraph.getNodeId("Coin1"));
        int distId2 = causalGraph.getNodesDistId(causalGraph.getNodeId("Coin1"));
        Messages.log("Prior: \t" +
                Arrays.toString(inferer.queryFactorById(distId1)) + ", " +
                Arrays.toString(inferer.queryFactorById(distId2)));
        double p_both0 = inferer.queryPossibilityById(causalGraph.getNodeId("And"));
        double p_either0 = inferer.queryPossibilityById(causalGraph.getNodeId("Or"));
        Messages.log("Predict: \tboth - " + p_both0 + "\teither - " + p_either0);

        Map<String, Boolean> obs = new HashMap<>();
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
