package de.julianweinelt.obj;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GAPTEQStatement {
    private String statementName;
    private String connectionName;
    private String createdBy;
    private String createdAt;
    private String modifiedBy;
    private String modifiedAt;
}
