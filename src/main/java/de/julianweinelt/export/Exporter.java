package de.julianweinelt.export;

import de.julianweinelt.obj.GAPTEQPage;
import de.julianweinelt.obj.GAPTEQStatement;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

@Getter
@Setter
public abstract class Exporter {
    private String pagesTableName;
    private String statementsTableName;
    private boolean createTables;

    public Exporter(String pagesTableName, String statementsTableName, boolean createTables) {
        this.pagesTableName = pagesTableName;
        this.statementsTableName = statementsTableName;
        this.createTables = createTables;
    }

    public abstract String createPageExport(ConcurrentLinkedDeque<GAPTEQPage> pages);
    public abstract String createStatementExport(ConcurrentLinkedDeque<GAPTEQStatement> statements);
}