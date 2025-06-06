package de.julianweinelt.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Configuration {
    private String repoPath;

    private boolean exportPages;
    private boolean exportStatements;
    private boolean exportStatementTableRef;
    private boolean exportConnections;

    private boolean smallBandwidth;
    private boolean multiThreading;
    private boolean createScripts;

    private Theme theme = Theme.LIGHT;
}
