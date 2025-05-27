package de.julianweinelt.export;

import de.julianweinelt.obj.GAPTEQPage;
import de.julianweinelt.obj.GAPTEQStatement;

import java.util.concurrent.ConcurrentLinkedDeque;

public class SQLExporter extends Exporter {
    public SQLExporter(String pagesTableName, String statementsTableName, boolean createTables) {
        super(pagesTableName, statementsTableName, createTables);
    }

    @Override
    public String createPageExport(ConcurrentLinkedDeque<GAPTEQPage> pages) {
        StringBuilder statement = new StringBuilder();
        if (isCreateTables()) {
            statement.append("""
                    CREATE TABLE [dbo].[TBNAME](
                    	[creator] [varchar](60) NULL,
                    	[createDate] [varchar](50) NULL,
                    	[modifier] [varchar](50) NULL,
                    	[modifierDate] [varchar](50) NULL,
                    	[pageTemplate] [varchar](50) NULL,
                    	[pageName] [varchar](200) NULL
                    ) ON [PRIMARY];
                    """.replace("TBNAME", getPagesTableName()));
            statement.append("\n\n");
        }
        for (GAPTEQPage p : pages) {
            statement.append("INSERT INTO ").append(getPagesTableName())
                    .append(" (creator, createDate, pageName, modifier, modifierDate, pageTemplate) VALUES('")
                    .append(p.getCreatedBy())
                    .append("', '").append(p.getCreatedAt())
                    .append("', '").append(p.getPageName())
                    .append("', '").append(p.getModifiedBy())
                    .append("', '").append(p.getModifiedAt())
                    .append("', '").append(p.getPageTemplate()).append("');").append("\n");
        }
        return statement.toString();
    }

    @Override
    public String createStatementExport(ConcurrentLinkedDeque<GAPTEQStatement> statements) {
        StringBuilder statement = new StringBuilder();
        if (isCreateTables()) {
            statement.append("""
                    CREATE TABLE [dbo].[TBNAME](
                    	[creator] [varchar](60) NULL,
                    	[createDate] [varchar](50) NULL,
                    	[modifier] [varchar](50) NULL,
                    	[modifierDate] [varchar](50) NULL,
                    	[connectionName] [varchar](50) NULL,
                    	[statementName] [varchar](200) NULL
                    ) ON [PRIMARY];
                    """.replace("TBNAME", getStatementsTableName()))
                    .append("\n\n");
            statement.append("""
                    CREATE TABLE [dbo].[TBNAME_Table_Ref](
                        [connectionName] [varchar](200) NOT NULL,
                        [statementName] [varchar](200) NOT NULL,
                        [tableName] [varchar](200) NOT NULL
                    );
                    """.replace("TBNAME", getStatementsTableName()))
                    .append("\n\n");
        }
        for (GAPTEQStatement s : statements) {
            statement.append("INSERT INTO ").append(getStatementsTableName())
                    .append(" (creator, createDate, statementName, modifier, modifierDate, connectionName) VALUES('")
                    .append(s.getCreatedBy())
                    .append("', '").append(s.getCreatedAt())
                    .append("', '").append(s.getStatementName())
                    .append("', '").append(s.getModifiedBy())
                    .append("', '").append(s.getModifiedAt())
                    .append("', '").append(s.getConnectionName()).append("');\n");
        }

        statement.append("\n\n");

        for (GAPTEQStatement s : statements) {
            for (GAPTEQStatement.StatementTable t : s.getTables())
                statement.append("INSERT INTO ").append(getStatementsTableName()).append("_Table_Ref")
                        .append(" (connectionName, statementName, tableName) VALUES('")
                        .append(t.connection()).append("', '")
                        .append(s.getStatementName()).append("', '")
                        .append(t.tableName()).append("');\n");
        }
        return statement.toString();
    }
}