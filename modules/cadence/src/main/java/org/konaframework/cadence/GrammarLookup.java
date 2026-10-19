package org.konaframework.cadence;

/** Java switch for [Grammar.of] so Kover does not leave an exhaustive-when closing brace uncovered. */
public final class GrammarLookup {
    private GrammarLookup() {}

    public static Grammar of(Dialect dialect) {
        if (dialect == null) {
            throw new NullPointerException("dialect");
        }
        return switch (dialect) {
            case STANDARD -> Grammar.ANSI;
            case H2 -> Grammar.H2;
            case SQLITE -> Grammar.SQLITE;
            case MYSQL -> Grammar.MYSQL;
            case MARIADB -> Grammar.MARIADB;
            case POSTGRES -> Grammar.POSTGRES;
            case SQLSERVER -> Grammar.SQLSERVER;
            case ORACLE -> Grammar.ORACLE;
        };
    }
}
