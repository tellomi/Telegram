package org.telegram.ui.tellomi.feed;

import org.telegram.messenger.TellomiUsername;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * @username spans inside a Discover post.
 *
 * The local shape matches {@code ChangeUsernameActivity.checkUserName}: a name
 * does not start or end with {@code _}, does not start with a digit, and is
 * only {@code 0-9}, {@code A-Z}, {@code a-z}, and {@code _}. The shortest name
 * is {@link TellomiUsername#MIN_LENGTH}. The longest is 32, the same cap that
 * screen uses. This is not {@code account.checkUsername}.
 *
 * {@code POST /v1/feed/posts} has no mentions field. A mention stays inside
 * {@code text}. The content engine is what would turn that into a discover
 * mention notification. This client does not invent that request.
 */
public final class TellomiDiscoverMention {

    public static final int MAX_LENGTH = 32;
    public static final int SUGGESTION_LIMIT = 6;

    private TellomiDiscoverMention() {
    }

    public static final class Mention {
        public final String username;
        public final int start;
        public final int end;

        public Mention(String username, int start, int end) {
            this.username = username;
            this.start = start;
            this.end = end;
        }
    }

    /** The @ token that contains the caret. {@code prefix} is the text after @ up to the caret. */
    public static final class Query {
        public final int at;
        public final int end;
        public final String prefix;

        public Query(int at, int end, String prefix) {
            this.at = at;
            this.end = end;
            this.prefix = prefix == null ? "" : prefix;
        }
    }

    public static final class Insertion {
        public final String text;
        public final int caret;

        public Insertion(String text, int caret) {
            this.text = text;
            this.caret = caret;
        }
    }

    public static final class Person {
        public final String username;
        public final String name;

        public Person(String username, String name) {
            this.username = username;
            this.name = name == null ? "" : name;
        }
    }

    public static boolean usernameChar(char ch) {
        return (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || ch == '_';
    }

    public static boolean validUsername(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (name.charAt(0) == '@') {
            name = name.substring(1);
        }
        if (name.isEmpty() || name.charAt(0) == '_' || name.charAt(name.length() - 1) == '_') {
            return false;
        }
        if (name.charAt(0) >= '0' && name.charAt(0) <= '9') {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            if (!usernameChar(name.charAt(i))) {
                return false;
            }
        }
        return !TellomiUsername.isTooShort(name) && name.length() <= MAX_LENGTH;
    }

    public static List<Mention> parse(String text) {
        List<Mention> mentions = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return mentions;
        }
        int index = 0;
        while (index < text.length()) {
            int at = text.indexOf('@', index);
            if (at < 0) {
                break;
            }
            int end = tokenEnd(text, at);
            if (end > at + 1) {
                String name = text.substring(at + 1, end);
                if (validUsername(name)) {
                    mentions.add(new Mention(name, at, end));
                }
                index = end;
            } else {
                index = at + 1;
            }
        }
        return mentions;
    }

    /** Non-empty @ tokens that fail {@link #validUsername}. A lone @ is not one of these. */
    public static List<String> invalid(String text) {
        List<String> names = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return names;
        }
        int index = 0;
        while (index < text.length()) {
            int at = text.indexOf('@', index);
            if (at < 0) {
                break;
            }
            int end = tokenEnd(text, at);
            if (end > at + 1) {
                String name = text.substring(at + 1, end);
                if (!validUsername(name)) {
                    names.add(name);
                }
                index = end;
            } else {
                index = at + 1;
            }
        }
        return names;
    }

    public static Query queryAt(String text, int caret) {
        if (text == null) {
            return null;
        }
        if (caret < 0) {
            caret = 0;
        }
        if (caret > text.length()) {
            caret = text.length();
        }
        int start = caret;
        while (start > 0 && usernameChar(text.charAt(start - 1))) {
            start--;
        }
        if (start <= 0 || text.charAt(start - 1) != '@') {
            return null;
        }
        int at = start - 1;
        if (!boundary(text, at)) {
            return null;
        }
        int end = caret;
        while (end < text.length() && usernameChar(text.charAt(end))) {
            end++;
        }
        if (caret < at + 1 || caret > end) {
            return null;
        }
        return new Query(at, end, text.substring(at + 1, caret));
    }

    public static Insertion insert(String text, Query query, String username) {
        if (text == null || query == null || username == null || username.isEmpty()) {
            return null;
        }
        String name = username.charAt(0) == '@' ? username.substring(1) : username;
        if (!validUsername(name) || query.at < 0 || query.end < query.at || query.end > text.length() || query.at >= text.length() || text.charAt(query.at) != '@') {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append(text, 0, query.at);
        builder.append('@').append(name);
        int tail = query.end;
        if (tail < text.length() && Character.isWhitespace(text.charAt(tail))) {
            builder.append(text.charAt(tail));
            tail++;
        } else {
            builder.append(' ');
        }
        int caret = builder.length();
        builder.append(text, tail, text.length());
        return new Insertion(builder.toString(), caret);
    }

    public static List<Person> suggestions(List<Person> people, String prefix, int limit) {
        int cap = limit > 0 ? limit : SUGGESTION_LIMIT;
        String needle = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        LinkedHashMap<String, Person> unique = new LinkedHashMap<>();
        if (people != null) {
            for (int i = 0; i < people.size(); i++) {
                Person person = people.get(i);
                if (person == null || !validUsername(person.username)) {
                    continue;
                }
                String key = person.username.toLowerCase(Locale.ROOT);
                if (!unique.containsKey(key)) {
                    unique.put(key, new Person(person.username, person.name));
                }
            }
        }
        List<Person> matched = new ArrayList<>();
        for (Person person : unique.values()) {
            String username = person.username.toLowerCase(Locale.ROOT);
            String name = person.name.toLowerCase(Locale.ROOT);
            if (needle.isEmpty() || username.startsWith(needle) || (!needle.isEmpty() && name.startsWith(needle))) {
                matched.add(person);
            }
        }
        Collections.sort(matched, (left, right) -> left.username.compareToIgnoreCase(right.username));
        if (matched.size() > cap) {
            return new ArrayList<>(matched.subList(0, cap));
        }
        return matched;
    }

    private static int tokenEnd(String text, int at) {
        if (!boundary(text, at)) {
            return at;
        }
        int end = at + 1;
        while (end < text.length() && usernameChar(text.charAt(end))) {
            end++;
        }
        return end;
    }

    private static boolean boundary(String text, int at) {
        if (at <= 0) {
            return true;
        }
        return !usernameChar(text.charAt(at - 1));
    }
}
