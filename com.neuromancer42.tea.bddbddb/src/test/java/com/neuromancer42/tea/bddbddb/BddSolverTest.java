package com.neuromancer42.tea.bddbddb;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDDomain;
import net.sf.javabdd.BDDFactory;

import java.io.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class BddSolverTest {
    @Test
    @DisplayName("BddSovler process dlog files correctly")
    public void test() throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        String dirName = System.getProperty("workdir", "test-out");
        File workDir = new File(dirName);
        workDir.mkdirs();
        String dlogName = "test-classes" + File.separator + "simple.dl";

        System.setProperty("bdd", "j");
        System.setProperty("basedir", dirName);
        System.setProperty("verbose", "1");

        int bddnodes = Integer.parseInt(
                System.getProperty("bddnodes", "500000"));
        int bddcache = Integer.parseInt(
                System.getProperty("bddcache", "125000"));
        double bddminfree = Double.parseDouble(
                System.getProperty("bddminfree", ".20"));
        BDDFactory factory = BDDFactory.init("java", bddnodes, bddcache);
        factory.setVerbose(10);
        factory.setIncreaseFactor(2);
        factory.setMinFreeNodes(bddminfree);

        String domName = "P";

        String[] domNames = {"P0", "P1"};
        BDDDomain[] domBdds = new BDDDomain[domNames.length];
        String domVarOrder = "P0xP1";
        int domElemNum = 3;

        try {
            File mapFile = new File(dirName, "P.map");
            PrintWriter mw = new PrintWriter(mapFile);
            for (int i = 0; i < domElemNum; i++) {
                mw.println("Node"+i);
            }
            mw.close();
            File domFile = new File(dirName, "P.dom");
            PrintWriter dw = new PrintWriter(domFile);
            int size = domElemNum;
            dw.println(domName + " " + size + " " + mapFile.getName());
            dw.close();
        } catch (IOException ex) {
            ex.printStackTrace();
            assert false;
        }

        for (int i = 0; i < domNames.length; i++) {
            String name = domNames[i];
            int numElems = domElemNum;
            BDDDomain d = factory.extDomain(new long[]{numElems})[0];
            d.setName(name);
            domBdds[i] = d;
        }
        int[] order = factory.makeVarOrdering(true, domVarOrder);
        factory.setVarOrder(order);
        int[] domIdxs = new int[domNames.length];
        BDD iterBdd = factory.one();
        for (int i = 0; i < domNames.length; i++) {
            BDDDomain domBdd = domBdds[i];
            domIdxs[i] = domBdd.getIndex();
            iterBdd = iterBdd.andWith(domBdd.set());
        }
        BDD bdd = factory.zero();
        bdd.orWith(domBdds[0].ithVar(0).andWith(domBdds[1].ithVar(1)));
        bdd.orWith(domBdds[0].ithVar(1).andWith(domBdds[1].ithVar(2)));

        try {
            File file = new File(dirName, "PP.bdd");
            BufferedWriter out = new BufferedWriter(new FileWriter(file));
            out.write('#');
            for (BDDDomain d : domBdds)
                out.write(" " + d + ":" + d.varNum());
            out.write('\n');
            for (BDDDomain d : domBdds) {
                out.write('#');
                for (int v : d.vars())
                    out.write(" " + v);
                out.write('\n');
            }
            factory.save(out, bdd);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
            assert false;
        }
        factory.done();
        bdd = null;

        String[] args = {dlogName};
        BDDSolver.main(args);
    }
}
