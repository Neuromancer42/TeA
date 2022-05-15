package com.neuromancer42.tea.bddbddb;

import java.io.IOException;
import net.sf.bddbddb.Solver;

public class BDDSolver {
    public static void main(String[] args) throws IOException, InstantiationException, IllegalAccessException, ClassNotFoundException {
        // Make sure we have the BDD library in our classpath.
        try {
            Class.forName("net.sf.javabdd.BDD");
        } catch (ClassNotFoundException x) {
            x.printStackTrace();
            return;
        }
        // Just call it directly.
        Solver.main(args);
    }
}
