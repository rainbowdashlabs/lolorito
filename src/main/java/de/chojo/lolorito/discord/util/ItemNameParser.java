package de.chojo.lolorito.discord.util;

import de.chojo.universalis.entities.Language;
import de.chojo.universalis.provider.NameSupplier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ItemNameParser {
    private final EnumMap<Language, Map<Token, Integer>> token;
    private final NameSupplier nameSupplier;

    public ItemNameParser(EnumMap<Language, Map<Token, Integer>> token, NameSupplier nameSupplier) {
        this.token = token;
        this.nameSupplier = nameSupplier;
    }

    public static ItemNameParser create(NameSupplier nameSupplier) {
        EnumMap<Language, Map<Token, Integer>> languageMap = new EnumMap<>(Language.class);
        for (Language lang : Language.values()) {
            Map<Token, Integer> language = nameSupplier.languageMap(lang).entrySet().stream()
                    .collect(Collectors.toMap(e -> Token.create(nameSupplier.fromId(e.getValue()).get(lang)), Map.Entry::getValue));
            languageMap.put(lang, language);
        }
        return new ItemNameParser(languageMap, nameSupplier);
    }

    public List<String> complete(Language language, String match) {
        return complete(language, match, 25);
    }

    public List<String> complete(Language language, String match, int limit) {
        Token token = Token.create(match);
        return tokenMap(language).entrySet().stream()
                .map(e -> new WeightedToken(e.getValue(), e.getKey().score(token)))
                .filter(weightedToken -> weightedToken.score() > 0)
                .sorted(Comparator.comparingDouble(e -> e.score() * -1))
                .limit(limit)
                .map(WeightedToken::id)
                .map(nameSupplier::fromId)
                .map(name -> name.get(language))
                .toList();
    }

    private Map<Token, Integer> tokenMap(Language language) {
        return token.get(language);
    }

    private record WeightedToken(Integer id, Double score) {
    }

    public record Token(String name, String[] token) {
        public static Token create(String name) {
            List<String> tokens = new ArrayList<>();
            StringBuilder builder = new StringBuilder();
            for (int c : name.chars().toArray()) {
                if ((Character.isUpperCase(c) || Character.isSpaceChar(c))) {
                    tokens.add(builder.toString().toLowerCase().strip());
                    builder.setLength(0);
                }
                builder.append(Character.toString(c));
            }
            tokens.add(builder.toString().toLowerCase().strip());
            tokens.removeIf(String::isBlank);
            return new Token(name, tokens.toArray(String[]::new));
        }

        public boolean matches(Token oToken) {
            return score(oToken) > 0;
        }

        public double score(Token oToken) {
            int currToken = 0;
            boolean match = true;
            double score = 0;
            for (String t : oToken.token()) {
                while (currToken < token.length && !token[currToken].startsWith(t)) {
                    match = false;
                    currToken++;
                }
                if (currToken != token.length) {
                    // score += Math.log10(token.length) - Math.log10(currToken) * (1 - ((double) currToken / token.length)) * t.length();
                    score += (token.length - currToken) * t.length();
                    match = true;
                }
            }
            return match ? score : 0;
        }

        public String[] token() {
            return token;
        }

        @Override
        public String toString() {
            return "\"" + name + "\" as " + Arrays.toString(token);
        }
    }
}
