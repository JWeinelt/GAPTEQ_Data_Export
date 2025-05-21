package de.julianweinelt;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GAPTEQPage {
    private String pageTemplate;
    private String createdBy;
    private String createdAt;
    private String modifiedBy;
    private String modifiedAt;
    private String pageName;

}
