package org.telegram.ui.tellomi.feed;

import org.junit.Test;
import org.telegram.ui.TellomiPack;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Discover compose @mentions. The post body stays the existing text field. */
public class TellomiDiscoverMentionTest {

    private static final String NONCE = "11111111-1111-4111-8111-111111111111";

    @Test
    public void usernameRulesMatchTheLocalClientCheck() {
        assertTrue(TellomiDiscoverMention.validUsername("abcd"));
        assertTrue(TellomiDiscoverMention.validUsername("A_b1"));
        assertTrue(TellomiDiscoverMention.validUsername("@abcd"));
        assertTrue(TellomiDiscoverMention.validUsername(repeat('a', 32)));
        assertTrue(!TellomiDiscoverMention.validUsername("abc"));
        assertTrue(!TellomiDiscoverMention.validUsername("_abcd"));
        assertTrue(!TellomiDiscoverMention.validUsername("abcd_"));
        assertTrue(!TellomiDiscoverMention.validUsername("1abcd"));
        assertTrue(!TellomiDiscoverMention.validUsername("ab-cd"));
        assertTrue(!TellomiDiscoverMention.validUsername(repeat('a', 33)));
        assertTrue(!TellomiDiscoverMention.validUsername(null));
        assertTrue(!TellomiDiscoverMention.validUsername(""));
        assertEquals(4, org.telegram.messenger.TellomiUsername.MIN_LENGTH);
        assertEquals(32, TellomiDiscoverMention.MAX_LENGTH);
    }

    @Test
    public void parseKeepsOnlyRealMentions() {
        java.util.List<TellomiDiscoverMention.Mention> mentions = TellomiDiscoverMention.parse("hi @abcd, (@efgh)\n@@ijkl mail a@mnop.com");
        assertEquals(3, mentions.size());
        assertEquals("abcd", mentions.get(0).username);
        assertEquals("efgh", mentions.get(1).username);
        assertEquals("ijkl", mentions.get(2).username);
        assertTrue(TellomiDiscoverMention.parse("@abc @_no @1no").isEmpty());
        assertEquals(Collections.singletonList("abc"), TellomiDiscoverMention.invalid("see @abc."));
        assertTrue(TellomiDiscoverMention.invalid("just @").isEmpty());
        assertTrue(TellomiDiscoverMention.invalid("价格 @ 100").isEmpty());
    }

    @Test
    public void insertReplacesTheOpenToken() {
        TellomiDiscoverMention.Query query = TellomiDiscoverMention.queryAt("hi @ab", 6);
        assertEquals("ab", query.prefix);
        assertEquals(3, query.at);
        TellomiDiscoverMention.Insertion insertion = TellomiDiscoverMention.insert("hi @ab", query, "alice");
        assertEquals("hi @alice ", insertion.text);
        assertEquals(insertion.text.length(), insertion.caret);
        assertNull(TellomiDiscoverMention.queryAt("hi @alice ", insertion.caret));
        assertNull(TellomiDiscoverMention.insert("hi @ab", query, "ab"));
        assertNull(TellomiDiscoverMention.queryAt("mail a@abcd", 11));
        TellomiDiscoverMention.Query typed = TellomiDiscoverMention.queryAt("@", 1);
        assertEquals("", typed.prefix);
        TellomiDiscoverMention.Insertion at = TellomiDiscoverMention.insert("@", typed, "abcd");
        assertEquals("@abcd ", at.text);
    }

    @Test
    public void suggestionsUseLocalUsernames() {
        java.util.List<TellomiDiscoverMention.Person> people = Arrays.asList(
            new TellomiDiscoverMention.Person("Alice", "李雷"),
            new TellomiDiscoverMention.Person("alice", "重复"),
            new TellomiDiscoverMention.Person("bobby", "Bob"),
            new TellomiDiscoverMention.Person("ab", "太短"),
            new TellomiDiscoverMention.Person("cara", "李娜")
        );
        java.util.List<TellomiDiscoverMention.Person> matched = TellomiDiscoverMention.suggestions(people, "al", 6);
        assertEquals(1, matched.size());
        assertEquals("Alice", matched.get(0).username);
        assertEquals("李雷", matched.get(0).name);
        java.util.List<TellomiDiscoverMention.Person> byName = TellomiDiscoverMention.suggestions(people, "李", 6);
        assertEquals(2, byName.size());
        assertEquals("Alice", byName.get(0).username);
        assertEquals("cara", byName.get(1).username);
        assertEquals(3, TellomiDiscoverMention.suggestions(people, "", 6).size());
        assertTrue(TellomiDiscoverMention.suggestions(null, "a", 6).isEmpty());
    }

    @Test
    public void publishKeepsMentionsInsideText() {
        byte[] body = TellomiDiscover.publishBody("hi @abcd", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, null);
        TellomiJson object = TellomiJson.parse(body);
        assertEquals(new HashSet<String>(Arrays.asList("kind", "text", "visibility", "client_nonce")), object.objectValue.keySet());
        assertEquals("hi @abcd", object.objectValue.get("text").stringValue);
        assertNull(object.objectValue.get("mentions"));
        assertNull(object.objectValue.get("entities"));
    }

    @Test
    public void copyUsesTheDiscoverPack() {
        assertEquals("提及", TellomiPack.bundled("Tellomi.Discover.Mention", "zh-Hans"));
        assertEquals("写点什么，用 @ 提及用户", TellomiPack.bundled("Tellomi.Discover.MentionHint", "zh-Hans"));
        assertEquals("%s 不是有效用户名，对方收不到提及。", TellomiPack.bundled("Tellomi.Discover.MentionInvalid", "zh"));
        assertEquals("No matching username", TellomiPack.bundled("Tellomi.Discover.MentionEmpty", "en"));
    }

    private static String repeat(char ch, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append(ch);
        }
        return builder.toString();
    }
}
