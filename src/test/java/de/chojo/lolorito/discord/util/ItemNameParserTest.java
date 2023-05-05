package de.chojo.lolorito.discord.util;

import de.chojo.lolorito.discord.util.ItemNameParser.Token;
import de.chojo.universalis.entities.Language;
import de.chojo.universalis.provider.items.Items;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

class ItemNameParserTest {

    @Test
    public void tokenSplitting() {
        Token abc = Token.create("ABC");
        Token a_b_c = Token.create("A B C");
        Assertions.assertArrayEquals(new String[]{"a", "b", "c"}, abc.token());
        Assertions.assertArrayEquals(new String[]{"a", "b", "c"}, a_b_c.token());

        abc = Token.create("AbcDeFGh");
        a_b_c = Token.create("Abc De F Gh");
        Assertions.assertArrayEquals(new String[]{"abc", "de", "f", "gh"}, abc.token());
        Assertions.assertArrayEquals(new String[]{"abc", "de", "f", "gh"}, a_b_c.token());

        a_b_c = Token.create("abc de f gh");
        Assertions.assertArrayEquals(new String[]{"abc", "de", "f", "gh"}, a_b_c.token());

        a_b_c = Token.create("abcDe f gh");
        Assertions.assertArrayEquals(new String[]{"abc", "de", "f", "gh"}, a_b_c.token());

        a_b_c = Token.create("ÄbcÖe f gh");
        Assertions.assertArrayEquals(new String[]{"äbc", "öe", "f", "gh"}, a_b_c.token());
    }

    @Test
    public void tokenMatching() {
        Token token = Token.create("Some Matching Word String");
        Token matchingToken = Token.create("SMWS");
        Assertions.assertTrue(token.matches(matchingToken));
        matchingToken = Token.create("SS");
        Assertions.assertTrue(token.matches(matchingToken));
        matchingToken = Token.create("SMW");
        Assertions.assertTrue(token.matches(matchingToken));
        matchingToken = Token.create("MW");
        Assertions.assertTrue(token.matches(matchingToken));
        matchingToken = Token.create("WS");
        Assertions.assertTrue(token.matches(matchingToken));
        matchingToken = Token.create("SW");
        Assertions.assertTrue(token.matches(matchingToken));
        matchingToken = Token.create("Some String");
        Assertions.assertTrue(token.matches(matchingToken));
        matchingToken = Token.create("Some Word");
        Assertions.assertTrue(token.matches(matchingToken));
        // Not matching. Wrong order
        matchingToken = Token.create("Word Some");
        Assertions.assertFalse(token.matches(matchingToken));
        // Not matching. Wrong word
        matchingToken = Token.create("Something");
        Assertions.assertFalse(token.matches(matchingToken));
        matchingToken = Token.create("Materia IX");
        Assertions.assertTrue(Token.create("Craftsman's Cunning Materia IX").matches(matchingToken));
        matchingToken = Token.create("command materia IX");
        Assertions.assertTrue(Token.create("Craftsman's Command Materia IX").matches(matchingToken));
        matchingToken = Token.create("Mat");
        Assertions.assertFalse(Token.create("Grade 2 Skybuilders' Lutinite").matches(matchingToken));
    }

    @Test
    public void tokenScore() {
        Token token = Token.create("Some Matching Word String");
        Token matchingToken = Token.create("SMWS");
        Assertions.assertEquals(10, token.score(matchingToken));
        matchingToken = Token.create("SomeMWS");
        Assertions.assertEquals(22, token.score(matchingToken));
        matchingToken = Token.create("SomeMaWS");
        Assertions.assertEquals(25, token.score(matchingToken));
        matchingToken = Token.create("SomeMaStr");
        Assertions.assertEquals(25, token.score(matchingToken));
        matchingToken = Token.create("MaStr");
        Assertions.assertEquals(9, token.score(matchingToken));
        Assertions.assertTrue(token.score(Token.create("SMWS")) > token.score(Token.create("MWS")));
        Assertions.assertTrue(token.score(Token.create("SomeMWS")) > token.score(Token.create("SomMWS")));
        Assertions.assertTrue(token.score(Token.create("SomeMWS")) < token.score(Token.create("SMatchingWS")));
    }

    @Test
    public void tokenComparison() {
        var token = Token.create("Gatherer's Grasp Materia X");
        double materiaX = token.score(Token.create("Materia X"));
        double materiaIX = token.score(Token.create("Materia IX"));
        Assertions.assertTrue(materiaX > materiaIX, "%s is not smaller than %s".formatted(materiaX, materiaIX));

    }

    @Test
    public void completeTest() throws IOException, InterruptedException {
        ItemNameParser itemNameParser = ItemNameParser.create(Items.create());
        List<String> results = itemNameParser.complete(Language.ENGLISH, "Pixie");
        Assertions.assertFalse(results.isEmpty());
        for (String s : results) {
            Assertions.assertTrue(s.toLowerCase().contains("pixie"), "%s does not contain pixie".formatted(s));
        }
        results = itemNameParser.complete(Language.ENGLISH, "mat");
        Assertions.assertFalse(results.isEmpty());
        for (String res : results) {
            Assertions.assertTrue(res.toLowerCase().contains("mat"), "%s does not contain pixie".formatted(res));
        }
    }

    @Test
    public void completeMateriaTest() throws IOException, InterruptedException {
        ItemNameParser itemNameParser = ItemNameParser.create(Items.create());
        var results = itemNameParser.complete(Language.ENGLISH, "Materia IX");
        Assertions.assertFalse(results.isEmpty());
        for (String res : results) {
            Assertions.assertTrue(res.toLowerCase().contains("materia ix"), "%s does not contain Materia IX".formatted(res));
        }

    }
}
