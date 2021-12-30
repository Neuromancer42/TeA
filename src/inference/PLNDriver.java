package inference;

import chord.analyses.JavaAnalysis;
import chord.project.Config;
import chord.util.Utils;
import provenance.Provenance;
import provenance.ProvenanceBuilder;

import java.io.File;
import java.util.List;
import java.util.Set;

public abstract class PLNDriver extends JavaAnalysis {
    private Provenance provenance;

    protected abstract String getDlogName();
    protected abstract List<String> getObserveRelationNames();

    @Override
    public void run() {
        String dlogName = getDlogName();
        ProvenanceBuilder pDriver = new ProvenanceBuilder(dlogName);
        pDriver.computeProvenance(getObserveRelationNames());
        provenance = pDriver.getProvenance();
        String dumpDirName = Config.v().outDirName + File.separator + "bnet";
        Utils.mkdirs(dumpDirName);
    }

    private void dumpBNet(String dir) {
        String bnetFileName = dir + File.separator + "bnet.txt";
    }

    private void dumpMultiBNet(String dir) {
        String bnetFileName = dir + File.separator + "bnetN.txt";
    }
}
