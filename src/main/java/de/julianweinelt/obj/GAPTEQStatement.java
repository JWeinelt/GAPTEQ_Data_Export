package de.julianweinelt.obj;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

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

    public record StatementTable(String connection, String tableName) {}
}
