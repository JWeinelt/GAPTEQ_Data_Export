package de.julianweinelt.obj;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Getter
@Setter
public class GAPTEQStatement {
    private String statementName;
    private String connectionName;
    private String createdBy;
    private String createdAt;
    private String modifiedBy;
    private String modifiedAt;

    private final List<StatementTable> tables = new ArrayList<>();

    public void setTables(Set<String> tables) {
        this.tables.addAll(
                tables.stream()
                        .map(tb -> new StatementTable(connectionName, tb))
                        .toList()
        );
    }

    public record StatementTable(String connection, String tableName) {}
}
