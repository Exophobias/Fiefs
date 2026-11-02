package dansplugins.fiefs.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link ArgumentParser}: a fief name is read the same whether or not it is typed in
 * double quotes (#237).
 */
class FiefNameArgumentTest {

    @Test
    void parse_aBareName_returnsIt() {
        assertEquals("Millbrook", new ArgumentParser().getFiefNameFromArguments(new String[]{"Millbrook"}));
    }

    @Test
    void parse_aQuotedName_dropsTheQuotes() {
        assertEquals("Millbrook", new ArgumentParser().getFiefNameFromArguments(new String[]{"\"Millbrook\""}));
    }

    @Test
    void parse_aQuotedNameSpanningSeveralArguments_returnsTheWholeName() {
        assertEquals("South Reach", new ArgumentParser().getFiefNameFromArguments(new String[]{"\"South", "Reach\""}));
    }

    @Test
    void parse_aBareNameSpanningSeveralArguments_joinsThemWithSpaces() {
        assertEquals("South Reach", new ArgumentParser().getFiefNameFromArguments(new String[]{"South", "Reach"}));
    }

    @Test
    void parse_withAQuotedNameAmongOtherWords_returnsOnlyTheQuotedPart() {
        assertEquals("South Reach", new ArgumentParser().getFiefNameFromArguments(new String[]{"the", "\"South", "Reach\"", "fief"}));
    }
}
