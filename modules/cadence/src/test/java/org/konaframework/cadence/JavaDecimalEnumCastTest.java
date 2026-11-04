package org.konaframework.cadence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.konaframework.cadence.schema.Schema;

public class JavaDecimalEnumCastTest {
    enum Status {
        OPEN,
        CLOSED
    }

    /** Java enum with a public `value` field and no getter — exercises the field path. */
    enum Tone {
        HOT("hot"),
        COLD("cold"),
        NONE(null);

        public final String value;

        Tone(String value) {
            this.value = value;
        }
    }

    public static class Ticket extends Cadence<Ticket> {
        public static final CadenceCompanion<Ticket> QUERY = Cadence.of(Ticket.class);

        @Override
        public String getTable() {
            return "tickets";
        }

        @Override
        public boolean getTimestamps() {
            return false;
        }

        @Override
        public Set<String> getFillable() {
            return Set.of("total", "status", "tones");
        }

        @Override
        public Map<String, Object> getCasts() {
            return Map.of(
                "total", CastType.decimal(2),
                "status", CastType.enumType(Status.class),
                "tones", CastType.enumCollection(Tone.class)
            );
        }
    }

    @BeforeEach
    void setUp() {
        TestDb.INSTANCE.create();
        Database.global().withConnection(connection -> {
            new Schema(connection).create("tickets", table -> {
                table.id();
                table.decimal("total", 16, 2);
                table.string("status");
                table.text("tones").nullable();
            });
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        Database.clearGlobal();
    }

    @Test
    void javaFactoriesRoundTripDecimalAndEnum() {
        Ticket ticket = Ticket.QUERY.create(Map.of(
            "total", "19.999",
            "status", "OPEN",
            "tones", List.of(Tone.HOT, Tone.COLD)
        ));
        assertEquals(new BigDecimal("20.00"), ticket.getAttribute("total"));
        assertEquals(Status.OPEN, ticket.getAttribute("status"));
        assertEquals("20.00", ticket.toMap().get("total"));
        assertInstanceOf(String.class, ticket.toMap().get("total"));
        assertEquals(List.of(Tone.HOT, Tone.COLD), ticket.getAttribute("tones"));

        ticket.setAttribute("status", Status.CLOSED);
        ticket.setAttribute("total", new BigDecimal("1.1"));
        ticket.save();

        Ticket found = Ticket.QUERY.find(ticket.getKey());
        assertEquals(Status.CLOSED, found.getAttribute("status"));
        assertEquals(new BigDecimal("1.10"), found.getAttribute("total"));
        assertEquals(List.of("CLOSED"), List.of(found.toMap().get("status")));
    }

    @Test
    void javaStaticFactoriesAndHelpers() {
        assertInstanceOf(DecimalCast.class, CastType.decimal());
        assertInstanceOf(DecimalCast.class, CastType.decimal(3));
        assertInstanceOf(EnumCast.class, CastType.enumCast(Status.class));
        assertInstanceOf(EnumCast.class, CastType.enumType(Status.class));
        assertInstanceOf(EnumCollectionCast.class, CastType.enumCollection(Status.class));
        assertInstanceOf(EnumCast.class, CastType.resolve(Status.class));
        assertEquals(CastType.HASHED, CastType.resolve("hashed"));

        DecimalCast oneArg = new DecimalCast(2);
        assertEquals(2, oneArg.getScale());
        assertEquals(new BigDecimal("1.5"), DecimalCast.toBigDecimal("1.5"));
        assertEquals(new BigDecimal("4"), DecimalCast.toBigDecimal(4L));

        assertSame(Status.OPEN, EnumCast.resolveEnum(Status.class, Status.OPEN));
        assertEquals("OPEN", EnumCast.enumStoredValue(Status.OPEN));
        assertEquals("OPEN", EnumCast.backingValue(Status.OPEN));
        assertEquals("hot", EnumCast.enumStoredValue(Tone.HOT));
        assertEquals("NONE", EnumCast.enumStoredValue(Tone.NONE));
        assertSame(Tone.HOT, EnumCast.resolveEnum(Tone.class, "hot"));
        assertTrue(EnumCast.backingValue(Tone.COLD).equals("cold"));
    }
}
