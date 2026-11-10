package org.konaframework.firefly.imports;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ImportActionJavaTest {
    @Test
    public void staticFactoriesAreCallableFromJava() {
        assertEquals("import", ImportAction.make().getName());
        assertEquals("named", ImportAction.make("named").getName());
        ImportColumn column = ImportColumn.make("sku").label("SKU").requiredMapping().aliases("code");
        assertEquals("SKU", column.getLabel());
        assertFalse(ImportColumn.make("sku").requiredMapping(false).getRequiredMapping());
    }
}
