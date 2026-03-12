package org.zstack.header.vm;

import java.io.Serializable;

public class DiffEntry implements Serializable {
    private String field;
    private String expected;
    private String actual;

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getExpected() {
        return expected;
    }

    public void setExpected(String expected) {
        this.expected = expected;
    }

    public String getActual() {
        return actual;
    }

    public void setActual(String actual) {
        this.actual = actual;
    }
}
